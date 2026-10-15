package com.example.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.TradeLog
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoSurface
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun EquityCurveCard(
    portfolio: PortfolioSnapshot,
    tradeLogs: List<TradeLog>,
    quoteCurrency: String,
    modifier: Modifier = Modifier
) {
    val closedSellLogs = remember(tradeLogs) {
        tradeLogs.filter {
            it.side.equals("SELL", ignoreCase = true) &&
            it.status.equals("FILLED", ignoreCase = true)
        }.sortedBy { it.timestamp }
    }

    val pnlDataPoints = remember(closedSellLogs, portfolio.totalRealizedPnl) {
        val points = mutableListOf(0.0)
        var runningPnl = 0.0
        closedSellLogs.forEach { log ->
            runningPnl += log.realizedPnl
            points.add(runningPnl)
        }
        if (points.size == 1 && portfolio.totalRealizedPnl != 0.0) {
            points.add(portfolio.totalRealizedPnl)
        }
        points
    }

    val bestTrade = remember(closedSellLogs) {
        closedSellLogs.maxOfOrNull { it.realizedPnl } ?: 0.0
    }

    val worstTrade = remember(closedSellLogs) {
        closedSellLogs.minOfOrNull { it.realizedPnl } ?: 0.0
    }

    val profitFactor = remember(closedSellLogs) {
        val grossProfit = closedSellLogs.filter { it.realizedPnl > 0 }.sumOf { it.realizedPnl }
        val grossLoss = closedSellLogs.filter { it.realizedPnl < 0 }.sumOf { kotlin.math.abs(it.realizedPnl) }
        if (grossLoss > 0.0) grossProfit / grossLoss else if (grossProfit > 0.0) 99.9 else 0.0
    }

    val netPnl = portfolio.totalRealizedPnl
    val isProfit = netPnl >= 0.0
    val curveColor = if (isProfit) CoinsEmerald else LossRed

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("equity_curve_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CryptoSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "📈 EQUITY CURVE & PERFORMANCE ANALYTICS",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Stat Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Best Trade Pill
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1B2230), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text("Best Trade", fontSize = 9.sp, color = TextMuted)
                        Text(
                            text = String.format(Locale.US, "%+.2f", bestTrade),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (bestTrade >= 0) CoinsEmerald else LossRed
                        )
                    }
                }

                // Worst Trade Pill
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1B2230), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text("Worst Trade", fontSize = 9.sp, color = TextMuted)
                        Text(
                            text = String.format(Locale.US, "%+.2f", worstTrade),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (worstTrade >= 0) CoinsEmerald else LossRed
                        )
                    }
                }

                // Profit Factor Pill
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1B2230), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text("Profit Factor", fontSize = 9.sp, color = TextMuted)
                        Text(
                            text = String.format(Locale.US, "%.2f", profitFactor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (profitFactor >= 1.2) CoinsEmerald else TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Chart Container (height: 120.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                if (pnlDataPoints.size < 2) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Awaiting closed trades for equity curve...", fontSize = 11.sp, color = TextMuted)
                    }
                } else {
                    val minPnl = remember(pnlDataPoints) { pnlDataPoints.minOf { it }.coerceAtMost(0.0) }
                    val maxPnl = remember(pnlDataPoints) { pnlDataPoints.maxOf { it }.coerceAtLeast(1.0) }
                    val pnlRange = remember(minPnl, maxPnl) { (maxPnl - minPnl).coerceAtLeast(1.0) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("equity_curve_canvas")
                    ) {
                        val width = size.width
                        val height = size.height

                        // Zero Baseline
                        val zeroY = height - (((0.0 - minPnl) / pnlRange) * height).toFloat()
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(0f, zeroY),
                            end = Offset(width, zeroY),
                            strokeWidth = 1.dp.toPx()
                        )

                        val count = pnlDataPoints.size
                        val points = pnlDataPoints.mapIndexed { index, pnl ->
                            val x = index * (width / (count - 1).coerceAtLeast(1))
                            val y = height - (((pnl - minPnl) / pnlRange) * height).toFloat()
                            Offset(x, y)
                        }

                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(points.last().x, height)
                            lineTo(points.first().x, height)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(curveColor.copy(alpha = 0.35f), Color.Transparent),
                                startY = 0f,
                                endY = height
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = curveColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        val lastPoint = points.last()
                        drawCircle(color = curveColor, radius = 4.dp.toPx(), center = lastPoint)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = lastPoint)
                    }
                }
            }
        }
    }
}
