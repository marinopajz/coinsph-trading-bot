package com.example.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import java.util.Locale

import com.example.data.model.BotRuntimeStatus
import com.example.data.model.CoinsSymbolInfo
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private fun formatWatchlistPrice(price: Double): String {
    if (price <= 0.0) return "--"
    return when {
        price >= 1.0 -> String.format(Locale.US, "%,.2f", price)
        price >= 0.01 -> String.format(Locale.US, "%.4f", price)
        else -> String.format(Locale.US, "%.6f", price)
    }
}

@Composable
fun HeaderStatusCard(
    status: BotRuntimeStatus,
    activeSymbol: String,
    exchangePlatform: com.example.data.model.ExchangePlatform,
    availableSymbols: List<CoinsSymbolInfo>,
    onSymbolSelected: (String) -> Unit,
    onExchangePlatformSelected: (com.example.data.model.ExchangePlatform) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSymbolDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .testTag("header_status_card")
    ) {
        // Exchange Switcher & Connection Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (status.exchangeConnected) CoinsEmerald else LossRed)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (status.exchangeConnected) "${exchangePlatform.displayName} Spot API Connected" else "${exchangePlatform.displayName} API Disconnected",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            // Platform Switcher
            Row(
                modifier = Modifier
                    .background(Color(0xFF1B2230), RoundedCornerShape(20.dp))
                    .padding(2.dp)
            ) {
                com.example.data.model.ExchangePlatform.values().forEach { platform ->
                    val selected = exchangePlatform == platform
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (selected) CoinsEmerald else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { onExchangePlatformSelected(platform) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("platform_switcher_${platform.name}")
                    ) {
                        Text(
                            text = platform.displayName.uppercase(),
                            color = if (selected) Color.Black else TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // Searchable Market Dropdown Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141D2A), RoundedCornerShape(8.dp))
                .clickable { showSymbolDialog = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🪙",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeSymbol,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "▼",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "Search / Switch Market",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Error notice banner if any
        AnimatedVisibility(visible = status.lastErrorMessage != null) {
            status.lastErrorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LossRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = LossRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = msg,
                        color = LossRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (showSymbolDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var filterCategory by remember { mutableStateOf("ALL") }
        var sortOption by remember { mutableStateOf("DEFAULT") }

        Dialog(
            onDismissRequest = { showSymbolDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B2230)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Select Market Pair",
                        color = CoinsEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.uppercase() },
                        placeholder = { Text("Search pair (e.g. BTC, XRP)...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CoinsEmerald,
                            unfocusedBorderColor = Color(0xFF263246),
                            focusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Category Chips & Sort Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category Chips
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("ALL", "PHP", "USDT", "FX").forEach { cat ->
                                val selected = filterCategory == cat
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selected) CoinsEmerald.copy(alpha = 0.2f) else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { filterCategory = cat }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 10.sp,
                                        color = if (selected) CoinsEmerald else TextMuted,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Sort Toggle Chips
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("DEFAULT" to "Default", "GAINERS" to "📈", "DIPS" to "📉").forEach { (code, lbl) ->
                                val selected = sortOption == code
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selected) CoinsEmerald.copy(alpha = 0.2f) else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { sortOption = code }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 10.sp,
                                        color = if (selected) CoinsEmerald else TextMuted,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val filteredSymbols = remember(availableSymbols, searchQuery, filterCategory, sortOption) {
                        val baseList = availableSymbols.filter {
                            val hasLetters = it.symbol.any { ch -> ch.isLetter() }
                            val matchesSearch = it.symbol.contains(searchQuery, ignoreCase = true)
                            val matchesCat = when (filterCategory) {
                                "PHP" -> it.symbol.endsWith("PHP")
                                "USDT" -> it.symbol.endsWith("USDT")
                                "FX" -> {
                                    val s = it.symbol.uppercase()
                                    s.startsWith("EUR") || s.startsWith("PAXG") || s.startsWith("GBP") ||
                                    s.startsWith("AUD") || s.startsWith("JPY") ||
                                    s == "USDTPHP" || s == "USDCPHP" || s == "USDT/PHP" || s == "USDC/PHP" ||
                                    s == "USDCUSDT" || s == "FDUSDUSDT"
                                }
                                else -> true
                            }
                            hasLetters && matchesSearch && matchesCat
                        }
                        when (sortOption) {
                            "GAINERS" -> baseList.sortedByDescending { it.priceChangePercent }
                            "DIPS" -> baseList.sortedBy { it.priceChangePercent }
                            else -> baseList
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredSymbols, key = { it.symbol }) { sym ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSymbolSelected(sym.symbol)
                                        showSymbolDialog = false
                                    }
                                    .padding(vertical = 10.dp)
                                    .testTag("symbol_chip_${sym.symbol}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = sym.symbol,
                                        color = if (sym.symbol.equals(activeSymbol, ignoreCase = true)) CoinsEmerald else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    val base = if (sym.symbol.endsWith("PHP")) sym.symbol.dropLast(3) else sym.symbol.dropLast(4)
                                    val quote = sym.symbol.drop(base.length)
                                    Text(
                                        text = "$base / $quote",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatWatchlistPrice(sym.lastPrice),
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSymbolDialog = false }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CANCEL", color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
