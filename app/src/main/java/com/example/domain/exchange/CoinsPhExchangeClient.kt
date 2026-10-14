package com.example.domain.exchange

import com.example.data.model.CandleStick
import com.example.data.model.CoinsBalance
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.MarketTicker
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import com.example.data.network.BackendApiService
import com.example.data.network.BackendOrderRequest
import com.example.data.network.CoinsPhApiService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

/**
 * CoinsPhExchangeClient connects directly to Coins.ph Spot API with HMAC-SHA256 signature,
 * and can optionally delegate live trading requests to a secure backend.
 */
class CoinsPhExchangeClient(
    private val apiService: CoinsPhApiService,
    private val backendBaseUrl: String = "https://backend.tradingbot.internal",
    private val backendApiService: BackendApiService? = null
) : ExchangeClient {

    override val mode: TradingMode = TradingMode.LIVE

    private var apiKey: String = ""
    private var secretKey: String = ""
    private var serverTimeOffsetMs: Long = 0L

    fun setCredentials(apiKey: String, secretKey: String) {
        this.apiKey = apiKey.trim()
        this.secretKey = secretKey.trim()
    }

    fun hasCredentials(): Boolean = apiKey.isNotBlank() && secretKey.isNotBlank()

    private fun currentSignedTimestamp(): Long = System.currentTimeMillis() + serverTimeOffsetMs

    private fun hmacSha256(data: String, secret: String): String {
        val sha256HMAC = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKeySpec = javax.crypto.spec.SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        sha256HMAC.init(secretKeySpec)
        return sha256HMAC.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun formatQuantityForSymbol(symbol: String, quantity: Double): String {
        val upperSymbol = symbol.uppercase()
        val decimals = when {
            upperSymbol.contains("BTC") -> 5
            upperSymbol.contains("ETH") -> 4
            upperSymbol.contains("SOL") -> 2
            upperSymbol.contains("XRP") -> 1
            else -> 4
        }
        val factor = Math.pow(10.0, decimals.toDouble())
        val floored = kotlin.math.floor(quantity * factor) / factor
        return String.format(Locale.US, "%.${decimals}f", floored)
    }

    private fun isBackendConfigured(): Boolean {
        return backendApiService != null &&
               backendBaseUrl.isNotBlank() &&
               !backendBaseUrl.contains("backend.tradingbot.internal")
    }

    private suspend fun fetchFirebaseToken(): String? {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            val tokenResult = currentUser.getIdToken(false).await()
            tokenResult.token
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun checkConnectivity(): Result<Long> {
        return try {
            val response = apiService.getServerTime()
            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(Exception("Coins.ph server time check failed: HTTP ${response.code()}"))
            }
            val serverTime = response.body()!!.serverTime
            serverTimeOffsetMs = serverTime - System.currentTimeMillis()

            if (!hasCredentials() && !isBackendConfigured()) {
                return Result.failure(SecurityException("Coins.ph API credentials are not configured."))
            }

            if (hasCredentials()) {
                val balancesRes = getBalances()
                if (balancesRes.isFailure) {
                    return Result.failure(
                        Exception("Coins.ph account credentials verification failed: ${balancesRes.exceptionOrNull()?.message}")
                    )
                }
            }
            Result.success(serverTime)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableSymbols(): Result<List<CoinsSymbolInfo>> {
        return try {
            val response = apiService.getExchangeInfo()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.symbols.filter {
                    (it.status == "TRADING" || it.isSpotTradingAllowed == true) &&
                    it.symbol.any { ch -> ch.isLetter() }
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to fetch exchangeInfo: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTicker(symbol: String): Result<MarketTicker> {
        return try {
            val tickerRes = apiService.get24HrTicker(symbol)
            if (tickerRes.isSuccessful && tickerRes.body() != null) {
                val t = tickerRes.body()!!
                val price = t.lastPrice?.toDoubleOrNull() ?: 0.0
                val change = t.priceChangePercent?.toDoubleOrNull() ?: 0.0
                val high = t.highPrice?.toDoubleOrNull() ?: price
                val low = t.lowPrice?.toDoubleOrNull() ?: price
                val vol = t.volume?.toDoubleOrNull() ?: 0.0
                val bid = t.bidPrice?.toDoubleOrNull() ?: price
                val ask = t.askPrice?.toDoubleOrNull() ?: price
                val (base, quote) = splitSymbol(symbol)

                Result.success(
                    MarketTicker(
                        symbol = symbol,
                        baseAsset = base,
                        quoteAsset = quote,
                        price = price,
                        change24h = change,
                        high24h = high,
                        low24h = low,
                        volume24h = vol,
                        bidPrice = bid,
                        askPrice = ask
                    )
                )
            } else {
                Result.failure(Exception("Failed to fetch ticker for $symbol: HTTP ${tickerRes.code()}"))
            }
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
            val response = apiService.getKlines(symbol = symbol, interval = interval, limit = limit)
            if (response.isSuccessful && response.body() != null) {
                val rawList = response.body()!!
                val candles = rawList.mapNotNull { row ->
                    if (row.size >= 6) {
                        val openTime = (row[0] as? Number)?.toLong() ?: row[0].toString().toDoubleOrNull()?.toLong() ?: 0L
                        val open = row[1].toString().toDoubleOrNull() ?: 0.0
                        val high = row[2].toString().toDoubleOrNull() ?: 0.0
                        val low = row[3].toString().toDoubleOrNull() ?: 0.0
                        val close = row[4].toString().toDoubleOrNull() ?: 0.0
                        val volume = row[5].toString().toDoubleOrNull() ?: 0.0
                        val closeTime = if (row.size > 6) {
                            (row[6] as? Number)?.toLong() ?: row[6].toString().toDoubleOrNull()?.toLong() ?: (openTime + 300000L)
                        } else openTime + 300000L

                        CandleStick(
                            openTime = openTime,
                            open = open,
                            high = high,
                            low = low,
                            close = close,
                            volume = volume,
                            closeTime = closeTime
                        )
                    } else null
                }
                Result.success(candles)
            } else {
                Result.failure(Exception("Failed to fetch klines for $symbol: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getOrderBook(
        symbol: String,
        limit: Int
    ): Result<com.example.data.model.OrderBookSnapshot> {
        return try {
            val res = apiService.getOrderBook(symbol, limit)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                val mappedBids = body.bids.take(limit).mapNotNull {
                    if (it.size >= 2) com.example.data.model.OrderBookEntry(it[0].toDoubleOrNull() ?: 0.0, it[1].toDoubleOrNull() ?: 0.0) else null
                }
                val mappedAsks = body.asks.take(limit).mapNotNull {
                    if (it.size >= 2) com.example.data.model.OrderBookEntry(it[0].toDoubleOrNull() ?: 0.0, it[1].toDoubleOrNull() ?: 0.0) else null
                }
                
                val totalBidQty = mappedBids.sumOf { it.quantity }
                val totalAskQty = mappedAsks.sumOf { it.quantity }
                val totalVol = totalBidQty + totalAskQty
                val buyPressure = if (totalVol > 0) (totalBidQty / totalVol) * 100.0 else 50.0

                val bestBid = mappedBids.maxByOrNull { it.price }?.price ?: 0.0
                val bestAsk = mappedAsks.minByOrNull { it.price }?.price ?: 0.0
                val spread = if (bestBid > 0.0 && bestAsk > bestBid) ((bestAsk - bestBid) / bestBid) * 100.0 else 0.0

                Result.success(
                    com.example.data.model.OrderBookSnapshot(
                        symbol = symbol,
                        bids = mappedBids,
                        asks = mappedAsks,
                        buyPressurePercent = buyPressure,
                        spreadPercent = spread
                    )
                )
            } else {
                Result.failure(Exception("Order book error: HTTP ${res.code()}"))
            }
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
        if (hasCredentials()) {
            return try {
                val timestamp = currentSignedTimestamp()
                val sideUpper = side.uppercase()
                val typeUpper = type.uppercase()

                var actualQty = quantity
                if (sideUpper == "SELL") {
                    val (baseAsset, _) = splitSymbol(symbol)
                    val balancesRes = getBalances()
                    if (balancesRes.isSuccess) {
                        val freeBase = balancesRes.getOrNull()?.find { it.asset.equals(baseAsset, ignoreCase = true) }?.free?.toDoubleOrNull()
                        if (freeBase != null && freeBase > 0.0 && freeBase < quantity) {
                            actualQty = freeBase
                        }
                    }
                }

                val formattedQty = if (sideUpper == "SELL" || typeUpper == "LIMIT") {
                    formatQuantityForSymbol(symbol, actualQty)
                } else null

                if (sideUpper == "SELL" || typeUpper == "LIMIT") {
                    val parsedFormattedQty = formattedQty?.toDoubleOrNull() ?: 0.0
                    if (parsedFormattedQty <= 0.0) {
                        return Result.failure(IllegalArgumentException("Sell quantity after step-size rounding is 0"))
                    }
                }

                val formattedQuoteQty = if (sideUpper == "BUY" && typeUpper == "MARKET" && quoteOrderAmount != null) {
                    String.format(Locale.US, "%.2f", quoteOrderAmount)
                } else null

                val formattedPrice = if (typeUpper == "LIMIT" && price != null) {
                    String.format(Locale.US, "%.2f", price)
                } else null

                val params = linkedMapOf<String, String>()
                params["symbol"] = symbol
                params["side"] = sideUpper
                params["type"] = typeUpper
                formattedQty?.let { params["quantity"] = it }
                formattedQuoteQty?.let { params["quoteOrderQty"] = it }
                formattedPrice?.let { params["price"] = it }
                params["timestamp"] = timestamp.toString()

                val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
                val signature = hmacSha256(queryString, secretKey)

                val response = apiService.createOrder(
                    apiKey = apiKey,
                    symbol = symbol,
                    side = sideUpper,
                    type = typeUpper,
                    quantity = formattedQty,
                    quoteOrderQty = formattedQuoteQty,
                    price = formattedPrice,
                    timestamp = timestamp,
                    signature = signature
                )

                if (response.isSuccessful && response.body() != null) {
                    val orderRes = response.body()!!
                    val execQty = orderRes.executedQty?.toDoubleOrNull() ?: quantity
                    val cumQuote = orderRes.cummulativeQuoteQty?.toDoubleOrNull()
                        ?: quoteOrderAmount
                        ?: (execQty * (price ?: 0.0))
                    val execPrice = if (execQty > 0.0 && cumQuote > 0.0) {
                        cumQuote / execQty
                    } else {
                        orderRes.price?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: price ?: 0.0
                    }
                    val fee = cumQuote * 0.001
                    val (_, quoteCurrency) = splitSymbol(symbol)

                    val log = TradeLog(
                        id = orderRes.orderId?.toString() ?: "live_${UUID.randomUUID().toString().replace("-", "").take(8)}",
                        timestamp = orderRes.transactTime ?: System.currentTimeMillis(),
                        type = typeUpper,
                        symbol = symbol,
                        side = sideUpper,
                        price = execPrice,
                        quantity = execQty,
                        amount = cumQuote,
                        fee = fee,
                        feeCurrency = quoteCurrency,
                        status = orderRes.status ?: "FILLED",
                        orderId = orderRes.orderId?.toString(),
                        message = "Live order executed on Coins.ph Spot API",
                        tradingMode = TradingMode.LIVE
                    )
                    Result.success(log)
                } else {
                    Result.failure(Exception("Coins.ph order rejection: HTTP ${response.code()} ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (!isBackendConfigured()) {
            return Result.failure(
                SecurityException("Coins.ph API credentials not configured for Live Trading.")
            )
        }

        val token = fetchFirebaseToken()
            ?: return Result.failure(
                SecurityException("Live trading authorization failed: User must be signed in with Firebase Auth.")
            )

        val clientOrderId = "ord_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        return try {
            val request = BackendOrderRequest(
                symbol = symbol,
                side = side,
                type = type,
                quantity = quantity,
                quoteOrderQty = quoteOrderAmount,
                price = price,
                clientOrderId = clientOrderId,
                isTestOrder = false
            )
            val response = backendApiService!!.submitOrder("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success || body.status == "EXECUTED" || body.status == "RECONCILED_OPEN") {
                    val log = TradeLog(
                        id = body.orderId ?: clientOrderId,
                        timestamp = System.currentTimeMillis(),
                        type = type,
                        symbol = symbol,
                        side = side.uppercase(),
                        price = price ?: 0.0,
                        quantity = quantity,
                        amount = quoteOrderAmount ?: (quantity * (price ?: 0.0)),
                        fee = 0.0,
                        feeCurrency = splitSymbol(symbol).second,
                        status = body.status ?: "FILLED",
                        orderId = body.orderId ?: clientOrderId,
                        message = body.message ?: "Live order executed via secure backend",
                        tradingMode = TradingMode.LIVE
                    )
                    Result.success(log)
                } else {
                    Result.failure(Exception("Live order rejected by backend: ${body.message}"))
                }
            } else {
                Result.failure(Exception("Backend order submission failed: HTTP ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            val openOrdersRes = getOpenOrders(symbol)
            if (openOrdersRes.isSuccess) {
                val reconciled = openOrdersRes.getOrDefault(emptyList()).firstOrNull { it.symbol == symbol }
                if (reconciled != null) {
                    return Result.success(
                        reconciled.copy(
                            message = "Order encountered network timeout but was reconciled on exchange open orders."
                        )
                    )
                }
            }
            Result.failure(e)
        }
    }

    override suspend fun cancelOrder(symbol: String, orderId: String): Result<Boolean> {
        if (hasCredentials()) {
            return try {
                val timestamp = currentSignedTimestamp()
                val orderIdLong = orderId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("Invalid orderId: $orderId"))
                val params = linkedMapOf(
                    "symbol" to symbol,
                    "orderId" to orderIdLong.toString(),
                    "timestamp" to timestamp.toString()
                )
                val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
                val signature = hmacSha256(queryString, secretKey)

                val response = apiService.cancelOrder(apiKey, symbol, orderIdLong, timestamp, signature)
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Coins.ph cancel order failed: HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (!isBackendConfigured()) {
            return Result.failure(
                SecurityException("Live order cancellation disabled: Credentials/Backend URL unconfigured.")
            )
        }
        return Result.failure(
            SecurityException("Live order cancellation endpoint unavailable.")
        )
    }

    override suspend fun getOpenOrders(symbol: String): Result<List<TradeLog>> {
        if (hasCredentials()) {
            return try {
                val timestamp = currentSignedTimestamp()
                val params = linkedMapOf(
                    "symbol" to symbol,
                    "timestamp" to timestamp.toString()
                )
                val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
                val signature = hmacSha256(queryString, secretKey)

                val response = apiService.getOpenOrders(apiKey, symbol, timestamp, signature)
                if (response.isSuccessful && response.body() != null) {
                    val logs = response.body()!!.map { item ->
                        val priceVal = item.price?.toDoubleOrNull() ?: 0.0
                        val qtyVal = item.origQty?.toDoubleOrNull() ?: 0.0
                        val (_, quoteCurrency) = splitSymbol(symbol)
                        TradeLog(
                            id = item.orderId?.toString() ?: UUID.randomUUID().toString(),
                            timestamp = item.transactTime ?: System.currentTimeMillis(),
                            type = item.type ?: "LIMIT",
                            symbol = symbol,
                            side = item.side?.uppercase() ?: "BUY",
                            price = priceVal,
                            quantity = qtyVal,
                            amount = priceVal * qtyVal,
                            fee = 0.0,
                            feeCurrency = quoteCurrency,
                            status = item.status ?: "NEW",
                            orderId = item.orderId?.toString(),
                            message = "Open order on Coins.ph",
                            tradingMode = TradingMode.LIVE
                        )
                    }
                    Result.success(logs)
                } else {
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                Result.success(emptyList())
            }
        }

        if (!isBackendConfigured()) {
            return Result.success(emptyList())
        }
        val token = fetchFirebaseToken() ?: return Result.success(emptyList())
        return try {
            val response = backendApiService!!.getOpenOrders("Bearer $token", symbol)
            if (response.isSuccessful && response.body() != null) {
                val logs = response.body()!!.map { item ->
                    TradeLog(
                        id = item.orderId ?: UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        type = "LIMIT",
                        symbol = symbol,
                        side = "BUY",
                        price = 0.0,
                        quantity = 0.0,
                        amount = 0.0,
                        fee = 0.0,
                        feeCurrency = splitSymbol(symbol).second,
                        status = item.status ?: "NEW",
                        orderId = item.orderId,
                        message = item.message ?: "",
                        tradingMode = TradingMode.LIVE
                    )
                }
                Result.success(logs)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun getBalances(): Result<List<CoinsBalance>> {
        if (hasCredentials()) {
            return try {
                val timestamp = currentSignedTimestamp()
                val queryString = "timestamp=$timestamp"
                val signature = hmacSha256(queryString, secretKey)
                val response = apiService.getAccountInfo(apiKey, timestamp, signature)
                if (response.isSuccessful && response.body() != null) {
                    val balances = response.body()!!.balances ?: emptyList()
                    Result.success(balances)
                } else {
                    Result.failure(Exception("Failed to fetch Coins.ph account info: HTTP ${response.code()} ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (!isBackendConfigured()) {
            return Result.failure(SecurityException("Coins.ph API credentials not configured."))
        }
        val token = fetchFirebaseToken() ?: return Result.success(emptyList())
        return try {
            val response = backendApiService!!.getAccount("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val coinsBalances = response.body()!!.balances.map { item ->
                    CoinsBalance(
                        asset = item.asset,
                        free = item.free,
                        locked = item.locked
                    )
                }
                Result.success(coinsBalances)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    private fun splitSymbol(symbol: String): Pair<String, String> {
        val upper = symbol.uppercase()
        return when {
            upper.endsWith("PHP") -> upper.removeSuffix("PHP") to "PHP"
            upper.endsWith("USDT") -> upper.removeSuffix("USDT") to "USDT"
            upper.endsWith("USDC") -> upper.removeSuffix("USDC") to "USDC"
            else -> upper.take(3) to upper.drop(3)
        }
    }
}
