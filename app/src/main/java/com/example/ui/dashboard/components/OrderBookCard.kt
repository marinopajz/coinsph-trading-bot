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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OrderBookSnapshot
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoSurface
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.util.Locale

@Composable
fun OrderBookCard(orderBook: OrderBookSnapshot, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CryptoSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE ORDER BOOK & DEPTH",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier.background(Color(0xFF1B2230), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "Spread: %.2f%%", orderBook.spreadPercent),
                        color = if (orderBook.spreadPercent <= 0.60) CoinsEmerald else LossRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Buy vs Sell Pressure Bar
            val buyPct = orderBook.buyPressurePercent.toFloat()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(LossRed, RoundedCornerShape(4.dp)) // Default background is Asks
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(buyPct / 100f)
                        .height(8.dp)
                        .background(CoinsEmerald, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("BIDS (Buy Wall) ${String.format(Locale.US, "%.1f%%", buyPct)}", fontSize = 9.sp, color = CoinsEmerald)
                Text("ASKS (Sell Wall) ${String.format(Locale.US, "%.1f%%", 100f - buyPct)}", fontSize = 9.sp, color = LossRed)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Order Book Table
            Row(modifier = Modifier.fillMaxWidth()) {
                // Bids (Left)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Qty", fontSize = 10.sp, color = TextMuted)
                        Text("Bid Price", fontSize = 10.sp, color = TextMuted)
                    }
                    val maxBidQty = orderBook.bids.maxOfOrNull { it.quantity } ?: 1.0
                    orderBook.bids.take(5).forEach { bid ->
                        val depthPct = (bid.quantity / maxBidQty).toFloat().coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(depthPct)
                                    .height(18.dp)
                                    .align(Alignment.CenterEnd)
                                    .background(CoinsEmerald.copy(alpha = 0.1f))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().height(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(String.format(Locale.US, "%.4f", bid.quantity), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                Text(String.format(Locale.US, "%,.2f", bid.price), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CoinsEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Divider
                Box(modifier = Modifier.width(1.dp).height(100.dp).background(Color(0xFF263246)))

                // Asks (Right)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ask Price", fontSize = 10.sp, color = TextMuted)
                        Text("Qty", fontSize = 10.sp, color = TextMuted)
                    }
                    val maxAskQty = orderBook.asks.maxOfOrNull { it.quantity } ?: 1.0
                    orderBook.asks.take(5).forEach { ask ->
                        val depthPct = (ask.quantity / maxAskQty).toFloat().coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(depthPct)
                                    .height(18.dp)
                                    .align(Alignment.CenterStart)
                                    .background(LossRed.copy(alpha = 0.1f))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().height(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(String.format(Locale.US, "%,.2f", ask.price), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LossRed, fontWeight = FontWeight.Bold)
                                Text(String.format(Locale.US, "%.4f", ask.quantity), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}