package com.example.domain.exchange

import com.example.data.model.CandleStick
import com.example.data.model.CoinsBalance
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.MarketTicker
import com.example.data.model.OrderBookEntry
import com.example.data.model.OrderBookSnapshot
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BinanceExchangeClient : ExchangeClient {
    override val mode: TradingMode = TradingMode.LIVE
    
    private var apiKey: String = ""
    private var secretKey: String = ""
    private var cachedSymbols: List<CoinsSymbolInfo>? = null
    private var cacheTimestamp: Long = 0L
    private val CACHE_TTL_MS = 10_000L

    private val mirrors = listOf(
        "https://api-gcp.binance.com",
        "https://api1.binance.com",
        "https://api2.binance.com",
        "https://api3.binance.com",
        "https://data-api.binance.vision",
        "https://api.binance.com"
    )
    private var currentMirrorIndex = 0

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    fun setCredentials(key: String, secret: String) {
        this.apiKey = key
        this.secretKey = secret
    }

    fun hasCredentials(): Boolean = apiKey.isNotBlank() && secretKey.isNotBlank()

    private suspend fun <T> executeWithMirrorFailover(block: suspend (String) -> T): Result<T> {
        var lastException: Exception? = null
        for (i in mirrors.indices) {
            val mirrorIndex = (currentMirrorIndex + i) % mirrors.size
            val baseUrl = mirrors[mirrorIndex]
            try {
                return Result.success(block(baseUrl)).also {
                    currentMirrorIndex = mirrorIndex
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        return Result.failure(lastException ?: Exception("All Binance mirrors failed"))
    }

    private suspend fun getRequest(path: String, queryParams: String = ""): String {
        return executeWithMirrorFailover { baseUrl ->
            withContext(Dispatchers.IO) {
                val url = if (queryParams.isNotEmpty()) "$baseUrl$path?$queryParams" else "$baseUrl$path"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    response.body?.string() ?: throw IOException("Empty body")
                }
            }
        }.getOrThrow()
    }

    private suspend fun signedGetRequest(path: String, queryParams: String = ""): String {
        return executeWithMirrorFailover { baseUrl ->
            withContext(Dispatchers.IO) {
                val timestamp = System.currentTimeMillis()
                val qString = if (queryParams.isNotEmpty()) "$queryParams&timestamp=$timestamp" else "timestamp=$timestamp"
                val signature = signHMACSHA256(qString, secretKey)
                val url = "$baseUrl$path?$qString&signature=$signature"
                
                val request = Request.Builder()
                    .url(url)
                    .header("X-MBX-APIKEY", apiKey)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    response.body?.string() ?: throw IOException("Empty body")
                }
            }
        }.getOrThrow()
    }

    private suspend fun signedPostRequest(path: String, params: Map<String, String>): String {
        return executeWithMirrorFailover { baseUrl ->
            withContext(Dispatchers.IO) {
                val timestamp = System.currentTimeMillis()
                val sortedParams = params.toSortedMap()
                sortedParams["timestamp"] = timestamp.toString()
                
                val queryString = sortedParams.entries.joinToString("&") { "${it.key}=${it.value}" }
                val signature = signHMACSHA256(queryString, secretKey)
                
                val formBodyBuilder = FormBody.Builder()
                sortedParams.forEach { (k, v) -> formBodyBuilder.add(k, v) }
                formBodyBuilder.add("signature", signature)

                val request = Request.Builder()
                    .url("$baseUrl$path")
                    .header("X-MBX-APIKEY", apiKey)
                    .post(formBodyBuilder.build())
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    response.body?.string() ?: throw IOException("Empty body")
                }
            }
        }.getOrThrow()
    }

    private suspend fun signedDeleteRequest(path: String, params: Map<String, String>): String {
        return executeWithMirrorFailover { baseUrl ->
            withContext(Dispatchers.IO) {
                val timestamp = System.currentTimeMillis()
                val sortedParams = params.toSortedMap()
                sortedParams["timestamp"] = timestamp.toString()
                
                val queryString = sortedParams.entries.joinToString("&") { "${it.key}=${it.value}" }
                val signature = signHMACSHA256(queryString, secretKey)
                
                val url = "$baseUrl$path?$queryString&signature=$signature"
                
                val request = Request.Builder()
                    .url(url)
                    .header("X-MBX-APIKEY", apiKey)
                    .delete()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    response.body?.string() ?: throw IOException("Empty body")
                }
            }
        }.getOrThrow()
    }

    private fun signHMACSHA256(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)
        val hash = mac.doFinal(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    override suspend fun checkConnectivity(): Result<Long> {
        return try {
            val res = getRequest("/api/v3/time")
            val json = JSONObject(res)
            Result.success(json.getLong("serverTime"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableSymbols(): Result<List<CoinsSymbolInfo>> {
        val now = System.currentTimeMillis()
        if (cachedSymbols != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            return Result.success(cachedSymbols!!)
        }
        return withContext(Dispatchers.Default) {
            try {
                val res = getRequest("/api/v3/exchangeInfo")
                val json = JSONObject(res)
                val symbolsArray = json.getJSONArray("symbols")
                val list = mutableListOf<CoinsSymbolInfo>()
                for (i in 0 until symbolsArray.length()) {
                    val s = symbolsArray.getJSONObject(i)
                    val status = s.optString("status", "")
                    val isSpot = s.optBoolean("isSpotTradingAllowed", true)
                    val quote = s.optString("quoteAsset", "")
                    if (status == "TRADING" && isSpot && (quote == "USDT" || quote == "USDC")) {
                        list.add(
                            CoinsSymbolInfo(
                                symbol = s.getString("symbol"),
                                baseAsset = s.getString("baseAsset"),
                                quoteAsset = quote,
                                status = "TRADING",
                                isSpotTradingAllowed = true
                            )
                        )
                    }
                }
                val priorityBases = setOf("BTC", "ETH", "SOL", "XRP", "BNB", "PAXG", "EUR", "EURI", "GBP", "AUD", "DOGE", "ADA", "AVAX", "LINK", "DOT", "MATIC", "SHIB", "LTC", "TRX", "UNI", "ATOM", "NEAR", "APT", "ARB", "OP", "SUI", "PEPE", "FDUSD", "USDC")
                val priorityList = list.filter { it.baseAsset.uppercase() in priorityBases }
                val remainingList = list.filterNot { it.baseAsset.uppercase() in priorityBases }.take(120)
                val finalSymbols = (priorityList + remainingList).distinctBy { it.symbol }

                // Fetch 24hr Tickers
                val tickerMap = runCatching {
                    val tickersRes = getRequest("/api/v3/ticker/24hr")
                    val tickersArray = JSONArray(tickersRes)
                    val map = mutableMapOf<String, JSONObject>()
                    for (j in 0 until tickersArray.length()) {
                        val tObj = tickersArray.getJSONObject(j)
                        map[tObj.getString("symbol").uppercase()] = tObj
                    }
                    map
                }.getOrDefault(emptyMap())

                val enrichedSymbols = finalSymbols.map { sym ->
                    val tObj = tickerMap[sym.symbol.uppercase()]
                    val price = tObj?.optDouble("lastPrice", 0.0) ?: 0.0
                    val change = tObj?.optDouble("priceChangePercent", 0.0) ?: 0.0
                    val vol = tObj?.optDouble("volume", 0.0) ?: 0.0
                    sym.copy(lastPrice = price, priceChangePercent = change, volume24h = vol)
                }

                cachedSymbols = enrichedSymbols
                cacheTimestamp = System.currentTimeMillis()
                Result.success(enrichedSymbols)
            } catch (e: Exception) {
                val fallback = listOf(
                    "BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "BNBUSDT", "PAXGUSDT", "EURUSDT", "GBPUSDT", "AUDUSDT", "USDCUSDT", "BTCUSDC"
                ).map { 
                    val quote = if (it.endsWith("USDT") || it.endsWith("USDC")) it.takeLast(4) else it.takeLast(3)
                    val base = it.dropLast(quote.length)
                    CoinsSymbolInfo(it, base, quote, "TRADING", true) 
                }
                cachedSymbols = fallback
                cacheTimestamp = System.currentTimeMillis()
                Result.success(fallback)
            }
        }
    }

    override suspend fun getTicker(symbol: String): Result<MarketTicker> {
        return try {
            val res = getRequest("/api/v3/ticker/24hr", "symbol=$symbol")
            val json = JSONObject(res)
            val price = json.getDouble("lastPrice")
            val base = if (symbol.endsWith("USDT") || symbol.endsWith("USDC")) symbol.dropLast(4) else symbol.dropLast(3)
            val quote = symbol.drop(base.length)
            
            Result.success(
                MarketTicker(
                    symbol = symbol,
                    baseAsset = base,
                    quoteAsset = quote,
                    price = price,
                    change24h = json.getDouble("priceChangePercent"),
                    high24h = json.getDouble("highPrice"),
                    low24h = json.getDouble("lowPrice"),
                    volume24h = json.getDouble("volume"),
                    bidPrice = json.optDouble("bidPrice", price),
                    askPrice = json.optDouble("askPrice", price)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getKlines(
        symbol: String,
        interval: String,
        limit: Int
    ): Result<List<CandleStick>> {
        return try {
            val res = getRequest("/api/v3/klines", "symbol=$symbol&interval=$interval&limit=$limit")
            val arr = JSONArray(res)
            val candles = mutableListOf<CandleStick>()
            for (i in 0 until arr.length()) {
                val row = arr.getJSONArray(i)
                candles.add(
                    CandleStick(
                        openTime = row.getLong(0),
                        open = row.getDouble(1),
                        high = row.getDouble(2),
                        low = row.getDouble(3),
                        close = row.getDouble(4),
                        volume = row.getDouble(5),
                        closeTime = row.getLong(6)
                    )
                )
            }
            Result.success(candles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getOrderBook(symbol: String, limit: Int): Result<OrderBookSnapshot> {
        return try {
            val res = getRequest("/api/v3/depth", "symbol=$symbol&limit=$limit")
            val json = JSONObject(res)
            val mappedBids = mutableListOf<OrderBookEntry>()
            val bidsArr = json.getJSONArray("bids")
            for (i in 0 until bidsArr.length()) {
                val b = bidsArr.getJSONArray(i)
                mappedBids.add(OrderBookEntry(b.getString(0).toDouble(), b.getString(1).toDouble()))
            }
            val mappedAsks = mutableListOf<OrderBookEntry>()
            val asksArr = json.getJSONArray("asks")
            for (i in 0 until asksArr.length()) {
                val a = asksArr.getJSONArray(i)
                mappedAsks.add(OrderBookEntry(a.getString(0).toDouble(), a.getString(1).toDouble()))
            }
            val totalBidQty = mappedBids.sumOf { it.quantity }
            val totalAskQty = mappedAsks.sumOf { it.quantity }
            val totalVol = totalBidQty + totalAskQty
            val buyPressure = if (totalVol > 0) (totalBidQty / totalVol) * 100.0 else 50.0

            val bestBid = mappedBids.maxByOrNull { it.price }?.price ?: 0.0
            val bestAsk = mappedAsks.minByOrNull { it.price }?.price ?: 0.0
            val spread = if (bestBid > 0.0 && bestAsk > bestBid) ((bestAsk - bestBid) / bestBid) * 100.0 else 0.0

            Result.success(
                OrderBookSnapshot(
                    symbol = symbol,
                    bids = mappedBids,
                    asks = mappedAsks,
                    buyPressurePercent = buyPressure,
                    spreadPercent = spread
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun submitOrder(
        symbol: String,
        side: String,
        type: String,
        quantity: Double,
        price: Double?,
        quoteOrderAmount: Double?
    ): Result<TradeLog> {
        if (!hasCredentials()) return Result.failure(Exception("Binance credentials not set"))
        return try {
            val params = mutableMapOf(
                "symbol" to symbol,
                "side" to side.uppercase(),
                "type" to type.uppercase(),
                "quantity" to String.format(java.util.Locale.US, "%.5f", quantity)
            )
            if (type.uppercase() == "LIMIT" && price != null) {
                params["price"] = String.format(java.util.Locale.US, "%.5f", price)
                params["timeInForce"] = "GTC"
            }
            if (quoteOrderAmount != null) {
                params.remove("quantity")
                params["quoteOrderQty"] = String.format(java.util.Locale.US, "%.5f", quoteOrderAmount)
            }

            val res = signedPostRequest("/api/v3/order", params)
            val json = JSONObject(res)
            val execQty = json.optDouble("executedQty", quantity)
            val execPrice = json.optDouble("price", price ?: 0.0)
            
            Result.success(
                TradeLog(
                    id = java.util.UUID.randomUUID().toString(),
                    type = type.uppercase(),
                    symbol = symbol,
                    side = side.uppercase(),
                    price = execPrice,
                    quantity = execQty,
                    amount = execPrice * execQty,
                    fee = 0.0,
                    feeCurrency = "USDT",
                    status = json.getString("status"),
                    orderId = json.getLong("orderId").toString(),
                    tradingMode = TradingMode.LIVE
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelOrder(symbol: String, orderId: String): Result<Boolean> {
        if (!hasCredentials()) return Result.failure(Exception("Binance credentials not set"))
        return try {
            signedDeleteRequest("/api/v3/order", mapOf("symbol" to symbol, "orderId" to orderId))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getOpenOrders(symbol: String): Result<List<TradeLog>> {
        if (!hasCredentials()) return Result.failure(Exception("Binance credentials not set"))
        return try {
            val res = signedGetRequest("/api/v3/openOrders", "symbol=$symbol")
            val arr = JSONArray(res)
            val logs = mutableListOf<TradeLog>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                logs.add(
                    TradeLog(
                        id = java.util.UUID.randomUUID().toString(),
                        type = o.getString("type"),
                        symbol = symbol,
                        side = o.getString("side"),
                        price = o.getDouble("price"),
                        quantity = o.getDouble("origQty"),
                        amount = o.getDouble("price") * o.getDouble("origQty"),
                        fee = 0.0,
                        feeCurrency = "USDT",
                        status = o.getString("status"),
                        orderId = o.getLong("orderId").toString(),
                        tradingMode = TradingMode.LIVE
                    )
                )
            }
            Result.success(logs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBalances(): Result<List<CoinsBalance>> {
        if (!hasCredentials()) return Result.failure(Exception("Binance credentials not set"))
        return try {
            val res = signedGetRequest("/api/v3/account")
            val json = JSONObject(res)
            val arr = json.getJSONArray("balances")
            val balances = mutableListOf<CoinsBalance>()
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                val free = b.getString("free")
                if ((free.toDoubleOrNull() ?: 0.0) > 0) {
                    balances.add(com.example.data.model.CoinsBalance(asset = b.getString("asset"), free = free, locked = b.getString("locked")))
                }
            }
            Result.success(balances)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}