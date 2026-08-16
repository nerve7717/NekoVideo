package com.neko.video.webdav

import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import com.neko.video.model.SubtitleCandidate
import com.neko.video.network.AppError
import com.neko.video.network.NetworkErrorMapper
import com.neko.video.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WebDavServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val userAgent: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

class WebDavClient(
    private val timeoutSeconds: Int = 20,
) {
    suspend fun testConnection(config: WebDavServerConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                listDirectory(config, "/", videosOnly = false).getOrThrow()
                Unit
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(mapException(it)) },
            )
        }

    suspend fun listDirectory(
        config: WebDavServerConfig,
        path: String,
        videosOnly: Boolean = true,
    ): Result<List<MediaItemInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val target = resolvePath(config.url, path)
            val client = createClient(config)
            val propfindBody = """
                <?xml version="1.0" encoding="utf-8" ?>
                <d:propfind xmlns:d="DAV:">
                  <d:prop>
                    <d:displayname/>
                    <d:getcontentlength/>
                    <d:getcontenttype/>
                    <d:getlastmodified/>
                    <d:resourcetype/>
                  </d:prop>
                </d:propfind>
            """.trimIndent()

            val requestBuilder = Request.Builder()
                .url(if (target.endsWith("/") || !looksLikeFile(target)) ensureTrailingSlash(target) else target)
                .method(
                    "PROPFIND",
                    propfindBody.toRequestBody("application/xml; charset=utf-8".toMediaType()),
                )
                .header("Depth", "1")
                .header("Accept", "*/*")

            applyAuthAndHeaders(requestBuilder, config)

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw WebDavException(NetworkErrorMapper.fromHttpCode(response.code))
                }
                val body = response.body?.string().orEmpty()
                val resources = parsePropfind(body, config.url)
                val currentNormalized = normalizeComparable(target)
                val items = resources.mapNotNull { resource ->
                        val absolute = toAbsoluteUrl(config.url, resource.href)
                        // Never show the current folder itself in the listing.
                        if (normalizeComparable(absolute) == currentNormalized) return@mapNotNull null
                        if (normalizeComparable(resource.href) == currentNormalized) return@mapNotNull null
                        val name = resource.displayName?.takeIf { it.isNotBlank() }
                            ?: absolute.trimEnd('/').substringAfterLast('/')
                            .let { android.net.Uri.decode(it) }
                        MediaItemInfo(
                            id = "webdav_${config.id}_${absolute.hashCode()}",
                            name = name,
                            uri = absolute,
                            path = absolute,
                            sourceType = SourceType.WEBDAV,
                            size = resource.contentLength,
                            modifiedTime = resource.modifiedTime,
                            mimeType = resource.contentType,
                            isDirectory = resource.isDirectory,
                            serverId = config.id,
                            headers = buildPlaybackHeaders(config),
                        )
                    }

                val files = items.filter { !it.isDirectory }
                val folders = items.filter { it.isDirectory }
                val videoFiles = if (videosOnly) {
                    files.filter { FormatUtils.isVideoFile(it.name, it.mimeType) }
                } else {
                    files
                }
                val subtitleFiles = files.filter { FormatUtils.isSubtitleFile(it.name) }
                val enrichedVideos = videoFiles.map { video ->
                    val base = video.name.substringBeforeLast('.', video.name)
                    val subs = subtitleFiles
                        .filter { it.name.startsWith(base, ignoreCase = true) }
                        .map {
                            SubtitleCandidate(
                                name = it.name,
                                uri = it.uri,
                                mimeType = it.mimeType,
                            )
                        }
                    video.copy(subtitleCandidates = subs)
                }

                folders.sortedBy { it.name.lowercase() } +
                    enrichedVideos.sortedBy { it.name.lowercase() }
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(mapException(it)) },
        )
    }

    fun buildPlaybackHeaders(config: WebDavServerConfig): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        headers.putAll(config.headers)
        if (config.username.isNotBlank()) {
            headers["Authorization"] = Credentials.basic(config.username, config.password)
        }
        config.userAgent?.takeIf { it.isNotBlank() }?.let {
            headers["User-Agent"] = it
        }
        return headers
    }

    private fun createClient(config: WebDavServerConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun applyAuthAndHeaders(builder: Request.Builder, config: WebDavServerConfig) {
        if (config.username.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password))
        }
        config.userAgent?.takeIf { it.isNotBlank() }?.let {
            builder.header("User-Agent", it)
        }
        config.headers.forEach { (k, v) ->
            if (!k.equals("Authorization", ignoreCase = true)) {
                builder.header(k, v)
            }
        }
    }

    private data class DavResource(
        val href: String,
        val displayName: String?,
        val contentLength: Long,
        val contentType: String?,
        val modifiedTime: Long,
        val isDirectory: Boolean,
    )

    private fun parsePropfind(xml: String, baseUrl: String): List<DavResource> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val result = mutableListOf<DavResource>()
        var event = parser.eventType
        var href: String? = null
        var displayName: String? = null
        val contentLength = LongHolder()
        var contentType: String? = null
        var modifiedTime = 0L
        var isDirectory = false
        var inResponse = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.lowercase(Locale.US)
                    when (name) {
                        "response" -> {
                            inResponse = true
                            href = null
                            displayName = null
                            contentLength.value = 0L
                            contentType = null
                            modifiedTime = 0L
                            isDirectory = false
                        }
                        "href" -> if (inResponse) href = parser.nextText()
                        "displayname" -> if (inResponse) displayName = parser.nextText()
                        "getcontentlength" -> if (inResponse) {
                            contentLength.value = parser.nextText().toLongOrNull() ?: 0L
                        }
                        "getcontenttype" -> if (inResponse) contentType = parser.nextText()
                        "getlastmodified" -> if (inResponse) {
                            modifiedTime = parseHttpDate(parser.nextText())
                        }
                        "collection" -> if (inResponse) isDirectory = true
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name?.lowercase(Locale.US) == "response" && inResponse) {
                        val resolvedHref = href ?: continue
                        result += DavResource(
                            href = resolvedHref,
                            displayName = displayName,
                            contentLength = contentLength.value,
                            contentType = contentType,
                            modifiedTime = modifiedTime,
                            isDirectory = isDirectory || resolvedHref.endsWith("/"),
                        )
                        inResponse = false
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private class LongHolder(var value: Long = 0L)

    private fun parseHttpDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
        )
        for (pattern in formats) {
            runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                return sdf.parse(value)?.time ?: 0L
            }
        }
        return 0L
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private fun looksLikeFile(url: String): Boolean {
        val last = url.substringAfterLast('/')
        return last.contains('.')
    }

    private fun resolvePath(baseUrl: String, path: String): String {
        if (path.isBlank() || path == "/") return normalizeUrl(baseUrl)
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return URI(normalizeUrl(baseUrl)).resolve(path.removePrefix("/")).toString()
    }

    private fun toAbsoluteUrl(baseUrl: String, href: String): String {
        return if (href.startsWith("http://") || href.startsWith("https://")) {
            href
        } else {
            URI(normalizeUrl(baseUrl)).resolve(href).toString()
        }
    }

    private fun normalizeComparable(url: String): String =
        url.trimEnd('/').lowercase(Locale.US)

    private fun mapException(throwable: Throwable): Exception {
        if (throwable is WebDavException) return throwable
        val appError = when {
            throwable.message?.contains("401") == true -> AppError.Auth()
            throwable.message?.contains("403") == true -> AppError.Forbidden()
            throwable.message?.contains("404") == true -> AppError.NotFound()
            else -> NetworkErrorMapper.fromThrowable(throwable)
        }
        return WebDavException(appError, throwable)
    }
}

class WebDavException(
    val appError: AppError,
    cause: Throwable? = null,
) : Exception(appError.details ?: appError.toString(), cause)
