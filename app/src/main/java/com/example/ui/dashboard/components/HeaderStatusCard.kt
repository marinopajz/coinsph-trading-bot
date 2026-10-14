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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BotRuntimeStatus
import com.example.data.model.CoinsSymbolInfo
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.LossRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HeaderStatusCard(
    status: BotRuntimeStatus,
    activeSymbol: String,
    availableSymbols: List<CoinsSymbolInfo>,
    onSymbolSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .testTag("header_status_card")
    ) {
        // Exchange Connection Status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (status.exchangeConnected) CoinsEmerald else LossRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (status.exchangeConnected) "Coins.ph Spot API Connected" else "Coins.ph API Disconnected",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // Symbol Selection Row
        Text(
            text = "MARKET PAIR",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            letterSpacing = 0.8.sp,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableSymbols) { sym ->
                val isSelected = sym.symbol.equals(activeSymbol, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSymbolSelected(sym.symbol) },
                    label = {
                        Text(
                            text = sym.symbol,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CoinsEmerald,
                        selectedLabelColor = Color(0xFF002919),
                        containerColor = Color(0xFF1B2230),
                        labelColor = TextPrimary
                    ),
                    modifier = Modifier.testTag("symbol_chip_${sym.symbol}")
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
}
