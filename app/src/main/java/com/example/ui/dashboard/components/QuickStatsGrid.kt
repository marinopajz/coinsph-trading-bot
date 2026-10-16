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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PortfolioSnapshot
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun QuickStatsGrid(
    portfolio: PortfolioSnapshot,
    quoteCurrency: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_stats_grid"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CryptoSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "PORTFOLIO",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                letterSpacing = 1.sp,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Row 1: Available & Allocated
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Available ($quoteCurrency)",
                    value = String.format(Locale.US, "%,.2f", portfolio.quoteBalance),
                    accentColor = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Allocated ($quoteCurrency)",
                    value = String.format(Locale.US, "%,.2f", portfolio.allocatedCapital),
                    accentColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: PnL & Win Rate (Matched Height & Symmetrical Subtitles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val totalPnl = portfolio.totalRealizedPnl + portfolio.unrealizedPnl
                val pnlColor = if (totalPnl >= 0) CoinsEmerald else LossRed
                val realizedPnl = portfolio.totalRealizedPnl
                val unrealizedPnl = portfolio.unrealizedPnl

                // Net PnL Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1B2230), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = "Net PnL ($quoteCurrency)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%+,.2f", totalPnl),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = pnlColor,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = String.format(Locale.US, "Closed: %+,.2f", realizedPnl),
                            fontSize = 8.5.sp,
                            color = if (realizedPnl >= 0) CoinsEmerald.copy(alpha = 0.85f) else TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(Locale.US, "HODL:   %+,.2f", unrealizedPnl),
                            fontSize = 8.5.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }

                // Win Rate / Trades Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1B2230), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = "Win Rate / Trades",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f%%", portfolio.winRate),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (portfolio.winRate >= 50.0) CoinsEmerald else TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = String.format(Locale.US, "Trades: %d closed", portfolio.totalTrades),
                            fontSize = 8.5.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Text(
                            text = "Target: >= 50.0%",
                            fontSize = 8.5.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1B2230), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = accentColor,
                fontSize = 12.sp
            )
        }
    }
}
