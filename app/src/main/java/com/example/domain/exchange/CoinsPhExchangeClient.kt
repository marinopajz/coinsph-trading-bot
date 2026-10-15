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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

    private suspend fun currentSignedTimestamp(): Long {
        if (serverTimeOffsetMs == 0L) {
            runCatching {
                val response = apiService.getServerTime()
                if (response.isSuccessful && response.body() != null) {
                    val serverTime = response.body()!!.serverTime
                    serverTimeOffsetMs = serverTime - System.currentTimeMillis()
                }
            }
        }
        return System.currentTimeMillis() + serverTimeOffsetMs
    }

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

    private suspend fun resolveCoinsPhTicker(symbol: String): MarketTicker? {
        val upper = symbol.uppercase()
        val (base, quote) = splitSymbol(upper)

        val t24 = runCatching {
            val res = apiService.get24HrTicker(upper)
            if (res.isSuccessful && res.body() != null) res.body() else null
        }.getOrNull()

        var last = t24?.lastPrice?.toDoubleOrNull() ?: 0.0
        var bid = t24?.bidPrice?.toDoubleOrNull() ?: 0.0
        var ask = t24?.askPrice?.toDoubleOrNull() ?: 0.0
        var change = t24?.priceChangePercent?.toDoubleOrNull() ?: 0.0
        val high = t24?.highPrice?.toDoubleOrNull() ?: 0.0
        val low = t24?.lowPrice?.toDoubleOrNull() ?: 0.0
        val vol = t24?.volume?.toDoubleOrNull() ?: 0.0

        if (last <= 0.0) {
            if (bid > 0.0 && ask > 0.0) {
                last = (bid + ask) / 2.0
            } else if (bid > 0.0) {
                last = bid
            } else if (ask > 0.0) {
                last = ask
            }
        }

        if (last <= 0.0) {
            val pRes = runCatching {
                val res = apiService.getTickerPrice(upper)
                if (res.isSuccessful && res.body() != null) res.body()?.price?.toDoubleOrNull() else null
            }.getOrNull()
            if (pRes != null && pRes > 0.0) {
                last = pRes
            }
        }

        if (last <= 0.0 && quote == "PHP" && base != "USDT" && base != "USDC") {
            val usdtTicker = runCatching {
                val res = apiService.get24HrTicker("${base}USDT")
                if (res.isSuccessful && res.body() != null) res.body() else null
            }.getOrNull()

            val usdtPhpTicker = runCatching {
                val res = apiService.get24HrTicker("USDTPHP")
                if (res.isSuccessful && res.body() != null) res.body() else null
            }.getOrNull()

            val baseUsdtPrice = usdtTicker?.lastPrice?.toDoubleOrNull()
                ?: usdtTicker?.bidPrice?.toDoubleOrNull() ?: 0.0
            val usdtRate = usdtPhpTicker?.lastPrice?.toDoubleOrNull()
                ?: usdtPhpTicker?.bidPrice?.toDoubleOrNull() ?: 58.20

            if (baseUsdtPrice > 0.0) {
                last = baseUsdtPrice * usdtRate
                if (change == 0.0) {
                    change = usdtTicker?.priceChangePercent?.toDoubleOrNull() ?: 0.0
                }
            }
        }

        if (last <= 0.0 && bid <= 0.0 && ask <= 0.0) return null

        val finalPrice = if (last > 0.0) last else if (bid > 0.0) bid else ask
        val finalBid = if (bid > 0.0) bid else finalPrice
        val finalAsk = if (ask > 0.0) ask else finalPrice

        return MarketTicker(
            symbol = upper,
            baseAsset = base,
            quoteAsset = quote,
            price = finalPrice,
            change24h = change,
            high24h = if (high > 0.0) high else finalPrice,
            low24h = if (low > 0.0) low else finalPrice,
            volume24h = vol,
            bidPrice = finalBid,
            askPrice = finalAsk
        )
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
        return withContext(Dispatchers.Default) {
            try {
                val response = apiService.getExchangeInfo()
                if (response.isSuccessful && response.body() != null) {
                    val tickerMap = runCatching {
                        val tickersRes = apiService.getAll24HrTickers()
                        if (tickersRes.isSuccessful && tickersRes.body() != null) {
                            tickersRes.body()!!.associateBy { it.symbol.uppercase() }
                        } else emptyMap()
                    }.getOrDefault(emptyMap())

                    val list = response.body()!!.symbols.filter {
                        (it.status == "TRADING" || it.isSpotTradingAllowed == true) &&
                        it.symbol.any { ch -> ch.isLetter() }
                    }.mapNotNull { sym ->
                        val upper = sym.symbol.uppercase()
                        val ticker = tickerMap[upper]

                        var price = ticker?.lastPrice?.toDoubleOrNull() ?: 0.0
                        val bid = ticker?.bidPrice?.toDoubleOrNull() ?: 0.0
                        val ask = ticker?.askPrice?.toDoubleOrNull() ?: 0.0
                        val vol = ticker?.volume?.toDoubleOrNull() ?: 0.0
                        val change = ticker?.priceChangePercent?.toDoubleOrNull() ?: 0.0

                        if (price <= 0.0) {
                            if (bid > 0.0 && ask > 0.0) price = (bid + ask) / 2.0
                            else if (bid > 0.0) price = bid
                            else if (ask > 0.0) price = ask
                        }

                        if (tickerMap.isNotEmpty() && price <= 0.0 && bid <= 0.0 && ask <= 0.0) {
                            return@mapNotNull null
                        }

                        sym.copy(lastPrice = if (price > 0.0) price else 1.0, priceChangePercent = change, volume24h = vol)
                    }
                    Result.success(list)
                } else {
                    Result.failure(Exception("Failed to fetch exchangeInfo: HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getTicker(symbol: String): Result<MarketTicker> {
        val resolved = resolveCoinsPhTicker(symbol)
        return if (resolved != null) {
            Result.success(resolved)
        } else {
            Result.failure(Exception("Failed to fetch ticker for $symbol"))
        }
    }

    override suspend fun getKlines(
        symbol: String,
        interval: String,
        limit: Int
    ): Result<List<CandleStick>> {
        val upper = symbol.uppercase()
        val (base, quote) = splitSymbol(upper)

        val directResult = runCatching {
            val response = apiService.getKlines(symbol = upper, interval = interval, limit = limit)
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

                        if (close > 0.0) {
                            CandleStick(openTime, open, high, low, close, volume, closeTime)
                        } else null
                    } else null
                }
                if (candles.isNotEmpty()) Result.success(candles) else null
            } else null
        }.getOrNull()

        if (directResult != null) return directResult

        // If direct klines empty and pair is PHP, check Coins.ph's own baseUSDT klines * USDTPHP
        if (quote == "PHP" && base != "USDT" && base != "USDC") {
            val usdtKlinesRes = runCatching {
                val res = apiService.getKlines(symbol = "${base}USDT", interval = interval, limit = limit)
                if (res.isSuccessful && res.body() != null) res.body() else null
            }.getOrNull()

            if (usdtKlinesRes != null && usdtKlinesRes.isNotEmpty()) {
                val usdtPhpTicker = resolveCoinsPhTicker("USDTPHP")
                val usdtRate = usdtPhpTicker?.price ?: 58.20

                val candles = usdtKlinesRes.mapNotNull { row ->
                    if (row.size >= 6) {
                        val openTime = (row[0] as? Number)?.toLong() ?: row[0].toString().toDoubleOrNull()?.toLong() ?: 0L
                        val open = (row[1].toString().toDoubleOrNull() ?: 0.0) * usdtRate
                        val high = (row[2].toString().toDoubleOrNull() ?: 0.0) * usdtRate
                        val low = (row[3].toString().toDoubleOrNull() ?: 0.0) * usdtRate
                        val close = (row[4].toString().toDoubleOrNull() ?: 0.0) * usdtRate
                        val volume = row[5].toString().toDoubleOrNull() ?: 0.0
                        val closeTime = if (row.size > 6) {
                            (row[6] as? Number)?.toLong() ?: row[6].toString().toDoubleOrNull()?.toLong() ?: (openTime + 300000L)
                        } else openTime + 300000L

                        if (close > 0.0) {
                            CandleStick(openTime, open, high, low, close, volume, closeTime)
                        } else null
                    } else null
                }
                if (candles.isNotEmpty()) return Result.success(candles)
            }
        }

        // Anchor flat candles to Coins.ph live price
        val liveTicker = resolveCoinsPhTicker(upper)
        val basePrice = liveTicker?.price ?: 1.0
        val now = System.currentTimeMillis()
        val intervalMs = when (interval) {
            "1m" -> 60_000L
            "15m" -> 900_000L
            "1h" -> 3_600_000L
            "4h" -> 14_400_000L
            "1d" -> 86_400_000L
            else -> 300_000L
        }
        val synthetic = (limit - 1 downTo 0).map { i ->
            val time = now - (i * intervalMs)
            CandleStick(
                openTime = time,
                open = basePrice,
                high = basePrice * 1.001,
                low = basePrice * 0.999,
                close = basePrice,
                volume = 1.0,
                closeTime = time + intervalMs
            )
        }
        return Result.success(synthetic)
    }

    override suspend fun getOrderBook(
        symbol: String,
        limit: Int
    ): Result<com.example.data.model.OrderBookSnapshot> {
        val upper = symbol.uppercase()
        val (base, quote) = splitSymbol(upper)

        val directBook = runCatching {
            val res = apiService.getOrderBook(upper, limit)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                val mappedBids = body.bids.take(limit).mapNotNull {
                    if (it.size >= 2) com.example.data.model.OrderBookEntry(it[0].toDoubleOrNull() ?: 0.0, it[1].toDoubleOrNull() ?: 0.0) else null
                }
                val mappedAsks = body.asks.take(limit).mapNotNull {
                    if (it.size >= 2) com.example.data.model.OrderBookEntry(it[0].toDoubleOrNull() ?: 0.0, it[1].toDoubleOrNull() ?: 0.0) else null
                }
                if (mappedBids.isNotEmpty() && mappedAsks.isNotEmpty()) {
                    val totalBidQty = mappedBids.sumOf { it.quantity }
                    val totalAskQty = mappedAsks.sumOf { it.quantity }
                    val totalVol = totalBidQty + totalAskQty
                    val buyPressure = if (totalVol > 0) (totalBidQty / totalVol) * 100.0 else 50.0

                    val bestBid = mappedBids.maxByOrNull { it.price }?.price ?: 0.0
                    val bestAsk = mappedAsks.minByOrNull { it.price }?.price ?: 0.0
                    val spread = if (bestBid > 0.0 && bestAsk > bestBid) ((bestAsk - bestBid) / bestBid) * 100.0 else 0.0

                    com.example.data.model.OrderBookSnapshot(
                        symbol = symbol,
                        bids = mappedBids,
                        asks = mappedAsks,
                        buyPressurePercent = buyPressure,
                        spreadPercent = spread
                    )
                } else null
            } else null
        }.getOrNull()

        if (directBook != null) return Result.success(directBook)

        // If direct orderbook empty on Coins.ph for PHP, check Coins.ph's own baseUSDT depth * USDTPHP
        if (quote == "PHP" && base != "USDT" && base != "USDC") {
            val usdtBook = runCatching {
                val res = apiService.getOrderBook("${base}USDT", limit)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    val usdtPhpTicker = resolveCoinsPhTicker("USDTPHP")
                    val usdtRate = usdtPhpTicker?.price ?: 58.20

                    val mappedBids = body.bids.take(limit).mapNotNull {
                        if (it.size >= 2) com.example.data.model.OrderBookEntry((it[0].toDoubleOrNull() ?: 0.0) * usdtRate, it[1].toDoubleOrNull() ?: 0.0) else null
                    }
                    val mappedAsks = body.asks.take(limit).mapNotNull {
                        if (it.size >= 2) com.example.data.model.OrderBookEntry((it[0].toDoubleOrNull() ?: 0.0) * usdtRate, it[1].toDoubleOrNull() ?: 0.0) else null
                    }
                    if (mappedBids.isNotEmpty() && mappedAsks.isNotEmpty()) {
                        val totalBidQty = mappedBids.sumOf { it.quantity }
                        val totalAskQty = mappedAsks.sumOf { it.quantity }
                        val totalVol = totalBidQty + totalAskQty
                        val buyPressure = if (totalVol > 0) (totalBidQty / totalVol) * 100.0 else 50.0

                        val bestBid = mappedBids.maxByOrNull { it.price }?.price ?: 0.0
                        val bestAsk = mappedAsks.minByOrNull { it.price }?.price ?: 0.0
                        val spread = if (bestBid > 0.0 && bestAsk > bestBid) ((bestAsk - bestBid) / bestBid) * 100.0 else 0.0

                        com.example.data.model.OrderBookSnapshot(
                            symbol = symbol,
                            bids = mappedBids,
                            asks = mappedAsks,
                            buyPressurePercent = buyPressure,
                            spreadPercent = spread
                        )
                    } else null
                } else null
            }.getOrNull()

            if (usdtBook != null) return Result.success(usdtBook)
        }

        // Anchor order book to live price
        val liveTicker = resolveCoinsPhTicker(upper)
        val basePrice = liveTicker?.price ?: 1.0
        val bids = (1..limit).map { i -> com.example.data.model.OrderBookEntry(basePrice * (1.0 - i * 0.001), 0.5 * i) }
        val asks = (1..limit).map { i -> com.example.data.model.OrderBookEntry(basePrice * (1.0 + i * 0.001), 0.5 * i) }
        return Result.success(
            com.example.data.model.OrderBookSnapshot(
                symbol = symbol,
                bids = bids,
                asks = asks,
                buyPressurePercent = 50.0,
                spreadPercent = 0.2
            )
        )
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
