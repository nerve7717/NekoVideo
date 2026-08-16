package com.neko.video.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import com.neko.video.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.Closeable
import java.io.InputStream
import java.util.EnumSet

data class RemoteListEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modifiedTime: Long = 0L,
)

interface RemoteFileHandle : Closeable {
    val length: Long
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
    fun seek(position: Long)
}

object RemoteClients {
    suspend fun test(config: RemoteServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            list(config, config.initialPath.ifBlank { "/" }, videosOnly = false).getOrThrow()
            Unit
        }
    }

    suspend fun list(
        config: RemoteServerConfig,
        path: String,
        videosOnly: Boolean,
    ): Result<List<MediaItemInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = when (config.protocol) {
                RemoteProtocol.SMB -> listSmb(config, path)
                RemoteProtocol.SFTP -> listSftp(config, path)
                RemoteProtocol.FTP -> listFtp(config, path)
            }
            toMediaItems(config, entries, videosOnly)
        }
    }

    fun openFile(config: RemoteServerConfig, path: String, position: Long = 0L): RemoteFileHandle {
        return when (config.protocol) {
            RemoteProtocol.SMB -> openSmb(config, path, position)
            RemoteProtocol.SFTP -> openSftp(config, path, position)
            RemoteProtocol.FTP -> openFtp(config, path, position)
        }
    }

    fun buildPlaybackUri(config: RemoteServerConfig, path: String): String {
        // Use path() so spaces / non-ASCII are percent-encoded; encodedPath() requires
        // an already-encoded value and breaks Chinese / spaced filenames.
        return android.net.Uri.Builder()
            .scheme(config.protocol.scheme)
            .encodedAuthority(config.id)
            .path(normalizePath(path))
            .build()
            .toString()
    }

    fun parsePlaybackUri(uri: String): Pair<String, String>? {
        val parsed = android.net.Uri.parse(uri)
        val protocol = RemoteProtocol.fromScheme(parsed.scheme) ?: return null
        val serverId = parsed.host ?: return null
        val path = android.net.Uri.decode(parsed.path.orEmpty()).ifBlank { "/" }
        // silence unused for smart cast clarity
        @Suppress("UNUSED_VARIABLE")
        val p = protocol
        return serverId to path
    }

    private fun toMediaItems(
        config: RemoteServerConfig,
        entries: List<RemoteListEntry>,
        videosOnly: Boolean,
    ): List<MediaItemInfo> {
        val folders = entries.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val files = entries.filter { !it.isDirectory }
        val videos = if (videosOnly) {
            files.filter { FormatUtils.isVideoFile(it.name, null) }
        } else {
            files
        }.sortedBy { it.name.lowercase() }

        fun mapEntry(entry: RemoteListEntry): MediaItemInfo {
            val playbackUri = buildPlaybackUri(config, entry.path)
            return MediaItemInfo(
                id = "${config.protocol.name.lowercase()}_${config.id}_${entry.path.hashCode()}",
                name = entry.name,
                uri = playbackUri,
                path = entry.path,
                sourceType = config.protocol.sourceType,
                size = entry.size,
                modifiedTime = entry.modifiedTime,
                isDirectory = entry.isDirectory,
                serverId = config.id,
            )
        }
        return folders.map(::mapEntry) + videos.map(::mapEntry)
    }

    private fun normalizePath(path: String): String {
        val trimmed = path.trim().ifBlank { "/" }
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    private fun joinPath(parent: String, name: String): String {
        val base = parent.trimEnd('/')
        return when {
            base.isEmpty() || base == "/" -> "/$name"
            else -> "$base/$name"
        }
    }

    // region SMB
    private fun listSmb(config: RemoteServerConfig, path: String): List<RemoteListEntry> {
        require(config.share.isNotBlank()) { "SMB share is required" }
        return withSmbShare(config) { share ->
            val remotePath = smbRelative(path)
            share.list(remotePath).mapNotNull { file ->
                val name = file.fileName
                if (name == "." || name == "..") return@mapNotNull null
                val full = joinPath(normalizePath(path), name)
                val attrs = runCatching { file.fileAttributes }.getOrDefault(0L)
                RemoteListEntry(
                    name = name,
                    path = full,
                    isDirectory = (attrs.toLong() and 0x10L) != 0L,
                    size = file.endOfFile,
                    modifiedTime = runCatching { file.lastWriteTime.toEpochMillis() }.getOrDefault(0L),
                )
            }
        }
    }

    private fun openSmb(config: RemoteServerConfig, path: String, position: Long): RemoteFileHandle {
        val client = SMBClient()
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null
        try {
            connection = client.connect(config.host, config.effectivePort)
            session = connection.authenticate(
                AuthenticationContext(
                    config.username.ifBlank { "Guest" },
                    config.password.toCharArray(),
                    config.domain,
                ),
            )
            share = session.connectShare(config.share) as DiskShare
            val file = share.openFile(
                smbRelative(path),
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            var fileOffset = position.coerceAtLeast(0L)
            val ownedShare = share
            val ownedSession = session
            val ownedConnection = connection
            share = null
            session = null
            connection = null
            return object : RemoteFileHandle {
                override val length: Long = file.fileInformation.standardInformation.endOfFile
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (fileOffset >= this.length) return -1
                    val read = file.read(buffer, fileOffset, offset, length)
                    if (read > 0) fileOffset += read.toLong()
                    return read
                }

                override fun seek(position: Long) {
                    fileOffset = position.coerceAtLeast(0L)
                }

                override fun close() {
                    runCatching { file.close() }
                    runCatching { ownedShare.close() }
                    runCatching { ownedSession.close() }
                    runCatching { ownedConnection.close() }
                    runCatching { client.close() }
                }
            }
        } catch (t: Throwable) {
            runCatching { share?.close() }
            runCatching { session?.close() }
            runCatching { connection?.close() }
            runCatching { client.close() }
            throw t
        }
    }

    private fun <T> withSmbShare(config: RemoteServerConfig, block: (DiskShare) -> T): T {
        SMBClient().use { client ->
            client.connect(config.host, config.effectivePort).use { connection: Connection ->
                val session: Session = connection.authenticate(
                    AuthenticationContext(
                        config.username.ifBlank { "Guest" },
                        config.password.toCharArray(),
                        config.domain,
                    ),
                )
                session.use {
                    (session.connectShare(config.share) as DiskShare).use { share ->
                        return block(share)
                    }
                }
            }
        }
    }

    private fun smbRelative(path: String): String =
        path.trim('/').replace('/', '\\')
    // endregion

    // region SFTP
    private fun listSftp(config: RemoteServerConfig, path: String): List<RemoteListEntry> {
        return withSftp(config) { sftp ->
            val target = normalizePath(path)
            sftp.ls(target).mapNotNull { info: RemoteResourceInfo ->
                val name = info.name
                if (name == "." || name == "..") return@mapNotNull null
                RemoteListEntry(
                    name = name,
                    path = joinPath(target, name),
                    isDirectory = info.attributes.type == FileMode.Type.DIRECTORY,
                    size = info.attributes.size,
                    modifiedTime = info.attributes.mtime * 1000L,
                )
            }
        }
    }

    private fun openSftp(config: RemoteServerConfig, path: String, position: Long): RemoteFileHandle {
        val ssh = SSHClient()
        var sftp: net.schmizz.sshj.sftp.SFTPClient? = null
        try {
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connect(config.host, config.effectivePort)
            authSftp(ssh, config)
            sftp = ssh.newSFTPClient()
            val remote = sftp.open(normalizePath(path))
            // Native offset open — InputStream.skip() is unreliable for seek/resume.
            val file = remote.RemoteFileInputStream(position.coerceAtLeast(0L))
            val size = remote.length()
            val ownedSftp = sftp
            sftp = null
            return object : RemoteFileHandle {
                override val length: Long = size
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    file.read(buffer, offset, length)

                override fun seek(position: Long) {
                    throw UnsupportedOperationException("Reopen required for SFTP seek")
                }

                override fun close() {
                    runCatching { file.close() }
                    runCatching { remote.close() }
                    runCatching { ownedSftp.close() }
                    runCatching { ssh.disconnect() }
                }
            }
        } catch (t: Throwable) {
            runCatching { sftp?.close() }
            runCatching { if (ssh.isConnected) ssh.disconnect() }
            throw t
        }
    }

    private fun authSftp(ssh: SSHClient, config: RemoteServerConfig) {
        if (config.username.isNotBlank()) {
            ssh.authPassword(config.username, config.password)
        } else {
            ssh.authPassword("anonymous", config.password.ifBlank { "anonymous@" })
        }
    }

    private fun <T> withSftp(config: RemoteServerConfig, block: (net.schmizz.sshj.sftp.SFTPClient) -> T): T {
        SSHClient().use { ssh ->
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connect(config.host, config.effectivePort)
            authSftp(ssh, config)
            ssh.newSFTPClient().use { sftp ->
                return block(sftp)
            }
        }
    }
    // endregion

    // region FTP
    private fun listFtp(config: RemoteServerConfig, path: String): List<RemoteListEntry> {
        return withFtp(config) { ftp ->
            val target = normalizePath(path)
            val files = ftp.listFiles(target) ?: emptyArray()
            files.mapNotNull { file: FTPFile ->
                val name = file.name ?: return@mapNotNull null
                if (name == "." || name == "..") return@mapNotNull null
                RemoteListEntry(
                    name = name,
                    path = joinPath(target, name),
                    isDirectory = file.isDirectory,
                    size = file.size,
                    modifiedTime = file.timestamp?.time?.time ?: 0L,
                )
            }
        }
    }

    private fun openFtp(config: RemoteServerConfig, path: String, position: Long): RemoteFileHandle {
        val ftp = FTPClient()
        ftp.connectTimeout = 20_000
        try {
            ftp.connect(config.host, config.effectivePort)
            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
                throw IllegalStateException("FTP connect failed: ${ftp.replyString}")
            }
            val user = config.username.ifBlank { "anonymous" }
            val pass = config.password.ifBlank { "anonymous@" }
            if (!ftp.login(user, pass)) {
                throw IllegalStateException("FTP login failed: ${ftp.replyString}")
            }
            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            val remotePath = normalizePath(path)
            // SIZE must run before retrieveFileStream — during transfer it corrupts the control channel.
            val size = runCatching {
                val reply = ftp.sendCommand("SIZE", remotePath)
                if (FTPReply.isPositiveCompletion(reply)) {
                    ftp.replyString.trim().substringAfterLast(' ').toLong()
                } else {
                    -1L
                }
            }.getOrDefault(-1L)
            if (position > 0) {
                ftp.setRestartOffset(position)
            }
            val stream: InputStream = ftp.retrieveFileStream(remotePath)
                ?: throw IllegalStateException("FTP open failed: ${ftp.replyString}")

            return object : RemoteFileHandle {
                override val length: Long = size
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    stream.read(buffer, offset, length)

                override fun seek(position: Long) {
                    throw UnsupportedOperationException("Reopen required for FTP seek")
                }

                override fun close() {
                    runCatching { stream.close() }
                    runCatching { ftp.completePendingCommand() }
                    runCatching { ftp.logout() }
                    runCatching { ftp.disconnect() }
                }
            }
        } catch (t: Throwable) {
            runCatching { ftp.logout() }
            runCatching { if (ftp.isConnected) ftp.disconnect() }
            throw t
        }
    }

    private fun <T> withFtp(config: RemoteServerConfig, block: (FTPClient) -> T): T {
        val ftp = FTPClient()
        ftp.connectTimeout = 20_000
        try {
            ftp.connect(config.host, config.effectivePort)
            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
                throw IllegalStateException("FTP connect failed: ${ftp.replyString}")
            }
            val user = config.username.ifBlank { "anonymous" }
            val pass = config.password.ifBlank { "anonymous@" }
            if (!ftp.login(user, pass)) {
                throw IllegalStateException("FTP login failed: ${ftp.replyString}")
            }
            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            return block(ftp)
        } finally {
            runCatching { ftp.logout() }
            runCatching { if (ftp.isConnected) ftp.disconnect() }
        }
    }
    // endregion
}
