package com.wakesync.network

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.wakesync.BuildConfig
import com.wakesync.core.places.PlacePrediction
import com.wakesync.network.model.AutocompleteRequestDto
import com.wakesync.network.model.AutocompleteResponseDto
import com.wakesync.network.model.InsightRequestDto
import com.wakesync.network.model.InsightResponseDto
import com.wakesync.network.model.LocationBiasDto
import com.wakesync.network.model.PlaceDetailsDto
import com.wakesync.network.model.ReverseGeocodeDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Sole network gateway client for the WakeSync application (RF-NET-01 to RF-NET-04).
 * Uses OkHttp and kotlinx.serialization to communicate strictly with the wakesync-gateway worker.
 */
open class BackendClient(
    private val connectivityChecker: NetworkConnectivityChecker = object : NetworkConnectivityChecker {
        override fun isNetworkValidated(): Boolean = true
    },
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL,
    private val appToken: String = BuildConfig.BACKEND_APP_TOKEN,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {
    constructor(context: Context) : this(
        connectivityChecker = DefaultNetworkConnectivityChecker(context)
    )

    companion object {
        private const val TAG = "BackendClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Executes autocomplete place prediction search (RF-PLC-01).
     */
    open suspend fun autocomplete(
        query: String,
        sessionToken: String,
        biasLat: Double? = null,
        biasLng: Double? = null
    ): ApiResult<List<PlacePrediction>> = withContext(Dispatchers.IO) {
        val biasDto = if (biasLat != null && biasLng != null) {
            LocationBiasDto(lat = biasLat, lng = biasLng)
        } else null

        val reqDto = AutocompleteRequestDto(
            query = query,
            sessionToken = sessionToken,
            bias = biasDto
        )

        val bodyJson = json.encodeToString(reqDto)
        val request = createRequestBuilder("/v1/places/autocomplete")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeRequest(request, "/v1/places/autocomplete") { responseBody ->
            val resDto = json.decodeFromString<AutocompleteResponseDto>(responseBody)
            resDto.predictions.map { dto ->
                PlacePrediction(
                    placeId = dto.placeId,
                    primaryText = dto.primaryText,
                    secondaryText = dto.secondaryText
                )
            }
        }
    }

    /**
     * Resolves coordinates and address details for a selected place prediction (RF-PLC-02).
     */
    open suspend fun getPlaceDetails(
        placeId: String,
        sessionToken: String
    ): ApiResult<PlaceDetailsDto> = withContext(Dispatchers.IO) {
        val path = "/v1/places/${encodePath(placeId)}?sessionToken=${encodePath(sessionToken)}"
        val request = createRequestBuilder(path).get().build()

        executeRequest(request, "/v1/places/{id}") { responseBody ->
            json.decodeFromString<PlaceDetailsDto>(responseBody)
        }
    }

    /**
     * Reverse geocodes coordinates to a human-readable street address (RF-PLC-03).
     */
    open suspend fun reverseGeocode(
        lat: Double,
        lng: Double
    ): ApiResult<ReverseGeocodeDto> = withContext(Dispatchers.IO) {
        val path = "/v1/geocode/reverse?lat=$lat&lng=$lng"
        val request = createRequestBuilder(path).get().build()

        executeRequest(request, "/v1/geocode/reverse") { responseBody ->
            json.decodeFromString<ReverseGeocodeDto>(responseBody)
        }
    }

    /**
     * Fetches static map image PNG bytes for central pin visualization (RF-PLC-03).
     */
    open suspend fun getStaticMap(
        lat: Double,
        lng: Double,
        zoom: Int,
        size: Int
    ): ApiResult<ByteArray> = withContext(Dispatchers.IO) {
        val path = "/v1/maps/static?lat=$lat&lng=$lng&zoom=$zoom&size=$size"
        val request = createRequestBuilder(path).get().build()

        if (!connectivityChecker.isNetworkValidated()) {
            logSanitized("GET", "/v1/maps/static", -1, 0)
            return@withContext ApiResult.NetworkUnavailable
        }

        val startTime = SystemClock.elapsedRealtime()
        try {
            client.newCall(request).execute().use { response ->
                val latency = SystemClock.elapsedRealtime() - startTime
                logSanitized("GET", "/v1/maps/static", response.code, latency)

                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        ApiResult.Success(bytes)
                    } else {
                        ApiResult.ParseError
                    }
                } else if (response.code == 504) {
                    ApiResult.Timeout
                } else {
                    ApiResult.HttpError(response.code)
                }
            }
        } catch (e: SocketTimeoutException) {
            val latency = SystemClock.elapsedRealtime() - startTime
            logSanitized("GET", "/v1/maps/static", 504, latency)
            ApiResult.Timeout
        } catch (e: IOException) {
            val latency = SystemClock.elapsedRealtime() - startTime
            logSanitized("GET", "/v1/maps/static", 0, latency)
            ApiResult.NetworkUnavailable
        } catch (e: Exception) {
            ApiResult.ParseError
        }
    }

    /**
     * Requests post-session AI wellness insight with strictly the 4 aggregated fields (RF-INS-01, RNF-INS-01).
     */
    open suspend fun requestInsight(
        sessionType: String,
        durationSeconds: Int,
        restLatencySeconds: Int?,
        outcome: String
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val reqDto = InsightRequestDto(
            sessionType = sessionType,
            durationSeconds = durationSeconds,
            restLatencySeconds = restLatencySeconds,
            outcome = outcome
        )

        val bodyJson = json.encodeToString(reqDto)
        val request = createRequestBuilder("/v1/insights")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeRequest(request, "/v1/insights") { responseBody ->
            val resDto = json.decodeFromString<InsightResponseDto>(responseBody)
            resDto.insight
        }
    }

    private fun createRequestBuilder(pathAndQuery: String): Request.Builder {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (pathAndQuery.startsWith('/')) pathAndQuery else "/$pathAndQuery"
        return Request.Builder()
            .url("$cleanBase$cleanPath")
            .header("X-WakeSync-App-Token", appToken)
            .header("Accept", "application/json")
    }

    private fun <T> executeRequest(
        request: Request,
        loggingPath: String,
        parser: (String) -> T
    ): ApiResult<T> {
        // Enforce network check before opening socket (RF-NET-02)
        if (!connectivityChecker.isNetworkValidated()) {
            logSanitized(request.method, loggingPath, -1, 0)
            return ApiResult.NetworkUnavailable
        }

        val startTime = SystemClock.elapsedRealtime()
        return try {
            client.newCall(request).execute().use { response ->
                val latency = SystemClock.elapsedRealtime() - startTime
                logSanitized(request.method, loggingPath, response.code, latency)

                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    try {
                        val parsed = parser(bodyString)
                        ApiResult.Success(parsed)
                    } catch (e: Exception) {
                        Log.e(TAG, "Parsing error for $loggingPath: ${e::class.simpleName}")
                        ApiResult.ParseError
                    }
                } else if (response.code == 504) {
                    ApiResult.Timeout
                } else {
                    ApiResult.HttpError(response.code)
                }
            }
        } catch (e: SocketTimeoutException) {
            val latency = SystemClock.elapsedRealtime() - startTime
            logSanitized(request.method, loggingPath, 504, latency)
            ApiResult.Timeout
        } catch (e: IOException) {
            val latency = SystemClock.elapsedRealtime() - startTime
            logSanitized(request.method, loggingPath, 0, latency)
            ApiResult.NetworkUnavailable
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in $loggingPath: ${e::class.simpleName}")
            ApiResult.ParseError
        }
    }

    /**
     * Sanitized logger: Only emits method, path, HTTP status, and latency (RF-NET-03, RNF-BE-02).
     * Never logs payloads, tokens, coordinates, or search queries.
     */
    private fun logSanitized(method: String, sanitizedPath: String, code: Int, latencyMs: Long) {
        Log.i(TAG, "$method $sanitizedPath -> $code (${latencyMs}ms)")
    }

    private fun encodePath(segment: String): String =
        java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
