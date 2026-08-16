package com.neko.video.remote

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Streams SMB/SFTP/FTP files into Media3 via custom neko-* URI schemes.
 */
@UnstableApi
class RemoteFileDataSource(
    private val resolver: (serverId: String) -> RemoteServerConfig?,
) : BaseDataSource(/* isNetwork= */ true) {
    private var handle: RemoteFileHandle? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val parsed = RemoteClients.parsePlaybackUri(dataSpec.uri.toString())
            ?: throw IOException("Unsupported remote URI: ${dataSpec.uri}")
        val (serverId, path) = parsed
        val config = resolver(serverId)
            ?: throw IOException("Remote server not found: $serverId")
        val position = dataSpec.position
        val openedHandle = try {
            RemoteClients.openFile(config, path, position)
        } catch (t: Throwable) {
            throw IOException("Failed to open remote file: $path", t)
        }
        handle = openedHandle
        uri = dataSpec.uri
        val length = openedHandle.length
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            length >= 0 -> (length - position).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        val read = try {
            handle?.read(buffer, offset, toRead) ?: -1
        } catch (t: Throwable) {
            throw IOException("Remote read failed", t)
        }
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read
        }
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        if (opened) {
            transferEnded()
            opened = false
        }
        runCatching { handle?.close() }
        handle = null
        bytesRemaining = 0
    }

    class Factory(
        private val resolver: (serverId: String) -> RemoteServerConfig?,
    ) : DataSource.Factory {
        private var listener: TransferListener? = null

        fun setTransferListener(transferListener: TransferListener?): Factory {
            listener = transferListener
            return this
        }

        override fun createDataSource(): DataSource {
            val source = RemoteFileDataSource(resolver)
            listener?.let { source.addTransferListener(it) }
            return source
        }
    }
}

/** Routes neko-smb/sftp/ftp URIs to [remote], everything else to [fallback]. */
@UnstableApi
class RoutingDataSourceFactory(
    private val fallback: DataSource.Factory,
    private val remote: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return RoutingDataSource(fallback.createDataSource(), remote.createDataSource())
    }
}

@UnstableApi
private class RoutingDataSource(
    private val fallback: DataSource,
    private val remote: DataSource,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        fallback.addTransferListener(transferListener)
        remote.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        active = if (RemoteProtocol.fromScheme(dataSpec.uri.scheme) != null) remote else fallback
        return active!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = active?.uri

    override fun close() {
        runCatching { active?.close() }
        active = null
    }
}
