package com.example.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CoinsSymbolInfo
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.CryptoSurface
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.util.Locale

private fun formatWatchlistPrice(price: Double): String {
    if (price <= 0.0) return "--"
    return when {
        price >= 1.0 -> String.format(Locale.US, "%,.2f", price)
        price >= 0.01 -> String.format(Locale.US, "%.4f", price)
        else -> String.format(Locale.US, "%.6f", price)
    }
}

@Composable
fun MarketWatchlistCard(
    availableSymbols: List<CoinsSymbolInfo>,
    activeSymbol: String,
    onSymbolSelected: (String) -> Unit,
    onScanBestCoinClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("Featured") }
    var searchQuery by remember { mutableStateOf("") }
    var showAllCoins by remember { mutableStateOf(false) }

    val allMatchingSymbols = remember(availableSymbols, selectedFilter, searchQuery) {
        val list = availableSymbols.filter {
            val hasLetters = it.symbol.any { ch -> ch.isLetter() }
            val matchesSearch = searchQuery.isBlank() || it.symbol.contains(searchQuery, ignoreCase = true)
            hasLetters && matchesSearch
        }

        when (selectedFilter) {
            "Crypto" -> list.filterNot { sym ->
                val s = sym.symbol.uppercase()
                s.startsWith("EUR") || s.startsWith("PAXG") || s.startsWith("GBP") || s.startsWith("AUD")
            }
            "Gold & FX" -> list.filter { sym ->
                val s = sym.symbol.uppercase()
                s.startsWith("EUR") || s.startsWith("PAXG") || s.startsWith("GBP") ||
                s.startsWith("AUD") || s == "USDTPHP" || s == "USDCPHP" || s == "USDT/PHP" ||
                s == "USDC/PHP" || s == "USDCUSDT" || s == "FDUSDUSDT"
            }
            "Top Gainers" -> list.filter { it.lastPrice > 0.0 }.sortedByDescending { it.priceChangePercent }
            "Top Dips" -> list.filter { it.lastPrice > 0.0 }.sortedBy { it.priceChangePercent }
            else -> list // Featured
        }
    }

    val totalMatchingCount = allMatchingSymbols.size
    val displaySymbols = if (showAllCoins || searchQuery.isNotBlank()) {
        allMatchingSymbols
    } else {
        allMatchingSymbols.take(20)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("market_watchlist_card"),
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
                    text = "📊 LIVE MARKET WATCHLIST (${totalMatchingCount} ASSETS)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .background(CoinsEmerald.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .clickable { onScanBestCoinClicked() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("btn_scan_best_coin")
                ) {
                    Text(
                        text = "⚡ SCAN BEST COIN",
                        color = CoinsEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Filter Tabs (Horizontally Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Featured", "Crypto", "Gold & FX", "Top Gainers", "Top Dips").forEach { filter ->
                    val selected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (selected) CoinsEmerald.copy(alpha = 0.2f) else Color(0xFF1B2230),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("watchlist_tab_$filter")
                    ) {
                        Text(
                            text = filter,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) CoinsEmerald else TextMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.uppercase() },
                placeholder = { Text("Search coin in watchlist (e.g. DOGE, PEPE, SOL)...", color = TextMuted, fontSize = 11.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_watchlist_search"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CoinsEmerald,
                    unfocusedBorderColor = Color(0xFF263246),
                    focusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (displaySymbols.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching assets found...", fontSize = 11.sp, color = TextMuted)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    displaySymbols.forEach { sym ->
                        val isActive = sym.symbol.equals(activeSymbol, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isActive) CoinsEmerald.copy(alpha = 0.12f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSymbolSelected(sym.symbol) }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("watchlist_item_${sym.symbol}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = sym.symbol,
                                    color = if (isActive) CoinsEmerald else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                val base = if (sym.symbol.endsWith("PHP")) sym.symbol.dropLast(3) else sym.symbol.dropLast(4)
                                val quote = sym.symbol.drop(base.length)
                                Text(
                                    text = "$base / $quote",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatWatchlistPrice(sym.lastPrice),
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                val isGain = sym.priceChangePercent >= 0
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isGain) CoinsEmerald.copy(alpha = 0.15f) else LossRed.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%+.2f%%", sym.priceChangePercent),
                                        color = if (isGain) CoinsEmerald else LossRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Show All / Show Top 20 Toggle Button
                if (searchQuery.isBlank() && totalMatchingCount > 20) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1B2230), RoundedCornerShape(8.dp))
                            .clickable { showAllCoins = !showAllCoins }
                            .padding(vertical = 10.dp)
                            .testTag("btn_toggle_watchlist_expansion"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showAllCoins) "▲ Show Top 20 Coins" else "▼ Show All $totalMatchingCount Coins",
                            color = CoinsEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
