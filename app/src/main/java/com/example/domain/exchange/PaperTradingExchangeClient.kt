package com.example.domain.exchange

import com.example.data.model.CandleStick
import com.example.data.model.CoinsBalance
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.MarketTicker
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import com.example.data.network.CoinsPhApiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

        return cacheMutex.withLock {
            val currentNow = System.currentTimeMillis()
            if (cachedSymbols != null && (currentNow - cacheTimestamp) < CACHE_TTL_MS) {
                return@withLock Result.success(cachedSymbols!!)
            }

            try {
                val response = apiService.getExchangeInfo()
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!.symbols.filter {
                        it.status == "TRADING" || it.isSpotTradingAllowed == true
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

    override suspend fun getTicker(symbol: String): Result<MarketTicker> {
        return try {
            val tickerRes = apiService.get24HrTicker(symbol)
            if (tickerRes.isSuccessful && tickerRes.body() != null) {
                val t = tickerRes.body()!!
                val price = t.lastPrice?.toDoubleOrNull() ?: throw Exception("Invalid price data from 24hr ticker")
                if (price.isNaN() || price.isInfinite() || price <= 0.0) {
                    throw Exception("Market price must be finite and greater than zero")
                }
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
                // Try simple price endpoint
                val priceRes = apiService.getTickerPrice(symbol)
                if (priceRes.isSuccessful && priceRes.body() != null) {
                    val p = priceRes.body()?.price?.toDoubleOrNull() ?: throw Exception("Invalid price data from ticker price")
                    if (p.isNaN() || p.isInfinite() || p <= 0.0) {
                        throw Exception("Market price must be finite and greater than zero")
                    }
                    val (base, quote) = splitSymbol(symbol)
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

    override suspend fun getOrderBook(
        symbol: String,
        limit: Int
    ): Result<com.example.data.model.OrderBookSnapshot> {
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
                if (candles.isNotEmpty()) {
                    return Result.success(candles)
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
            Result.success(synthetic)
        } catch (e: Exception) {
            val basePrice = 5000000.0
            val now = System.currentTimeMillis()
            val intervalMs = 300_000L
            val synthetic = (limit - 1 downTo 0).map { i ->
                val time = now - (i * intervalMs)
                CandleStick(openTime = time, open = basePrice, high = basePrice * 1.002, low = basePrice * 0.998, close = basePrice, volume = 5.0, closeTime = time + intervalMs)
            }
            Result.success(synthetic)
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
