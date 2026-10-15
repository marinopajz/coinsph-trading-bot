package com.example.ui.dashboard

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BotState
import com.example.data.model.TradingMode
import com.example.ui.dashboard.components.ActivityFeedList
import com.example.ui.dashboard.components.BotActionsBar
import com.example.ui.dashboard.components.HeaderStatusCard
import com.example.ui.dashboard.components.MarketWatchlistCard
import com.example.ui.dashboard.components.OpenPositionsCard
import com.example.ui.dashboard.components.OrderBookCard
import com.example.ui.dashboard.components.PriceChart
import com.example.ui.dashboard.components.QuickStatsGrid
import com.example.ui.dashboard.components.StrategyControlsCard
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoBackground
import com.example.ui.theme.LiveBadgeBg
import com.example.ui.theme.LiveBadgeText
import com.example.ui.theme.LossRed
import com.example.ui.theme.PaperBadgeBg
import com.example.ui.theme.PaperBadgeText
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DashboardTab(val label: String, val iconText: String) {
    MARKET("Market", "📈"),
    BOT("Bot & Strategy", "🤖"),
    PORTFOLIO("Portfolio", "💼"),
    LOGS("History", "📜")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BotDashboardViewModel,
    userEmail: String? = null,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(DashboardTab.MARKET) }

    val totalQty = uiState.openPositions.sumOf { it.quantity }
    val totalCost = uiState.openPositions.sumOf { it.amountInvested }
    val avgEntry = if (totalQty > 0.0 && totalCost > 0.0) totalCost / totalQty else null
    val tpPrice = if (avgEntry != null) avgEntry * (1.0 + (uiState.botSettings.takeProfitPercentage / 100.0)) else null
    val slPrice = if (avgEntry != null) avgEntry * (1.0 - (uiState.botSettings.stopLossPercentage / 100.0)) else null

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = CryptoBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.botSettings.exchangePlatform.displayName.uppercase(),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = CoinsEmerald,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            // Mode Pill
                            val (modeBg, modeText) = if (uiState.botSettings.tradingMode == TradingMode.PAPER) {
                                PaperBadgeBg to PaperBadgeText
                            } else {
                                LiveBadgeBg to LiveBadgeText
                            }
                            Box(
                                modifier = Modifier
                                    .background(modeBg, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (uiState.botSettings.tradingMode == TradingMode.PAPER) "PAPER" else "LIVE",
                                    color = modeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.size(6.dp))
                            // State Pill
                            val (stateBg, stateColor) = when (uiState.botStatus.state) {
                                BotState.RUNNING -> CoinsEmerald.copy(alpha = 0.2f) to CoinsEmerald
                                BotState.PAUSED -> WarningAmber.copy(alpha = 0.2f) to WarningAmber
                                BotState.STOPPED -> TextMuted.copy(alpha = 0.2f) to TextMuted
                                BotState.EMERGENCY_STOP, BotState.ERROR -> LossRed.copy(alpha = 0.2f) to LossRed
                            }
                            Box(
                                modifier = Modifier
                                    .background(stateBg, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = uiState.botStatus.state.label.uppercase(),
                                    color = stateColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        userEmail?.let {
                            Text(
                                text = it,
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CryptoBackground,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    if (uiState.isOperationInProgress) {
                        CircularProgressIndicator(
                            color = CoinsEmerald,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF121824),
                contentColor = TextPrimary
            ) {
                DashboardTab.values().forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Box {
                                Text(tab.iconText, fontSize = 18.sp)
                                // Badges
                                when (tab) {
                                    DashboardTab.BOT -> {
                                        val (badgeColor, textColor) = when (uiState.botStatus.state) {
                                            BotState.RUNNING -> CoinsEmerald to Color.Black
                                            BotState.PAUSED -> WarningAmber to Color.Black
                                            else -> TextMuted to Color.White
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .background(badgeColor, CircleShape)
                                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = uiState.botStatus.state.label.take(1),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )
                                        }
                                    }
                                    DashboardTab.PORTFOLIO -> {
                                        if (uiState.openPositions.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .background(CoinsEmerald, CircleShape)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "${uiState.openPositions.size}",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) CoinsEmerald else TextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CoinsEmerald,
                            selectedTextColor = CoinsEmerald,
                            indicatorColor = CoinsEmerald.copy(alpha = 0.15f),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                DashboardTab.MARKET -> {
                    item {
                        HeaderStatusCard(
                            status = uiState.botStatus,
                            activeSymbol = uiState.botSettings.symbol,
                            exchangePlatform = uiState.botSettings.exchangePlatform,
                            availableSymbols = uiState.availableSymbols,
                            onSymbolSelected = { viewModel.onSymbolSelected(it) },
                            onExchangePlatformSelected = { viewModel.onExchangePlatformSelected(it) }
                        )
                    }

                    item {
                        val ticker = uiState.marketTicker
                        PriceChart(
                            priceHistory = uiState.priceHistory,
                            candles = uiState.candles,
                            currentPrice = ticker?.price ?: 0.0,
                            quoteCurrency = uiState.botSettings.quoteCurrency,
                            change24h = ticker?.change24h ?: 0.0,
                            symbol = uiState.botSettings.symbol,
                            baseCurrency = uiState.botSettings.baseCurrency,
                            high24h = ticker?.high24h ?: (ticker?.price ?: 0.0),
                            low24h = ticker?.low24h ?: (ticker?.price ?: 0.0),
                            volume24h = ticker?.volume24h ?: 0.0,
                            exchangeName = uiState.botSettings.exchangePlatform.displayName,
                            chartType = uiState.chartType,
                            chartInterval = uiState.chartInterval,
                            averageEntryPrice = avgEntry,
                            takeProfitPrice = tpPrice,
                            stopLossPrice = slPrice,
                            onChartTypeSelected = { viewModel.onChartTypeSelected(it) },
                            onChartIntervalSelected = { viewModel.onChartIntervalSelected(it) }
                        )
                    }

                    item {
                        OrderBookCard(
                            orderBook = uiState.orderBook
                        )
                    }

                    item {
                        MarketWatchlistCard(
                            availableSymbols = uiState.availableSymbols,
                            activeSymbol = uiState.botSettings.symbol,
                            onSymbolSelected = { viewModel.onSymbolSelected(it) }
                        )
                    }

                    item {
                        BotActionsBar(
                            botState = uiState.botStatus.state,
                            tradingMode = uiState.botSettings.tradingMode,
                            isOperationInProgress = uiState.isOperationInProgress,
                            openPositionsCount = uiState.openPositions.size,
                            onStartBot = { viewModel.onStartBotClicked(context) },
                            onPauseBot = { viewModel.onPauseBotClicked() },
                            onStopBot = { viewModel.onStopBotClicked(context) },
                            onEmergencyStop = { viewModel.onEmergencyStopClicked() },
                            onRequestEnableLiveTrading = { viewModel.onRequestEnableLiveTrading() },
                            onSwitchToPaperTrading = { viewModel.onSwitchToPaperTrading() },
                            onCancelAllOrders = { viewModel.onCancelAllOrders() },
                            onManualBuy = { viewModel.onManualBuyClicked() },
                            onCloseAllPositions = { viewModel.onCloseAllPositionsClicked() }
                        )
                    }
                }

                DashboardTab.BOT -> {
                    item {
                        HeaderStatusCard(
                            status = uiState.botStatus,
                            activeSymbol = uiState.botSettings.symbol,
                            exchangePlatform = uiState.botSettings.exchangePlatform,
                            availableSymbols = uiState.availableSymbols,
                            onSymbolSelected = { viewModel.onSymbolSelected(it) },
                            onExchangePlatformSelected = { viewModel.onExchangePlatformSelected(it) }
                        )
                    }

                    item {
                        BotActionsBar(
                            botState = uiState.botStatus.state,
                            tradingMode = uiState.botSettings.tradingMode,
                            isOperationInProgress = uiState.isOperationInProgress,
                            openPositionsCount = uiState.openPositions.size,
                            onStartBot = { viewModel.onStartBotClicked(context) },
                            onPauseBot = { viewModel.onPauseBotClicked() },
                            onStopBot = { viewModel.onStopBotClicked(context) },
                            onEmergencyStop = { viewModel.onEmergencyStopClicked() },
                            onRequestEnableLiveTrading = { viewModel.onRequestEnableLiveTrading() },
                            onSwitchToPaperTrading = { viewModel.onSwitchToPaperTrading() },
                            onCancelAllOrders = { viewModel.onCancelAllOrders() },
                            onManualBuy = { viewModel.onManualBuyClicked() },
                            onCloseAllPositions = { viewModel.onCloseAllPositionsClicked() }
                        )
                    }

                    item {
                        StrategyControlsCard(
                            settings = uiState.botSettings,
                            onStrategySelected = { viewModel.onStrategySelected(it) },
                            onOrderAmountChanged = { viewModel.onOrderAmountChanged(it) },
                            onDipTriggerChanged = { viewModel.onDipTriggerChanged(it) },
                            onTakeProfitChanged = { viewModel.onTakeProfitChanged(it) },
                            onStopLossChanged = { viewModel.onStopLossChanged(it) },
                            onMaxCapitalChanged = { viewModel.onMaxCapitalChanged(it) },
                            onMaxDailyLossChanged = { viewModel.onMaxDailyLossChanged(it) },
                            onRsiPeriodChanged = { viewModel.onRsiPeriodChanged(it) },
                            onGridLevelsChanged = { viewModel.onGridLevelsChanged(it) },
                            onGridLowerPriceChanged = { viewModel.onGridLowerPriceChanged(it) },
                            onGridUpperPriceChanged = { viewModel.onGridUpperPriceChanged(it) },
                            onTrailingTakeProfitToggled = { viewModel.onTrailingTakeProfitToggled(it) },
                            onTrailingStopPercentageChanged = { viewModel.onTrailingStopPercentageChanged(it) },
                            onSmartEntryToggled = { viewModel.onSmartEntryToggled(it) },
                            onBreakEvenLockToggled = { viewModel.onBreakEvenLockToggled(it) },
                            onGreenCandleConfirmationToggled = { viewModel.onGreenCandleConfirmationToggled(it) },
                            onDcaMultiplierChanged = { viewModel.onDcaMultiplierChanged(it) },
                            onNeverSellAtLossToggled = { viewModel.onNeverSellAtLossToggled(it) },
                            onSpreadGuardToggled = { viewModel.onSpreadGuardToggled(it) },
                            onCrashTrendFilterToggled = { viewModel.onCrashTrendFilterToggled(it) },
                            onPartialTakeProfitToggled = { viewModel.onPartialTakeProfitToggled(it) },
                            onAtrDynamicTargetsToggled = { viewModel.onAtrDynamicTargetsToggled(it) },
                            onOrderBookWallFilterToggled = { viewModel.onOrderBookWallFilterToggled(it) },
                            onRunBacktestClicked = { viewModel.onRunBacktestClicked() },
                            onAutoOptimizeClicked = { viewModel.onAutoOptimizeClicked() }
                        )
                    }
                }

                DashboardTab.PORTFOLIO -> {
                    item {
                        QuickStatsGrid(
                            portfolio = uiState.portfolio,
                            quoteCurrency = uiState.botSettings.quoteCurrency
                        )
                    }

                    item {
                        OpenPositionsCard(
                            positions = uiState.openPositions,
                            quoteCurrency = uiState.botSettings.quoteCurrency,
                            onClosePosition = { viewModel.onClosePosition(it) }
                        )
                    }

                    item {
                        BotActionsBar(
                            botState = uiState.botStatus.state,
                            tradingMode = uiState.botSettings.tradingMode,
                            isOperationInProgress = uiState.isOperationInProgress,
                            openPositionsCount = uiState.openPositions.size,
                            onStartBot = { viewModel.onStartBotClicked(context) },
                            onPauseBot = { viewModel.onPauseBotClicked() },
                            onStopBot = { viewModel.onStopBotClicked(context) },
                            onEmergencyStop = { viewModel.onEmergencyStopClicked() },
                            onRequestEnableLiveTrading = { viewModel.onRequestEnableLiveTrading() },
                            onSwitchToPaperTrading = { viewModel.onSwitchToPaperTrading() },
                            onCancelAllOrders = { viewModel.onCancelAllOrders() },
                            onManualBuy = { viewModel.onManualBuyClicked() },
                            onCloseAllPositions = { viewModel.onCloseAllPositionsClicked() }
                        )
                    }
                }

                DashboardTab.LOGS -> {
                    item {
                        ActivityFeedList(
                            logs = uiState.tradeLogs,
                            onExportCsv = { logs -> exportLogsToCsv(context, logs) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Live Trading Security Confirmation Dialog
    if (uiState.showLiveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissLiveTradingDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Risk", tint = LossRed)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Enable Live Trading?", fontWeight = FontWeight.Bold, color = LossRed)
                }
            },
            text = {
                Text(
                    text = "You are about to switch to LIVE TRADING mode on ${uiState.botSettings.exchangePlatform.displayName}.\n\nReal funds will be committed. Please verify your API keys and risk parameters.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmLiveTrading() },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                ) {
                    Text("PROCEED LIVE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissLiveTradingDialog() }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = Color(0xFF1B2230)
        )
    }

    // API Key Credentials Configuration Modal
    if (uiState.showApiCredentialsDialog) {
        var inputApiKey by remember { mutableStateOf("") }
        var inputSecretKey by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.onDismissCredentialsDialog() },
            title = { Text("Configure API Keys", fontWeight = FontWeight.Bold, color = CoinsEmerald) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your ${uiState.botSettings.exchangePlatform.displayName} API Key & Secret:", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = inputApiKey,
                        onValueChange = { inputApiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CoinsEmerald,
                            unfocusedBorderColor = Color(0xFF263246),
                            focusedLabelColor = CoinsEmerald,
                            unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_api_key")
                    )
                    OutlinedTextField(
                        value = inputSecretKey,
                        onValueChange = { inputSecretKey = it },
                        label = { Text("Secret Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CoinsEmerald,
                            unfocusedBorderColor = Color(0xFF263246),
                            focusedLabelColor = CoinsEmerald,
                            unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_secret_key")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onSaveCredentialsAndEnableLive(inputApiKey, inputSecretKey) },
                    enabled = inputApiKey.isNotBlank() && inputSecretKey.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CoinsEmerald),
                    modifier = Modifier.testTag("btn_save_credentials")
                ) {
                    Text("SAVE & ENABLE LIVE", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onDismissCredentialsDialog() },
                    modifier = Modifier.testTag("btn_cancel_credentials")
                ) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = Color(0xFF1B2230)
        )
    }

    // Generic Error Dialog
    uiState.errorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Exchange / Risk Notice", fontWeight = FontWeight.Bold, color = WarningAmber) },
            text = { Text(err, color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearError() },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = Color.Black)
                ) {
                    Text("ACKNOWLEDGE", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1B2230)
        )
    }

    // Backtest Result Dialog
    uiState.lastBacktestResult?.let { res ->
        AlertDialog(
            onDismissRequest = { viewModel.onDismissBacktestResult() },
            title = {
                Text(
                    text = if (res.recommendedSettings != null) "⚡ Auto-Optimization Result" else "🧪 Strategy Backtest Results",
                    fontWeight = FontWeight.Bold,
                    color = CoinsEmerald
                )
            },
            text = {
                Column {
                    Text(
                        text = "Simulated on ${res.symbol} (${res.interval}) across ${res.candlesTested} historical candles:",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141D2A), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Strategy: ${res.strategy.label}", color = TextSecondary, fontSize = 11.sp)
                            Text("Closed Trades: ${res.totalTrades} | Win Rate: ${String.format(Locale.US, "%.1f%%", res.winRate)}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (res.openPositionsCount > 0) {
                                Text(
                                    text = String.format(Locale.US, "Holding Positions: %d | Open Unrealized: %+.2f %s", res.openPositionsCount, res.unrealizedPnl, uiState.botSettings.quoteCurrency),
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "Total Net Simulated PnL: %+.2f %s", res.netPnl, uiState.botSettings.quoteCurrency),
                                color = if (res.netPnl >= 0) CoinsEmerald else LossRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (res.recommendedSettings != null) {
                        val rec = res.recommendedSettings
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CoinsEmerald.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("💡 Optimal Configuration Found:", color = CoinsEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "• Strategy: %s\n• Target TP: %.1f%% | Dip Trigger: %.1f%%\n• Trailing Stop: %s (%.1f%%)\n• DCA Multiplier: %.2fx",
                                        rec.strategyType.label,
                                        rec.takeProfitPercentage,
                                        rec.dipTriggerPercentage,
                                        if (rec.trailingTakeProfitEnabled) "Enabled" else "Off",
                                        rec.trailingStopPercentage,
                                        rec.dcaMultiplier
                                    ),
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Note: Includes 0.10% buy + 0.10% sell spot trading fees. Past performance does not guarantee future results.",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (res.recommendedSettings != null) {
                        Button(
                            onClick = { viewModel.onApplyRecommendedSettings(res.recommendedSettings) },
                            colors = ButtonDefaults.buttonColors(containerColor = CoinsEmerald, contentColor = Color.Black),
                            modifier = Modifier.testTag("btn_apply_recommended")
                        ) {
                            Text("APPLY BEST SETTINGS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    TextButton(
                        onClick = { viewModel.onDismissBacktestResult() },
                        modifier = Modifier.testTag("btn_close_backtest_modal")
                    ) {
                        Text("CLOSE", color = TextMuted)
                    }
                }
            },
            containerColor = Color(0xFF1B2230)
        )
    }
}

private fun exportLogsToCsv(context: android.content.Context, logs: List<com.example.data.model.TradeLog>) {
    val sb = StringBuilder()
    sb.append("Timestamp,Mode,Symbol,Side,Type,Price,Quantity,Amount,Fee,RealizedPnL,Status,Message\n")
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    logs.forEach { log ->
        val dateStr = dateFormat.format(Date(log.timestamp))
        val cleanMsg = log.message.replace(",", ";").replace("\n", " ")
        sb.append("${dateStr},${log.tradingMode.name},${log.symbol},${log.side},${log.type},${log.price},${log.quantity},${log.amount},${log.fee},${log.realizedPnl},${log.status},\"${cleanMsg}\"\n")
    }
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/csv"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Export Coins.ph Trade Logs CSV")
    context.startActivity(shareIntent)
}
