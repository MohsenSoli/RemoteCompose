package com.mohsen.rcclient

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess

/** Viewport in dp plus density, sent to the server so documents fit exactly. */
data class ViewportDp(val width: Int, val height: Int, val density: Float)

class RemoteUiRepo(
    private val baseUrl: String,
    private val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
    },
) {
    suspend fun usersList(viewport: ViewportDp, waves: Int, favorites: Set<Int>): ByteArray {
        val favs = favorites.sorted().joinToString(",")
        return getBytes("$baseUrl/ui/users?${viewport.query()}&waves=$waves&favs=$favs")
    }

    suspend fun userDetail(id: Int, viewport: ViewportDp, favorite: Boolean): ByteArray =
        getBytes("$baseUrl/ui/users/$id?${viewport.query()}&fav=${if (favorite) 1 else 0}")

    private fun ViewportDp.query() = "w=$width&h=$height&d=$density"

    private suspend fun getBytes(url: String): ByteArray {
        val response = client.get(url)
        check(response.status.isSuccess()) { "HTTP ${response.status.value} for $url" }
        val bytes = response.readRawBytes()
        Log.d(TAG, "GET $url -> ${response.status.value}, ${bytes.size} bytes")
        return bytes
    }

    private companion object {
        const val TAG = "RemoteUiRepo"
    }
}
