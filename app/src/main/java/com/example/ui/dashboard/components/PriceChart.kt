package com.example.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatChartPrice(price: Double): String {
    if (price <= 0.0) return "0.00"
    return when {
        price >= 1.0 -> String.format(Locale.US, "%,.2f", price)
        price >= 0.01 -> String.format(Locale.US, "%.4f", price)
        else -> String.format(Locale.US, "%.6f", price)
    }
}

private fun formatCompactNumber(value: Double): String {
    val absVal = kotlin.math.abs(value)
    return when {
        absVal >= 1_000_000_000.0 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000.0)
        absVal >= 1_000_000.0 -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
        absVal >= 1_000.0 -> String.format(Locale.US, "%.2fK", value / 1_000.0)
        else -> String.format(Locale.US, "%.2f", value)
    }
}

@Composable
fun PriceChart(
    priceHistory: List<PricePoint>,
    candles: List<CandleStick> = emptyList(),
    currentPrice: Double,
    quoteCurrency: String,
    change24h: Double,
    symbol: String = "BTCPHP",
    baseCurrency: String = "BTC",
    high24h: Double = currentPrice,
    low24h: Double = currentPrice,
    volume24h: Double = 0.0,
    exchangeName: String = "Coins.ph",
    chartType: ChartType = ChartType.CANDLE,
    chartInterval: ChartInterval = ChartInterval.FIVE_MIN,
    averageEntryPrice: Double? = null,
    takeProfitPrice: Double? = null,
    stopLossPrice: Double? = null,
    onChartTypeSelected: (ChartType) -> Unit = {},
    onChartIntervalSelected: (ChartInterval) -> Unit = {},
    onPairHeaderClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isPositive = change24h >= 0
    val chartColor = if (isPositive) CoinsEmerald else LossRed

    var visibleCandleCount by remember { mutableStateOf(30) }
    var scrollOffset by remember { mutableStateOf(0) }
    var selectedCandle by remember(candles, priceHistory) { mutableStateOf<CandleStick?>(null) }

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

    val clampedVisibleCount = visibleCandleCount.coerceIn(10, effectiveCandles.size.coerceAtLeast(10))
    val maxScrollOffset = (effectiveCandles.size - clampedVisibleCount).coerceAtLeast(0)
    val clampedScrollOffset = scrollOffset.coerceIn(0, maxScrollOffset)

    val visibleCandlesSlice = remember(effectiveCandles, clampedVisibleCount, clampedScrollOffset) {
        if (effectiveCandles.isEmpty()) emptyList()
        else {
            val startIndex = (effectiveCandles.size - clampedVisibleCount - clampedScrollOffset).coerceAtLeast(0)
            val endIndex = (startIndex + clampedVisibleCount).coerceAtMost(effectiveCandles.size)
            effectiveCandles.subList(startIndex, endIndex)
        }
    }

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
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            // 2-Column Coins.ph Spot Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPairHeaderClicked() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left Column: Pair, Price, 24h Change
                Column {
                    Text(
                        text = "$baseCurrency/$quoteCurrency ▼",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatChartPrice(currentPrice),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 24.sp
                        ),
                        color = TextPrimary
                    )
                    val absChange = currentPrice * (change24h / 100.0)
                    Text(
                        text = String.format(Locale.US, "%s (%+.2f%%)", formatChartPrice(absChange), change24h),
                        color = chartColor,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }

                // Right Column: 24h Stats Mini-Table
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("24h High", fontSize = 10.sp, color = TextMuted)
                        Text(formatChartPrice(high24h), fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("24h Low", fontSize = 10.sp, color = TextMuted)
                        Text(formatChartPrice(low24h), fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("24h Vol. ($baseCurrency)", fontSize = 10.sp, color = TextMuted)
                        Text(formatCompactNumber(volume24h), fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("24h Total ($quoteCurrency)", fontSize = 10.sp, color = TextMuted)
                        Text(formatCompactNumber(volume24h * currentPrice), fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Selector Row: Chart Type, Timeframe, Zoom Controls (Horizontally Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                // Zoom Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1B2230), RoundedCornerShape(6.dp))
                            .clickable { visibleCandleCount = (visibleCandleCount - 8).coerceAtLeast(10) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("btn_zoom_in")
                    ) {
                        Text("+", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1B2230), RoundedCornerShape(6.dp))
                            .clickable { visibleCandleCount = (visibleCandleCount + 8).coerceAtMost(effectiveCandles.size.coerceAtLeast(60)) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("btn_zoom_out")
                    ) {
                        Text("-", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1B2230), RoundedCornerShape(6.dp))
                            .clickable {
                                visibleCandleCount = 30
                                scrollOffset = 0
                                selectedCandle = null
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("btn_zoom_reset")
                    ) {
                        Text("Reset", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    }
                }
            }

            // Position Overlay Target Banner (3 Compact Columns)
            if (averageEntryPrice != null && averageEntryPrice > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Entry Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1B2230), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text("ENTRY", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                            Text(
                                text = formatChartPrice(averageEntryPrice),
                                color = Color(0xFF29B6F6),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // TP Target Box
                    if (takeProfitPrice != null && takeProfitPrice > 0.0) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1B2230), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text("TAKE PROFIT", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                                Text(
                                    text = formatChartPrice(takeProfitPrice),
                                    color = CoinsEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Stop Loss Box
                    if (stopLossPrice != null && stopLossPrice > 0.0) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1B2230), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text("STOP LOSS", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                                Text(
                                    text = formatChartPrice(stopLossPrice),
                                    color = LossRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Canvas Chart Container (height: 280.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {

                if (visibleCandlesSlice.isEmpty() && priceHistory.size < 2) {
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
                    val rawMin = remember(visibleCandlesSlice, currentPrice) {
                        val lows = visibleCandlesSlice.map { it.low }
                        if (lows.isNotEmpty()) lows.minOf { it }.coerceAtMost(currentPrice) else currentPrice
                    }
                    val rawMax = remember(visibleCandlesSlice, currentPrice) {
                        val highs = visibleCandlesSlice.map { it.high }
                        if (highs.isNotEmpty()) highs.maxOf { it }.coerceAtLeast(currentPrice) else currentPrice
                    }
                    val rawRange = remember(rawMin, rawMax) { (rawMax - rawMin).coerceAtLeast(rawMax * 0.002) }

                    val displayMaxPrice = remember(rawMax, rawRange) { rawMax + (rawRange * 0.15) }
                    val displayMinPrice = remember(rawMin, rawRange) { (rawMin - (rawRange * 0.12)).coerceAtLeast(0.0) }
                    val displayRange = remember(displayMinPrice, displayMaxPrice) { (displayMaxPrice - displayMinPrice).coerceAtLeast(1.0) }
                    val maxVolume = remember(visibleCandlesSlice) { (visibleCandlesSlice.maxOfOrNull { it.volume } ?: 1.0).coerceAtLeast(1.0) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(effectiveCandles, clampedVisibleCount, maxScrollOffset) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    val stepX = size.width / clampedVisibleCount.coerceAtLeast(1)
                                    val candleDelta = (dragAmount / stepX).toInt()
                                    if (candleDelta != 0) {
                                        scrollOffset = (scrollOffset + candleDelta).coerceIn(0, maxScrollOffset)
                                    }
                                }
                            }
                            .pointerInput(visibleCandlesSlice) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        if (visibleCandlesSlice.isNotEmpty()) {
                                            val count = visibleCandlesSlice.size
                                            val rightAxisWidthPx = 78.dp.toPx()
                                            val pWidth = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
                                            val stepX = pWidth / count.coerceAtLeast(1)
                                            val tappedIndex = (tapOffset.x / stepX).toInt().coerceIn(0, count - 1)
                                            val tapped = visibleCandlesSlice[tappedIndex]
                                            selectedCandle = if (selectedCandle == tapped) null else tapped
                                        }
                                    },
                                    onLongPress = { tapOffset ->
                                        if (visibleCandlesSlice.isNotEmpty()) {
                                            val count = visibleCandlesSlice.size
                                            val rightAxisWidthPx = 78.dp.toPx()
                                            val pWidth = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
                                            val stepX = pWidth / count.coerceAtLeast(1)
                                            val tappedIndex = (tapOffset.x / stepX).toInt().coerceIn(0, count - 1)
                                            selectedCandle = visibleCandlesSlice[tappedIndex]
                                        }
                                    }
                                )
                            }
                            .testTag("chart_canvas")
                    ) {
                        val width = size.width
                        val height = size.height

                        val rightAxisWidthPx = 78.dp.toPx()
                        val plotWidth = (width - rightAxisWidthPx).coerceAtLeast(1f)

                        val pricePlotHeight = height * 0.68f
                        val volumeTop = height * 0.72f
                        val volumeHeight = height * 0.18f

                        val topPadding = 42.dp.toPx()
                        val usableHeight = (pricePlotHeight - topPadding).coerceAtLeast(1f)

                        fun mapPriceToY(price: Double): Float {
                            val norm = ((price - displayMinPrice) / displayRange).toFloat().coerceIn(0f, 1f)
                            return topPadding + (usableHeight - (norm * usableHeight))
                        }

                        val textPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = 0xFF8E9BAE.toInt()
                            textSize = 9.sp.toPx()
                            textAlign = android.graphics.Paint.Align.RIGHT
                            typeface = android.graphics.Typeface.MONOSPACE
                        }

                        // 1. Main Price Pane: Draw 5 Grid Lines & Right Price Scale Labels
                        for (i in 0..4) {
                            val y = topPadding + (usableHeight * (i / 4f))
                            val gridPrice = displayMaxPrice - (i / 4f) * displayRange
                            drawLine(
                                color = Color.White.copy(alpha = 0.06f),
                                start = Offset(0f, y),
                                end = Offset(plotWidth, y),
                                strokeWidth = 1f,
                                pathEffect = if (chartType == ChartType.AREA) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                formatChartPrice(gridPrice),
                                width - 2.dp.toPx(),
                                y + 3.dp.toPx(),
                                textPaint
                            )
                        }

                        // Overlay Lines: Entry, TP, SL
                        if (averageEntryPrice != null && averageEntryPrice > 0.0) {
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                            val tagPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                textSize = 8.sp.toPx()
                                textAlign = android.graphics.Paint.Align.LEFT
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                            }

                            if (averageEntryPrice in displayMinPrice..displayMaxPrice) {
                                val entryY = mapPriceToY(averageEntryPrice)
                                drawLine(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.75f),
                                    start = Offset(0f, entryY),
                                    end = Offset(plotWidth, entryY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                tagPaint.color = 0xFF00E5FF.toInt()
                                drawContext.canvas.nativeCanvas.drawText("ENTRY", 4.dp.toPx(), entryY - 2.dp.toPx(), tagPaint)
                            }

                            if (takeProfitPrice != null && takeProfitPrice in displayMinPrice..displayMaxPrice) {
                                val tpY = mapPriceToY(takeProfitPrice)
                                drawLine(
                                    color = CoinsEmerald.copy(alpha = 0.75f),
                                    start = Offset(0f, tpY),
                                    end = Offset(plotWidth, tpY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                tagPaint.color = 0xFF00E676.toInt()
                                drawContext.canvas.nativeCanvas.drawText("TP", 4.dp.toPx(), tpY - 2.dp.toPx(), tagPaint)
                            }

                            if (stopLossPrice != null && stopLossPrice in displayMinPrice..displayMaxPrice) {
                                val slY = mapPriceToY(stopLossPrice)
                                drawLine(
                                    color = LossRed.copy(alpha = 0.75f),
                                    start = Offset(0f, slY),
                                    end = Offset(plotWidth, slY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                tagPaint.color = 0xFFFF5252.toInt()
                                drawContext.canvas.nativeCanvas.drawText("SL", 4.dp.toPx(), slY - 2.dp.toPx(), tagPaint)
                            }
                        }

                        // Render Price Chart Types restricted to plotWidth
                        when (chartType) {
                            ChartType.CANDLE -> {
                                val count = visibleCandlesSlice.size
                                val stepX = plotWidth / count.coerceAtLeast(1)
                                val candleWidth = (stepX * 0.65f).coerceAtLeast(2f)

                                val ma7Path = Path()
                                var hasMaStart = false

                                visibleCandlesSlice.forEachIndexed { i, candle ->
                                    val centerX = i * stepX + stepX / 2f
                                    val candleLeft = centerX - candleWidth / 2f

                                    val isBullish = candle.close >= candle.open
                                    val candleColor = if (isBullish) CoinsEmerald else LossRed

                                    val highY = mapPriceToY(candle.high)
                                    val lowY = mapPriceToY(candle.low)
                                    val openY = mapPriceToY(candle.open)
                                    val closeY = mapPriceToY(candle.close)

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

                                    // MA7 Calculation
                                    if (i >= 6) {
                                        val ma7 = visibleCandlesSlice.subList(i - 6, i + 1).map { it.close }.average()
                                        val maY = mapPriceToY(ma7)
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
                                val prices = visibleCandlesSlice.map { it.close }
                                val points = prices.mapIndexed { index, price ->
                                    val x = index * (plotWidth / (prices.size - 1).coerceAtLeast(1))
                                    val y = mapPriceToY(price)
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
                                    lineTo(points.last().x, pricePlotHeight)
                                    lineTo(points.first().x, pricePlotHeight)
                                    close()
                                }

                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(chartColor.copy(alpha = 0.25f), Color.Transparent),
                                        startY = topPadding,
                                        endY = pricePlotHeight
                                    )
                                )

                                drawPath(
                                    path = linePath,
                                    color = chartColor,
                                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                )

                                val lastPoint = points.last()
                                drawCircle(color = chartColor, radius = 4.dp.toPx(), center = lastPoint)
                                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = lastPoint)
                            }

                            ChartType.AREA -> {
                                val prices = visibleCandlesSlice.map { it.close }
                                val points = prices.mapIndexed { index, price ->
                                    val x = index * (plotWidth / (prices.size - 1).coerceAtLeast(1))
                                    val y = mapPriceToY(price)
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
                                    lineTo(points.last().x, pricePlotHeight)
                                    lineTo(points.first().x, pricePlotHeight)
                                    close()
                                }

                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(CoinsEmerald.copy(alpha = 0.35f), Color.Transparent),
                                        startY = topPadding,
                                        endY = pricePlotHeight
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
                                        val x = i * (plotWidth / (prices.size - 1).coerceAtLeast(1))
                                        val y = mapPriceToY(ma7)
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
                                        val x = i * (plotWidth / (prices.size - 1).coerceAtLeast(1))
                                        val y = mapPriceToY(ma20)
                                        if (!hasMa20) { ma20Path.moveTo(x, y); hasMa20 = true } else { ma20Path.lineTo(x, y) }
                                    }
                                }
                                if (hasMa20) {
                                    drawPath(path = ma20Path, color = Color(0xFF4FC3F7), style = Stroke(width = 1.5.dp.toPx()))
                                }
                            }
                        }

                        // Live Price Dotted Line & Right Price Badge
                        val currentPriceY = mapPriceToY(currentPrice)
                        drawLine(
                            color = chartColor,
                            start = Offset(0f, currentPriceY),
                            end = Offset(plotWidth, currentPriceY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )

                        val badgeH = 16.dp.toPx()
                        val badgeTop = (currentPriceY - badgeH / 2f).coerceIn(0f, pricePlotHeight - badgeH)
                        val badgeLeft = plotWidth + 2.dp.toPx()
                        val badgeWidth = width - badgeLeft - 2.dp.toPx()

                        drawRoundRect(
                            color = chartColor,
                            topLeft = Offset(badgeLeft, badgeTop),
                            size = Size(badgeWidth, badgeH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        val badgePaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.WHITE
                            textSize = 9.sp.toPx()
                            textAlign = android.graphics.Paint.Align.RIGHT
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            formatChartPrice(currentPrice),
                            width - 4.dp.toPx(),
                            badgeTop + badgeH - 4.dp.toPx(),
                            badgePaint
                        )

                        // 2. Dedicated Volume Sub-Panel (height * 0.72f to height * 0.90f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(0f, volumeTop - 2.dp.toPx()),
                            end = Offset(plotWidth, volumeTop - 2.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            "Vol SMA7",
                            width - 2.dp.toPx(),
                            volumeTop + 10.dp.toPx(),
                            textPaint
                        )

                        val count = visibleCandlesSlice.size
                        val stepX = plotWidth / count.coerceAtLeast(1)
                        val candleWidth = (stepX * 0.65f).coerceAtLeast(2f)
                        val volSmaPath = Path()
                        var hasVolSma = false

                        visibleCandlesSlice.forEachIndexed { i, candle ->
                            val centerX = i * stepX + stepX / 2f
                            val candleLeft = centerX - candleWidth / 2f
                            val candleColor = if (candle.close >= candle.open) CoinsEmerald else LossRed

                            val volRatio = (candle.volume / maxVolume).toFloat().coerceIn(0.05f, 1f)
                            val volBarH = volRatio * volumeHeight
                            val volTopY = (height * 0.90f) - volBarH

                            drawRect(
                                color = candleColor.copy(alpha = 0.28f),
                                topLeft = Offset(candleLeft, volTopY),
                                size = Size(candleWidth, volBarH)
                            )

                            // Volume SMA7
                            if (i >= 6) {
                                val volSma = visibleCandlesSlice.subList(i - 6, i + 1).map { it.volume }.average()
                                val vY = (height * 0.90f) - ((volSma / maxVolume).toFloat().coerceIn(0.05f, 1f) * volumeHeight)
                                if (!hasVolSma) {
                                    volSmaPath.moveTo(centerX, vY)
                                    hasVolSma = true
                                } else {
                                    volSmaPath.lineTo(centerX, vY)
                                }
                            }
                        }

                        if (hasVolSma) {
                            drawPath(
                                path = volSmaPath,
                                color = Color(0xFF4FC3F7),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }

                        // Right Volume Axis Label
                        drawContext.canvas.nativeCanvas.drawText(
                            formatCompactNumber(maxVolume),
                            width - 2.dp.toPx(),
                            volumeTop + 22.dp.toPx(),
                            textPaint
                        )

                        // 3. Bottom X-Axis Time Ruler
                        val numLabels = 4
                        val xLabelPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = 0xFF8E9BAE.toInt()
                            textSize = 9.sp.toPx()
                            textAlign = android.graphics.Paint.Align.LEFT
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                        for (lblIdx in 0 until numLabels) {
                            val cIdx = ((visibleCandlesSlice.size - 1) * (lblIdx / (numLabels - 1).toFloat())).toInt().coerceIn(0, visibleCandlesSlice.size - 1)
                            if (cIdx in visibleCandlesSlice.indices) {
                                val cTime = visibleCandlesSlice[cIdx].openTime
                                val labelText = SimpleDateFormat("HH:mm", Locale.US).format(Date(cTime))
                                val labelX = cIdx * stepX
                                drawContext.canvas.nativeCanvas.drawText(
                                    labelText,
                                    labelX.coerceIn(0f, plotWidth - 28.dp.toPx()),
                                    height - 2.dp.toPx(),
                                    xLabelPaint
                                )
                            }
                        }

                        // Inspector Crosshair
                        if (selectedCandle != null) {
                            val selectedIndexInSlice = visibleCandlesSlice.indexOf(selectedCandle)
                            if (selectedIndexInSlice != -1) {
                                val selX = selectedIndexInSlice * stepX + stepX / 2f
                                val selY = mapPriceToY(selectedCandle!!.close)

                                val crosshairDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

                                // Vertical Crosshair Line
                                drawLine(
                                    color = Color.White.copy(alpha = 0.45f),
                                    start = Offset(selX, 0f),
                                    end = Offset(selX, height),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = crosshairDash
                                )

                                // Horizontal Crosshair Line
                                drawLine(
                                    color = Color.White.copy(alpha = 0.45f),
                                    start = Offset(0f, selY),
                                    end = Offset(plotWidth, selY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = crosshairDash
                                )

                                // Highlight Circle
                                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(selX, selY))
                                drawCircle(color = CoinsEmerald, radius = 2.dp.toPx(), center = Offset(selX, selY))
                            }
                        }
                    }
                }

                // Top-Left In-Canvas Watermark & Active Candle Overlay (Rendered ON TOP of Canvas)
                val activeCandle = selectedCandle ?: visibleCandlesSlice.lastOrNull()
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 2.dp)
                        .background(CryptoSurface.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$baseCurrency/$quoteCurrency · ${chartInterval.label} · $exchangeName",
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    if (activeCandle != null) {
                        val cDiff = activeCandle.close - activeCandle.open
                        val cPct = if (activeCandle.open > 0) (cDiff / activeCandle.open) * 100.0 else 0.0
                        Text(
                            text = String.format(Locale.US, "C: %s  %s (%+.2f%%)", formatChartPrice(activeCandle.close), formatChartPrice(cDiff), cPct),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (cPct >= 0) CoinsEmerald else LossRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Floating CoinMarketCap Style OHLCV Crosshair Inspector Tooltip
                if (selectedCandle != null) {
                    val selIdx = visibleCandlesSlice.indexOf(selectedCandle)
                    val isOnRightHalf = selIdx >= (visibleCandlesSlice.size / 2)
                    val alignModifier = if (isOnRightHalf) {
                        Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 28.dp)
                    } else {
                        Modifier.align(Alignment.TopEnd).padding(end = 82.dp, top = 28.dp)
                    }

                    Box(
                        modifier = alignModifier
                            .background(Color(0xEE141923), RoundedCornerShape(10.dp))
                            .border(1.dp, CoinsEmerald.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .clickable { selectedCandle = null }
                            .padding(10.dp)
                            .testTag("chart_inspector_tooltip")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(0.52f),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(selectedCandle!!.openTime)),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "✕",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    modifier = Modifier.clickable { selectedCandle = null }
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(modifier = Modifier.fillMaxWidth(0.52f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Open", fontSize = 10.sp, color = TextMuted)
                                Text(formatChartPrice(selectedCandle!!.open), fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(0.52f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("High", fontSize = 10.sp, color = TextMuted)
                                Text(formatChartPrice(selectedCandle!!.high), fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(0.52f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Low", fontSize = 10.sp, color = TextMuted)
                                Text(formatChartPrice(selectedCandle!!.low), fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(0.52f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Close", fontSize = 10.sp, color = TextMuted)
                                Text(formatChartPrice(selectedCandle!!.close), fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            val cDiff = selectedCandle!!.close - selectedCandle!!.open
                            val cPct = if (selectedCandle!!.open > 0) (cDiff / selectedCandle!!.open) * 100.0 else 0.0
                            val cColor = if (cPct >= 0) CoinsEmerald else LossRed
                            Row(modifier = Modifier.fillMaxWidth(0.52f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Change", fontSize = 10.sp, color = TextMuted)
                                Text(
                                    text = String.format(Locale.US, "%s (%+.2f%%)", formatChartPrice(cDiff), cPct),
                                    fontSize = 10.sp,
                                    color = cColor,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(0.52f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Volume", fontSize = 10.sp, color = TextMuted)
                                Text(formatCompactNumber(selectedCandle!!.volume), fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // Jump to Latest Button
                if (clampedScrollOffset > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 82.dp)
                            .background(CoinsEmerald, RoundedCornerShape(12.dp))
                            .clickable {
                                scrollOffset = 0
                                selectedCandle = null
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_jump_latest")
                    ) {
                        Text(
                            text = "⏩ Latest",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
