package com.example.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BotSettings
import com.example.data.model.StrategyType
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StrategyControlsCard(
    settings: BotSettings,
    onStrategySelected: (StrategyType) -> Unit,
    onOrderAmountChanged: (Double) -> Unit,
    onDipTriggerChanged: (Double) -> Unit,
    onTakeProfitChanged: (Double) -> Unit,
    onStopLossChanged: (Double) -> Unit,
    onMaxCapitalChanged: (Double) -> Unit,
    onMaxDailyLossChanged: (Double) -> Unit,
    onRsiPeriodChanged: (Int) -> Unit = {},
    onGridLevelsChanged: (Int) -> Unit = {},
    onGridLowerPriceChanged: (Double) -> Unit = {},
    onGridUpperPriceChanged: (Double) -> Unit = {},
    onTrailingTakeProfitToggled: (Boolean) -> Unit = {},
    onTrailingStopPercentageChanged: (Double) -> Unit = {},
    onSmartEntryToggled: (Boolean) -> Unit = {},
    onBreakEvenLockToggled: (Boolean) -> Unit = {},
    onGreenCandleConfirmationToggled: (Boolean) -> Unit = {},
    onDcaMultiplierChanged: (Double) -> Unit = {},
    onNeverSellAtLossToggled: (Boolean) -> Unit = {},
    onSpreadGuardToggled: (Boolean) -> Unit = {},
    onCrashTrendFilterToggled: (Boolean) -> Unit = {},
    onPartialTakeProfitToggled: (Boolean) -> Unit = {},
    onAtrDynamicTargetsToggled: (Boolean) -> Unit = {},
    onOrderBookWallFilterToggled: (Boolean) -> Unit = {},
    onRunBacktestClicked: () -> Unit = {},
    onAutoOptimizeClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strategies = listOf(StrategyType.DCA, StrategyType.RSI_SWING, StrategyType.GRID, StrategyType.BOLLINGER, StrategyType.TURTLE_BREAKOUT, StrategyType.VWAP_REVERSAL)
    val selectedIndex = strategies.indexOf(settings.strategyType).coerceAtLeast(0)

    var orderAmountText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.orderAmount.toString()) }
    var dipTriggerText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.dipTriggerPercentage.toString()) }
    var rsiPeriodText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.rsiPeriod.toString()) }
    var gridLevelsText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.gridLevels.toString()) }
    var gridLowerPriceText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.gridLowerPrice.toString()) }
    var gridUpperPriceText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.gridUpperPrice.toString()) }
    var takeProfitText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.takeProfitPercentage.toString()) }
    var stopLossText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.stopLossPercentage.toString()) }
    var maxCapitalText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.maxCapital.toString()) }
    var maxDailyLossText by remember(settings.symbol, settings.strategyType) { mutableStateOf(settings.maxDailyLoss.toString()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("strategy_controls_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CryptoSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "ALGORITHMIC STRATEGY CONTROLLER",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Strategy Tabs (Scrollable)
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                containerColor = Color(0xFF1B2230),
                contentColor = CoinsEmerald,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        color = CoinsEmerald
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B2230), RoundedCornerShape(10.dp))
            ) {
                strategies.forEachIndexed { index, strat ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { onStrategySelected(strat) },
                        text = {
                            Text(
                                text = when (strat) {
                                    StrategyType.DCA -> "DCA"
                                    StrategyType.RSI_SWING -> "RSI"
                                    StrategyType.GRID -> "GRID"
                                    StrategyType.BOLLINGER -> "BOLL"
                                    StrategyType.TURTLE_BREAKOUT -> "BREAKOUT"
                                    StrategyType.VWAP_REVERSAL -> "VWAP"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selectedIndex == index) CoinsEmerald else TextSecondary
                            )
                        },
                        modifier = Modifier.testTag("strategy_tab_${strat.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Strategy Description Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141D2A), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = settings.strategyType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Order Amount & Strategy Specific Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = orderAmountText,
                    onValueChange = {
                        orderAmountText = it
                        it.toDoubleOrNull()?.let { amt -> onOrderAmountChanged(amt) }
                    },
                    label = { Text("Order Size (${settings.quoteCurrency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinsEmerald,
                        unfocusedBorderColor = Color(0xFF263246),
                        focusedLabelColor = CoinsEmerald,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_order_amount")
                )

                when (settings.strategyType) {
                    StrategyType.DCA -> {
                        OutlinedTextField(
                            value = dipTriggerText,
                            onValueChange = {
                                dipTriggerText = it
                                it.toDoubleOrNull()?.let { dip -> onDipTriggerChanged(dip) }
                            },
                            label = { Text("Dip Trigger (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CoinsEmerald,
                                unfocusedBorderColor = Color(0xFF263246),
                                focusedLabelColor = CoinsEmerald,
                                unfocusedLabelColor = TextMuted
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_dip_trigger")
                        )
                    }
                    StrategyType.RSI_SWING -> {
                        OutlinedTextField(
                            value = rsiPeriodText,
                            onValueChange = {
                                rsiPeriodText = it
                                it.toIntOrNull()?.let { period -> onRsiPeriodChanged(period) }
                            },
                            label = { Text("RSI Period") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CoinsEmerald,
                                unfocusedBorderColor = Color(0xFF263246),
                                focusedLabelColor = CoinsEmerald,
                                unfocusedLabelColor = TextMuted
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_rsi_period")
                        )
                    }
                    StrategyType.GRID -> {
                        OutlinedTextField(
                            value = gridLevelsText,
                            onValueChange = {
                                gridLevelsText = it
                                it.toIntOrNull()?.let { levels -> onGridLevelsChanged(levels) }
                            },
                            label = { Text("Grid Levels") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CoinsEmerald,
                                unfocusedBorderColor = Color(0xFF263246),
                                focusedLabelColor = CoinsEmerald,
                                unfocusedLabelColor = TextMuted
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_grid_levels")
                        )
                    }
                    StrategyType.BOLLINGER, StrategyType.TURTLE_BREAKOUT, StrategyType.VWAP_REVERSAL -> {
                        OutlinedTextField(
                            value = takeProfitText,
                            onValueChange = {
                                takeProfitText = it
                                it.toDoubleOrNull()?.let { tp -> onTakeProfitChanged(tp) }
                            },
                            label = { Text("Target Profit (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CoinsEmerald,
                                unfocusedBorderColor = Color(0xFF263246),
                                focusedLabelColor = CoinsEmerald,
                                unfocusedLabelColor = TextMuted
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_bollinger_tp")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Take Profit & Stop Loss Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = takeProfitText,
                    onValueChange = {
                        takeProfitText = it
                        it.toDoubleOrNull()?.let { tp -> onTakeProfitChanged(tp) }
                    },
                    label = { Text("Take Profit (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinsEmerald,
                        unfocusedBorderColor = Color(0xFF263246),
                        focusedLabelColor = CoinsEmerald,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_take_profit")
                )

                OutlinedTextField(
                    value = stopLossText,
                    onValueChange = {
                        stopLossText = it
                        it.toDoubleOrNull()?.let { sl -> onStopLossChanged(sl) }
                    },
                    label = { Text("Stop Loss (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinsEmerald,
                        unfocusedBorderColor = Color(0xFF263246),
                        focusedLabelColor = CoinsEmerald,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_stop_loss")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // PROFIT & WIN-RATE BOOSTERS SECTION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141D2A), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "⚡ PROFIT & WIN-RATE BOOSTERS",
                        color = CoinsEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Toggle 1: Smart Entry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Smart Entry (Wait for Dip/RSI)", color = TextPrimary, fontSize = 11.sp)
                        Switch(
                            checked = settings.smartEntryEnabled,
                            onCheckedChange = { onSmartEntryToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                            modifier = Modifier.scale(0.8f).testTag("switch_smart_entry")
                        )
                    }

                    // Toggle 2: Break-Even Lock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Break-Even Lock (+1.5% -> +0.3%)", color = TextPrimary, fontSize = 11.sp)
                        Switch(
                            checked = settings.breakEvenLockEnabled,
                            onCheckedChange = { onBreakEvenLockToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                            modifier = Modifier.scale(0.8f).testTag("switch_breakeven_lock")
                        )
                    }

                    // Toggle 3: Green Candle Confirm
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Green Candle Reversal Confirm", color = TextPrimary, fontSize = 11.sp)
                        Switch(
                            checked = settings.greenCandleConfirmation,
                            onCheckedChange = { onGreenCandleConfirmationToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                            modifier = Modifier.scale(0.8f).testTag("switch_green_candle")
                        )
                    }

                    // Toggle 4: Never Sell at Loss (Spot HODL)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Never Sell at a Loss (Spot HODL)", color = TextPrimary, fontSize = 11.sp)
                        Switch(
                            checked = settings.neverSellAtLoss,
                            onCheckedChange = { onNeverSellAtLossToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                            modifier = Modifier.scale(0.8f).testTag("switch_never_sell_loss")
                        )
                    }

                    // Toggle 5: Spread Guard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bid-Ask Spread Guard (<= 0.60%)", color = TextPrimary, fontSize = 11.sp)
                        Switch(
                            checked = settings.spreadGuardEnabled,
                            onCheckedChange = { onSpreadGuardToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                            modifier = Modifier.scale(0.8f).testTag("switch_spread_guard")
                        )
                    }

                    // Toggle 6: Crash Trend Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Crash Trend Filter (MA7/MA20)", color = TextPrimary, fontSize = 11.sp)
                        Switch(
                            checked = settings.crashTrendFilterEnabled,
                            onCheckedChange = { onCrashTrendFilterToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                            modifier = Modifier.scale(0.8f).testTag("switch_crash_filter")
                        )
                    }

                    // Toggle 7: 2-Stage Partial TP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2-Stage Partial TP (50% Scale-Out + Moonbag)", color = TextPrimary, fontSize = 11.sp)
                        Switch(
                            checked = settings.partialTakeProfitEnabled,
                            onCheckedChange = { onPartialTakeProfitToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                            modifier = Modifier.scale(0.8f).testTag("switch_partial_tp")
                        )
                    }

                    // Toggle 8: ATR Dynamic Targets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ATR Dynamic Targets (Chandelier TP/SL)", color = TextPrimary, fontSize = 11.sp)
                        Switch(
                            checked = settings.atrDynamicTargetsEnabled,
                            onCheckedChange = { onAtrDynamicTargetsToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                            modifier = Modifier.scale(0.8f).testTag("switch_atr_targets")
                        )
                    }

                    // Toggle 9: Order Book Whale Wall Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Order Book Whale Wall Filter (Bids >= 45%)", color = TextPrimary, fontSize = 11.sp)
                        Switch(
                            checked = settings.orderBookWallFilterEnabled,
                            onCheckedChange = { onOrderBookWallFilterToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                            modifier = Modifier.scale(0.8f).testTag("switch_orderbook_wall")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // DCA Multiplier Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DCA Tranche Multiplier", color = TextMuted, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(1.0, 1.25, 1.5).forEach { mult ->
                                val selected = Math.abs(settings.dcaMultiplier - mult) < 0.05
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (selected) CoinsEmerald.copy(alpha = 0.25f) else Color(0xFF1B2230),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable { onDcaMultiplierChanged(mult) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("chip_dca_multiplier_${mult}")
                                ) {
                                    Text(
                                        text = "${mult}x",
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) CoinsEmerald else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Backtest & Auto-Optimizer Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRunBacktestClicked,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("btn_run_backtest")
                ) {
                    Text("🧪 BACKTEST CURRENT", color = CoinsEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onAutoOptimizeClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = CoinsEmerald, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("btn_auto_optimize")
                ) {
                    Text("⚡ AUTO-FIND BEST", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Max Capital & Daily Loss Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = maxCapitalText,
                    onValueChange = {
                        maxCapitalText = it
                        it.toDoubleOrNull()?.let { cap -> onMaxCapitalChanged(cap) }
                    },
                    label = { Text("Max Capital (${settings.quoteCurrency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinsEmerald,
                        unfocusedBorderColor = Color(0xFF263246),
                        focusedLabelColor = CoinsEmerald,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_max_capital")
                )

                OutlinedTextField(
                    value = maxDailyLossText,
                    onValueChange = {
                        maxDailyLossText = it
                        it.toDoubleOrNull()?.let { loss -> onMaxDailyLossChanged(loss) }
                    },
                    label = { Text("Daily Loss Stop") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinsEmerald,
                        unfocusedBorderColor = Color(0xFF263246),
                        focusedLabelColor = CoinsEmerald,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_max_daily_loss")
                )
            }
        }
    }
}
