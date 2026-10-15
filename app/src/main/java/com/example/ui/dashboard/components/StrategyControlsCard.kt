package com.example.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.window.Dialog
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
    onKellyDynamicSizingToggled: (Boolean) -> Unit = {},
    onSeafarerLowDataModeToggled: (Boolean) -> Unit = {},
    onRunBacktestClicked: () -> Unit = {},
    onAutoOptimizeClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strategies = listOf(
        StrategyType.DCA,
        StrategyType.RSI_SWING,
        StrategyType.GRID,
        StrategyType.BOLLINGER,
        StrategyType.TURTLE_BREAKOUT,
        StrategyType.VWAP_REVERSAL,
        StrategyType.AI_GOD_MODE
    )

    var showStrategyDropdownModal by remember { mutableStateOf(false) }
    var boostersExpanded by remember { mutableStateOf(false) }

    val activeBoostersCount = remember(settings) {
        listOf(
            settings.smartEntryEnabled,
            settings.breakEvenLockEnabled,
            settings.greenCandleConfirmation,
            settings.neverSellAtLoss,
            settings.spreadGuardEnabled,
            settings.crashTrendFilterEnabled,
            settings.partialTakeProfitEnabled,
            settings.atrDynamicTargetsEnabled,
            settings.orderBookWallFilterEnabled,
            settings.kellyDynamicSizingEnabled,
            settings.seafarerLowDataMode
        ).count { it }
    }

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

            // Active Strategy Dropdown Selector Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B2230), RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = CoinsEmerald.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { showStrategyDropdownModal = true }
                    .padding(12.dp)
                    .testTag("strategy_dropdown_selector")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE STRATEGY ENGINE",
                            fontSize = 9.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = settings.strategyType.label,
                            fontSize = 15.sp,
                            color = CoinsEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = settings.strategyType.description,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(CoinsEmerald.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "CHANGE ▼",
                            color = CoinsEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
                    StrategyType.BOLLINGER, StrategyType.TURTLE_BREAKOUT, StrategyType.VWAP_REVERSAL, StrategyType.AI_GOD_MODE -> {
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

            // PROFIT & WIN-RATE BOOSTERS SECTION (Collapsible Drawer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141D2A), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { boostersExpanded = !boostersExpanded }
                            .testTag("btn_toggle_boosters_dropdown"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "⚡ PROFIT & WIN-RATE BOOSTERS",
                                color = CoinsEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$activeBoostersCount of 11 active — Tap to ${if (boostersExpanded) "hide" else "configure"}",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1B2230), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (boostersExpanded) "▲ HIDE" else "▼ CONFIGURE",
                                color = CoinsEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnimatedVisibility(visible = boostersExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(modifier = Modifier.height(4.dp))

                            // Category 1: ENTRY & SIZING
                            Text("🎯 ENTRY & SIZING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.5.sp)

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

                            // Toggle 2: Green Candle Confirm
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

                            // Toggle 3: Half-Kelly Dynamic Sizing
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Half-Kelly Dynamic Sizing (0.6x–1.5x)", color = TextPrimary, fontSize = 11.sp)
                                Switch(
                                    checked = settings.kellyDynamicSizingEnabled,
                                    onCheckedChange = { onKellyDynamicSizingToggled(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                                    modifier = Modifier.scale(0.8f).testTag("switch_kelly_sizing")
                                )
                            }

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

                            Spacer(modifier = Modifier.height(4.dp))

                            // Category 2: PROFIT LOCKING
                            Text("💰 PROFIT LOCKING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.5.sp)

                            // Toggle 4: Break-Even Lock
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

                            // Toggle 5: 2-Stage Partial TP
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

                            // Toggle 6: ATR Dynamic Targets
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

                            Spacer(modifier = Modifier.height(4.dp))

                            // Category 3: RISK, WHALE & SEAFARER GUARDS
                            Text("🛡️ RISK, WHALE & SEAFARER GUARDS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.5.sp)

                            // Toggle 7: Never Sell at Loss (Spot HODL)
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

                            // Toggle 8: Spread Guard
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

                            // Toggle 9: Crash Trend Filter
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

                            // Toggle 10: Order Book Whale Wall Filter
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

                            // Toggle 11: Seafarer Satellite / Low-Data Mode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚓ Seafarer Satellite / Low-Data Mode", color = TextPrimary, fontSize = 11.sp)
                                Switch(
                                    checked = settings.seafarerLowDataMode,
                                    onCheckedChange = { onSeafarerLowDataModeToggled(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CoinsEmerald),
                                    modifier = Modifier.scale(0.8f).testTag("switch_seafarer_mode")
                                )
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

    // Strategy Selection Modal Dialog
    if (showStrategyDropdownModal) {
        Dialog(
            onDismissRequest = { showStrategyDropdownModal = false }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1B2230)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Select Algorithmic Strategy (7 Engines)",
                        color = CoinsEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(strategies.size) { idx ->
                            val strat = strategies[idx]
                            val isSelected = settings.strategyType == strat
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSelected) CoinsEmerald.copy(alpha = 0.15f) else Color(0xFF141D2A),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onStrategySelected(strat)
                                        showStrategyDropdownModal = false
                                    }
                                    .padding(12.dp)
                                    .testTag("strategy_tab_${strat.name}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = strat.label,
                                            color = if (isSelected) CoinsEmerald else TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = strat.description,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .background(CoinsEmerald, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("ACTIVE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStrategyDropdownModal = false }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CANCEL", color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
