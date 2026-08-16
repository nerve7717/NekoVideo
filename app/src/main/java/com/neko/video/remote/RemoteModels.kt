package com.neko.video.remote

import com.neko.video.model.SourceType

enum class RemoteProtocol {
    SMB,
    SFTP,
    FTP,
    ;

    val sourceType: SourceType
        get() = when (this) {
            SMB -> SourceType.SMB
            SFTP -> SourceType.SFTP
            FTP -> SourceType.FTP
        }

    val defaultPort: Int
        get() = when (this) {
            SMB -> 445
            SFTP -> 22
            FTP -> 21
        }

    val scheme: String
        get() = when (this) {
            SMB -> "neko-smb"
            SFTP -> "neko-sftp"
            FTP -> "neko-ftp"
        }

    companion object {
        fun fromSourceType(type: SourceType): RemoteProtocol? = when (type) {
            SourceType.SMB -> SMB
            SourceType.SFTP -> SFTP
            SourceType.FTP -> FTP
            else -> null
        }

        fun fromScheme(scheme: String?): RemoteProtocol? = when (scheme?.lowercase()) {
            "neko-smb", "smb" -> SMB
            "neko-sftp", "sftp" -> SFTP
            "neko-ftp", "ftp" -> FTP
            else -> null
        }
    }
}

data class RemoteServerConfig(
    val id: String,
    val protocol: RemoteProtocol,
    val name: String,
    val host: String,
    val port: Int,
    /** SMB share name; unused for SFTP/FTP. */
    val share: String = "",
    val initialPath: String = "/",
    val username: String = "",
    val password: String = "",
    /** SMB domain / workgroup. */
    val domain: String = "",
) {
    val effectivePort: Int get() = if (port > 0) port else protocol.defaultPort

    fun displayEndpoint(): String = buildString {
        append(host)
        if (port > 0 && port != protocol.defaultPort) append(":$port")
        when (protocol) {
            RemoteProtocol.SMB -> if (share.isNotBlank()) append("/$share")
            RemoteProtocol.SFTP, RemoteProtocol.FTP -> {
                val path = initialPath.trim().ifBlank { "/" }
                if (path != "/") append(path)
            }
        }
    }
}
