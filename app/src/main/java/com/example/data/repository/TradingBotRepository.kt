package com.example.data.repository

import com.example.data.local.dao.TradingDao
import com.example.data.local.entity.BotSettingsEntity
import com.example.data.local.entity.OpenPositionEntity
import com.example.data.local.entity.PaperBalanceEntity
import com.example.data.local.entity.PortfolioSnapshotEntity
import com.example.data.local.entity.TradeLogEntity
import com.example.data.model.BotRuntimeStatus
import com.example.data.model.BotSettings
import com.example.data.model.BotState
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.MarketTicker
import com.example.data.model.OpenPosition
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.CandleStick
import com.example.data.model.ChartInterval
import com.example.data.model.PricePoint
import com.example.data.model.StrategyType
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import com.example.domain.exchange.CoinsPhExchangeClient
import com.example.domain.exchange.ExchangeClient
import com.example.domain.exchange.PaperTradingExchangeClient
import com.example.domain.risk.RiskCheckResult
import com.example.domain.risk.RiskManager
import com.example.domain.strategy.DcaStrategy
import com.example.domain.strategy.GridTradingStrategy
import com.example.domain.strategy.RsiSwingStrategy
import com.example.domain.strategy.SignalAction
import com.example.domain.strategy.TradingSignal
import com.example.domain.strategy.TradingStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

interface TradingBotRepository {
    val botSettingsFlow: Flow<BotSettings>
    val botStatusFlow: Flow<BotRuntimeStatus>
    val portfolioFlow: Flow<PortfolioSnapshot>
    val tradeLogsFlow: Flow<List<TradeLog>>
    val marketTickerFlow: Flow<MarketTicker?>
    val priceHistoryFlow: Flow<List<PricePoint>>
    val candlesFlow: Flow<List<CandleStick>>
    val selectedIntervalFlow: Flow<ChartInterval>
    val openPositionsFlow: Flow<List<OpenPosition>>
    val orderBookFlow: Flow<com.example.data.model.OrderBookSnapshot>

    suspend fun setChartInterval(interval: ChartInterval)
    suspend fun updateSettings(settings: BotSettings): Result<Unit>
    suspend fun startBot(): Result<Unit>
    suspend fun pauseBot(): Result<Unit>
    suspend fun stopBot(): Result<Unit>
    suspend fun emergencyStop(): Result<Unit>
    suspend fun setTradingMode(mode: TradingMode): Result<Unit>
    suspend fun switchSymbol(symbol: String): Result<Unit>
    suspend fun getAvailableSymbols(): Result<List<CoinsSymbolInfo>>
    suspend fun fetchLatestTicker(symbol: String): Result<MarketTicker>
    suspend fun closePosition(positionId: String): Result<Unit>
    suspend fun cancelAllPendingOrders(): Result<Unit>
    suspend fun manualBuyNow(): Result<Unit>
    suspend fun closeAllPositions(): Result<Unit>
    suspend fun runQuickBacktest(): Result<com.example.data.model.BacktestResult>
    suspend fun autoOptimizeStrategy(): Result<com.example.data.model.BacktestResult>
    suspend fun switchExchangePlatform(platform: com.example.data.model.ExchangePlatform): Result<Unit>
    suspend fun configureLiveCredentials(apiKey: String, secretKey: String): Result<Unit>
    suspend fun resetPaperPortfolio(): Result<Unit>
    fun hasLiveCredentials(): Boolean
    fun onAppForegrounded()
    fun onAppBackgrounded()
}

class TradingBotRepositoryImpl(
    private val paperExchangeClient: PaperTradingExchangeClient,
    private val liveExchangeClient: ExchangeClient,
    private val tradingDao: TradingDao? = null,
    private val sharedPreferences: android.content.SharedPreferences? = null,
    private val context: android.content.Context? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : TradingBotRepository {

    private val notificationManager = if (context != null) com.example.util.BotNotificationManager(context) else null

    private val riskManager = RiskManager()
    private val dcaStrategy = DcaStrategy()
    private val rsiStrategy = RsiSwingStrategy()
    private val gridStrategy = GridTradingStrategy()
    private val bollingerStrategy = com.example.domain.strategy.BollingerBandsStrategy()
    private val turtleStrategy = com.example.domain.strategy.TurtleBreakoutStrategy()
    private val vwapStrategy = com.example.domain.strategy.VwapPinbarStrategy()
    private val godModeStrategy = com.example.domain.strategy.AiGodModeStrategy()

    private val _botSettings = MutableStateFlow(BotSettings())
    override val botSettingsFlow: Flow<BotSettings> = _botSettings.asStateFlow()

    private val _botStatus = MutableStateFlow(
        BotRuntimeStatus(
            state = BotState.STOPPED,
            mode = TradingMode.PAPER,
            activeSymbol = "BTCPHP",
            backendHealthy = true,
            exchangeConnected = true
        )
    )
    override val botStatusFlow: Flow<BotRuntimeStatus> = _botStatus.asStateFlow()

    private val _portfolio = MutableStateFlow(PortfolioSnapshot())
    override val portfolioFlow: Flow<PortfolioSnapshot> = _portfolio.asStateFlow()

    private val _orderBook = MutableStateFlow(com.example.data.model.OrderBookSnapshot())
    override val orderBookFlow: Flow<com.example.data.model.OrderBookSnapshot> = _orderBook.asStateFlow()

    private val _tradeLogs = MutableStateFlow<List<TradeLog>>(emptyList())
    override val tradeLogsFlow: Flow<List<TradeLog>> = _tradeLogs.asStateFlow()

    private val _marketTicker = MutableStateFlow<MarketTicker?>(null)
    override val marketTickerFlow: Flow<MarketTicker?> = _marketTicker.asStateFlow()

    private val _priceHistory = MutableStateFlow<List<PricePoint>>(emptyList())
    override val priceHistoryFlow: Flow<List<PricePoint>> = _priceHistory.asStateFlow()

    private val _candles = MutableStateFlow<List<CandleStick>>(emptyList())
    override val candlesFlow: Flow<List<CandleStick>> = _candles.asStateFlow()

    private val _selectedInterval = MutableStateFlow(ChartInterval.FIVE_MIN)
    override val selectedIntervalFlow: Flow<ChartInterval> = _selectedInterval.asStateFlow()

    private val _openPositions = MutableStateFlow<List<OpenPosition>>(emptyList())
    override val openPositionsFlow: Flow<List<OpenPosition>> = _openPositions.asStateFlow()

    private var executionJob: Job? = null
    private var tickerPollingJob: Job? = null
    private var isForegrounded = true
    private val binanceExchangeClient = com.example.domain.exchange.BinanceExchangeClient()

    init {
        sharedPreferences?.let { prefs ->
            val coinsKey = prefs.getString("coins_api_key", "") ?: ""
            val coinsSecret = prefs.getString("coins_secret_key", "") ?: ""
            if (coinsKey.isNotBlank() && coinsSecret.isNotBlank() && liveExchangeClient is CoinsPhExchangeClient) {
                liveExchangeClient.setCredentials(coinsKey, coinsSecret)
            }
            val binanceKey = prefs.getString("binance_api_key", "") ?: ""
            val binanceSecret = prefs.getString("binance_secret_key", "") ?: ""
            if (binanceKey.isNotBlank() && binanceSecret.isNotBlank()) {
                binanceExchangeClient.setCredentials(binanceKey, binanceSecret)
            }
        }
        coroutineScope.launch {
            loadStateFromDatabase()
            fetchKlinesForActiveSymbol()
            if (isForegrounded) {
                startTickerPolling(_botSettings.value.symbol)
            }
        }
    }

    private suspend fun fetchKlinesForActiveSymbol() {
        val client = getMarketDataClient()
        val symbol = _botSettings.value.symbol
        val interval = _selectedInterval.value.code
        val res = client.getKlines(symbol, interval, 120)
        res.onSuccess { klines ->
            _candles.value = klines
        }
    }

    private suspend fun loadStateFromDatabase() {
        tradingDao?.let { dao ->
            val dbSettings = dao.getBotSettings()?.toDomain()
            if (dbSettings != null) {
                var restoredSettings: BotSettings = dbSettings
                sharedPreferences?.let { prefs ->
                    restoredSettings = restoredSettings.copy(
                        trailingTakeProfitEnabled = prefs.getBoolean("trailingTakeProfitEnabled", restoredSettings.trailingTakeProfitEnabled),
                        trailingStopPercentage = prefs.getFloat("trailingStopPercentage", restoredSettings.trailingStopPercentage.toFloat()).toDouble(),
                        smartEntryEnabled = prefs.getBoolean("smartEntryEnabled", restoredSettings.smartEntryEnabled),
                        breakEvenLockEnabled = prefs.getBoolean("breakEvenLockEnabled", restoredSettings.breakEvenLockEnabled),
                        greenCandleConfirmation = prefs.getBoolean("greenCandleConfirmation", restoredSettings.greenCandleConfirmation),
                        dcaMultiplier = prefs.getFloat("dcaMultiplier", restoredSettings.dcaMultiplier.toFloat()).toDouble(),
                        exchangePlatform = com.example.data.model.ExchangePlatform.valueOf(prefs.getString("exchangePlatform", restoredSettings.exchangePlatform.name) ?: "COINS_PH"),
                        partialTakeProfitEnabled = prefs.getBoolean("partialTakeProfitEnabled", restoredSettings.partialTakeProfitEnabled),
                        partialTakeProfitPercentage = prefs.getFloat("partialTakeProfitPercentage", restoredSettings.partialTakeProfitPercentage.toFloat()).toDouble(),
                        atrDynamicTargetsEnabled = prefs.getBoolean("atrDynamicTargetsEnabled", restoredSettings.atrDynamicTargetsEnabled),
                        orderBookWallFilterEnabled = prefs.getBoolean("orderBookWallFilterEnabled", restoredSettings.orderBookWallFilterEnabled),
                        kellyDynamicSizingEnabled = prefs.getBoolean("kellyDynamicSizingEnabled", restoredSettings.kellyDynamicSizingEnabled),
                        seafarerLowDataMode = prefs.getBoolean("seafarerLowDataMode", restoredSettings.seafarerLowDataMode)
                    )
                }
                _botSettings.value = restoredSettings
                _botStatus.value = _botStatus.value.copy(
                    activeSymbol = restoredSettings.symbol,
                    mode = restoredSettings.tradingMode
                )
            } else {
                sharedPreferences?.let { prefs ->
                    _botSettings.value = _botSettings.value.copy(
                        trailingTakeProfitEnabled = prefs.getBoolean("trailingTakeProfitEnabled", false),
                        trailingStopPercentage = prefs.getFloat("trailingStopPercentage", 1.0f).toDouble(),
                        smartEntryEnabled = prefs.getBoolean("smartEntryEnabled", false),
                        breakEvenLockEnabled = prefs.getBoolean("breakEvenLockEnabled", false),
                        greenCandleConfirmation = prefs.getBoolean("greenCandleConfirmation", false),
                        dcaMultiplier = prefs.getFloat("dcaMultiplier", 1.0f).toDouble(),
                        exchangePlatform = com.example.data.model.ExchangePlatform.valueOf(prefs.getString("exchangePlatform", "COINS_PH") ?: "COINS_PH"),
                        partialTakeProfitEnabled = prefs.getBoolean("partialTakeProfitEnabled", false),
                        partialTakeProfitPercentage = prefs.getFloat("partialTakeProfitPercentage", 1.8f).toDouble(),
                        atrDynamicTargetsEnabled = prefs.getBoolean("atrDynamicTargetsEnabled", false),
                        orderBookWallFilterEnabled = prefs.getBoolean("orderBookWallFilterEnabled", false),
                        kellyDynamicSizingEnabled = prefs.getBoolean("kellyDynamicSizingEnabled", false),
                        seafarerLowDataMode = prefs.getBoolean("seafarerLowDataMode", false)
                    )
                }
            }
            val dbPortfolio = dao.getPortfolioSnapshot()?.toDomain()
            if (dbPortfolio != null) {
                _portfolio.value = dbPortfolio
            }
            val dbPositions = dao.getAllOpenPositions().map { it.toDomain() }
            if (dbPositions.isNotEmpty()) {
                _openPositions.value = dbPositions
                _botStatus.value = _botStatus.value.copy(openPositionsCount = dbPositions.size)
            }
            val dbLogs = dao.getAllTradeLogs().map { it.toDomain() }
            if (dbLogs.isNotEmpty()) {
                _tradeLogs.value = dbLogs

                val currentMode = _botSettings.value.tradingMode
                val closedSellLogs = _tradeLogs.value.filter {
                    it.tradingMode == currentMode &&
                    it.side.equals("SELL", ignoreCase = true) &&
                    it.status.equals("FILLED", ignoreCase = true)
                }
                val totalRealizedPnl = closedSellLogs.sumOf { it.realizedPnl }
                val totalTrades = closedSellLogs.size
                val winningTrades = closedSellLogs.count { it.realizedPnl > 0.0 }
                val winRate = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0

                _portfolio.value = _portfolio.value.copy(
                    totalRealizedPnl = totalRealizedPnl,
                    dailyPnl = totalRealizedPnl,
                    totalTrades = totalTrades,
                    winningTrades = winningTrades,
                    winRate = winRate
                )
            }
        }
        syncPortfolioBalances()
    }

    override fun onAppForegrounded() {
        isForegrounded = true
        startTickerPolling(_botSettings.value.symbol)
    }

    override fun onAppBackgrounded() {
        isForegrounded = false
        if (_botStatus.value.state != BotState.RUNNING) {
            tickerPollingJob?.cancel()
            tickerPollingJob = null
        }
    }

    private fun getActiveExchangeClient(): ExchangeClient {
        val platform = _botSettings.value.exchangePlatform
        return if (_botSettings.value.tradingMode == TradingMode.LIVE) {
            if (platform == com.example.data.model.ExchangePlatform.BINANCE) binanceExchangeClient else liveExchangeClient
        } else {
            paperExchangeClient
        }
    }

    private fun getMarketDataClient(): ExchangeClient {
        val platform = _botSettings.value.exchangePlatform
        return if (platform == com.example.data.model.ExchangePlatform.BINANCE) binanceExchangeClient else liveExchangeClient
    }

    private fun startTickerPolling(symbol: String) {
        if (!isForegrounded && _botStatus.value.state != BotState.RUNNING) return
        tickerPollingJob?.cancel()
        tickerPollingJob = coroutineScope.launch {
            var pollCount = 0
            while (isActive) {
                try {
                    val client = getMarketDataClient()
                    val res = client.getTicker(symbol)
                    res.onSuccess { ticker ->
                        _marketTicker.value = ticker
                        val currentHistory = _priceHistory.value.toMutableList()
                        currentHistory.add(PricePoint(ticker.lastUpdated, ticker.price))
                        if (currentHistory.size > 1000) {
                            currentHistory.removeAt(0)
                        }
                        _priceHistory.value = currentHistory

                        // Real-time update of last candle
                        val currentCandles = _candles.value.toMutableList()
                        if (currentCandles.isNotEmpty()) {
                            val lastIndex = currentCandles.size - 1
                            val last = currentCandles[lastIndex]
                            val newHigh = maxOf(last.high, ticker.price)
                            val newLow = if (last.low > 0.0) minOf(last.low, ticker.price) else ticker.price
                            currentCandles[lastIndex] = last.copy(
                                close = ticker.price,
                                high = newHigh,
                                low = newLow
                            )
                            _candles.value = currentCandles
                        }

                        // Update unrealized PnL of open positions matching this ticker's symbol
                        updateOpenPositionsPnl(ticker.symbol, ticker.price)
                    }.onFailure { err ->
                        println("DEBUG getTicker failure: ${err.message}")
                    }

                    // Also fetch/update open positions that belong to a different symbol than active symbol
                    val openPositions = _openPositions.value
                    if (openPositions.isNotEmpty()) {
                        val otherSymbols = openPositions
                            .map { it.symbol }
                            .filter { posSymbol -> !symbolsMatch(posSymbol, symbol) }
                            .distinct()

                        for (otherSymbol in otherSymbols) {
                            val otherRes = client.getTicker(otherSymbol)
                            otherRes.onSuccess { otherTicker ->
                                updateOpenPositionsPnl(otherTicker.symbol, otherTicker.price)
                            }
                        }
                    }
                    
                    val orderBookRes = client.getOrderBook(symbol, 5)
                    orderBookRes.onSuccess { ob -> _orderBook.value = ob }

                    val isLowData = _botSettings.value.seafarerLowDataMode
                    val klineInterval = if (isLowData) 6 else 3
                    val syncInterval = if (isLowData) 6 else 5

                    pollCount++
                    if (pollCount % klineInterval == 0) {
                        fetchKlinesForActiveSymbol()
                    }
                    if (pollCount % syncInterval == 0) {
                        syncPortfolioBalances()
                    }
                } catch (e: Exception) {
                    println("DEBUG startTickerPolling exception: ${e.message}")
                }
                delay(if (_botSettings.value.seafarerLowDataMode) 8000L else 3000L)
            }
        }
    }

    override suspend fun updateSettings(settings: BotSettings): Result<Unit> {
        val previousSymbol = _botSettings.value.symbol
        val updated = settings.copy(lastUpdated = System.currentTimeMillis())
        _botSettings.value = updated
        _botStatus.value = _botStatus.value.copy(
            activeSymbol = settings.symbol,
            mode = settings.tradingMode
        )
        tradingDao?.saveBotSettings(BotSettingsEntity.fromDomain(updated))

        sharedPreferences?.edit()?.apply {
            putBoolean("trailingTakeProfitEnabled", updated.trailingTakeProfitEnabled)
            putFloat("trailingStopPercentage", updated.trailingStopPercentage.toFloat())
            putBoolean("smartEntryEnabled", updated.smartEntryEnabled)
            putBoolean("breakEvenLockEnabled", updated.breakEvenLockEnabled)
            putBoolean("greenCandleConfirmation", updated.greenCandleConfirmation)
            putFloat("dcaMultiplier", updated.dcaMultiplier.toFloat())
            putBoolean("neverSellAtLoss", updated.neverSellAtLoss)
            putBoolean("spreadGuardEnabled", updated.spreadGuardEnabled)
            putFloat("maxSpreadPercentage", updated.maxSpreadPercentage.toFloat())
            putBoolean("crashTrendFilterEnabled", updated.crashTrendFilterEnabled)
            putString("exchangePlatform", updated.exchangePlatform.name)
            putBoolean("partialTakeProfitEnabled", updated.partialTakeProfitEnabled)
            putFloat("partialTakeProfitPercentage", updated.partialTakeProfitPercentage.toFloat())
            putBoolean("atrDynamicTargetsEnabled", updated.atrDynamicTargetsEnabled)
            putBoolean("orderBookWallFilterEnabled", updated.orderBookWallFilterEnabled)
            putBoolean("kellyDynamicSizingEnabled", updated.kellyDynamicSizingEnabled)
            putBoolean("seafarerLowDataMode", updated.seafarerLowDataMode)
            apply()
        }

        if (previousSymbol != settings.symbol) {
            _marketTicker.value = null
            startTickerPolling(settings.symbol)
        }
        syncPortfolioBalances()
        return Result.success(Unit)
    }

    override suspend fun setChartInterval(interval: ChartInterval) {
        _selectedInterval.value = interval
        fetchKlinesForActiveSymbol()
    }

    override suspend fun switchSymbol(symbol: String): Result<Unit> {
        val upper = symbol.uppercase()
        val quote = when {
            upper.endsWith("USDT") -> "USDT"
            upper.endsWith("USDC") -> "USDC"
            else -> "PHP"
        }
        val base = upper.removeSuffix(quote)
        val current = _botSettings.value
        if (current.symbol != symbol) {
            _priceHistory.value = emptyList()
            _marketTicker.value = null
            _candles.value = emptyList()
        }
        updateSettings(
            current.copy(
                symbol = symbol,
                quoteCurrency = quote,
                baseCurrency = base
            )
        )
        fetchKlinesForActiveSymbol()
        syncPortfolioBalances()
        return Result.success(Unit)
    }

    override suspend fun startBot(): Result<Unit> {
        val settings = _botSettings.value

        // Validate mode safety
        if (settings.tradingMode == TradingMode.LIVE) {
            val liveCheck = liveExchangeClient.checkConnectivity()
            if (liveCheck.isFailure) {
                _botStatus.value = _botStatus.value.copy(
                    state = BotState.ERROR,
                    lastErrorMessage = "Coins.ph Exchange connection failed. Live trading not authorized."
                )
                return Result.failure(IllegalStateException("Coins.ph Exchange connection verification failed."))
            }
        }

        _botStatus.value = _botStatus.value.copy(
            state = BotState.RUNNING,
            lastErrorMessage = null,
            lastCheckTime = System.currentTimeMillis()
        )

        startStrategyLoop()
        return Result.success(Unit)
    }

    override suspend fun pauseBot(): Result<Unit> {
        executionJob?.cancel()
        executionJob = null
        _botStatus.value = _botStatus.value.copy(
            state = BotState.PAUSED,
            lastCheckTime = System.currentTimeMillis()
        )
        return Result.success(Unit)
    }

    override suspend fun stopBot(): Result<Unit> {
        executionJob?.cancel()
        executionJob = null
        _botStatus.value = _botStatus.value.copy(
            state = BotState.STOPPED,
            lastCheckTime = System.currentTimeMillis()
        )
        return Result.success(Unit)
    }

    override suspend fun emergencyStop(): Result<Unit> {
        executionJob?.cancel()
        executionJob = null
        _botStatus.value = _botStatus.value.copy(
            state = BotState.EMERGENCY_STOP,
            lastErrorMessage = "Emergency stop engaged: All autonomous entries paused."
        )

        // Record audit log
        val log = TradeLog(
            id = "alert_${System.currentTimeMillis()}",
            type = "SYSTEM",
            symbol = _botSettings.value.symbol,
            side = "HALT",
            price = _marketTicker.value?.price ?: 0.0,
            quantity = 0.0,
            amount = 0.0,
            fee = 0.0,
            feeCurrency = _botSettings.value.quoteCurrency,
            status = "HALTED",
            message = "EMERGENCY STOP: Autonomous trading halted by operator.",
            tradingMode = _botSettings.value.tradingMode
        )
        addTradeLog(log)
        return Result.success(Unit)
    }

    override suspend fun switchExchangePlatform(platform: com.example.data.model.ExchangePlatform): Result<Unit> {
        val current = _botSettings.value
        if (current.exchangePlatform == platform) return Result.success(Unit)
        val newSymbol = if (platform == com.example.data.model.ExchangePlatform.BINANCE) {
            if (current.symbol.endsWith("PHP")) current.symbol.dropLast(3) + "USDT" else current.symbol
        } else {
            if (current.symbol.endsWith("USDT")) current.symbol.dropLast(4) + "PHP" else current.symbol
        }
        val newQuote = if (platform == com.example.data.model.ExchangePlatform.BINANCE) "USDT" else "PHP"
        
        updateSettings(current.copy(exchangePlatform = platform, symbol = newSymbol, quoteCurrency = newQuote))
        return Result.success(Unit)
    }

    override suspend fun setTradingMode(mode: TradingMode): Result<Unit> {
        if (mode == TradingMode.LIVE) {
            // Safety check before setting mode
            val check = liveExchangeClient.checkConnectivity()
            if (check.isFailure) {
                val reason = check.exceptionOrNull()?.message ?: "Cannot enable live mode without verified credentials."
                return Result.failure(
                    IllegalStateException("Live trading check failed: $reason")
                )
            }
        }
        val updatedSettings = _botSettings.value.copy(tradingMode = mode)
        updateSettings(updatedSettings)

        // Separate Paper vs. Live PnL & Trade Counters
        val closedSellLogs = _tradeLogs.value.filter {
            it.tradingMode == mode &&
            it.side.equals("SELL", ignoreCase = true) &&
            it.status.equals("FILLED", ignoreCase = true)
        }
        val totalRealizedPnl = closedSellLogs.sumOf { it.realizedPnl }
        val totalTrades = closedSellLogs.size
        val winningTrades = closedSellLogs.count { it.realizedPnl > 0.0 }
        val winRate = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0
        val allocatedCapital = _openPositions.value.sumOf { it.amountInvested }

        val updatedPort = _portfolio.value.copy(
            totalRealizedPnl = totalRealizedPnl,
            dailyPnl = totalRealizedPnl,
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            winRate = winRate,
            allocatedCapital = allocatedCapital
        )
        _portfolio.value = updatedPort
        tradingDao?.savePortfolioSnapshot(PortfolioSnapshotEntity.fromDomain(updatedPort))

        syncPortfolioBalances()
        return Result.success(Unit)
    }

    override suspend fun configureLiveCredentials(apiKey: String, secretKey: String): Result<Unit> {
        if (liveExchangeClient is CoinsPhExchangeClient) {
            liveExchangeClient.setCredentials(apiKey, secretKey)
        }
        sharedPreferences?.edit()?.apply {
            putString("coins_api_key", apiKey)
            putString("coins_secret_key", secretKey)
            apply()
        }
        return Result.success(Unit)
    }

    override fun hasLiveCredentials(): Boolean {
        return if (liveExchangeClient is CoinsPhExchangeClient) {
            liveExchangeClient.hasCredentials()
        } else false
    }

    override suspend fun getAvailableSymbols(): Result<List<CoinsSymbolInfo>> {
        return getMarketDataClient().getAvailableSymbols()
    }

    override suspend fun fetchLatestTicker(symbol: String): Result<MarketTicker> {
        val res = getMarketDataClient().getTicker(symbol)
        return res.map { it.copy(lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun closePosition(positionId: String): Result<Unit> {
        val position = _openPositions.value.firstOrNull { it.id == positionId }
            ?: return Result.failure(IllegalArgumentException("Position not found"))

        val currentPrice = if (_marketTicker.value != null && symbolsMatch(_marketTicker.value!!.symbol, position.symbol)) {
            _marketTicker.value!!.price
        } else {
            fetchLatestTicker(position.symbol).getOrNull()?.price ?: position.currentPrice
        }
        val client = getActiveExchangeClient()

        val orderRes = client.submitOrder(
            symbol = position.symbol,
            side = "SELL",
            type = "MARKET",
            quantity = position.quantity,
            price = currentPrice
        )

        return orderRes.map { order ->
            val proceeds = order.amount - order.fee
            val pnl = proceeds - position.amountInvested
            
            val updatedPositions = _openPositions.value.filter { it.id != positionId }
            _openPositions.value = updatedPositions
            tradingDao?.deleteOpenPosition(positionId)

            // Update portfolio
            val port = _portfolio.value
            val newRealizedPnl = port.totalRealizedPnl + pnl
            val newAllocated = port.allocatedCapital - position.amountInvested
            
            val totalClosedTrades = port.totalTrades + 1
            val newWins = port.winningTrades + if (pnl > 0.0) 1 else 0
            val winRate = if (totalClosedTrades > 0) (newWins.toDouble() / totalClosedTrades) * 100.0 else 0.0

            val updatedPort = port.copy(
                totalRealizedPnl = newRealizedPnl,
                dailyPnl = port.dailyPnl + pnl,
                allocatedCapital = maxOf(0.0, newAllocated),
                totalTrades = totalClosedTrades,
                winningTrades = newWins,
                winRate = winRate
            )
            _portfolio.value = updatedPort
            tradingDao?.savePortfolioSnapshot(PortfolioSnapshotEntity.fromDomain(updatedPort))

            val log = order.copy(
                realizedPnl = pnl,
                message = String.format("Closed position #%s: Realized PnL: %+.2f %s", positionId.take(6), pnl, _botSettings.value.quoteCurrency)
            )
            addTradeLog(log)
            syncPortfolioBalances()
            Unit
        }
    }

    override suspend fun cancelAllPendingOrders(): Result<Unit> {
        val client = getActiveExchangeClient()
        val openOrders = client.getOpenOrders(_botSettings.value.symbol).getOrDefault(emptyList())
        for (order in openOrders) {
            order.orderId?.let { client.cancelOrder(_botSettings.value.symbol, it) }
        }
        addTradeLog(
            TradeLog(
                id = "cancel_${System.currentTimeMillis()}",
                type = "ACTION",
                symbol = _botSettings.value.symbol,
                side = "CANCEL_ALL",
                price = _marketTicker.value?.price ?: 0.0,
                quantity = 0.0,
                amount = 0.0,
                fee = 0.0,
                feeCurrency = _botSettings.value.quoteCurrency,
                status = "COMPLETED",
                message = "Cancelled all pending orders for ${_botSettings.value.symbol}",
                tradingMode = _botSettings.value.tradingMode
            )
        )
        return Result.success(Unit)
    }

    private fun startStrategyLoop() {
        executionJob?.cancel()
        executionJob = coroutineScope.launch {
            while (isActive && _botStatus.value.state == BotState.RUNNING) {
                try {
                    evaluateAndExecuteStrategy()
                } catch (e: Exception) {
                    _botStatus.value = _botStatus.value.copy(
                        lastErrorMessage = "Strategy evaluation error: ${e.message}"
                    )
                }
                val intervalMs = (_botSettings.value.checkIntervalMinutes * 60 * 1000L).coerceAtLeast(5000L)
                delay(intervalMs)
            }
        }
    }

    private suspend fun evaluateAndExecuteStrategy() {
        val settings = _botSettings.value
        val now = System.currentTimeMillis()

        val activeTicker = if (_marketTicker.value == null || (now - (_marketTicker.value?.lastUpdated ?: 0L)) > 15_000L) {
            val freshRes = fetchLatestTicker(settings.symbol)
            if (freshRes.isSuccess && (freshRes.getOrNull()?.price ?: 0.0) > 0.0) {
                freshRes.getOrThrow().copy(lastUpdated = System.currentTimeMillis())
            } else {
                _marketTicker.value?.copy(lastUpdated = System.currentTimeMillis())
            }
        } else {
            _marketTicker.value
        } ?: return

        _marketTicker.value = activeTicker
        val ticker = activeTicker
        val positions = _openPositions.value
        val history = _priceHistory.value
        val portfolio = _portfolio.value

        val strategy: TradingStrategy = when (settings.strategyType) {
            StrategyType.DCA -> dcaStrategy
            StrategyType.RSI_SWING -> rsiStrategy
            StrategyType.GRID -> gridStrategy
            StrategyType.BOLLINGER -> bollingerStrategy
            StrategyType.TURTLE_BREAKOUT -> turtleStrategy
            StrategyType.VWAP_REVERSAL -> vwapStrategy
            StrategyType.AI_GOD_MODE -> godModeStrategy
        }

        val signal = strategy.evaluate(
            settings = settings,
            currentTicker = ticker,
            priceHistory = history,
            openPositions = positions,
            allocatedCapital = portfolio.allocatedCapital,
            candles = _candles.value
        )

        // Order Book Wall Filter Guard before BUY
        if (signal.action == SignalAction.BUY && settings.orderBookWallFilterEnabled) {
            val buyPressure = _orderBook.value.buyPressurePercent
            if (buyPressure < 45.0) {
                addTradeLog(
                    TradeLog(
                        id = "ob_guard_${System.currentTimeMillis()}",
                        type = "ORDERBOOK_GUARD",
                        symbol = settings.symbol,
                        side = "BLOCKED",
                        price = ticker.price,
                        quantity = 0.0,
                        amount = 0.0,
                        fee = 0.0,
                        feeCurrency = settings.quoteCurrency,
                        status = "REJECTED",
                        message = String.format(Locale.US, "Order Book Whale Filter: Buy wall weak (%.1f%% < 45%%), waiting for bid support", buyPressure),
                        tradingMode = settings.tradingMode
                    )
                )
                return
            }
        }

        // Risk Manager validation
        val riskCheck = riskManager.validateOrder(
            signal = signal,
            settings = settings,
            ticker = ticker,
            openPositions = positions,
            allocatedCapital = portfolio.allocatedCapital,
            dailyRealizedLoss = portfolio.dailyPnl.coerceAtMost(0.0) * -1,
            recentTradeLogs = _tradeLogs.value
        )

        when (riskCheck) {
            is RiskCheckResult.Rejected -> {
                addTradeLog(
                    TradeLog(
                        id = "risk_${System.currentTimeMillis()}",
                        type = "RISK_GUARD",
                        symbol = settings.symbol,
                        side = "BLOCKED",
                        price = ticker.price,
                        quantity = 0.0,
                        amount = 0.0,
                        fee = 0.0,
                        feeCurrency = settings.quoteCurrency,
                        status = "REJECTED",
                        message = "Risk guard blocked order: ${riskCheck.reason}",
                        tradingMode = settings.tradingMode
                    )
                )
                return
            }
            is RiskCheckResult.Approved -> {
                // Execute signal
                when (signal.action) {
                    SignalAction.BUY -> executeBuyOrder(signal, settings, ticker)
                    SignalAction.SELL -> executeSellOrder(signal, settings, ticker)
                    SignalAction.HOLD -> {
                        _botStatus.value = _botStatus.value.copy(
                            lastCheckTime = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

    private suspend fun executeBuyOrder(
        signal: TradingSignal,
        settings: BotSettings,
        ticker: MarketTicker
    ) {
        val client = getActiveExchangeClient()
        var baseAmount = signal.suggestedAmount ?: settings.orderAmount
        val remainingCap = (settings.maxCapital - _portfolio.value.allocatedCapital).coerceAtLeast(0.0)

        if (settings.kellyDynamicSizingEnabled) {
            val totalTrades = _portfolio.value.totalTrades
            val winRate = _portfolio.value.winRate
            val kellyConfidenceMultiplier = if (totalTrades >= 3 && winRate >= 60.0) 1.2 else if (totalTrades >= 3 && winRate < 40.0) 0.75 else 1.0
            baseAmount = (baseAmount * kellyConfidenceMultiplier).coerceAtMost(settings.maxOrderValue).coerceAtMost(remainingCap)
        }

        val price = signal.suggestedPrice ?: ticker.price
        val amount = baseAmount

        val result = client.submitOrder(
            symbol = settings.symbol,
            side = "BUY",
            type = "MARKET",
            quantity = amount / price,
            price = price,
            quoteOrderAmount = amount
        )

        result.onSuccess { order ->
            // Create open position record
            val position = OpenPosition(
                id = java.util.UUID.randomUUID().toString(),
                symbol = settings.symbol,
                side = "BUY",
                entryPrice = order.price,
                currentPrice = order.price,
                quantity = order.quantity,
                amountInvested = order.amount + order.fee, // Include BUY fee in total cost
                unrealizedPnl = 0.0,
                pnlPercentage = 0.0,
                entryTimestamp = order.timestamp
            )
            val isFirstEntry = _openPositions.value.isEmpty()
            val updatedPositions = _openPositions.value + position
            _openPositions.value = updatedPositions
            tradingDao?.insertOpenPosition(OpenPositionEntity.fromDomain(position))

            if (isFirstEntry) {
                _botSettings.value = _botSettings.value.copy(peakPrice = 0.0, breakEvenActivated = false)
            }

            // Update portfolio
            val port = _portfolio.value
            val updatedPort = port.copy(
                allocatedCapital = port.allocatedCapital + (order.amount + order.fee) // Don't increment totalTrades on Entry
            )
            _portfolio.value = updatedPort
            tradingDao?.savePortfolioSnapshot(PortfolioSnapshotEntity.fromDomain(updatedPort))

            val log = order.copy(message = "${signal.reason} | ${order.message}")
            addTradeLog(log)

            _botStatus.value = _botStatus.value.copy(
                openPositionsCount = updatedPositions.size,
                lastCheckTime = System.currentTimeMillis()
            )
            syncPortfolioBalances()
        }.onFailure { err ->
            addTradeLog(
                TradeLog(
                    id = "err_${System.currentTimeMillis()}",
                    type = "ERROR",
                    symbol = settings.symbol,
                    side = "BUY",
                    price = price,
                    quantity = 0.0,
                    amount = amount,
                    fee = 0.0,
                    feeCurrency = settings.quoteCurrency,
                    status = "FAILED",
                    message = "Buy order failed: ${err.message}",
                    tradingMode = settings.tradingMode
                )
            )
        }
    }

    private suspend fun executeSellOrder(
        signal: TradingSignal,
        settings: BotSettings,
        ticker: MarketTicker
    ) {
        val positions = _openPositions.value
        if (positions.isEmpty()) return

        val client = getActiveExchangeClient()
        val totalQty = positions.sumOf { it.quantity }
        val totalCost = positions.sumOf { it.amountInvested }
        val price = signal.suggestedPrice ?: ticker.price

        val result = client.submitOrder(
            symbol = settings.symbol,
            side = "SELL",
            type = "MARKET",
            quantity = totalQty,
            price = price
        )

        result.onSuccess { order ->
            val proceeds = order.amount - order.fee
            val realizedPnl = proceeds - totalCost

            // Clear positions
            _openPositions.value = emptyList()
            tradingDao?.deleteAllOpenPositions()

            // Update portfolio
            val port = _portfolio.value
            val totalClosedTrades = port.totalTrades + 1
            val newWins = port.winningTrades + if (realizedPnl > 0.0) 1 else 0
            val winRate = if (totalClosedTrades > 0) (newWins.toDouble() / totalClosedTrades) * 100.0 else 0.0

            val updatedPort = port.copy(
                allocatedCapital = 0.0,
                totalRealizedPnl = port.totalRealizedPnl + realizedPnl,
                dailyPnl = port.dailyPnl + realizedPnl,
                totalTrades = totalClosedTrades,
                winningTrades = newWins,
                winRate = winRate
            )
            _portfolio.value = updatedPort
            tradingDao?.savePortfolioSnapshot(PortfolioSnapshotEntity.fromDomain(updatedPort))

            val log = order.copy(
                realizedPnl = realizedPnl,
                message = "${signal.reason} | Realized PnL: ${String.format("%+.2f", realizedPnl)} ${settings.quoteCurrency}"
            )
            addTradeLog(log)

            _botSettings.value = _botSettings.value.copy(peakPrice = 0.0, breakEvenActivated = false)

            _botStatus.value = _botStatus.value.copy(
                openPositionsCount = 0,
                lastCheckTime = System.currentTimeMillis()
            )
            syncPortfolioBalances()
        }.onFailure { err ->
            addTradeLog(
                TradeLog(
                    id = "err_${System.currentTimeMillis()}",
                    type = "ERROR",
                    symbol = settings.symbol,
                    side = "SELL",
                    price = price,
                    quantity = totalQty,
                    amount = totalQty * price,
                    fee = 0.0,
                    feeCurrency = settings.quoteCurrency,
                    status = "FAILED",
                    message = "Sell order failed: ${err.message}",
                    tradingMode = settings.tradingMode
                )
            )
        }
    }

    private fun symbolsMatch(s1: String, s2: String): Boolean {
        val clean1 = s1.replace("/", "").replace("_", "").replace("-", "")
        val clean2 = s2.replace("/", "").replace("_", "").replace("-", "")
        return clean1.equals(clean2, ignoreCase = true)
    }

    private fun updateOpenPositionsPnl(tickerSymbol: String, currentPrice: Double) {
        val positions = _openPositions.value
        val settings = _botSettings.value
        if (positions.isEmpty()) {
            if (settings.peakPrice != 0.0 || settings.breakEvenActivated) {
                _botSettings.value = settings.copy(peakPrice = 0.0, breakEvenActivated = false)
            }
            return
        }

        val updated = mutableListOf<OpenPosition>()
        for (pos in positions) {
            if (!symbolsMatch(tickerSymbol, pos.symbol)) {
                updated.add(pos)
                continue
            }

            val pnl = (currentPrice - pos.entryPrice) * pos.quantity
            val pct = ((currentPrice - pos.entryPrice) / pos.entryPrice) * 100.0
            
            if (settings.partialTakeProfitEnabled && !pos.partialTpTaken && pct >= settings.partialTakeProfitPercentage) {
                val halfQty = pos.quantity * 0.5
                if (halfQty * currentPrice >= 200.0) {
                    coroutineScope.launch {
                        val client = getActiveExchangeClient()
                        val orderRes = client.submitOrder(pos.symbol, "SELL", "MARKET", halfQty, currentPrice, null)
                        orderRes.onSuccess { execLog ->
                            val grossProceeds = halfQty * currentPrice
                            val sellFee = grossProceeds * 0.001
                            val partialCost = pos.amountInvested * 0.5
                            val partialRealizedPnl = (grossProceeds - sellFee) - partialCost

                            val port = _portfolio.value
                            val updatedPort = port.copy(
                                allocatedCapital = (port.allocatedCapital - partialCost).coerceAtLeast(0.0),
                                totalRealizedPnl = port.totalRealizedPnl + partialRealizedPnl,
                                dailyPnl = port.dailyPnl + partialRealizedPnl
                            )
                            _portfolio.value = updatedPort
                            tradingDao?.savePortfolioSnapshot(PortfolioSnapshotEntity.fromDomain(updatedPort))

                            addTradeLog(execLog.copy(
                                realizedPnl = partialRealizedPnl,
                                message = String.format(Locale.US, "Partial Take-Profit (50%% Scale-Out) locked in at +%.2f%% | Realized PnL: %+.2f %s", pct, partialRealizedPnl, settings.quoteCurrency)
                            ))
                        }
                    }
                    val newPos = pos.copy(
                        quantity = pos.quantity - halfQty,
                        amountInvested = pos.amountInvested * 0.5,
                        currentPrice = currentPrice,
                        unrealizedPnl = (currentPrice - pos.entryPrice) * (pos.quantity - halfQty),
                        pnlPercentage = pct,
                        partialTpTaken = true
                    )
                    updated.add(newPos)
                    _botSettings.value = settings.copy(breakEvenActivated = true)
                    continue
                }
            }

            updated.add(pos.copy(
                currentPrice = currentPrice,
                unrealizedPnl = pnl,
                pnlPercentage = pct
            ))
        }
        _openPositions.value = updated

        val totalUnrealized = updated.sumOf { it.unrealizedPnl }
        _portfolio.value = _portfolio.value.copy(unrealizedPnl = totalUnrealized)

        if (settings.trailingTakeProfitEnabled) {
            val totalQty = updated.sumOf { it.quantity }
            val totalCost = updated.sumOf { it.amountInvested }
            if (totalQty > 0.0 && totalCost > 0.0) {
                val avgEntry = totalCost / totalQty
                val activePosPrice = updated.firstOrNull { symbolsMatch(it.symbol, settings.symbol) }?.currentPrice ?: currentPrice
                val profitPct = ((activePosPrice - avgEntry) / avgEntry) * 100.0
                if (profitPct >= settings.takeProfitPercentage) {
                    val newPeak = maxOf(settings.peakPrice, currentPrice)
                    if (newPeak != settings.peakPrice) {
                        _botSettings.value = settings.copy(peakPrice = newPeak)
                    }
                }
            }
        }
    }

    private suspend fun syncPortfolioBalances() {
        val client = getActiveExchangeClient()
        val balancesRes = client.getBalances()
        balancesRes.onSuccess { balances ->
            val quoteCurr = _botSettings.value.quoteCurrency
            val baseCurr = _botSettings.value.baseCurrency
            val quoteBal = balances.find { it.asset.equals(quoteCurr, ignoreCase = true) }?.free?.toDoubleOrNull()
                ?: _portfolio.value.quoteBalance
            val baseBal = balances.find { it.asset.equals(baseCurr, ignoreCase = true) }?.free?.toDoubleOrNull()
                ?: _portfolio.value.baseBalance
            val updatedPort = _portfolio.value.copy(
                quoteBalance = quoteBal,
                baseBalance = baseBal,
                quoteCurrency = quoteCurr
            )
            _portfolio.value = updatedPort
            tradingDao?.savePortfolioSnapshot(PortfolioSnapshotEntity.fromDomain(updatedPort))
        }
    }

    override suspend fun manualBuyNow(): Result<Unit> {
        val ticker = _marketTicker.value ?: fetchLatestTicker(_botSettings.value.symbol).getOrNull()
            ?: return Result.failure(IllegalStateException("Market data unavailable"))

        val signal = TradingSignal(
            action = SignalAction.BUY,
            reason = "Manual Quick Buy executed by user",
            suggestedAmount = _botSettings.value.orderAmount,
            suggestedPrice = ticker.price
        )
        executeBuyOrder(signal, _botSettings.value, ticker)
        return Result.success(Unit)
    }

    override suspend fun closeAllPositions(): Result<Unit> {
        if (_openPositions.value.isEmpty()) {
            return Result.failure(IllegalStateException("No open positions to close"))
        }
        val ticker = _marketTicker.value ?: fetchLatestTicker(_botSettings.value.symbol).getOrNull()
            ?: return Result.failure(IllegalStateException("Market data unavailable"))

        val signal = TradingSignal(
            action = SignalAction.SELL,
            reason = "Manual Close All / Panic Sell executed by user",
            suggestedPrice = ticker.price
        )
        executeSellOrder(signal, _botSettings.value, ticker)
        _botSettings.value = _botSettings.value.copy(peakPrice = 0.0)
        return Result.success(Unit)
    }

    override suspend fun resetPaperPortfolio(): Result<Unit> {
        _openPositions.value = emptyList()
        _tradeLogs.value = emptyList()
        
        coroutineScope.launch {
            tradingDao?.deleteAllOpenPositions()
            tradingDao?.deleteAllTradeLogs()
            tradingDao?.savePaperBalance(PaperBalanceEntity("PHP", 50000.0))
            tradingDao?.savePaperBalance(PaperBalanceEntity("USDT", 1000.0))
        }

        val quoteCurr = _botSettings.value.quoteCurrency
        val defaultQuoteBal = if (quoteCurr == "PHP") 50000.0 else 1000.0
        
        val newPort = PortfolioSnapshot(
            quoteBalance = defaultQuoteBal,
            baseBalance = 0.0,
            quoteCurrency = quoteCurr,
            allocatedCapital = 0.0,
            totalRealizedPnl = 0.0,
            unrealizedPnl = 0.0,
            winRate = 0.0,
            totalTrades = 0,
            winningTrades = 0,
            dailyPnl = 0.0
        )
        _portfolio.value = newPort
        
        coroutineScope.launch {
            tradingDao?.savePortfolioSnapshot(PortfolioSnapshotEntity.fromDomain(newPort))
        }

        return Result.success(Unit)
    }

    private suspend fun addTradeLog(log: TradeLog) {
        val current = _tradeLogs.value.toMutableList()
        current.add(0, log)
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        _tradeLogs.value = current
        tradingDao?.insertTradeLog(TradeLogEntity.fromDomain(log))

        if (log.status == "FILLED" || log.status == "EXECUTED") {
            notificationManager?.notifyTradeExecuted(log)
        }
    }

    override suspend fun runQuickBacktest(): Result<com.example.data.model.BacktestResult> {
        val currentSettings = _botSettings.value
        val interval = _selectedInterval.value.code
        val client = getMarketDataClient()

        val klinesRes = client.getKlines(currentSettings.symbol, interval, 200)
        val candlesToTest = if (klinesRes.isSuccess && klinesRes.getOrThrow().isNotEmpty()) {
            klinesRes.getOrThrow()
        } else if (_candles.value.isNotEmpty()) {
            _candles.value
        } else {
            emptyList()
        }

        if (candlesToTest.isEmpty()) {
            return Result.failure(IllegalStateException("No historical klines available for backtesting"))
        }

        val result = executeSimulation(currentSettings, interval, candlesToTest)
        return Result.success(result)
    }

    override suspend fun autoOptimizeStrategy(): Result<com.example.data.model.BacktestResult> {
        val client = getMarketDataClient()
        val currentSettings = _botSettings.value
        val interval = _selectedInterval.value.code

        val klinesRes = client.getKlines(currentSettings.symbol, interval, 200)
        val candlesToTest = if (klinesRes.isSuccess && klinesRes.getOrThrow().isNotEmpty()) {
            klinesRes.getOrThrow()
        } else if (_candles.value.isNotEmpty()) {
            _candles.value
        } else {
            emptyList()
        }

        if (candlesToTest.size < 5) {
            return Result.failure(IllegalStateException("Insufficient klines (${candlesToTest.size}) for auto-optimization"))
        }

        val minLow = candlesToTest.minOf { it.low }
        val maxHigh = candlesToTest.maxOf { it.high }

        val candidates = listOf(
            currentSettings,
            currentSettings.copy(
                strategyType = StrategyType.DCA,
                dipTriggerPercentage = 1.2,
                takeProfitPercentage = 1.8,
                trailingTakeProfitEnabled = true,
                trailingStopPercentage = 0.4,
                breakEvenLockEnabled = true,
                dcaMultiplier = 1.25
            ),
            currentSettings.copy(
                strategyType = StrategyType.DCA,
                dipTriggerPercentage = 2.5,
                takeProfitPercentage = 4.0,
                trailingTakeProfitEnabled = true,
                trailingStopPercentage = 0.8,
                smartEntryEnabled = true,
                greenCandleConfirmation = true,
                dcaMultiplier = 1.5
            ),
            currentSettings.copy(
                strategyType = StrategyType.RSI_SWING,
                rsiPeriod = 14,
                takeProfitPercentage = 3.0,
                stopLossPercentage = 3.0
            ),
            currentSettings.copy(
                strategyType = StrategyType.GRID,
                gridLevels = 6,
                gridLowerPrice = minLow * 0.995,
                gridUpperPrice = maxHigh * 1.005
            ),
            currentSettings.copy(
                strategyType = StrategyType.BOLLINGER,
                takeProfitPercentage = 2.5,
                neverSellAtLoss = true,
                greenCandleConfirmation = true
            ),
            currentSettings.copy(
                strategyType = StrategyType.TURTLE_BREAKOUT,
                takeProfitPercentage = 3.5,
                stopLossPercentage = 2.5,
                atrDynamicTargetsEnabled = true
            ),
            currentSettings.copy(
                strategyType = StrategyType.VWAP_REVERSAL,
                takeProfitPercentage = 2.0,
                neverSellAtLoss = true,
                atrDynamicTargetsEnabled = true
            ),
            currentSettings.copy(
                strategyType = StrategyType.AI_GOD_MODE,
                atrDynamicTargetsEnabled = true,
                kellyDynamicSizingEnabled = true,
                breakEvenLockEnabled = true
            )
        )

        val bestResult = candidates.map { candidate ->
            executeSimulation(candidate, interval, candlesToTest)
        }.maxByOrNull { it.netPnl }

        return if (bestResult != null) {
            val summary = String.format(
                Locale.US,
                "Optimal Strategy: %s | Net PnL: %+.2f %s | Win Rate: %.1f%%",
                bestResult.strategy.label,
                bestResult.netPnl,
                currentSettings.quoteCurrency,
                bestResult.winRate
            )
            Result.success(bestResult.copy(summary = summary))
        } else {
            Result.failure(IllegalStateException("Failed to evaluate strategy candidates"))
        }
    }

    private fun executeSimulation(
        settings: BotSettings,
        interval: String,
        candlesToTest: List<CandleStick>
    ): com.example.data.model.BacktestResult {
        val strategy: TradingStrategy = when (settings.strategyType) {
            StrategyType.DCA -> dcaStrategy
            StrategyType.RSI_SWING -> rsiStrategy
            StrategyType.GRID -> gridStrategy
            StrategyType.BOLLINGER -> bollingerStrategy
            StrategyType.TURTLE_BREAKOUT -> turtleStrategy
            StrategyType.VWAP_REVERSAL -> vwapStrategy
            StrategyType.AI_GOD_MODE -> godModeStrategy
        }

        val simulatedPositions = mutableListOf<OpenPosition>()
        val simulatedPriceHistory = mutableListOf<PricePoint>()
        var simulatedAllocated = 0.0
        var totalClosedTrades = 0
        var winningTrades = 0
        var realizedNetPnl = 0.0
        var currentSettings = settings.copy(peakPrice = 0.0, breakEvenActivated = false)

        candlesToTest.forEachIndexed { index, candle ->
            val ticker = MarketTicker(
                symbol = settings.symbol,
                baseAsset = settings.baseCurrency,
                quoteAsset = settings.quoteCurrency,
                price = candle.close,
                change24h = 0.0,
                high24h = candle.high,
                low24h = candle.low,
                volume24h = candle.volume,
                bidPrice = candle.close,
                askPrice = candle.close,
                lastUpdated = candle.closeTime
            )

            simulatedPriceHistory.add(PricePoint(candle.closeTime, candle.close))

            val signal = strategy.evaluate(
                settings = currentSettings,
                currentTicker = ticker,
                priceHistory = simulatedPriceHistory,
                openPositions = simulatedPositions,
                allocatedCapital = simulatedAllocated,
                candles = candlesToTest.subList(0, index + 1)
            )

            when (signal.action) {
                SignalAction.BUY -> {
                    val orderAmt = signal.suggestedAmount ?: currentSettings.orderAmount
                    val qty = orderAmt / candle.close
                    val buyFee = orderAmt * 0.0010
                    val totalCost = orderAmt + buyFee

                    if ((simulatedAllocated + totalCost) <= currentSettings.maxCapital) {
                        val pos = OpenPosition(
                            id = "sim_pos_$index",
                            symbol = currentSettings.symbol,
                            side = "BUY",
                            entryPrice = candle.close,
                            currentPrice = candle.close,
                            quantity = qty,
                            amountInvested = totalCost,
                            unrealizedPnl = 0.0,
                            pnlPercentage = 0.0,
                            entryTimestamp = candle.closeTime
                        )
                        simulatedPositions.add(pos)
                        simulatedAllocated += totalCost
                    }
                }
                SignalAction.SELL -> {
                    if (simulatedPositions.isNotEmpty()) {
                        val totalQty = simulatedPositions.sumOf { it.quantity }
                        val totalCost = simulatedPositions.sumOf { it.amountInvested }
                        val grossProceeds = totalQty * candle.close
                        val sellFee = grossProceeds * 0.0010
                        val netProceeds = grossProceeds - sellFee
                        val tradePnl = netProceeds - totalCost

                        realizedNetPnl += tradePnl
                        totalClosedTrades++
                        if (tradePnl > 0.0) winningTrades++

                        simulatedPositions.clear()
                        simulatedAllocated = 0.0
                        currentSettings = currentSettings.copy(peakPrice = 0.0)
                    }
                }
                SignalAction.HOLD -> {
                    if (currentSettings.trailingTakeProfitEnabled && simulatedPositions.isNotEmpty()) {
                        val totalQty = simulatedPositions.sumOf { it.quantity }
                        val totalCost = simulatedPositions.sumOf { it.amountInvested }
                        if (totalQty > 0.0 && totalCost > 0.0) {
                            val avgEntry = totalCost / totalQty
                            val profitPct = ((candle.close - avgEntry) / avgEntry) * 100.0
                            if (profitPct >= currentSettings.takeProfitPercentage) {
                                val newPeak = maxOf(currentSettings.peakPrice, candle.close)
                                if (newPeak != currentSettings.peakPrice) {
                                    currentSettings = currentSettings.copy(peakPrice = newPeak)
                                }
                            }
                        }
                    }
                }
            }
        }

        val lastClose = candlesToTest.lastOrNull()?.close ?: 0.0
        val unrealizedPnl = simulatedPositions.sumOf { pos -> (lastClose * pos.quantity) - pos.amountInvested }
        val winRate = if (totalClosedTrades > 0) (winningTrades.toDouble() / totalClosedTrades) * 100.0 else 0.0
        val totalNetPnl = realizedNetPnl + unrealizedPnl

        val summary = String.format(
            Locale.US,
            "Simulated %d trades across %d candles: Win Rate %.1f%%, Net PnL %+.2f %s",
            totalClosedTrades,
            candlesToTest.size,
            winRate,
            totalNetPnl,
            settings.quoteCurrency
        )

        return com.example.data.model.BacktestResult(
            symbol = settings.symbol,
            strategy = settings.strategyType,
            interval = interval,
            candlesTested = candlesToTest.size,
            totalTrades = totalClosedTrades,
            winRate = winRate,
            netPnl = totalNetPnl,
            summary = summary,
            openPositionsCount = simulatedPositions.size,
            unrealizedPnl = unrealizedPnl,
            recommendedSettings = settings
        )
    }
}
