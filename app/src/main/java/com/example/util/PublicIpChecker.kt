package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object PublicIpChecker {
    private val services = listOf(
        "https://api.ipify.org?format=text",
        "https://checkip.amazonaws.com",
        "https://icanhazip.com"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getPublicIp(): String? = withContext(Dispatchers.IO) {
        for (url in services) {
            try {
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()?.trim()
                        if (!body.isNullOrBlank() && isValidIp(body)) {
                            return@withContext body
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next service on error
            }
        }
        null
    }

    private fun isValidIp(ip: String): Boolean {
        return ip.length in 7..45 && (ip.contains(".") || ip.contains(":"))
    }
}
