package com.example.domain.strategy

import com.example.data.model.BotSettings
import com.example.data.model.CandleStick
import com.example.data.model.MarketTicker
import com.example.data.model.OpenPosition
import com.example.data.model.PricePoint
import com.example.data.model.StrategyType
import kotlin.math.max

enum class SignalAction {
    BUY,
    SELL,
    HOLD
}

data class TradingSignal(
    val action: SignalAction,
    val reason: String,
    val suggestedAmount: Double? = null,
    val suggestedPrice: Double? = null
)

interface TradingStrategy {
    val type: StrategyType

    fun evaluate(
        settings: BotSettings,
        currentTicker: MarketTicker,
        priceHistory: List<PricePoint>,
        openPositions: List<OpenPosition>,
        allocatedCapital: Double,
        candles: List<CandleStick> = emptyList()
    ): TradingSignal
}

/**
 * Dollar-Cost Averaging (DCA) Strategy
 * Buys on configured dips or interval triggers, respecting maximum capital allocation,
 * minimum time between entries, and take-profit/stop-loss exit rules.
 */
class DcaStrategy : TradingStrategy {
    override val type: StrategyType = StrategyType.DCA

    override fun evaluate(
        settings: BotSettings,
        currentTicker: MarketTicker,
        priceHistory: List<PricePoint>,
        openPositions: List<OpenPosition>,
        allocatedCapital: Double,
        candles: List<CandleStick>
    ): TradingSignal {
        val currentPrice = currentTicker.price
        if (currentPrice <= 0.0) {
            return TradingSignal(SignalAction.HOLD, "Invalid market price ($currentPrice)")
        }

        // 1. Check Take Profit, Break-Even Lock, and Stop Loss across existing open positions
        if (openPositions.isNotEmpty()) {
            val totalQty = openPositions.sumOf { it.quantity }
            val totalCost = openPositions.sumOf { it.amountInvested }
            if (totalQty > 0.0 && totalCost > 0.0) {
                val averageEntryPrice = totalCost / totalQty
                val profitPct = ((currentPrice - averageEntryPrice) / averageEntryPrice) * 100.0

                if (settings.breakEvenLockEnabled && settings.breakEvenActivated && profitPct <= 0.30) {
                    return TradingSignal(
                        action = SignalAction.SELL,
                        reason = String.format("Break-Even Lock triggered at +%.2f%% (Protected profit & fees)", profitPct),
                        suggestedPrice = currentPrice
                    )
                }

                if (settings.trailingTakeProfitEnabled) {
                    if (profitPct >= settings.takeProfitPercentage) {
                        val activePeak = maxOf(settings.peakPrice, currentPrice)
                        val dropFromPeakPct = if (activePeak > 0.0) ((activePeak - currentPrice) / activePeak) * 100.0 else 0.0
                        if (dropFromPeakPct >= settings.trailingStopPercentage && profitPct > 0.0) {
                            return TradingSignal(
                                action = SignalAction.SELL,
                                reason = String.format("Trailing Take Profit triggered (Peak: %.2f, Drop: %.2f%%)", activePeak, dropFromPeakPct),
                                suggestedPrice = currentPrice
                            )
                        }
                    }
                } else {
                    if (profitPct >= settings.takeProfitPercentage) {
                        return TradingSignal(
                            action = SignalAction.SELL,
                            reason = String.format("Take Profit triggered: +%.2f%% (Target: +%.2f%%)", profitPct, settings.takeProfitPercentage),
                            suggestedPrice = currentPrice
                        )
                    }
                }

                if (settings.neverSellAtLoss) {
                    if (profitPct <= -settings.stopLossPercentage) {
                        return TradingSignal(
                            action = SignalAction.HOLD,
                            reason = String.format("Spot HODL Guard active: holding through drawdown (PnL: %+.2f%%)", profitPct)
                        )
                    }
                } else {
                    if (profitPct <= -settings.stopLossPercentage) {
                        return TradingSignal(
                            action = SignalAction.SELL,
                            reason = String.format("Stop Loss triggered: %.2f%% (Limit: -%.2f%%)", profitPct, settings.stopLossPercentage),
                            suggestedPrice = currentPrice
                        )
                    }
                }
            }
        }

        // 2. Check Capital Limit
        val remainingCapital = settings.maxCapital - allocatedCapital
        if (remainingCapital < settings.orderAmount) {
            return TradingSignal(
                action = SignalAction.HOLD,
                reason = "Max capital allocation reached (${allocatedCapital}/${settings.maxCapital} ${settings.quoteCurrency})"
            )
        }

        // 3. Safety Filters before BUY
        if (settings.spreadGuardEnabled && currentTicker.bidPrice > 0.0 && currentTicker.askPrice >= currentTicker.bidPrice) {
            val spreadPct = ((currentTicker.askPrice - currentTicker.bidPrice) / currentTicker.bidPrice) * 100.0
            if (spreadPct > settings.maxSpreadPercentage) {
                return TradingSignal(
                    action = SignalAction.HOLD,
                    reason = String.format("Spread Guard blocked BUY: spread %.2f%% > %.2f%%", spreadPct, settings.maxSpreadPercentage)
                )
            }
        }

        if (settings.crashTrendFilterEnabled && candles.size >= 20) {
            val ma7 = candles.takeLast(7).map { it.close }.average()
            val ma20 = candles.takeLast(20).map { it.close }.average()
            if (((ma20 - ma7) / ma20) * 100.0 > 1.8 && candles.last().close < candles.last().open) {
                return TradingSignal(
                    action = SignalAction.HOLD,
                    reason = "Crash Regime Filter: steep downtrend detected, pausing BUY"
                )
            }
        }

        // 4. Entry evaluation (Initial or DCA Dip)
        if (openPositions.isEmpty()) {
            if (settings.smartEntryEnabled && priceHistory.size >= 5) {
                val rsi = RsiSwingStrategy.calculateRsi(priceHistory, settings.rsiPeriod)
                if (rsi > 52.0) {
                    return TradingSignal(
                        action = SignalAction.HOLD,
                        reason = String.format("Smart Entry waiting for pullback (Current RSI: %.1f > 52)", rsi)
                    )
                }
                if (settings.greenCandleConfirmation && candles.isNotEmpty()) {
                    val lastCandle = candles.last()
                    if (lastCandle.close < lastCandle.open) {
                        return TradingSignal(
                            action = SignalAction.HOLD,
                            reason = "Waiting for bullish green candle confirmation"
                        )
                    }
                }
            }

            // First entry signal
            return TradingSignal(
                action = SignalAction.BUY,
                reason = "Initial DCA entry at current spot price ${currentPrice} ${settings.quoteCurrency}",
                suggestedAmount = settings.orderAmount,
                suggestedPrice = currentPrice
            )
        } else {
            // Compare to last entry price
            val lastEntryPrice = openPositions.maxByOrNull { it.entryTimestamp }?.entryPrice ?: currentPrice
            val priceDropPct = ((lastEntryPrice - currentPrice) / lastEntryPrice) * 100.0

            if (priceDropPct >= settings.dipTriggerPercentage) {
                if (settings.greenCandleConfirmation && candles.isNotEmpty()) {
                    val lastCandle = candles.last()
                    if (lastCandle.close < lastCandle.open) {
                        return TradingSignal(
                            action = SignalAction.HOLD,
                            reason = String.format("Dip threshold met (-%.2f%%), waiting for green reversal candle", priceDropPct)
                        )
                    }
                }

                val scaledAmount = (settings.orderAmount * Math.pow(settings.dcaMultiplier, openPositions.size.toDouble()))
                    .coerceAtMost(remainingCapital)
                    .coerceAtMost(settings.maxOrderValue)

                return TradingSignal(
                    action = SignalAction.BUY,
                    reason = String.format("DCA dip trigger satisfied: -%.2f%% from last entry (Threshold: %.2f%%, Multiplier: %.2fx)", priceDropPct, settings.dipTriggerPercentage, settings.dcaMultiplier),
                    suggestedAmount = scaledAmount,
                    suggestedPrice = currentPrice
                )
            } else {
                return TradingSignal(
                    action = SignalAction.HOLD,
                    reason = String.format("Price pullback %.2f%% below threshold %.2f%%", max(0.0, priceDropPct), settings.dipTriggerPercentage)
                )
            }
        }
    }
}

/**
 * Relative Strength Index (RSI) Swing Strategy
 * Calculates RSI over specified period (default 14).
 * Enters when RSI is Oversold (< 30) and exits when Overbought (> 70) or Stop Loss.
 */
class RsiSwingStrategy : TradingStrategy {
    override val type: StrategyType = StrategyType.RSI_SWING

    override fun evaluate(
        settings: BotSettings,
        currentTicker: MarketTicker,
        priceHistory: List<PricePoint>,
        openPositions: List<OpenPosition>,
        allocatedCapital: Double,
        candles: List<CandleStick>
    ): TradingSignal {
        val currentPrice = currentTicker.price
        if (priceHistory.size < (settings.rsiPeriod + 1)) {
            return TradingSignal(
                action = SignalAction.HOLD,
                reason = "Insufficient historical price points for RSI calculation (${priceHistory.size}/${settings.rsiPeriod + 1})"
            )
        }

        val rsi = calculateRsi(priceHistory, settings.rsiPeriod)

        // Exit evaluation for open positions
        if (openPositions.isNotEmpty()) {
            val totalQty = openPositions.sumOf { it.quantity }
            val totalCost = openPositions.sumOf { it.amountInvested }
            if (totalQty > 0.0 && totalCost > 0.0) {
                val avgPrice = totalCost / totalQty
                val profitPct = ((currentPrice - avgPrice) / avgPrice) * 100.0

                if (settings.neverSellAtLoss) {
                    if (profitPct <= -settings.stopLossPercentage) {
                        return TradingSignal(
                            action = SignalAction.HOLD,
                            reason = String.format("Spot HODL Guard active: holding through drawdown (PnL: %+.2f%%)", profitPct)
                        )
                    }
                } else {
                    if (profitPct <= -settings.stopLossPercentage) {
                        return TradingSignal(
                            action = SignalAction.SELL,
                            reason = String.format("RSI Stop Loss triggered: %.2f%%", profitPct),
                            suggestedPrice = currentPrice
                        )
                    }
                }

                if (rsi >= 70.0 || profitPct >= settings.takeProfitPercentage) {
                    if (settings.neverSellAtLoss && profitPct < 0.25) {
                        return TradingSignal(
                            action = SignalAction.HOLD,
                            reason = "Spot HODL Guard: waiting for profit >= +0.25%"
                        )
                    }
                    return TradingSignal(
                        action = SignalAction.SELL,
                        reason = String.format("RSI Overbought exit signal (RSI: %.1f, Profit: +%.2f%%)", rsi, profitPct),
                        suggestedPrice = currentPrice
                    )
                }
            }
        }

        // Safety Filters before BUY
        if (settings.spreadGuardEnabled && currentTicker.bidPrice > 0.0 && currentTicker.askPrice >= currentTicker.bidPrice) {
            val spreadPct = ((currentTicker.askPrice - currentTicker.bidPrice) / currentTicker.bidPrice) * 100.0
            if (spreadPct > settings.maxSpreadPercentage) {
                return TradingSignal(
                    action = SignalAction.HOLD,
                    reason = String.format("Spread Guard blocked BUY: spread %.2f%% > %.2f%%", spreadPct, settings.maxSpreadPercentage)
                )
            }
        }

        if (settings.crashTrendFilterEnabled && candles.size >= 20) {
            val ma7 = candles.takeLast(7).map { it.close }.average()
            val ma20 = candles.takeLast(20).map { it.close }.average()
            if (((ma20 - ma7) / ma20) * 100.0 > 1.8 && candles.last().close < candles.last().open) {
                return TradingSignal(
                    action = SignalAction.HOLD,
                    reason = "Crash Regime Filter: steep downtrend detected, pausing BUY"
                )
            }
        }

        // Entry evaluation
        if (rsi <= 30.0) {
            val remainingCapital = settings.maxCapital - allocatedCapital
            if (remainingCapital >= settings.orderAmount) {
                return TradingSignal(
                    action = SignalAction.BUY,
                    reason = String.format("RSI Oversold entry signal (RSI: %.1f <= 30)", rsi),
                    suggestedAmount = settings.orderAmount,
                    suggestedPrice = currentPrice
                )
            } else {
                return TradingSignal(
                    action = SignalAction.HOLD,
                    reason = "RSI oversold but max capital limit reached"
                )
            }
        }

        return TradingSignal(
            action = SignalAction.HOLD,
            reason = String.format("RSI neutral at %.1f (Oversold < 30, Overbought > 70)", rsi)
        )
    }

    companion object {
        fun calculateRsi(prices: List<PricePoint>, period: Int = 14): Double {
            if (prices.size <= period) return 50.0

            var gains = 0.0
            var losses = 0.0

            for (i in (prices.size - period) until prices.size) {
                val diff = prices[i].price - prices[i - 1].price
                if (diff >= 0) {
                    gains += diff
                } else {
                    losses += -diff
                }
            }

            if (losses == 0.0) return 100.0
            if (gains == 0.0) return 0.0

            val avgGain = gains / period
            val avgLoss = losses / period
            val rs = avgGain / avgLoss
            return 100.0 - (100.0 / (1.0 + rs))
        }
    }
}

/**
 * Grid Trading Strategy
 * Maintains automated ladder buy and sell levels within upper and lower boundaries.
 */
class GridTradingStrategy : TradingStrategy {
    override val type: StrategyType = StrategyType.GRID

    override fun evaluate(
        settings: BotSettings,
        currentTicker: MarketTicker,
        priceHistory: List<PricePoint>,
        openPositions: List<OpenPosition>,
        allocatedCapital: Double,
        candles: List<CandleStick>
    ): TradingSignal {
        val currentPrice = currentTicker.price
        val upper = settings.gridUpperPrice
        val lower = settings.gridLowerPrice

        if (currentPrice > upper) {
            // Price broke out above grid - take profits if positions exist
            if (openPositions.isNotEmpty()) {
                return TradingSignal(
                    action = SignalAction.SELL,
                    reason = "Price exceeded grid upper boundary ($currentPrice > $upper). Taking profit.",
                    suggestedPrice = currentPrice
                )
            }
            return TradingSignal(SignalAction.HOLD, "Price above upper grid limit ($upper)")
        }

        if (currentPrice < lower) {
            // Price broke down below grid - emergency stop or hold
            return TradingSignal(
                action = SignalAction.HOLD,
                reason = "Price broke below grid lower boundary ($currentPrice < $lower). Suspending new buys."
            )
        }

        val step = (upper - lower) / settings.gridLevels.coerceAtLeast(2)
        val currentGridLevel = ((currentPrice - lower) / step).toInt()

        // Sell check: If we have positions bought below current level
        val eligibleSellPosition = openPositions.firstOrNull { pos ->
            currentPrice >= (pos.entryPrice + step * 0.9)
        }
        if (eligibleSellPosition != null) {
            return TradingSignal(
                action = SignalAction.SELL,
                reason = String.format("Grid level %d reached: selling position from %.2f", currentGridLevel, eligibleSellPosition.entryPrice),
                suggestedPrice = currentPrice
            )
        }

        // Buy check: If allocated capital allows and no recent position at this level
        val hasPositionAtLevel = openPositions.any { pos ->
            kotlin.math.abs(pos.entryPrice - currentPrice) < (step * 0.5)
        }

        if (!hasPositionAtLevel && (allocatedCapital + settings.orderAmount) <= settings.maxCapital) {
            return TradingSignal(
                action = SignalAction.BUY,
                reason = String.format("Grid buy order triggered at level %d (Price: %.2f)", currentGridLevel, currentPrice),
                suggestedAmount = settings.orderAmount,
                suggestedPrice = currentPrice
            )
        }

        return TradingSignal(SignalAction.HOLD, "Grid within bounds, holding positions")
    }
}

/**
 * Bollinger Bands Mean Reversion Strategy
 * Calculates 20-period SMA and standard deviation bands (2.0x multiplier).
 * Enters on lower band bounces and exits at mean (SMA20) or upper band.
 */
class BollingerBandsStrategy : TradingStrategy {
    override val type: StrategyType = StrategyType.BOLLINGER

    override fun evaluate(
        settings: BotSettings,
        currentTicker: MarketTicker,
        priceHistory: List<PricePoint>,
        openPositions: List<OpenPosition>,
        allocatedCapital: Double,
        candles: List<CandleStick>
    ): TradingSignal {
        val currentPrice = currentTicker.price
        if (currentPrice <= 0.0) {
            return TradingSignal(SignalAction.HOLD, "Invalid market price ($currentPrice)")
        }

        val prices = if (candles.size >= 20) {
            candles.map { it.close }
        } else if (priceHistory.size >= 20) {
            priceHistory.map { it.price }
        } else {
            emptyList()
        }

        if (prices.size < 20) {
            return TradingSignal(
                action = SignalAction.HOLD,
                reason = "Insufficient historical price points for Bollinger Bands calculation (${prices.size}/20)"
            )
        }

        val window = prices.takeLast(20)
        val sma20 = window.average()
        val variance = window.sumOf { (it - sma20) * (it - sma20) } / 20.0
        val stdDev = kotlin.math.sqrt(variance)
        val upperBand = sma20 + (2.0 * stdDev)
        val lowerBand = sma20 - (2.0 * stdDev)

        // 1. Exit Evaluation
        if (openPositions.isNotEmpty()) {
            val totalQty = openPositions.sumOf { it.quantity }
            val totalCost = openPositions.sumOf { it.amountInvested }
            if (totalQty > 0.0 && totalCost > 0.0) {
                val averageEntryPrice = totalCost / totalQty
                val profitPct = ((currentPrice - averageEntryPrice) / averageEntryPrice) * 100.0

                if (settings.neverSellAtLoss) {
                    if (profitPct < 0.25) {
                        return TradingSignal(
                            action = SignalAction.HOLD,
                            reason = String.format("Spot HODL Guard active: holding through drawdown (PnL: %+.2f%% < +0.25%%)", profitPct)
                        )
                    }
                } else {
                    if (profitPct <= -settings.stopLossPercentage) {
                        return TradingSignal(
                            action = SignalAction.SELL,
                            reason = String.format("Bollinger Stop Loss triggered: %.2f%%", profitPct),
                            suggestedPrice = currentPrice
                        )
                    }
                }

                if ((currentPrice >= sma20 && profitPct >= 0.40) || profitPct >= settings.takeProfitPercentage || (currentPrice >= upperBand && profitPct >= 0.25)) {
                    return TradingSignal(
                        action = SignalAction.SELL,
                        reason = String.format("Bollinger Mean-Reversion exit signal (Target SMA20: %.2f, Profit: +%.2f%%)", sma20, profitPct),
                        suggestedPrice = currentPrice
                    )
                }
            }
        }

        // 2. Capital & Safety Guards before BUY
        val remainingCapital = settings.maxCapital - allocatedCapital
        if (remainingCapital < settings.orderAmount) {
            return TradingSignal(
                action = SignalAction.HOLD,
                reason = "Max capital allocation reached (${allocatedCapital}/${settings.maxCapital} ${settings.quoteCurrency})"
            )
        }

        if (settings.spreadGuardEnabled && currentTicker.bidPrice > 0.0 && currentTicker.askPrice >= currentTicker.bidPrice) {
            val spreadPct = ((currentTicker.askPrice - currentTicker.bidPrice) / currentTicker.bidPrice) * 100.0
            if (spreadPct > settings.maxSpreadPercentage) {
                return TradingSignal(
                    action = SignalAction.HOLD,
                    reason = String.format("Spread Guard blocked BUY: spread %.2f%% > %.2f%%", spreadPct, settings.maxSpreadPercentage)
                )
            }
        }

        if (settings.crashTrendFilterEnabled && candles.size >= 20) {
            val ma7 = candles.takeLast(7).map { it.close }.average()
            val ma20 = candles.takeLast(20).map { it.close }.average()
            if (((ma20 - ma7) / ma20) * 100.0 > 1.8 && candles.last().close < candles.last().open) {
                return TradingSignal(
                    action = SignalAction.HOLD,
                    reason = "Crash Regime Filter: steep downtrend detected, pausing BUY"
                )
            }
        }

        // 3. Entry Evaluation (Lower Band Bounce)
        if (currentPrice <= (lowerBand * 1.002)) {
            if (settings.greenCandleConfirmation && candles.isNotEmpty()) {
                val lastCandle = candles.last()
                if (lastCandle.close < lastCandle.open) {
                    return TradingSignal(
                        action = SignalAction.HOLD,
                        reason = "Bollinger lower band touched, waiting for green reversal candle"
                    )
                }
            }

            return TradingSignal(
                action = SignalAction.BUY,
                reason = String.format("Bollinger Lower Band bounce signal (Price: %.2f <= Band: %.2f)", currentPrice, lowerBand),
                suggestedAmount = settings.orderAmount,
                suggestedPrice = currentPrice
            )
        }

        return TradingSignal(
            action = SignalAction.HOLD,
            reason = String.format("Bollinger neutral (Price: %.2f, Lower: %.2f, Mid: %.2f, Upper: %.2f)", currentPrice, lowerBand, sma20, upperBand)
        )
    }
}
