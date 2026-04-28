package com.recomo.user.data.media

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

@Singleton
class UserOrinMediaClient @Inject constructor(
    json: Json
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        engine {
            proxy = null
        }
    }

    suspend fun listMedia(
        baseUrl: String,
        page: Int = 0,
        pageSize: Int = 120,
        filter: UserMediaFilter? = null
    ): Result<UserMediaListResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get("${baseUrl.trimEnd('/')}/media/list") {
                parameter("page", page)
                parameter("pageSize", pageSize)
                filter?.let {
                    it.type?.let { type -> parameter("type", type.name.lowercase()) }
                    parameter("sortBy", it.sortBy.name.lowercase())
                    parameter("sortOrder", it.sortOrder.name.lowercase())
                }
            }
            check(response.status.isSuccess()) {
                "HTTP ${response.status.value}: ${response.status.description}"
            }
            response.body<UserMediaListResponse>().let { mediaList ->
                mediaList.copy(
                    items = mediaList.items.map { item ->
                        item.copy(
                            thumbnailUrl = item.thumbnailUrl?.let { resolveUrl(baseUrl, it) },
                            downloadUrl = resolveUrl(baseUrl, item.downloadUrl)
                        )
                    }
                )
            }
        }
    }

    fun downloadMedia(
        downloadUrl: String,
        destination: File
    ): Flow<Float> = flow {
        client.get(downloadUrl).apply {
            check(status.isSuccess()) {
                "HTTP ${status.value}: ${status.description}"
            }
            val contentLength = max(headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L, -1L)
            val channel = bodyAsChannel()
            destination.parentFile?.mkdirs()
            destination.outputStream().use { output ->
                val buffer = ByteArray(8_192)
                var totalBytesDownloaded = 0L
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    totalBytesDownloaded += bytesRead
                    if (contentLength > 0) {
                        emit(totalBytesDownloaded.toFloat() / contentLength.toFloat())
                    }
                }
                output.flush()
            }
        }
        emit(1f)
    }

    private fun resolveUrl(baseUrl: String, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val normalizedBase = baseUrl.trimEnd('/')
        val normalizedPath = if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        return "$normalizedBase$normalizedPath"
    }
}
