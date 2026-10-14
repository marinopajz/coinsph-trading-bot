package com.example.data.repository

import com.example.data.local.dao.TradingDao
import com.example.data.local.entity.BotSettingsEntity
import com.example.data.local.entity.OpenPositionEntity
import com.example.data.local.entity.PortfolioSnapshotEntity
import com.example.data.local.entity.TradeLogEntity
import com.example.data.model.BotRuntimeStatus
import com.example.data.model.BotSettings
import com.example.data.model.BotState
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.MarketTicker
import com.example.data.model.OpenPosition
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.PricePoint
import com.example.data.model.StrategyType
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
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
import java.util.UUID

interface TradingBotRepository {
    val botSettingsFlow: Flow<BotSettings>
    val botStatusFlow: Flow<BotRuntimeStatus>
    val portfolioFlow: Flow<PortfolioSnapshot>
    val tradeLogsFlow: Flow<List<TradeLog>>
    val marketTickerFlow: Flow<MarketTicker?>
    val priceHistoryFlow: Flow<List<PricePoint>>
    val openPositionsFlow: Flow<List<OpenPosition>>

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
    fun onAppForegrounded()
    fun onAppBackgrounded()
}

class TradingBotRepositoryImpl(
    private val paperExchangeClient: PaperTradingExchangeClient,
    private val liveExchangeClient: ExchangeClient,
    private val tradingDao: TradingDao? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : TradingBotRepository {

    private val riskManager = RiskManager()
    private val dcaStrategy = DcaStrategy()
    private val rsiStrategy = RsiSwingStrategy()
    private val gridStrategy = GridTradingStrategy()

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

    private val _tradeLogs = MutableStateFlow<List<TradeLog>>(emptyList())
    override val tradeLogsFlow: Flow<List<TradeLog>> = _tradeLogs.asStateFlow()

    private val _marketTicker = MutableStateFlow<MarketTicker?>(null)
    override val marketTickerFlow: Flow<MarketTicker?> = _marketTicker.asStateFlow()

    private val _priceHistory = MutableStateFlow<List<PricePoint>>(emptyList())
    override val priceHistoryFlow: Flow<List<PricePoint>> = _priceHistory.asStateFlow()

    private val _openPositions = MutableStateFlow<List<OpenPosition>>(emptyList())
    override val openPositionsFlow: Flow<List<OpenPosition>> = _openPositions.asStateFlow()

    private var executionJob: Job? = null
    private var tickerPollingJob: Job? = null
    private var isForegrounded = true

    init {
        coroutineScope.launch {
            loadStateFromDatabase()
            if (isForegrounded) {
                startTickerPolling(_botSettings.value.symbol)
            }
        }
    }

    private suspend fun loadStateFromDatabase() {
        tradingDao?.let { dao ->
            val dbSettings = dao.getBotSettings()?.toDomain()
            if (dbSettings != null) {
                _botSettings.value = dbSettings
                _botStatus.value = _botStatus.value.copy(
                    activeSymbol = dbSettings.symbol,
                    mode = dbSettings.tradingMode
                )
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
            }
        }
    }

    override fun onAppForegrounded() {
        isForegrounded = true
        startTickerPolling(_botSettings.value.symbol)
    }

    override fun onAppBackgrounded() {
        isForegrounded = false
        tickerPollingJob?.cancel()
        tickerPollingJob = null
    }

    private fun getActiveExchangeClient(): ExchangeClient {
        return if (_botSettings.value.tradingMode == TradingMode.LIVE) {
            liveExchangeClient
        } else {
            paperExchangeClient
        }
    }

    private fun startTickerPolling(symbol: String) {
        if (!isForegrounded) return
        tickerPollingJob?.cancel()
        tickerPollingJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val client = getActiveExchangeClient()
                    val res = client.getTicker(symbol)
                    res.onSuccess { ticker ->
                        _marketTicker.value = ticker
                        val currentHistory = _priceHistory.value.toMutableList()
                        currentHistory.add(PricePoint(ticker.lastUpdated, ticker.price))
                        if (currentHistory.size > 50) {
                            currentHistory.removeAt(0)
                        }
                        _priceHistory.value = currentHistory

                        // Update unrealized PnL of open positions
                        updateOpenPositionsPnl(ticker.price)
                    }.onFailure { err ->
                        println("DEBUG getTicker failure: ${err.message}")
                    }
                } catch (e: Exception) {
                    println("DEBUG startTickerPolling exception: ${e.message}")
                }
                delay(3000) // Poll every 3 seconds for active UI updates
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

        if (previousSymbol != settings.symbol) {
            startTickerPolling(settings.symbol)
        }
        return Result.success(Unit)
    }

    override suspend fun switchSymbol(symbol: String): Result<Unit> {
        val quote = if (symbol.uppercase().endsWith("USDT")) "USDT" else "PHP"
        val base = symbol.uppercase().removeSuffix(quote)
        val current = _botSettings.value
        updateSettings(
            current.copy(
                symbol = symbol,
                quoteCurrency = quote,
                baseCurrency = base
            )
        )
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

    override suspend fun setTradingMode(mode: TradingMode): Result<Unit> {
        if (mode == TradingMode.LIVE) {
            // Safety check before setting mode
            val check = liveExchangeClient.checkConnectivity()
            if (check.isFailure) {
                return Result.failure(
                    IllegalStateException("Live trading backend check failed: Cannot enable live mode without verified credentials.")
                )
            }
        }
        val updated = _botSettings.value.copy(tradingMode = mode)
        updateSettings(updated)
        return Result.success(Unit)
    }

    override suspend fun getAvailableSymbols(): Result<List<CoinsSymbolInfo>> {
        return getActiveExchangeClient().getAvailableSymbols()
    }

    override suspend fun fetchLatestTicker(symbol: String): Result<MarketTicker> {
        return getActiveExchangeClient().getTicker(symbol)
    }

    override suspend fun closePosition(positionId: String): Result<Unit> {
        val position = _openPositions.value.firstOrNull { it.id == positionId }
            ?: return Result.failure(IllegalArgumentException("Position not found"))

        val currentPrice = _marketTicker.value?.price ?: position.currentPrice
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
        val ticker = _marketTicker.value ?: return
        val positions = _openPositions.value
        val history = _priceHistory.value
        val portfolio = _portfolio.value

        val strategy: TradingStrategy = when (settings.strategyType) {
            StrategyType.DCA -> dcaStrategy
            StrategyType.RSI_SWING -> rsiStrategy
            StrategyType.GRID -> gridStrategy
        }

        val signal = strategy.evaluate(
            settings = settings,
            currentTicker = ticker,
            priceHistory = history,
            openPositions = positions,
            allocatedCapital = portfolio.allocatedCapital
        )

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
        val amount = signal.suggestedAmount ?: settings.orderAmount
        val price = signal.suggestedPrice ?: ticker.price

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
            val updatedPositions = _openPositions.value + position
            _openPositions.value = updatedPositions
            tradingDao?.insertOpenPosition(OpenPositionEntity.fromDomain(position))

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

            _botStatus.value = _botStatus.value.copy(
                openPositionsCount = 0,
                lastCheckTime = System.currentTimeMillis()
            )
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

    private fun updateOpenPositionsPnl(currentPrice: Double) {
        val positions = _openPositions.value
        if (positions.isEmpty()) return

        val updated = positions.map { pos ->
            val pnl = (currentPrice - pos.entryPrice) * pos.quantity
            val pct = ((currentPrice - pos.entryPrice) / pos.entryPrice) * 100.0
            pos.copy(
                currentPrice = currentPrice,
                unrealizedPnl = pnl,
                pnlPercentage = pct
            )
        }
        _openPositions.value = updated

        val totalUnrealized = updated.sumOf { it.unrealizedPnl }
        _portfolio.value = _portfolio.value.copy(unrealizedPnl = totalUnrealized)
    }

    private suspend fun addTradeLog(log: TradeLog) {
        val current = _tradeLogs.value.toMutableList()
        current.add(0, log)
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        _tradeLogs.value = current
        tradingDao?.insertTradeLog(TradeLogEntity.fromDomain(log))
    }
}
