package com.wakesync.network

import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class BackendClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var fakeChecker: FakeNetworkConnectivityChecker
    private lateinit var backendClient: BackendClient

    class FakeNetworkConnectivityChecker(var isValidated: Boolean = true) : NetworkConnectivityChecker {
        override fun isNetworkValidated(): Boolean = isValidated
    }

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        val baseUrl = mockServer.url("/").toString()
        val appToken = "test-token"
        fakeChecker = FakeNetworkConnectivityChecker(isValidated = true)

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .writeTimeout(500, TimeUnit.MILLISECONDS)
            .build()

        backendClient = BackendClient(
            connectivityChecker = fakeChecker,
            baseUrl = baseUrl,
            appToken = appToken,
            client = okHttpClient
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `autocomplete returns Success when server responds 200 with valid predictions`() = runBlocking {
        val json = """
            {
                "predictions": [
                    {
                        "placeId": "ChIJ111",
                        "primaryText": "Universidad Cooperativa",
                        "secondaryText": "Medellin, Antioquia"
                    }
                ]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = backendClient.autocomplete("universidad", "token-uuid")

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(1, data.size)
        assertEquals("ChIJ111", data[0].placeId)
        assertEquals("Universidad Cooperativa", data[0].primaryText)
        assertEquals("Medellin, Antioquia", data[0].secondaryText)

        val recorded = mockServer.takeRequest()
        assertEquals("test-token", recorded.getHeader("X-WakeSync-App-Token"))
        assertEquals("/v1/places/autocomplete", recorded.path)
    }

    @Test
    fun `getPlaceDetails returns Success when server responds 200 with details`() = runBlocking {
        val json = """
            {
                "placeId": "ChIJ111",
                "name": "Universidad Cooperativa de Colombia",
                "address": "Calle 50 #45, Medellin",
                "lat": 6.248,
                "lng": -75.568
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = backendClient.getPlaceDetails("ChIJ111", "token-uuid")

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("ChIJ111", data.placeId)
        assertEquals("Universidad Cooperativa de Colombia", data.name)
        assertEquals(6.248, data.lat, 0.0001)
        assertEquals(-75.568, data.lng, 0.0001)
    }

    @Test
    fun `reverseGeocode returns Success when server responds 200`() = runBlocking {
        val json = """
            {
                "name": "Calle 50 #45-20",
                "address": "Calle 50 #45-20, Medellin"
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = backendClient.reverseGeocode(6.2518, -75.5684)

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("Calle 50 #45-20", data.name)
        assertEquals("Calle 50 #45-20, Medellin", data.address)
    }

    @Test
    fun `getStaticMap returns Success with byte array on 200`() = runBlocking {
        val dummyPng = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(dummyPng))
        )

        val result = backendClient.getStaticMap(6.25, -75.56, 16, 227)

        assertTrue(result is ApiResult.Success)
        val bytes = (result as ApiResult.Success).data
        assertEquals(dummyPng.size, bytes.size)
        assertEquals(137.toByte(), bytes[0])
    }

    @Test
    fun `requestInsight returns Success with text on 200`() = runBlocking {
        val json = """
            {
                "insight": "Completaste tu descanso satisfactoriamente."
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = backendClient.requestInsight("NAP", 1200, 180, "COMPLETED")

        assertTrue(result is ApiResult.Success)
        val text = (result as ApiResult.Success).data
        assertEquals("Completaste tu descanso satisfactoriamente.", text)

        val recorded = mockServer.takeRequest()
        assertEquals("/v1/insights", recorded.path)
    }

    @Test
    fun `server 401 returns ApiResult HttpError 401`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))

        val result = backendClient.autocomplete("test", "uuid")
        assertTrue(result is ApiResult.HttpError)
        assertEquals(401, (result as ApiResult.HttpError).code)
    }

    @Test
    fun `server 429 returns ApiResult HttpError 429`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited"}"""))

        val result = backendClient.autocomplete("test", "uuid")
        assertTrue(result is ApiResult.HttpError)
        assertEquals(429, (result as ApiResult.HttpError).code)
    }

    @Test
    fun `server 504 returns ApiResult Timeout`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(504).setBody("""{"error":"upstream_timeout"}"""))

        val result = backendClient.autocomplete("test", "uuid")
        assertTrue(result is ApiResult.Timeout)
    }

    @Test
    fun `socket timeout returns ApiResult Timeout`() = runBlocking {
        mockServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val result = backendClient.autocomplete("test", "uuid")
        assertTrue(result is ApiResult.Timeout)
    }

    @Test
    fun `malformed JSON returns ApiResult ParseError`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("not a json at all"))

        val result = backendClient.autocomplete("test", "uuid")
        assertTrue(result is ApiResult.ParseError)
    }

    @Test
    fun `network without validation returns NetworkUnavailable immediately without network call`() = runBlocking {
        fakeChecker.isValidated = false

        val result = backendClient.autocomplete("test", "uuid")

        assertTrue(result is ApiResult.NetworkUnavailable)
        assertEquals(0, mockServer.requestCount)
    }
}
