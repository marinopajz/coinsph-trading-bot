package com.example.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CandleStick
import com.example.data.model.ChartInterval
import com.example.data.model.ChartType
import com.example.data.model.PricePoint
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoSurface
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun PriceChart(
    priceHistory: List<PricePoint>,
    candles: List<CandleStick> = emptyList(),
    currentPrice: Double,
    quoteCurrency: String,
    change24h: Double,
    chartType: ChartType = ChartType.CANDLE,
    chartInterval: ChartInterval = ChartInterval.FIVE_MIN,
    averageEntryPrice: Double? = null,
    takeProfitPrice: Double? = null,
    stopLossPrice: Double? = null,
    onChartTypeSelected: (ChartType) -> Unit = {},
    onChartIntervalSelected: (ChartInterval) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isPositive = change24h >= 0
    val chartColor = if (isPositive) CoinsEmerald else LossRed

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("price_chart_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CryptoSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Price & 24h Change
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "REAL-TIME SPOT MARKET",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.US, "%,.2f %s", currentPrice, quoteCurrency),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = if (isPositive) CoinsEmerald.copy(alpha = 0.15f) else LossRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%+.2f%% 24h", change24h),
                        color = chartColor,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selector Row: Chart Type & Timeframe Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chart Type Selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartType.values().forEach { type ->
                        val selected = type == chartType
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (selected) CoinsEmerald.copy(alpha = 0.2f) else Color(0xFF1B2230),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onChartTypeSelected(type) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("chart_type_${type.name}")
                        ) {
                            Text(
                                text = type.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) CoinsEmerald else TextMuted
                            )
                        }
                    }
                }

                // Timeframe Selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartInterval.values().forEach { interval ->
                        val selected = interval == chartInterval
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (selected) CoinsEmerald.copy(alpha = 0.2f) else Color(0xFF1B2230),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onChartIntervalSelected(interval) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("chart_interval_${interval.code}")
                        ) {
                            Text(
                                text = interval.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) CoinsEmerald else TextMuted
                            )
                        }
                    }
                }
            }

            if (averageEntryPrice != null && averageEntryPrice > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B2230), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "Entry: %,.2f", averageEntryPrice),
                        color = Color(0xFF29B6F6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    takeProfitPrice?.let { tp ->
                        Text(
                            text = String.format(Locale.US, "TP: %,.2f", tp),
                            color = CoinsEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    stopLossPrice?.let { sl ->
                        Text(
                            text = String.format(Locale.US, "SL: %,.2f", sl),
                            color = LossRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Chart Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Determine active data source
                val effectiveCandles = remember(candles, priceHistory, currentPrice) {
                    if (candles.isNotEmpty()) {
                        candles
                    } else if (priceHistory.size >= 2) {
                        priceHistory.mapIndexed { i, p ->
                            val prevPrice = if (i > 0) priceHistory[i - 1].price else p.price
                            CandleStick(
                                openTime = p.timestamp,
                                open = prevPrice,
                                high = maxOf(prevPrice, p.price),
                                low = minOf(prevPrice, p.price),
                                close = p.price,
                                volume = 1.0,
                                closeTime = p.timestamp + 300000L
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

                if (effectiveCandles.isEmpty() && priceHistory.size < 2) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Awaiting market kline data...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                } else {
                    val minPrice = remember(effectiveCandles) { effectiveCandles.minOfOrNull { it.low } ?: currentPrice }
                    val maxPrice = remember(effectiveCandles) { effectiveCandles.maxOfOrNull { it.high } ?: currentPrice }
                    val maxVolume = remember(effectiveCandles) { (effectiveCandles.maxOfOrNull { it.volume } ?: 1.0).coerceAtLeast(1.0) }
                    val range = remember(minPrice, maxPrice) { (maxPrice - minPrice).coerceAtLeast(1.0) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chart_canvas")
                    ) {
                        val width = size.width
                        val height = size.height

                        val pricePlotHeight = height * 0.76f
                        val volumeHeight = height * 0.18f

                        // Draw background horizontal gridlines
                        for (i in 0..2) {
                            val y = pricePlotHeight * (i / 2f)
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1f,
                                pathEffect = if (chartType == ChartType.AREA) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null
                            )
                        }

                        // Overlay Lines: Entry, TP, SL
                        if (averageEntryPrice != null && averageEntryPrice > 0.0) {
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))

                            if (averageEntryPrice in minPrice..maxPrice) {
                                val entryY = pricePlotHeight - (((averageEntryPrice - minPrice) / range) * pricePlotHeight).toFloat()
                                drawLine(
                                    color = Color(0xFF29B6F6),
                                    start = Offset(0f, entryY),
                                    end = Offset(width, entryY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }

                            if (takeProfitPrice != null && takeProfitPrice in minPrice..maxPrice) {
                                val tpY = pricePlotHeight - (((takeProfitPrice - minPrice) / range) * pricePlotHeight).toFloat()
                                drawLine(
                                    color = CoinsEmerald,
                                    start = Offset(0f, tpY),
                                    end = Offset(width, tpY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }

                            if (stopLossPrice != null && stopLossPrice in minPrice..maxPrice) {
                                val slY = pricePlotHeight - (((stopLossPrice - minPrice) / range) * pricePlotHeight).toFloat()
                                drawLine(
                                    color = LossRed,
                                    start = Offset(0f, slY),
                                    end = Offset(width, slY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }
                        }

                        when (chartType) {
                            ChartType.CANDLE -> {
                                val count = effectiveCandles.size
                                val stepX = width / count.coerceAtLeast(1)
                                val candleWidth = (stepX * 0.65f).coerceAtLeast(2f)

                                val ma7Path = Path()
                                var hasMaStart = false

                                effectiveCandles.forEachIndexed { i, candle ->
                                    val centerX = i * stepX + stepX / 2f
                                    val candleLeft = centerX - candleWidth / 2f

                                    val isBullish = candle.close >= candle.open
                                    val candleColor = if (isBullish) CoinsEmerald else LossRed

                                    val highY = pricePlotHeight - (((candle.high - minPrice) / range) * pricePlotHeight).toFloat()
                                    val lowY = pricePlotHeight - (((candle.low - minPrice) / range) * pricePlotHeight).toFloat()
                                    val openY = pricePlotHeight - (((candle.open - minPrice) / range) * pricePlotHeight).toFloat()
                                    val closeY = pricePlotHeight - (((candle.close - minPrice) / range) * pricePlotHeight).toFloat()

                                    // High-Low Wick Line
                                    drawLine(
                                        color = candleColor,
                                        start = Offset(centerX, highY),
                                        end = Offset(centerX, lowY),
                                        strokeWidth = 1.5.dp.toPx()
                                    )

                                    // Open-Close Body Rect
                                    val topY = minOf(openY, closeY)
                                    val bodyHeight = (maxOf(openY, closeY) - topY).coerceAtLeast(2f)
                                    drawRect(
                                        color = candleColor,
                                        topLeft = Offset(candleLeft, topY),
                                        size = Size(candleWidth, bodyHeight)
                                    )

                                    // Volume Bar
                                    val volRatio = (candle.volume / maxVolume).toFloat().coerceIn(0.05f, 1f)
                                    val volBarH = volRatio * volumeHeight
                                    val volTopY = height - volBarH
                                    drawRect(
                                        color = candleColor.copy(alpha = 0.28f),
                                        topLeft = Offset(candleLeft, volTopY),
                                        size = Size(candleWidth, volBarH)
                                    )

                                    // MA7 Calculation
                                    if (i >= 6) {
                                        val ma7 = effectiveCandles.subList(i - 6, i + 1).map { it.close }.average()
                                        val maY = pricePlotHeight - (((ma7 - minPrice) / range) * pricePlotHeight).toFloat()
                                        if (!hasMaStart) {
                                            ma7Path.moveTo(centerX, maY)
                                            hasMaStart = true
                                        } else {
                                            ma7Path.lineTo(centerX, maY)
                                        }
                                    }
                                }

                                if (hasMaStart) {
                                    drawPath(
                                        path = ma7Path,
                                        color = Color(0xFFFFB74D), // WarningAmber MA7
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }
                            }

                            ChartType.LINE -> {
                                val prices = effectiveCandles.map { it.close }
                                val points = prices.mapIndexed { index, price ->
                                    val x = index * (width / (prices.size - 1).coerceAtLeast(1))
                                    val normalizedY = ((price - minPrice) / range).toFloat()
                                    val y = pricePlotHeight - (normalizedY * pricePlotHeight)
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
                                        colors = listOf(chartColor.copy(alpha = 0.25f), Color.Transparent),
                                        startY = 0f,
                                        endY = height
                                    )
                                )

                                drawPath(
                                    path = linePath,
                                    color = chartColor,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )

                                val lastPoint = points.last()
                                drawCircle(color = chartColor, radius = 5.dp.toPx(), center = lastPoint)
                                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = lastPoint)
                            }

                            ChartType.AREA -> {
                                val prices = effectiveCandles.map { it.close }
                                val points = prices.mapIndexed { index, price ->
                                    val x = index * (width / (prices.size - 1).coerceAtLeast(1))
                                    val normalizedY = ((price - minPrice) / range).toFloat()
                                    val y = pricePlotHeight - (normalizedY * pricePlotHeight)
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
                                        colors = listOf(CoinsEmerald.copy(alpha = 0.35f), Color.Transparent),
                                        startY = 0f,
                                        endY = height
                                    )
                                )

                                drawPath(
                                    path = linePath,
                                    color = CoinsEmerald,
                                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                )

                                // MA7 Overlay
                                val ma7Path = Path()
                                var hasMa7 = false
                                prices.forEachIndexed { i, _ ->
                                    if (i >= 6) {
                                        val ma7 = prices.subList(i - 6, i + 1).average()
                                        val x = i * (width / (prices.size - 1).coerceAtLeast(1))
                                        val y = pricePlotHeight - (((ma7 - minPrice) / range) * pricePlotHeight).toFloat()
                                        if (!hasMa7) { ma7Path.moveTo(x, y); hasMa7 = true } else { ma7Path.lineTo(x, y) }
                                    }
                                }
                                if (hasMa7) {
                                    drawPath(path = ma7Path, color = Color(0xFFFFB74D), style = Stroke(width = 1.5.dp.toPx()))
                                }

                                // MA20 Overlay
                                val ma20Path = Path()
                                var hasMa20 = false
                                prices.forEachIndexed { i, _ ->
                                    if (i >= 19) {
                                        val ma20 = prices.subList(i - 19, i + 1).average()
                                        val x = i * (width / (prices.size - 1).coerceAtLeast(1))
                                        val y = pricePlotHeight - (((ma20 - minPrice) / range) * pricePlotHeight).toFloat()
                                        if (!hasMa20) { ma20Path.moveTo(x, y); hasMa20 = true } else { ma20Path.lineTo(x, y) }
                                    }
                                }
                                if (hasMa20) {
                                    drawPath(path = ma20Path, color = Color(0xFF4FC3F7), style = Stroke(width = 1.5.dp.toPx()))
                                }
                            }
                        }
                    }

                    // High and Low labels at bottom of chart card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.US, "Low: %,.2f", minPrice),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Text(
                            text = String.format(Locale.US, "High: %,.2f", maxPrice),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
