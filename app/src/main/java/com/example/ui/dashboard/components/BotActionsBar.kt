package com.example.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BotState
import com.example.data.model.TradingMode
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@Composable
fun BotActionsBar(
    botState: BotState,
    tradingMode: TradingMode,
    isOperationInProgress: Boolean,
    openPositionsCount: Int = 0,
    onStartBot: () -> Unit,
    onPauseBot: () -> Unit,
    onStopBot: () -> Unit,
    onEmergencyStop: () -> Unit,
    onRequestEnableLiveTrading: () -> Unit,
    onSwitchToPaperTrading: () -> Unit,
    onCancelAllOrders: () -> Unit,
    onManualBuy: () -> Unit = {},
    onCloseAllPositions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showEmergencyConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bot_actions_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CryptoSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "EXECUTION CONTROLS",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                letterSpacing = 1.sp,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Quick-Trade Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onManualBuy,
                    enabled = !isOperationInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E3A8A),
                        contentColor = Color(0xFF93C5FD)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_quick_buy")
                ) {
                    Text("⚡ INSTANT BUY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = onCloseAllPositions,
                    enabled = !isOperationInProgress && openPositionsCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LossRed.copy(alpha = 0.25f),
                        contentColor = LossRed
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_close_all")
                ) {
                    Text("🔥 SELL ALL ($openPositionsCount)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Row: Engine Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (botState != BotState.RUNNING) {
                    Button(
                        onClick = onStartBot,
                        enabled = !isOperationInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoinsEmerald,
                            contentColor = Color(0xFF002919)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_start_bot")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (botState == BotState.PAUSED) "RESUME" else "START BOT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onPauseBot,
                        enabled = !isOperationInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningAmber,
                            contentColor = Color(0xFF382300)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_pause_bot")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PAUSE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onStopBot,
                        enabled = !isOperationInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF263246),
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_stop_bot")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("STOP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Row: Cancel Orders & Emergency Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelAllOrders,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_cancel_orders")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CANCEL ORDERS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showEmergencyConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LossRed.copy(alpha = 0.2f),
                        contentColor = LossRed
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_emergency_stop")
                ) {
                    Icon(Icons.Default.Block, contentDescription = "Halt", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("HALT ALL", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tertiary Row: Mode Switching (Compact)
            if (tradingMode == TradingMode.PAPER) {
                TextButton(
                    onClick = onRequestEnableLiveTrading,
                    modifier = Modifier.fillMaxWidth().testTag("btn_switch_live")
                ) {
                    Icon(Icons.Default.Security, contentDescription = "Live", tint = LossRed, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enable Live Trading Mode", color = LossRed, fontSize = 11.sp)
                }
            } else {
                TextButton(
                    onClick = onSwitchToPaperTrading,
                    modifier = Modifier.fillMaxWidth().testTag("btn_switch_paper")
                ) {
                    Icon(Icons.Default.Security, contentDescription = "Paper", tint = ElectricCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Switch to Paper Trading", color = ElectricCyan, fontSize = 11.sp)
                }
            }
        }
    }

    // Emergency Stop Confirmation Dialog
    if (showEmergencyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmergencyConfirm = false },
            title = { Text("Confirm Emergency Stop", fontWeight = FontWeight.Bold, color = LossRed) },
            text = {
                Text(
                    "This immediately halts all autonomous trading loops and rejects new entry signals. Open positions will remain intact until manually closed.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyConfirm = false
                        onEmergencyStop()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                ) {
                    Text("HALT NOW", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyConfirm = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = Color(0xFF1B2230)
        )
    }
}
