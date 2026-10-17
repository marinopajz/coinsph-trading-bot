package com.example.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OpenPosition
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoSurface
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun OpenPositionsCard(
    positions: List<OpenPosition>,
    quoteCurrency: String,
    onClosePosition: (String) -> Unit,
    takeProfitPercentage: Double = 5.0,
    modifier: Modifier = Modifier
) {
    if (positions.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("open_positions_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CryptoSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE OPEN POSITIONS (${positions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                positions.forEach { pos ->
                    val isProfit = pos.unrealizedPnl >= 0
                    val pnlColor = if (isProfit) CoinsEmerald else LossRed
                    val posQuote = when {
                        pos.symbol.uppercase().endsWith("USDT") -> "USDT"
                        pos.symbol.uppercase().endsWith("USDC") -> "USDC"
                        else -> quoteCurrency
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1B2230), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("position_item_${pos.id.take(6)}")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Top Row: Symbol/Qty and PnL Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${pos.symbol} · ${String.format(Locale.US, "%.6f", pos.quantity)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(pnlColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%+.2f%% (%+,.2f %s)", pos.pnlPercentage, pos.unrealizedPnl, posQuote),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = pnlColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Middle Row: Entry / Now Prices and Close Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "Entry: %,.2f", pos.entryPrice),
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = String.format(Locale.US, "Now: %,.2f", pos.currentPrice),
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { onClosePosition(pos.id) },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("btn_close_pos_${pos.id.take(6)}")
                                ) {
                                    Text("Close", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Distance-to-Take-Profit Progress Bar
                            val targetTpPrice = pos.entryPrice * (1.0 + (takeProfitPercentage / 100.0))
                            val remainingPct = (((targetTpPrice - pos.currentPrice) / pos.currentPrice) * 100.0).coerceAtLeast(0.0)
                            val baseMin = pos.entryPrice * 0.985
                            val progress = if (targetTpPrice > baseMin) {
                                ((pos.currentPrice - baseMin) / (targetTpPrice - baseMin)).toFloat().coerceIn(0.05f, 1.0f)
                            } else 0.05f

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "Target TP: %,.2f", targetTpPrice),
                                        fontSize = 10.sp,
                                        color = CoinsEmerald,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = String.format(Locale.US, "Need +%.2f%% to TP", remainingPct),
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .fillMaxHeight()
                                            .background(if (pos.currentPrice >= pos.entryPrice) CoinsEmerald else Color(0xFF00E5FF))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
