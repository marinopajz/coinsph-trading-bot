package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import com.example.data.network.CoinsPhApiClient
import com.example.data.repository.TradingBotRepository
import com.example.data.repository.TradingBotRepositoryImpl
import com.example.domain.exchange.CoinsPhExchangeClient
import com.example.domain.exchange.PaperTradingExchangeClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val botSettings: BotSettings = BotSettings(),
    val botStatus: BotRuntimeStatus = BotRuntimeStatus(),
    val portfolio: PortfolioSnapshot = PortfolioSnapshot(),
    val tradeLogs: List<TradeLog> = emptyList(),
    val marketTicker: MarketTicker? = null,
    val priceHistory: List<PricePoint> = emptyList(),
    val openPositions: List<OpenPosition> = emptyList(),
    val availableSymbols: List<CoinsSymbolInfo> = emptyList(),
    val isOperationInProgress: Boolean = false,
    val isSavingSettings: Boolean = false,
    val showLiveConfirmDialog: Boolean = false,
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
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val symbolsRes = repository.getAvailableSymbols()
            val symbols = symbolsRes.getOrDefault(
                listOf(
                    CoinsSymbolInfo("BTCPHP", "TRADING", "BTC", "PHP", true),
                    CoinsSymbolInfo("BTCUSDT", "TRADING", "BTC", "USDT", true),
                    CoinsSymbolInfo("ETHPHP", "TRADING", "ETH", "PHP", true),
                    CoinsSymbolInfo("ETHUSDT", "TRADING", "ETH", "USDT", true),
                    CoinsSymbolInfo("SOLPHP", "TRADING", "SOL", "PHP", true),
                    CoinsSymbolInfo("SOLUSDT", "TRADING", "SOL", "USDT", true),
                    CoinsSymbolInfo("XRPPHP", "TRADING", "XRP", "PHP", true)
                )
            )
            _uiState.update {
                it.copy(
                    availableSymbols = symbols,
                    isLoading = false
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

    private fun updateSettings(settings: BotSettings) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSettings = true) }
            repository.updateSettings(settings)
            _uiState.update { it.copy(isSavingSettings = false) }
        }
    }

    fun onStartBotClicked() {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            val res = repository.startBot()
            res.onSuccess {
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

    fun onStopBotClicked() {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            repository.stopBot()
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

    fun onConfirmLiveTrading() {
        viewModelScope.launch {
            _uiState.update { it.copy(showLiveConfirmDialog = false, isOperationInProgress = true) }
            val res = repository.setTradingMode(TradingMode.LIVE)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        snackbarMessage = "Live Trading Mode enabled. Real funds are now active."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
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
                val repository = TradingBotRepositoryImpl(paperClient, liveClient, tradingDao = db.tradingDao())
                return BotDashboardViewModel(repository) as T
            }
        }
    }
}
