package com.example.domain.exchange

import com.example.data.model.CandleStick
import com.example.data.model.CoinsBalance
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.MarketTicker
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import com.example.data.network.CoinsPhApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class PaperTradingExchangeClient(
    private val apiService: CoinsPhApiService,
    initialPhpBalance: Double = 50000.0,
    initialUsdtBalance: Double = 1000.0,
    private val tradingDao: com.example.data.local.dao.TradingDao? = null
) : ExchangeClient {

    override val mode: TradingMode = TradingMode.PAPER

    init {
        require(!initialPhpBalance.isNaN() && !initialPhpBalance.isInfinite() && initialPhpBalance >= 0.0) {
            "Invalid initialPhpBalance: $initialPhpBalance"
        }
        require(!initialUsdtBalance.isNaN() && !initialUsdtBalance.isInfinite() && initialUsdtBalance >= 0.0) {
            "Invalid initialUsdtBalance: $initialUsdtBalance"
        }
    }

    private val mutex = Mutex()
    private val virtualBalances = mutableMapOf<String, Double>(
        "PHP" to initialPhpBalance,
        "USDT" to initialUsdtBalance,
        "BTC" to 0.0,
        "ETH" to 0.0,
        "SOL" to 0.0
    )

    private val simulatedOpenOrders = mutableListOf<TradeLog>()

    private var cachedSymbols: List<CoinsSymbolInfo>? = null
    private var cacheTimestamp: Long = 0L
    private val CACHE_TTL_MS = 5 * 60 * 1000L
    private val cacheMutex = Mutex()

    private var balancesRestored = false

    private suspend fun ensureBalancesRestored(): Result<Unit> {
        if (!balancesRestored && tradingDao != null) {
            try {
                val savedBalances = tradingDao.getAllPaperBalances()
                if (savedBalances.isNotEmpty()) {
                    savedBalances.forEach { entity ->
                        virtualBalances[entity.asset] = entity.freeAmount
                    }
                } else {
                    // Persist initial default balances if paper_balances table is empty
                    val initialEntities = virtualBalances.map { (asset, amount) ->
                        com.example.data.local.entity.PaperBalanceEntity(asset, amount)
                    }
                    tradingDao.savePaperBalances(initialEntities)
                }
                balancesRestored = true
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to restore paper balances from database: ${e.message}", e))
            }
        }
        return Result.success(Unit)
    }

    private suspend fun fetchUsdtPhpRate(): Double {
        return try {
            val tickerRes = apiService.get24HrTicker("USDTPHP")
            val p = if (tickerRes.isSuccessful && tickerRes.body() != null) {
                tickerRes.body()!!.lastPrice?.toDoubleOrNull() ?: 0.0
            } else 0.0
            if (p > 45.0) p else 58.20
        } catch (e: Exception) {
            58.20
        }
    }

    override suspend fun checkConnectivity(): Result<Long> {
        return try {
            val response = apiService.getServerTime()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.serverTime)
            } else {
                Result.failure(Exception("Failed to fetch server time: HTTP ${response.code()}"))
            }
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
            cacheMutex.withLock {
                val currentNow = System.currentTimeMillis()
                if (cachedSymbols != null && (currentNow - cacheTimestamp) < CACHE_TTL_MS) {
                    return@withLock Result.success(cachedSymbols!!)
                }

                try {
                    val response = apiService.getExchangeInfo()
                    if (response.isSuccessful && response.body() != null) {
                        val usdtRate = fetchUsdtPhpRate()
                        val globalTickers = GlobalCrossRateFetcher.getGlobalUsdtTickers()

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
                            val (base, quote) = splitSymbol(upper)
                            val globalUsdt = if (quote == "PHP" && base != "USDT" && base != "USDC") {
                                globalTickers["${base}USDT"]
                            } else null

                            var price = 0.0
                            var change = 0.0
                            var vol = 0.0
                            val ticker = tickerMap[upper]
                            val bid = ticker?.bidPrice?.toDoubleOrNull() ?: 0.0
                            val ask = ticker?.askPrice?.toDoubleOrNull() ?: 0.0

                            if (globalUsdt != null) {
                                price = globalUsdt.optDouble("lastPrice", 0.0) * usdtRate
                                change = globalUsdt.optDouble("priceChangePercent", 0.0)
                                vol = globalUsdt.optDouble("volume", 0.0)
                            } else {
                                price = ticker?.lastPrice?.toDoubleOrNull() ?: 0.0
                                change = ticker?.priceChangePercent?.toDoubleOrNull() ?: 0.0
                                vol = ticker?.volume?.toDoubleOrNull() ?: 0.0
                            }

                            if (price <= 0.0) {
                                if (bid > 0.0 && ask > 0.0) price = (bid + ask) / 2.0
                                else if (bid > 0.0) price = bid
                                else if (ask > 0.0) price = ask
                            }

                            if (tickerMap.isNotEmpty()) {
                                if (price <= 0.0 && bid <= 0.0 && ask <= 0.0) {
                                    return@mapNotNull null
                                }
                            }

                            val finalPrice = if (price > 0.0) price else 1.0
                            sym.copy(lastPrice = finalPrice, priceChangePercent = change, volume24h = vol)
                        }
                        cachedSymbols = list
                        cacheTimestamp = System.currentTimeMillis()
                        Result.success(list)
                    } else {
                        Result.failure(Exception("Failed to fetch available symbols: HTTP ${response.code()}"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun getTicker(symbol: String): Result<MarketTicker> {
        val upper = symbol.uppercase()
        val (base, quote) = splitSymbol(upper)
        val usdtRate = fetchUsdtPhpRate()

        if (quote == "PHP" && base != "USDT" && base != "USDC") {
            val globalTickers = GlobalCrossRateFetcher.getGlobalUsdtTickers()
            val globalObj = globalTickers["${base}USDT"]
            if (globalObj != null) {
                val gPrice = globalObj.optDouble("lastPrice", 0.0) * usdtRate
                val gChange = globalObj.optDouble("priceChangePercent", 0.0)
                val gHigh = globalObj.optDouble("highPrice", 0.0) * usdtRate
                val gLow = globalObj.optDouble("lowPrice", 0.0) * usdtRate
                val gVol = globalObj.optDouble("volume", 0.0)
                val gBid = globalObj.optDouble("bidPrice", gPrice / usdtRate) * usdtRate
                val gAsk = globalObj.optDouble("askPrice", gPrice / usdtRate) * usdtRate

                if (gPrice > 0.0) {
                    return Result.success(
                        MarketTicker(
                            symbol = symbol,
                            baseAsset = base,
                            quoteAsset = quote,
                            price = gPrice,
                            change24h = gChange,
                            high24h = gHigh,
                            low24h = gLow,
                            volume24h = gVol,
                            bidPrice = gBid,
                            askPrice = gAsk
                        )
                    )
                }
            }
        }

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
                val priceRes = apiService.getTickerPrice(symbol)
                if (priceRes.isSuccessful && priceRes.body() != null) {
                    val p = priceRes.body()?.price?.toDoubleOrNull() ?: 0.0
                    Result.success(
                        MarketTicker(
                            symbol = symbol,
                            baseAsset = base,
                            quoteAsset = quote,
                            price = p,
                            change24h = 0.0,
                            high24h = p,
                            low24h = p,
                            volume24h = 0.0
                        )
                    )
                } else {
                    Result.failure(Exception("Failed to fetch ticker for $symbol: HTTP ${priceRes.code()}"))
                }
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
        val upper = symbol.uppercase()
        val (base, quote) = splitSymbol(upper)
        val usdtRate = fetchUsdtPhpRate()

        val directResult = try {
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
                if (candles.isNotEmpty()) {
                    Result.success(candles)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }

        if (directResult != null) return directResult

        if (quote == "PHP" && base != "USDT" && base != "USDC") {
            val globalKlines = GlobalCrossRateFetcher.getGlobalKlines(base, interval, limit, usdtRate)
            if (globalKlines.isNotEmpty()) {
                return Result.success(globalKlines)
            }
        }

        val basePrice = getTicker(symbol).getOrNull()?.price ?: 5000000.0
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
            val variance = (Math.random() - 0.5) * (basePrice * 0.01)
            val open = basePrice + variance
            val close = open + (Math.random() - 0.5) * (basePrice * 0.008)
            val high = maxOf(open, close) + Math.random() * (basePrice * 0.004)
            val low = minOf(open, close) - Math.random() * (basePrice * 0.004)
            val volume = Math.random() * 10.0
            CandleStick(openTime = time, open = open, high = high, low = low, close = close, volume = volume, closeTime = time + intervalMs)
        }
        return Result.success(synthetic)
    }

    override suspend fun getOrderBook(
        symbol: String,
        limit: Int
    ): Result<com.example.data.model.OrderBookSnapshot> {
        val upper = symbol.uppercase()
        val (base, quote) = splitSymbol(upper)
        val usdtRate = fetchUsdtPhpRate()

        val directBook = try {
            val res = apiService.getOrderBook(symbol, limit)
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
        } catch (e: Exception) {
            null
        }

        if (directBook != null) return Result.success(directBook)

        if (quote == "PHP" && base != "USDT" && base != "USDC") {
            val globalBook = GlobalCrossRateFetcher.getGlobalOrderBook(base, limit, usdtRate, symbol)
            if (globalBook != null) return Result.success(globalBook)
        }

        val basePrice = getTicker(symbol).getOrNull()?.price ?: 5000000.0
        val bids = (1..limit).map { i -> com.example.data.model.OrderBookEntry(basePrice * (1.0 - i * 0.001), 0.5 * i) }
        val asks = (1..limit).map { i -> com.example.data.model.OrderBookEntry(basePrice * (1.0 + i * 0.001), 0.5 * i) }
        val totalBidQty = bids.sumOf { it.quantity }
        val totalAskQty = asks.sumOf { it.quantity }
        val totalVol = totalBidQty + totalAskQty
        val buyPressure = if (totalVol > 0) (totalBidQty / totalVol) * 100.0 else 50.0

        return Result.success(
            com.example.data.model.OrderBookSnapshot(
                symbol = symbol,
                bids = bids,
                asks = asks,
                buyPressurePercent = buyPressure,
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
        val (base, quote) = splitSymbol(symbol)
        
        if (base.isBlank() || quote.isBlank()) {
            return Result.failure(IllegalArgumentException("Malformed symbol: $symbol"))
        }
        if (base == quote) {
            return Result.failure(IllegalArgumentException("Base and quote assets must be distinct"))
        }
        if (quote != "PHP" && quote != "USDT" && quote != "USDC") {
            return Result.failure(IllegalArgumentException("Unsupported quote currency: $quote"))
        }

        // Validate the full symbol against the cached available symbols
        val availableSymbolsResult = getAvailableSymbols()
        if (availableSymbolsResult.isFailure) {
            return Result.failure(Exception("Cannot submit order: unable to verify supported symbols.", availableSymbolsResult.exceptionOrNull()))
        }
        val supportedSymbols = availableSymbolsResult.getOrThrow()
        val isSupported = supportedSymbols.any { it.symbol.equals(symbol, ignoreCase = true) }
        
        if (!isSupported) {
            return Result.failure(IllegalArgumentException("Unsupported trading pair: $symbol"))
        }

        // Enforce authoritative base-asset list derived from the hardcoded initial default keys.
        val allowedBases = setOf("BTC", "ETH", "SOL", "XRP")
        if (base !in allowedBases) {
            return Result.failure(IllegalArgumentException("Unsupported base asset: $base"))
        }

        val currentPrice = price ?: run {
            val tickerResult = getTicker(symbol)
            if (tickerResult.isFailure) {
                return Result.failure(Exception("Cannot submit order: market price unavailable.", tickerResult.exceptionOrNull()))
            }
            tickerResult.getOrThrow().price
        }

        if (currentPrice.isNaN() || currentPrice.isInfinite() || currentPrice <= 0.0) {
            return Result.failure(IllegalArgumentException("Invalid execution price: $currentPrice"))
        }

        val execQty: Double
        val totalAmount: Double

        if (quoteOrderAmount != null) {
            if (quoteOrderAmount.isNaN() || quoteOrderAmount.isInfinite() || quoteOrderAmount <= 0.0) {
                return Result.failure(IllegalArgumentException("Invalid quoteOrderAmount: $quoteOrderAmount"))
            }
            totalAmount = quoteOrderAmount
            execQty = totalAmount / currentPrice
        } else {
            if (quantity.isNaN() || quantity.isInfinite() || quantity <= 0.0) {
                return Result.failure(IllegalArgumentException("Invalid quantity: $quantity"))
            }
            execQty = quantity
            totalAmount = execQty * currentPrice
        }

        val feeRate = 0.001 // Coins.ph standard spot fee: 0.10%
        val fee = totalAmount * feeRate
        val totalCost = totalAmount + fee
        val netProceeds = totalAmount - fee

        if (execQty.isNaN() || execQty.isInfinite() || execQty <= 0.0) {
            return Result.failure(IllegalArgumentException("Invalid calculated execution quantity: $execQty"))
        }
        if (totalAmount.isNaN() || totalAmount.isInfinite() || totalAmount <= 0.0) {
            return Result.failure(IllegalArgumentException("Invalid calculated total amount: $totalAmount"))
        }
        if (fee.isNaN() || fee.isInfinite() || fee < 0.0) {
            return Result.failure(IllegalArgumentException("Invalid calculated fee: $fee"))
        }
        if (totalCost.isNaN() || totalCost.isInfinite() || totalCost <= 0.0) {
            return Result.failure(IllegalArgumentException("Invalid total cost calculation: $totalCost"))
        }
        if (netProceeds.isNaN() || netProceeds.isInfinite() || netProceeds <= 0.0) {
            return Result.failure(IllegalArgumentException("Invalid net proceeds calculation: $netProceeds"))
        }

        val upperSide = side.uppercase()
        if (upperSide != "BUY" && upperSide != "SELL") {
            return Result.failure(IllegalArgumentException("Invalid order side: $side. Must be BUY or SELL."))
        }

        return mutex.withLock {
            val restoreRes = ensureBalancesRestored()
            if (restoreRes.isFailure) {
                return@withLock Result.failure(restoreRes.exceptionOrNull() ?: Exception("Database restore error."))
            }
            val currentQuoteBalance = virtualBalances[quote] ?: 0.0
            val currentBaseBalance = virtualBalances[base] ?: 0.0

            if (currentQuoteBalance.isNaN() || currentQuoteBalance.isInfinite() || currentQuoteBalance < 0.0) {
                return@withLock Result.failure(IllegalStateException("Corrupted quote balance: $currentQuoteBalance"))
            }
            if (currentBaseBalance.isNaN() || currentBaseBalance.isInfinite() || currentBaseBalance < 0.0) {
                return@withLock Result.failure(IllegalStateException("Corrupted base balance: $currentBaseBalance"))
            }

            if (upperSide == "BUY") {
                if (currentQuoteBalance < totalCost) {
                    return@withLock Result.failure(
                        IllegalStateException("Insufficient paper balance: Required $totalCost $quote, Available $currentQuoteBalance $quote")
                    )
                }
                
                val newQuote = currentQuoteBalance - totalCost
                val newBase = currentBaseBalance + execQty
                
                if (newQuote.isNaN() || newQuote.isInfinite() || newQuote < 0.0) {
                    return@withLock Result.failure(IllegalArgumentException("Invalid resulting quote balance: $newQuote"))
                }
                if (newBase.isNaN() || newBase.isInfinite() || newBase < 0.0) {
                    return@withLock Result.failure(IllegalArgumentException("Invalid resulting base balance: $newBase"))
                }

                virtualBalances[quote] = newQuote
                virtualBalances[base] = newBase
                tradingDao?.savePaperBalances(
                    listOf(
                        com.example.data.local.entity.PaperBalanceEntity(quote, newQuote),
                        com.example.data.local.entity.PaperBalanceEntity(base, newBase)
                    )
                )
            } else { // SELL
                if (currentBaseBalance < execQty) {
                    return@withLock Result.failure(
                        IllegalStateException("Insufficient paper asset: Required $execQty $base, Available $currentBaseBalance $base")
                    )
                }
                
                val newBase = currentBaseBalance - execQty
                val newQuote = currentQuoteBalance + netProceeds
                
                if (newBase.isNaN() || newBase.isInfinite() || newBase < 0.0) {
                    return@withLock Result.failure(IllegalArgumentException("Invalid resulting base balance: $newBase"))
                }
                if (newQuote.isNaN() || newQuote.isInfinite() || newQuote < 0.0) {
                    return@withLock Result.failure(IllegalArgumentException("Invalid resulting quote balance: $newQuote"))
                }

                virtualBalances[base] = newBase
                virtualBalances[quote] = newQuote
                tradingDao?.savePaperBalances(
                    listOf(
                        com.example.data.local.entity.PaperBalanceEntity(base, newBase),
                        com.example.data.local.entity.PaperBalanceEntity(quote, newQuote)
                    )
                )
            }

            val order = TradeLog(
                id = "sim_${UUID.randomUUID().toString().take(8)}",
                timestamp = System.currentTimeMillis(),
                type = type,
                symbol = symbol,
                side = upperSide,
                price = currentPrice,
                quantity = execQty,
                amount = totalAmount,
                fee = fee,
                feeCurrency = quote,
                status = "FILLED",
                orderId = "sim_ord_${System.currentTimeMillis() % 1000000}",
                message = "Simulated $side order filled at $currentPrice $quote (Fee: $fee $quote)",
                tradingMode = TradingMode.PAPER
            )

            Result.success(order)
        }
    }

    override suspend fun cancelOrder(symbol: String, orderId: String): Result<Boolean> = mutex.withLock {
        simulatedOpenOrders.removeAll { it.orderId == orderId }
        return Result.success(true)
    }

    override suspend fun getOpenOrders(symbol: String): Result<List<TradeLog>> = mutex.withLock {
        return Result.success(simulatedOpenOrders.filter { it.symbol == symbol })
    }

    override suspend fun getBalances(): Result<List<CoinsBalance>> = mutex.withLock {
        val restoreRes = ensureBalancesRestored()
        if (restoreRes.isFailure) {
            return@withLock Result.failure(restoreRes.exceptionOrNull() ?: Exception("Database restore error."))
        }
        val list = virtualBalances.map { (asset, freeAmount) ->
            CoinsBalance(
                asset = asset,
                free = String.format("%.6f", freeAmount),
                locked = "0.000000"
            )
        }
        return Result.success(list)
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
