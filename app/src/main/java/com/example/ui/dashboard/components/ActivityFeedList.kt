package com.example.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityFeedList(
    logs: List<TradeLog>,
    onExportCsv: (List<TradeLog>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("activity_feed_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CryptoSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXECUTION & AUDIT LOGS (LATEST)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                    fontSize = 10.sp
                )
                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick = { onExportCsv(logs) },
                        modifier = Modifier.testTag("btn_export_csv")
                    ) {
                        Text(
                            text = "Export CSV",
                            color = ElectricCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trades or events executed yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            } else {
                val displayLogs = if (isExpanded) logs else logs.take(3)
                
                displayLogs.forEach { log ->
                    TradeLogItem(log = log)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                if (logs.size > 3) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Show less" else "View all ${logs.size} logs...",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeLogItem(log: TradeLog) {
    val isBuy = log.side.equals("BUY", ignoreCase = true)
    val isSell = log.side.equals("SELL", ignoreCase = true)
    val isRiskBlocked = log.side.equals("BLOCKED", ignoreCase = true)
    val isHalt = log.side.equals("HALT", ignoreCase = true)

    val icon = when {
        isBuy -> Icons.Default.ArrowDownward
        isSell -> Icons.Default.ArrowUpward
        isRiskBlocked -> Icons.Default.Warning
        else -> Icons.Default.Info
    }

    val iconColor = when {
        isBuy -> CoinsEmerald
        isSell -> if (log.realizedPnl >= 0) CoinsEmerald else LossRed
        isRiskBlocked -> WarningAmber
        isHalt -> LossRed
        else -> ElectricCyan
    }

    val timeString = rememberFormattedTime(log.timestamp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2230), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("log_item_${log.id}")
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = log.side,
                    tint = iconColor,
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${log.side} ${log.symbol}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Mode tag
                        Box(
                            modifier = Modifier
                                .background(
                                    if (log.tradingMode == TradingMode.PAPER) ElectricCyan.copy(alpha = 0.15f) else LossRed.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (log.tradingMode == TradingMode.PAPER) "PAPER" else "LIVE",
                                color = if (log.tradingMode == TradingMode.PAPER) ElectricCyan else LossRed,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (log.price > 0 && log.quantity > 0) {
                    Text(
                        text = String.format(Locale.US, "@ %,.2f | Qty: %.6f | Amt: %,.2f %s", log.price, log.quantity, log.amount, log.feeCurrency),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                if (log.message.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRiskBlocked) WarningAmber else TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberFormattedTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
