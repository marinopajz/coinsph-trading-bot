package com.example.domain.risk

import com.example.data.model.BotSettings
import com.example.data.model.MarketTicker
import com.example.data.model.OpenPosition
import com.example.data.model.TradeLog
import com.example.domain.strategy.SignalAction
import com.example.domain.strategy.TradingSignal

sealed class RiskCheckResult {
    object Approved : RiskCheckResult()
    data class Rejected(val reason: String) : RiskCheckResult()
}

class RiskManager {

    fun validateOrder(
        signal: TradingSignal,
        settings: BotSettings,
        ticker: MarketTicker,
        openPositions: List<OpenPosition>,
        allocatedCapital: Double,
        dailyRealizedLoss: Double,
        recentTradeLogs: List<TradeLog>
    ): RiskCheckResult {
        if (signal.action == SignalAction.HOLD) {
            return RiskCheckResult.Approved
        }

        // 1. Stale-Price Rejection: Tickers older than 60 seconds are rejected
        val now = System.currentTimeMillis()
        if (now - ticker.lastUpdated > 60_000) {
            return RiskCheckResult.Rejected(
                "Stale price rejected: Market data is ${(now - ticker.lastUpdated) / 1000}s old (Max allowed: 60s)"
            )
        }

        // 2. Daily Loss Limit Check
        if (dailyRealizedLoss >= settings.maxDailyLoss) {
            return RiskCheckResult.Rejected(
                String.format(
                    java.util.Locale.US,
                    "Daily loss limit reached: Accumulated loss is %.2f %s (Limit: %.2f)",
                    dailyRealizedLoss,
                    settings.quoteCurrency,
                    settings.maxDailyLoss
                )
            )
        }

        if (signal.action == SignalAction.BUY) {
            val orderAmount = signal.suggestedAmount ?: settings.orderAmount

            // 3. Max Order Value
            if (orderAmount > settings.maxOrderValue) {
                return RiskCheckResult.Rejected(
                    "Order amount ($orderAmount) exceeds max allowable order value (${settings.maxOrderValue} ${settings.quoteCurrency})"
                )
            }

            // 4. Max Capital Allocation
            if ((allocatedCapital + orderAmount) > settings.maxCapital) {
                return RiskCheckResult.Rejected(
                    "Max total capital reached: New allocation would be ${allocatedCapital + orderAmount} (Cap: ${settings.maxCapital} ${settings.quoteCurrency})"
                )
            }

            // 5. Max Open Positions (Limit exposure to 10 concurrent tranches)
            if (openPositions.size >= 10) {
                return RiskCheckResult.Rejected(
                    "Maximum open position count reached (10 open tranches)"
                )
            }

            // 6. Duplicate-Order Prevention: Prevent orders within 15 seconds of the last order
            val lastOrder = recentTradeLogs
                .filter {
                    (it.side.equals("BUY", ignoreCase = true) || it.side.equals("SELL", ignoreCase = true)) &&
                    it.status.equals("FILLED", ignoreCase = true)
                }
                .maxByOrNull { it.timestamp }
            if (lastOrder != null && (now - lastOrder.timestamp) < 15_000) {
                return RiskCheckResult.Rejected(
                    "Duplicate order cooldown active: Last order was executed ${(now - lastOrder.timestamp) / 1000}s ago"
                )
            }
        }

        return RiskCheckResult.Approved
    }
}
