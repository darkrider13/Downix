package com.downix.app

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Headers every Downix request sends, so the server sees a normal phone browser asking for the file. */
object LinkHeaders {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    fun apply(builder: Request.Builder, url: String): Request.Builder {
        builder
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity") // keep Content-Length / ranges reliable
        url.toHttpUrlOrNull()?.let { builder.header("Referer", "${it.scheme}://${it.host}/") }
        return builder
    }
}

enum class ProbeStatus { OK, WARN, FAIL }

data class LinkProbe(val status: ProbeStatus, val message: String)

/** The "Connect" button in the Add download dialog: asks the server for the first byte only. */
object LinkChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun check(rawUrl: String): LinkProbe = withContext(Dispatchers.IO) {
        val url = DownloadEngine.normalizeUrl(rawUrl)
        try {
            val request = LinkHeaders.apply(Request.Builder().url(url), url)
                .header("Range", "bytes=0-0")
                .build()
            client.newCall(request).execute().use { describe(it) }
        } catch (e: UnknownHostException) {
            LinkProbe(ProbeStatus.FAIL, "Can't find this address. Check the link and your internet.")
        } catch (e: SocketTimeoutException) {
            LinkProbe(ProbeStatus.FAIL, "The server took too long to answer.")
        } catch (e: SSLException) {
            LinkProbe(ProbeStatus.FAIL, "Secure connection to the server failed.")
        } catch (e: IOException) {
            LinkProbe(ProbeStatus.FAIL, "Could not connect: ${e.message ?: "network error"}")
        } catch (e: IllegalArgumentException) {
            LinkProbe(ProbeStatus.FAIL, "That doesn't look like a valid link.")
        }
    }

    private fun describe(resp: Response): LinkProbe {
        val code = resp.code
        if (code == 200 || code == 206) {
            val type = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            if (type == "text/html") {
                return LinkProbe(
                    ProbeStatus.WARN,
                    "Connected, but this link opens a web page, not a file."
                )
            }
            val total = if (code == 206) {
                resp.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
            } else {
                resp.header("Content-Length")?.trim()?.toLongOrNull()
            }
            val resumable = code == 206 || resp.header("Accept-Ranges")?.contains("bytes", true) == true
            val size = if (total != null && total > 0) formatBytes(total) else "size unknown"
            val resume = if (resumable) "can resume" else "no resume"
            return LinkProbe(ProbeStatus.OK, "Connected. $size, $resume.")
        }
        val text = when (code) {
            401 -> "This link needs a login (HTTP 401)."
            403 -> "The server refused access (HTTP 403). The link may have expired, or the site only opens it from its own page."
            404, 410 -> "File not found, the link may have expired (HTTP $code)."
            429 -> "Too many requests (HTTP 429). Wait a little and tap Connect again."
            in 500..599 -> "Server problem (HTTP $code). Try again later."
            else -> "Server returned HTTP $code."
        }
        return LinkProbe(ProbeStatus.FAIL, text)
    }
}
