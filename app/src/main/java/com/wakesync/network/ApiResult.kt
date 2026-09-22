package com.wakesync.network

/**
 * Sealed result type for all network operations executed by [BackendClient].
 * Guarantees that raw network exceptions never escape to domain or UI layers (RF-NET-03).
 */
sealed class ApiResult<out T> {
    /**
     * Successful HTTP response with parsed typed data.
     */
    data class Success<T>(val data: T) : ApiResult<T>()

    /**
     * Device has no active validated network connection (NET_CAPABILITY_VALIDATED missing).
     * Request was not dispatched to save power (RF-NET-02).
     */
    object NetworkUnavailable : ApiResult<Nothing>()

    /**
     * Request exceeded either client socket timeout or backend upstream timeout (504).
     */
    object Timeout : ApiResult<Nothing>()

    /**
     * Non-2xx HTTP status returned by the gateway (e.g., 400, 401, 429, 502).
     */
    data class HttpError(val code: Int) : ApiResult<Nothing>()

    /**
     * Response payload could not be parsed into expected data structure.
     */
    object ParseError : ApiResult<Nothing>()
}
