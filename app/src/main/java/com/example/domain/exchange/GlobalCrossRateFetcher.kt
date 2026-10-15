package com.example.domain.exchange

import com.example.data.model.CandleStick
import com.example.data.model.OrderBookEntry
import com.example.data.model.OrderBookSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GlobalCrossRateFetcher {
    private val mirrors = listOf(
        "https://api-gcp.binance.com",
        "https://api1.binance.com",
        "https://data-api.binance.vision",
        "https://api.binance.com"
    )
    private var currentMirrorIndex = 0

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private var cachedTickers: Map<String, JSONObject>? = null
    private var lastTickerCacheTime = 0L
    private const val CACHE_TTL_MS = 10_000L

    private suspend fun getRequest(path: String, queryParams: String = ""): String {
        var lastException: Exception? = null
        for (i in mirrors.indices) {
            val idx = (currentMirrorIndex + i) % mirrors.size
            val baseUrl = mirrors[idx]
            try {
                return withContext(Dispatchers.IO) {
                    val url = if (queryParams.isNotEmpty()) "$baseUrl$path?$queryParams" else "$baseUrl$path"
                    val req = Request.Builder().url(url).get().build()
                    httpClient.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        response.body?.string() ?: throw IOException("Empty body")
                    }
                }.also { currentMirrorIndex = idx }
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw (lastException ?: Exception("All global mirrors failed"))
    }

    suspend fun getGlobalUsdtTickers(): Map<String, JSONObject> {
        val now = System.currentTimeMillis()
        if (cachedTickers != null && (now - lastTickerCacheTime) < CACHE_TTL_MS) {
            return cachedTickers!!
        }
        return withContext(Dispatchers.Default) {
            try {
                val raw = getRequest("/api/v3/ticker/24hr")
                val arr = JSONArray(raw)
                val map = mutableMapOf<String, JSONObject>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    map[obj.getString("symbol").uppercase()] = obj
                }
                cachedTickers = map
                lastTickerCacheTime = System.currentTimeMillis()
                map
            } catch (e: Exception) {
                cachedTickers ?: emptyMap()
            }
        }
    }

    suspend fun getGlobalKlines(
        baseAsset: String,
        interval: String,
        limit: Int,
        usdtPhpRate: Double
    ): List<CandleStick> {
        return withContext(Dispatchers.Default) {
            try {
                val symbol = "${baseAsset.uppercase()}USDT"
                val raw = getRequest("/api/v3/klines", "symbol=$symbol&interval=$interval&limit=$limit")
                val arr = JSONArray(raw)
                val candles = mutableListOf<CandleStick>()
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONArray(i)
                    val openTime = row.getLong(0)
                    val open = row.getDouble(1) * usdtPhpRate
                    val high = row.getDouble(2) * usdtPhpRate
                    val low = row.getDouble(3) * usdtPhpRate
                    val close = row.getDouble(4) * usdtPhpRate
                    val volume = row.getDouble(5)
                    val closeTime = row.getLong(6)
                    candles.add(CandleStick(openTime, open, high, low, close, volume, closeTime))
                }
                candles
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getGlobalOrderBook(
        baseAsset: String,
        limit: Int,
        usdtPhpRate: Double,
        targetSymbol: String
    ): OrderBookSnapshot? {
        return withContext(Dispatchers.Default) {
            try {
                val symbol = "${baseAsset.uppercase()}USDT"
                val raw = getRequest("/api/v3/depth", "symbol=$symbol&limit=$limit")
                val json = JSONObject(raw)

                val bidsArr = json.getJSONArray("bids")
                val bids = mutableListOf<OrderBookEntry>()
                for (i in 0 until bidsArr.length()) {
                    val row = bidsArr.getJSONArray(i)
                    bids.add(OrderBookEntry(row.getString(0).toDouble() * usdtPhpRate, row.getString(1).toDouble()))
                }

                val asksArr = json.getJSONArray("asks")
                val asks = mutableListOf<OrderBookEntry>()
                for (i in 0 until asksArr.length()) {
                    val row = asksArr.getJSONArray(i)
                    asks.add(OrderBookEntry(row.getString(0).toDouble() * usdtPhpRate, row.getString(1).toDouble()))
                }

                val totalBidQty = bids.sumOf { it.quantity }
                val totalAskQty = asks.sumOf { it.quantity }
                val totalVol = totalBidQty + totalAskQty
                val buyPressure = if (totalVol > 0) (totalBidQty / totalVol) * 100.0 else 50.0

                val bestBid = bids.maxByOrNull { it.price }?.price ?: 0.0
                val bestAsk = asks.minByOrNull { it.price }?.price ?: 0.0
                val spread = if (bestBid > 0.0 && bestAsk > bestBid) ((bestAsk - bestBid) / bestBid) * 100.0 else 0.0

                OrderBookSnapshot(
                    symbol = targetSymbol,
                    bids = bids,
                    asks = asks,
                    buyPressurePercent = buyPressure,
                    spreadPercent = spread
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
