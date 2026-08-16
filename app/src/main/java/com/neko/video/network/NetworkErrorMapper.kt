package com.neko.video.network

import android.content.Context
import com.neko.video.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

sealed class AppError(open val messageRes: Int, open val details: String? = null) {
    data class Network(override val details: String? = null) :
        AppError(R.string.error_network, details)

    data class Timeout(override val details: String? = null) :
        AppError(R.string.error_timeout, details)

    data class Ssl(override val details: String? = null) :
        AppError(R.string.error_ssl, details)

    data class Auth(override val details: String? = null) :
        AppError(R.string.error_auth, details)

    data class NotFound(override val details: String? = null) :
        AppError(R.string.error_not_found, details)

    data class Forbidden(override val details: String? = null) :
        AppError(R.string.error_forbidden, details)

    data class Dns(override val details: String? = null) :
        AppError(R.string.error_dns, details)

    data class Connection(override val details: String? = null) :
        AppError(R.string.error_connection, details)

    data class Unsupported(override val details: String? = null) :
        AppError(R.string.error_unsupported, details)

    data class Decoder(override val details: String? = null) :
        AppError(R.string.error_decoder, details)

    data class Generic(override val details: String? = null) :
        AppError(R.string.error_generic, details)
}

object NetworkErrorMapper {
    fun fromThrowable(throwable: Throwable): AppError {
        var current: Throwable? = throwable
        while (current != null) {
            when (current) {
                is SocketTimeoutException -> return AppError.Timeout(current.message)
                is UnknownHostException -> return AppError.Dns(current.message)
                is ConnectException -> return AppError.Connection(current.message)
                is SSLHandshakeException, is CertificateException, is SSLException ->
                    return AppError.Ssl(current.message)
            }
            current = current.cause
        }
        return AppError.Generic(throwable.message)
    }

    fun fromHttpCode(code: Int, message: String? = null): AppError {
        return when (code) {
            401 -> AppError.Auth(message)
            403 -> AppError.Forbidden(message)
            404 -> AppError.NotFound(message)
            else -> AppError.Network("HTTP $code${message?.let { ": $it" } ?: ""}")
        }
    }

    fun toUserMessage(context: Context, error: AppError): String {
        val base = context.getString(error.messageRes)
        return if (error is AppError.Generic && !error.details.isNullOrBlank()) {
            context.getString(R.string.error_generic, error.details)
        } else {
            base
        }
    }
}
