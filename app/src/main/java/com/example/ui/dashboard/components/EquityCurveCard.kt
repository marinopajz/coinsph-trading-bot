package com.example.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
import java.util.Locale
import kotlin.math.roundToInt

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

    var selectedPointIndex by remember(pnlDataPoints) { mutableStateOf<Int?>(null) }

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
                            .pointerInput(pnlDataPoints) {
                                detectTapGestures { tapOffset ->
                                    if (pnlDataPoints.isNotEmpty()) {
                                        val count = pnlDataPoints.size
                                        val rightGutterPx = 60.dp.toPx()
                                        val pWidth = (size.width - rightGutterPx).coerceAtLeast(1f)
                                        val stepX = pWidth / (count - 1).coerceAtLeast(1)
                                        val idx = (tapOffset.x / stepX).roundToInt().coerceIn(0, count - 1)
                                        selectedPointIndex = if (selectedPointIndex == idx) null else idx
                                    }
                                }
                            }
                            .pointerInput(pnlDataPoints) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    if (pnlDataPoints.isNotEmpty()) {
                                        val count = pnlDataPoints.size
                                        val rightGutterPx = 60.dp.toPx()
                                        val pWidth = (size.width - rightGutterPx).coerceAtLeast(1f)
                                        val stepX = pWidth / (count - 1).coerceAtLeast(1)
                                        val idx = (change.position.x / stepX).roundToInt().coerceIn(0, count - 1)
                                        selectedPointIndex = idx
                                    }
                                }
                            }
                            .testTag("equity_curve_canvas")
                    ) {
                        val width = size.width
                        val height = size.height

                        val rightGutterPx = 60.dp.toPx()
                        val plotWidth = (width - rightGutterPx).coerceAtLeast(1f)

                        val textPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = 0xFF8E9BAE.toInt()
                            textSize = 8.sp.toPx()
                            textAlign = android.graphics.Paint.Align.RIGHT
                            typeface = android.graphics.Typeface.MONOSPACE
                        }

                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                        // Top line
                        val topY = 8.dp.toPx()
                        drawLine(color = Color.White.copy(alpha = 0.07f), start = Offset(0f, topY), end = Offset(plotWidth, topY), strokeWidth = 1f, pathEffect = dashEffect)
                        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%+.0f", maxPnl), width - 2.dp.toPx(), topY + 3.dp.toPx(), textPaint)

                        // Zero Baseline
                        val usablePlotHeight = (height - 16.dp.toPx()).coerceAtLeast(1f)
                        val zeroY = (height - 8.dp.toPx()) - (((0.0 - minPnl) / pnlRange) * usablePlotHeight).toFloat()
                        drawLine(color = Color.White.copy(alpha = 0.15f), start = Offset(0f, zeroY), end = Offset(plotWidth, zeroY), strokeWidth = 1.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText("0", width - 2.dp.toPx(), zeroY + 3.dp.toPx(), textPaint)

                        // Bottom line
                        val botY = height - 8.dp.toPx()
                        drawLine(color = Color.White.copy(alpha = 0.07f), start = Offset(0f, botY), end = Offset(plotWidth, botY), strokeWidth = 1f, pathEffect = dashEffect)
                        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%+.0f", minPnl), width - 2.dp.toPx(), botY + 3.dp.toPx(), textPaint)

                        val count = pnlDataPoints.size
                        val points = pnlDataPoints.mapIndexed { index, pnl ->
                            val x = index * (plotWidth / (count - 1).coerceAtLeast(1))
                            val y = (height - 8.dp.toPx()) - (((pnl - minPnl) / pnlRange) * usablePlotHeight).toFloat()
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

                        // Scrubber Guide Line & Dot
                        val selIdx = selectedPointIndex
                        if (selIdx != null && selIdx in points.indices) {
                            val selPt = points[selIdx]
                            drawLine(
                                color = Color.White.copy(alpha = 0.45f),
                                start = Offset(selPt.x, 0f),
                                end = Offset(selPt.x, height),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )
                            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = selPt)
                            drawCircle(color = curveColor, radius = 3.dp.toPx(), center = selPt)
                        } else {
                            val lastPoint = points.last()
                            drawCircle(color = curveColor, radius = 4.dp.toPx(), center = lastPoint)
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = lastPoint)
                        }
                    }

                    // Scrubber Floating Top Pill
                    if (selectedPointIndex != null && selectedPointIndex!! in pnlDataPoints.indices) {
                        val idx = selectedPointIndex!!
                        val pnlVal = pnlDataPoints[idx]
                        val isPos = pnlVal >= 0
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(Color(0xEE141923), RoundedCornerShape(6.dp))
                                .border(1.dp, CoinsEmerald.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .clickable { selectedPointIndex = null }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("equity_scrubber_pill")
                        ) {
                            Text(
                                text = String.format(Locale.US, "Trade #%d · PnL: %+.2f %s", idx, pnlVal, quoteCurrency),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isPos) CoinsEmerald else LossRed
                            )
                        }
                    }
                }
            }
        }
    }
}
