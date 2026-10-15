package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.BotRuntimeStatus
import com.example.data.model.BotSettings
import com.example.data.model.BotState
import com.example.data.model.CandleStick
import com.example.data.model.ChartInterval
import com.example.data.model.ChartType
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.MarketTicker
import com.example.data.model.OpenPosition
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.PricePoint
import com.example.data.model.StrategyType
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import com.example.data.network.CoinsPhApiClient
import com.example.data.repository.TradingBotRepository
import com.example.data.repository.TradingBotRepositoryImpl
import com.example.domain.exchange.CoinsPhExchangeClient
import com.example.domain.exchange.PaperTradingExchangeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val isLoading: Boolean = false,
    val botSettings: BotSettings = BotSettings(),
    val botStatus: BotRuntimeStatus = BotRuntimeStatus(),
    val portfolio: PortfolioSnapshot = PortfolioSnapshot(),
    val tradeLogs: List<TradeLog> = emptyList(),
    val marketTicker: MarketTicker? = null,
    val priceHistory: List<PricePoint> = emptyList(),
    val candles: List<CandleStick> = emptyList(),
    val orderBook: com.example.data.model.OrderBookSnapshot = com.example.data.model.OrderBookSnapshot(),
    val chartType: ChartType = ChartType.CANDLE,
    val chartInterval: ChartInterval = ChartInterval.FIVE_MIN,
    val openPositions: List<OpenPosition> = emptyList(),
    val availableSymbols: List<CoinsSymbolInfo> = emptyList(),
    val isOperationInProgress: Boolean = false,
    val isSavingSettings: Boolean = false,
    val showLiveConfirmDialog: Boolean = false,
    val showApiCredentialsDialog: Boolean = false,
    val hasLiveCredentials: Boolean = false,
    val lastBacktestResult: com.example.data.model.BacktestResult? = null,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null
)

sealed class DashboardEvent {
    data class ShowToast(val message: String) : DashboardEvent()
    data class OrderExecuted(val trade: TradeLog) : DashboardEvent()
    object EmergencyStopTriggered : DashboardEvent()
}

class BotDashboardViewModel(
    private val repository: TradingBotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DashboardEvent>()
    val events: SharedFlow<DashboardEvent> = _events.asSharedFlow()

    init {
        loadInitialData()
        observeRepositoryFlows()
        startWatchlistPolling()
    }

    fun refreshMarketWatchlist() {
        viewModelScope.launch(Dispatchers.Default) {
            val symbolsRes = repository.getAvailableSymbols()
            val rawSymbols = symbolsRes.getOrDefault(emptyList())
            if (rawSymbols.isEmpty()) return@launch

            val isBinance = _uiState.value.botSettings.exchangePlatform == com.example.data.model.ExchangePlatform.BINANCE
            val priorityOrder = if (isBinance) {
                listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "BNBUSDT", "DOGEUSDT", "PEPEUSDT", "SHIBUSDT", "SUIUSDT", "ADAUSDT", "TRXUSDT", "AVAXUSDT", "LINKUSDT", "DOTUSDT", "LTCUSDT", "TONUSDT", "BONKUSDT", "WIFUSDT", "NEARUSDT", "APTUSDT", "ARBUSDT", "OPUSDT", "RENDERUSDT", "FETUSDT", "PAXGUSDT", "EURUSDT", "GBPUSDT", "AUDUSDT", "USDCUSDT")
            } else {
                listOf("BTCPHP", "ETHPHP", "XRPPHP", "SOLPHP", "DOGEPHP", "PEPEPHP", "SHIBPHP", "SUIPHP", "ADAPHP", "TRXPHP", "LINKPHP", "AVAXPHP", "DOTPHP", "LTCPHP", "TONPHP", "BONKPHP", "FLOKIPHP", "WIFPHP", "GALAPHP", "SANDPHP", "POLPHP", "NEARPHP", "APTPHP", "ARBPHP", "OPPHP", "RENDERPHP", "FETPHP", "USDTPHP", "USDCPHP", "BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT")
            }

            val sortedSymbols = rawSymbols.sortedWith { a, b ->
                val indexA = priorityOrder.indexOf(a.symbol.uppercase())
                val indexB = priorityOrder.indexOf(b.symbol.uppercase())
                when {
                    indexA != -1 && indexB != -1 -> indexA.compareTo(indexB)
                    indexA != -1 -> -1
                    indexB != -1 -> 1
                    else -> a.symbol.compareTo(b.symbol, ignoreCase = true)
                }
            }

            _uiState.update { it.copy(availableSymbols = sortedSymbols) }
        }
    }

    private fun startWatchlistPolling() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000L)
                refreshMarketWatchlist()
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasLiveCredentials = repository.hasLiveCredentials()) }
            
            val sortedSymbols = withContext(Dispatchers.Default) {
                val symbolsRes = repository.getAvailableSymbols()
                val defaultSymbols = listOf(
                    CoinsSymbolInfo("BTCPHP", "TRADING", "BTC", "PHP", true),
                    CoinsSymbolInfo("ETHPHP", "TRADING", "ETH", "PHP", true),
                    CoinsSymbolInfo("XRPPHP", "TRADING", "XRP", "PHP", true),
                    CoinsSymbolInfo("SOLPHP", "TRADING", "SOL", "PHP", true),
                    CoinsSymbolInfo("USDT/PHP", "TRADING", "USDT", "PHP", true),
                    CoinsSymbolInfo("USDC/PHP", "TRADING", "USDC", "PHP", true),
                    CoinsSymbolInfo("BTCUSDT", "TRADING", "BTC", "USDT", true),
                    CoinsSymbolInfo("ETHUSDT", "TRADING", "ETH", "USDT", true),
                    CoinsSymbolInfo("SOLUSDT", "TRADING", "SOL", "USDT", true),
                    CoinsSymbolInfo("XRPUSDT", "TRADING", "XRP", "USDT", true),
                    CoinsSymbolInfo("BNBUSDT", "TRADING", "BNB", "USDT", true),
                    CoinsSymbolInfo("PAXGUSDT", "TRADING", "PAXG", "USDT", true),
                    CoinsSymbolInfo("EURUSDT", "TRADING", "EUR", "USDT", true),
                    CoinsSymbolInfo("BTCUSDC", "TRADING", "BTC", "USDC", true)
                )
                val rawSymbols = symbolsRes.getOrDefault(defaultSymbols)

                val isBinance = _uiState.value.botSettings.exchangePlatform == com.example.data.model.ExchangePlatform.BINANCE
                val priorityOrder = if (isBinance) {
                    listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "BNBUSDT", "DOGEUSDT", "PEPEUSDT", "SHIBUSDT", "SUIUSDT", "ADAUSDT", "TRXUSDT", "AVAXUSDT", "LINKUSDT", "DOTUSDT", "LTCUSDT", "TONUSDT", "BONKUSDT", "WIFUSDT", "NEARUSDT", "APTUSDT", "ARBUSDT", "OPUSDT", "RENDERUSDT", "FETUSDT", "PAXGUSDT", "EURUSDT", "GBPUSDT", "AUDUSDT", "USDCUSDT")
                } else {
                    listOf("BTCPHP", "ETHPHP", "XRPPHP", "SOLPHP", "DOGEPHP", "PEPEPHP", "SHIBPHP", "SUIPHP", "ADAPHP", "TRXPHP", "LINKPHP", "AVAXPHP", "DOTPHP", "LTCPHP", "TONPHP", "BONKPHP", "FLOKIPHP", "WIFPHP", "GALAPHP", "SANDPHP", "POLPHP", "NEARPHP", "APTPHP", "ARBPHP", "OPPHP", "RENDERPHP", "FETPHP", "USDTPHP", "USDCPHP", "BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT")
                }

                rawSymbols.sortedWith { a, b ->
                    val indexA = priorityOrder.indexOf(a.symbol.uppercase())
                    val indexB = priorityOrder.indexOf(b.symbol.uppercase())
                    when {
                        indexA != -1 && indexB != -1 -> indexA.compareTo(indexB)
                        indexA != -1 -> -1
                        indexB != -1 -> 1
                        else -> a.symbol.compareTo(b.symbol, ignoreCase = true)
                    }
                }
            }

            _uiState.update {
                it.copy(
                    availableSymbols = sortedSymbols,
                    isLoading = false,
                    hasLiveCredentials = repository.hasLiveCredentials()
                )
            }
        }
    }

    private fun observeRepositoryFlows() {
        viewModelScope.launch {
            repository.botSettingsFlow.collect { settings ->
                _uiState.update { it.copy(botSettings = settings) }
            }
        }
        viewModelScope.launch {
            repository.botStatusFlow.collect { status ->
                _uiState.update { it.copy(botStatus = status) }
            }
        }
        viewModelScope.launch {
            repository.portfolioFlow.collect { port ->
                _uiState.update { it.copy(portfolio = port) }
            }
        }
        viewModelScope.launch {
            repository.orderBookFlow.collect { ob ->
                _uiState.update { it.copy(orderBook = ob) }
            }
        }
        viewModelScope.launch {
            repository.tradeLogsFlow.collect { logs ->
                _uiState.update { it.copy(tradeLogs = logs) }
            }
        }
        viewModelScope.launch {
            repository.marketTickerFlow.collect { ticker ->
                _uiState.update { it.copy(marketTicker = ticker) }
            }
        }
        viewModelScope.launch {
            repository.priceHistoryFlow.collect { history ->
                _uiState.update { it.copy(priceHistory = history) }
            }
        }
        viewModelScope.launch {
            repository.openPositionsFlow.collect { positions ->
                _uiState.update { it.copy(openPositions = positions) }
            }
        }
        viewModelScope.launch {
            repository.candlesFlow.collect { candlesList ->
                _uiState.update { it.copy(candles = candlesList) }
            }
        }
        viewModelScope.launch {
            repository.selectedIntervalFlow.collect { interval ->
                _uiState.update { it.copy(chartInterval = interval) }
            }
        }
    }

    fun onSymbolSelected(symbol: String) {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            repository.switchSymbol(symbol)
            _uiState.update {
                it.copy(
                    isOperationInProgress = false,
                    snackbarMessage = "Market switched to $symbol"
                )
            }
        }
    }

    fun onChartTypeSelected(type: ChartType) {
        _uiState.update { it.copy(chartType = type) }
    }

    fun onChartIntervalSelected(interval: ChartInterval) {
        viewModelScope.launch {
            repository.setChartInterval(interval)
        }
    }

    fun onStrategySelected(strategy: StrategyType) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(strategyType = strategy))
    }

    fun onOrderAmountChanged(amount: Double) {
        if (amount <= 0.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(orderAmount = amount))
    }

    fun onDipTriggerChanged(dip: Double) {
        if (dip <= 0.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(dipTriggerPercentage = dip))
    }

    fun onTakeProfitChanged(tp: Double) {
        if (tp <= 0.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(takeProfitPercentage = tp))
    }

    fun onStopLossChanged(sl: Double) {
        if (sl <= 0.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(stopLossPercentage = sl))
    }

    fun onMaxCapitalChanged(cap: Double) {
        if (cap <= 0.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(maxCapital = cap))
    }

    fun onMaxDailyLossChanged(loss: Double) {
        if (loss <= 0.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(maxDailyLoss = loss))
    }

    fun onCheckIntervalChanged(minutes: Int) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(checkIntervalMinutes = minutes.coerceAtLeast(1)))
    }

    fun onRsiPeriodChanged(period: Int) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(rsiPeriod = period.coerceAtLeast(2)))
    }

    fun onGridLevelsChanged(levels: Int) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(gridLevels = levels.coerceAtLeast(2)))
    }

    fun onGridLowerPriceChanged(price: Double) {
        if (price <= 0.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(gridLowerPrice = price))
    }

    fun onGridUpperPriceChanged(price: Double) {
        if (price <= 0.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(gridUpperPrice = price))
    }

    fun onTrailingTakeProfitToggled(enabled: Boolean) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(trailingTakeProfitEnabled = enabled))
    }

    fun onTrailingStopPercentageChanged(pct: Double) {
        if (pct <= 0.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(trailingStopPercentage = pct))
    }

    fun onSmartEntryToggled(enabled: Boolean) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(smartEntryEnabled = enabled))
    }

    fun onBreakEvenLockToggled(enabled: Boolean) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(breakEvenLockEnabled = enabled))
    }

    fun onGreenCandleConfirmationToggled(enabled: Boolean) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(greenCandleConfirmation = enabled))
    }

    fun onDcaMultiplierChanged(multiplier: Double) {
        if (multiplier < 1.0) return
        val current = _uiState.value.botSettings
        updateSettings(current.copy(dcaMultiplier = multiplier))
    }

    fun onNeverSellAtLossToggled(enabled: Boolean) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(neverSellAtLoss = enabled))
    }

    fun onSpreadGuardToggled(enabled: Boolean) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(spreadGuardEnabled = enabled))
    }

    fun onCrashTrendFilterToggled(enabled: Boolean) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(crashTrendFilterEnabled = enabled))
    }

    fun onRunBacktestClicked() {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            val res = repository.runQuickBacktest()
            res.onSuccess { backtest ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        lastBacktestResult = backtest
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        errorMessage = "Backtest failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun onAutoOptimizeClicked() {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            val res = repository.autoOptimizeStrategy()
            res.onSuccess { backtest ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        lastBacktestResult = backtest
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        errorMessage = "Auto-Optimization failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun onApplyRecommendedSettings(settings: BotSettings) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSettings = true, lastBacktestResult = null) }
            repository.updateSettings(settings)
            _uiState.update {
                it.copy(
                    isSavingSettings = false,
                    snackbarMessage = "Applied optimal ${settings.strategyType.label} settings"
                )
            }
        }
    }

    fun onDismissBacktestResult() {
        _uiState.update { it.copy(lastBacktestResult = null) }
    }

    private fun updateSettings(settings: BotSettings) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSettings = true) }
            repository.updateSettings(settings)
            _uiState.update { it.copy(isSavingSettings = false) }
        }
    }

    fun onStartBotClicked(context: android.content.Context? = null) {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            val res = repository.startBot()
            res.onSuccess {
                context?.let { ctx -> com.example.service.BotForegroundService.startService(ctx) }
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        snackbarMessage = "Trading bot started in ${_uiState.value.botSettings.tradingMode.displayName} mode"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        errorMessage = err.message ?: "Failed to start bot"
                    )
                }
            }
        }
    }

    fun onExchangePlatformSelected(platform: com.example.data.model.ExchangePlatform) {
        viewModelScope.launch {
            repository.switchExchangePlatform(platform)
            loadInitialData()
        }
    }

    fun onPartialTakeProfitToggled(enabled: Boolean) {
        val current = _uiState.value.botSettings
        updateSettings(current.copy(partialTakeProfitEnabled = enabled))
    }

    fun onPauseBotClicked() {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            repository.pauseBot()
            _uiState.update {
                it.copy(
                    isOperationInProgress = false,
                    snackbarMessage = "Trading bot paused"
                )
            }
        }
    }

    fun onStopBotClicked(context: android.content.Context? = null) {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            repository.stopBot()
            context?.let { ctx -> com.example.service.BotForegroundService.stopService(ctx) }
            _uiState.update {
                it.copy(
                    isOperationInProgress = false,
                    snackbarMessage = "Trading bot stopped"
                )
            }
        }
    }

    fun onEmergencyStopClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            repository.emergencyStop()
            _events.emit(DashboardEvent.EmergencyStopTriggered)
            _uiState.update {
                it.copy(
                    isOperationInProgress = false,
                    snackbarMessage = "EMERGENCY STOP EXECUTED: All trading operations halted."
                )
            }
        }
    }

    fun onRequestEnableLiveTrading() {
        _uiState.update { it.copy(showLiveConfirmDialog = true) }
    }

    fun onDismissLiveTradingDialog() {
        _uiState.update { it.copy(showLiveConfirmDialog = false) }
    }

    fun onRequestConfigureCredentials() {
        _uiState.update { it.copy(showApiCredentialsDialog = true) }
    }

    fun onDismissCredentialsDialog() {
        _uiState.update { it.copy(showApiCredentialsDialog = false) }
    }

    fun onSaveCredentialsAndEnableLive(apiKey: String, secretKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(showApiCredentialsDialog = false, isOperationInProgress = true) }
            repository.configureLiveCredentials(apiKey, secretKey)
            _uiState.update { it.copy(hasLiveCredentials = repository.hasLiveCredentials()) }
            val res = repository.setTradingMode(TradingMode.LIVE)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        showLiveConfirmDialog = false,
                        snackbarMessage = "Coins.ph API credentials saved & Live Trading Mode enabled."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        errorMessage = "Failed to enable Live Trading: ${err.message}"
                    )
                }
            }
        }
    }

    fun onConfirmLiveTrading() {
        if (!repository.hasLiveCredentials()) {
            _uiState.update { it.copy(showLiveConfirmDialog = false, showApiCredentialsDialog = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(showLiveConfirmDialog = false, isOperationInProgress = true) }
            val res = repository.setTradingMode(TradingMode.LIVE)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        hasLiveCredentials = repository.hasLiveCredentials(),
                        snackbarMessage = "Live Trading Mode enabled. Real funds are now active."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        hasLiveCredentials = repository.hasLiveCredentials(),
                        errorMessage = "Cannot enable Live Trading: ${err.message}"
                    )
                }
            }
        }
    }

    fun onSwitchToPaperTrading() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            repository.setTradingMode(TradingMode.PAPER)
            _uiState.update {
                it.copy(
                    isOperationInProgress = false,
                    snackbarMessage = "Switched to Paper Trading (Simulated)"
                )
            }
        }
    }

    fun onClosePosition(positionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            val res = repository.closePosition(positionId)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        snackbarMessage = "Position closed at market price"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        errorMessage = "Failed to close position: ${err.message}"
                    )
                }
            }
        }
    }

    fun onCancelAllOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            repository.cancelAllPendingOrders()
            _uiState.update {
                it.copy(
                    isOperationInProgress = false,
                    snackbarMessage = "All pending orders cancelled"
                )
            }
        }
    }

    fun onManualBuyClicked() {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            val res = repository.manualBuyNow()
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        snackbarMessage = "Manual Quick Buy executed"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        errorMessage = "Manual Buy failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun onCloseAllPositionsClicked() {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            val res = repository.closeAllPositions()
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        snackbarMessage = "All open positions closed"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        errorMessage = "Close All failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onAppForegrounded() {
        repository.onAppForegrounded()
    }

    fun onAppBackgrounded() {
        repository.onAppBackgrounded()
    }

    companion object {
        fun provideFactory(context: android.content.Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val api = CoinsPhApiClient.apiService
                val db = com.example.data.local.TradingDatabase.getDatabase(context)
                val paperClient = PaperTradingExchangeClient(api, tradingDao = db.tradingDao())
                val liveClient = CoinsPhExchangeClient(api)
                val prefs = context.getSharedPreferences("coins_live_prefs", android.content.Context.MODE_PRIVATE)
                val repository = TradingBotRepositoryImpl(
                    paperExchangeClient = paperClient,
                    liveExchangeClient = liveClient,
                    tradingDao = db.tradingDao(),
                    sharedPreferences = prefs,
                    context = context
                )
                return BotDashboardViewModel(repository) as T
            }
        }
    }
}
