package com.example.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import java.util.Locale

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
    modifier: Modifier = Modifier
) {
    val strategies = listOf(StrategyType.DCA, StrategyType.RSI_SWING, StrategyType.GRID)
    val selectedIndex = strategies.indexOf(settings.strategyType).coerceAtLeast(0)

    var orderAmountText by remember(settings.orderAmount) { mutableStateOf(settings.orderAmount.toString()) }
    var dipTriggerText by remember(settings.dipTriggerPercentage) { mutableStateOf(settings.dipTriggerPercentage.toString()) }
    var takeProfitText by remember(settings.takeProfitPercentage) { mutableStateOf(settings.takeProfitPercentage.toString()) }
    var stopLossText by remember(settings.stopLossPercentage) { mutableStateOf(settings.stopLossPercentage.toString()) }
    var maxCapitalText by remember(settings.maxCapital) { mutableStateOf(settings.maxCapital.toString()) }
    var maxDailyLossText by remember(settings.maxDailyLoss) { mutableStateOf(settings.maxDailyLoss.toString()) }

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

            // Strategy Tabs
            TabRow(
                selectedTabIndex = selectedIndex,
                containerColor = Color(0xFF1B2230),
                contentColor = CoinsEmerald,
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
                                    StrategyType.RSI_SWING -> "RSI SWING"
                                    StrategyType.GRID -> "GRID"
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

            // Row 1: Order Amount & Dip Trigger (or Grid/RSI params)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Take Profit % & Stop Loss %
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

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Max Capital & Max Daily Loss Limit
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
