package com.example.ui.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TradingMode
import com.example.ui.dashboard.components.ActivityFeedList
import com.example.ui.dashboard.components.BotActionsBar
import com.example.ui.dashboard.components.HeaderStatusCard
import com.example.ui.dashboard.components.OpenPositionsCard
import com.example.ui.dashboard.components.PriceChart
import com.example.ui.dashboard.components.QuickStatsGrid
import com.example.ui.dashboard.components.StrategyControlsCard
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoBackground
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.IconButton
import com.example.data.model.BotState
import com.example.ui.theme.LiveBadgeBg
import com.example.ui.theme.LiveBadgeText
import com.example.ui.theme.PaperBadgeBg
import com.example.ui.theme.PaperBadgeText

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
                                text = "COINS.PH",
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeaderStatusCard(
                    status = uiState.botStatus,
                    activeSymbol = uiState.botSettings.symbol,
                    availableSymbols = uiState.availableSymbols,
                    onSymbolSelected = { viewModel.onSymbolSelected(it) }
                )
            }

            item {
                val ticker = uiState.marketTicker
                PriceChart(
                    priceHistory = uiState.priceHistory,
                    currentPrice = ticker?.price ?: 0.0,
                    quoteCurrency = uiState.botSettings.quoteCurrency,
                    change24h = ticker?.change24h ?: 0.0
                )
            }

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
                StrategyControlsCard(
                    settings = uiState.botSettings,
                    onStrategySelected = { viewModel.onStrategySelected(it) },
                    onOrderAmountChanged = { viewModel.onOrderAmountChanged(it) },
                    onDipTriggerChanged = { viewModel.onDipTriggerChanged(it) },
                    onTakeProfitChanged = { viewModel.onTakeProfitChanged(it) },
                    onStopLossChanged = { viewModel.onStopLossChanged(it) },
                    onMaxCapitalChanged = { viewModel.onMaxCapitalChanged(it) },
                    onMaxDailyLossChanged = { viewModel.onMaxDailyLossChanged(it) }
                )
            }

            item {
                BotActionsBar(
                    botState = uiState.botStatus.state,
                    tradingMode = uiState.botSettings.tradingMode,
                    isOperationInProgress = uiState.isOperationInProgress,
                    onStartBot = { viewModel.onStartBotClicked() },
                    onPauseBot = { viewModel.onPauseBotClicked() },
                    onStopBot = { viewModel.onStopBotClicked() },
                    onEmergencyStop = { viewModel.onEmergencyStopClicked() },
                    onRequestEnableLiveTrading = { viewModel.onRequestEnableLiveTrading() },
                    onSwitchToPaperTrading = { viewModel.onSwitchToPaperTrading() },
                    onCancelAllOrders = { viewModel.onCancelAllOrders() }
                )
            }

            item {
                ActivityFeedList(logs = uiState.tradeLogs)
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
                Column {
                    Text(
                        "You are about to switch from Paper Trading to LIVE TRADING. Live trading submits real orders to your authenticated Coins.ph account.",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B1D1D), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            "Mandatory Safety Checklist:\n" +
                            "• Real funds will be deducted from your exchange balance.\n" +
                            "• Orders are routed exclusively via secure backend server.\n" +
                            "• Daily loss stop (${uiState.botSettings.maxDailyLoss} ${uiState.botSettings.quoteCurrency}) will be enforced.\n" +
                            "• Trading involves financial risk. No profit is guaranteed.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmLiveTrading() },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                    modifier = Modifier.testTag("btn_confirm_live_modal")
                ) {
                    Text("I UNDERSTAND & ENABLE LIVE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onDismissLiveTradingDialog() },
                    modifier = Modifier.testTag("btn_cancel_live_modal")
                ) {
                    Text("KEEP PAPER MODE", color = TextMuted)
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
}
