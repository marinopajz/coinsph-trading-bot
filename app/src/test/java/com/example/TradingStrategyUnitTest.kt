package com.example

import com.example.data.model.BotSettings
import com.example.data.model.CandleStick
import com.example.data.model.MarketTicker
import com.example.data.model.OpenPosition
import com.example.data.model.PricePoint
import com.example.data.model.StrategyType
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import com.example.domain.risk.RiskCheckResult
import com.example.domain.risk.RiskManager
import com.example.domain.strategy.DcaStrategy
import com.example.domain.strategy.GridTradingStrategy
import com.example.domain.strategy.RsiSwingStrategy
import com.example.domain.strategy.SignalAction
import com.example.domain.strategy.TradingSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingStrategyUnitTest {

    private val defaultTicker = MarketTicker(
        symbol = "BTCPHP",
        baseAsset = "BTC",
        quoteAsset = "PHP",
        price = 5000000.0,
        change24h = 1.5,
        high24h = 5100000.0,
        low24h = 4900000.0,
        volume24h = 50.0,
        lastUpdated = System.currentTimeMillis()
    )

    private val defaultSettings = BotSettings(
        symbol = "BTCPHP",
        strategyType = StrategyType.DCA,
        orderAmount = 1000.0,
        quoteCurrency = "PHP",
        dipTriggerPercentage = 2.5,
        takeProfitPercentage = 5.0,
        stopLossPercentage = 3.0,
        maxCapital = 10000.0,
        maxOrderValue = 2500.0,
        maxDailyLoss = 1500.0
    )

    @Test
    fun dcaStrategy_initialSignal_triggersBuy() {
        val strategy = DcaStrategy()
        val signal = strategy.evaluate(
            settings = defaultSettings,
            currentTicker = defaultTicker,
            priceHistory = emptyList(),
            openPositions = emptyList(),
            allocatedCapital = 0.0
        )

        assertEquals(SignalAction.BUY, signal.action)
        assertTrue(signal.reason.contains("Initial DCA entry"))
    }

    @Test
    fun dcaStrategy_dipMet_triggersBuy() {
        val strategy = DcaStrategy()
        val openPos = OpenPosition(
            id = "pos_1",
            symbol = "BTCPHP",
            entryPrice = 5000000.0,
            currentPrice = 4850000.0, // 3% drop
            quantity = 0.0002,
            amountInvested = 1000.0,
            unrealizedPnl = -30.0,
            pnlPercentage = -3.0,
            entryTimestamp = System.currentTimeMillis() - 60000
        )

        val dippedTicker = defaultTicker.copy(price = 4850000.0)
        val signal = strategy.evaluate(
            settings = defaultSettings.copy(dipTriggerPercentage = 2.5, stopLossPercentage = 10.0),
            currentTicker = dippedTicker,
            priceHistory = emptyList(),
            openPositions = listOf(openPos),
            allocatedCapital = 1000.0
        )

        assertEquals(SignalAction.BUY, signal.action)
        assertTrue(signal.reason.contains("dip trigger satisfied"))
    }

    @Test
    fun dcaStrategy_takeProfitTriggered_triggersSell() {
        val strategy = DcaStrategy()
        val openPos = OpenPosition(
            id = "pos_1",
            symbol = "BTCPHP",
            entryPrice = 5000000.0,
            currentPrice = 5300000.0, // +6% gain
            quantity = 0.0002,
            amountInvested = 1000.0,
            unrealizedPnl = 60.0,
            pnlPercentage = 6.0,
            entryTimestamp = System.currentTimeMillis() - 60000
        )

        val pumpedTicker = defaultTicker.copy(price = 5300000.0)
        val signal = strategy.evaluate(
            settings = defaultSettings.copy(takeProfitPercentage = 5.0),
            currentTicker = pumpedTicker,
            priceHistory = emptyList(),
            openPositions = listOf(openPos),
            allocatedCapital = 1000.0
        )

        assertEquals(SignalAction.SELL, signal.action)
        assertTrue(signal.reason.contains("Take Profit triggered"))
    }

    @Test
    fun dcaStrategy_trailingTakeProfit_holdsWhenRisingAndTriggersOnPullback() {
        val strategy = DcaStrategy()
        val openPos = OpenPosition(
            id = "pos_trailing",
            symbol = "BTCPHP",
            entryPrice = 5000000.0,
            currentPrice = 5300000.0, // +6% gain (above 5% TP)
            quantity = 0.0002,
            amountInvested = 1000.0,
            unrealizedPnl = 60.0,
            pnlPercentage = 6.0,
            entryTimestamp = System.currentTimeMillis() - 60000
        )

        val settingsTrailing = defaultSettings.copy(
            takeProfitPercentage = 5.0,
            trailingTakeProfitEnabled = true,
            trailingStopPercentage = 1.0,
            peakPrice = 5400000.0 // Peak reached +8%
        )

        // Case A: Price is at peak 5,400,000 (+8% gain). Drop from peak is 0%. Should HOLD.
        val peakTicker = defaultTicker.copy(price = 5400000.0)
        val holdSignal = strategy.evaluate(
            settings = settingsTrailing,
            currentTicker = peakTicker,
            priceHistory = emptyList(),
            openPositions = listOf(openPos),
            allocatedCapital = 1000.0
        )
        assertEquals("Trailing TP should HOLD while price stays at peak", SignalAction.HOLD, holdSignal.action)

        // Case B: Price drops from peak 5,400,000 to 5,300,000 (-1.85% drop >= 1% trailingStopPercentage). Should SELL.
        val dropTicker = defaultTicker.copy(price = 5300000.0)
        val sellSignal = strategy.evaluate(
            settings = settingsTrailing,
            currentTicker = dropTicker,
            priceHistory = emptyList(),
            openPositions = listOf(openPos),
            allocatedCapital = 1000.0
        )
        assertEquals("Trailing TP should trigger SELL on pullback from peak", SignalAction.SELL, sellSignal.action)
        assertTrue(sellSignal.reason.contains("Trailing Take Profit triggered"))
    }

    @Test
    fun rsiCalculation_oversold_triggersBuySignal() {
        val prices = mutableListOf<PricePoint>()
        var p = 5000000.0
        // Simulate falling prices for oversold RSI
        for (i in 0..20) {
            prices.add(PricePoint(timestamp = i.toLong(), price = p))
            p -= 10000.0
        }

        val rsi = RsiSwingStrategy.calculateRsi(prices, 14)
        assertTrue("RSI should be oversold (< 30), got $rsi", rsi <= 30.0)

        val strategy = RsiSwingStrategy()
        val signal = strategy.evaluate(
            settings = defaultSettings.copy(strategyType = StrategyType.RSI_SWING),
            currentTicker = defaultTicker.copy(price = p),
            priceHistory = prices,
            openPositions = emptyList(),
            allocatedCapital = 0.0
        )

        assertEquals(SignalAction.BUY, signal.action)
        assertTrue(signal.reason.contains("RSI Oversold"))
    }

    @Test
    fun riskManager_exceedDailyLoss_rejectsOrder() {
        val riskManager = RiskManager()
        val signal = TradingSignal(SignalAction.BUY, "Test Buy", 1000.0, 5000000.0)

        val result = riskManager.validateOrder(
            signal = signal,
            settings = defaultSettings.copy(maxDailyLoss = 1000.0),
            ticker = defaultTicker,
            openPositions = emptyList(),
            allocatedCapital = 0.0,
            dailyRealizedLoss = 1200.0, // Exceeded
            recentTradeLogs = emptyList()
        )

        assertTrue(result is RiskCheckResult.Rejected)
        assertTrue((result as RiskCheckResult.Rejected).reason.contains("Daily loss limit reached"))
    }

    @Test
    fun riskManager_stalePrice_rejectsOrder() {
        val riskManager = RiskManager()
        val staleTicker = defaultTicker.copy(lastUpdated = System.currentTimeMillis() - 70_000) // 70s old
        val signal = TradingSignal(SignalAction.BUY, "Test Buy", 1000.0, 5000000.0)

        val result = riskManager.validateOrder(
            signal = signal,
            settings = defaultSettings,
            ticker = staleTicker,
            openPositions = emptyList(),
            allocatedCapital = 0.0,
            dailyRealizedLoss = 0.0,
            recentTradeLogs = emptyList()
        )

        assertTrue(result is RiskCheckResult.Rejected)
        assertTrue((result as RiskCheckResult.Rejected).reason.contains("Stale price rejected"))
    }

    @Test
    fun riskManager_cooldown_onlyTriggersOnFilledBuyOrSell() {
        val riskManager = RiskManager()
        val signal = TradingSignal(SignalAction.BUY, "Test Buy", 1000.0, 5000000.0)
        val now = System.currentTimeMillis()

        // Log 1: Non-filled / blocked / failed log from 2 seconds ago
        val blockedLog = TradeLog(
            id = "log_1",
            timestamp = now - 2000,
            type = "RISK_GUARD",
            symbol = "BTCPHP",
            side = "BLOCKED",
            price = 5000000.0,
            quantity = 0.0,
            amount = 0.0,
            fee = 0.0,
            feeCurrency = "PHP",
            status = "REJECTED",
            message = "Blocked by risk guard"
        )

        val failedLog = TradeLog(
            id = "log_2",
            timestamp = now - 1000,
            type = "ERROR",
            symbol = "BTCPHP",
            side = "BUY",
            price = 5000000.0,
            quantity = 0.0,
            amount = 1000.0,
            fee = 0.0,
            feeCurrency = "PHP",
            status = "FAILED",
            message = "Order failed"
        )

        val nonFilledLogs = listOf(failedLog, blockedLog)

        val approvedResult = riskManager.validateOrder(
            signal = signal,
            settings = defaultSettings,
            ticker = defaultTicker.copy(lastUpdated = now),
            openPositions = emptyList(),
            allocatedCapital = 0.0,
            dailyRealizedLoss = 0.0,
            recentTradeLogs = nonFilledLogs
        )

        assertTrue("Order should be Approved when recent logs are non-filled / blocked", approvedResult is RiskCheckResult.Approved)

        // Log 2: Filled BUY log from 2 seconds ago
        val filledBuyLog = TradeLog(
            id = "log_3",
            timestamp = now - 2000,
            type = "MARKET",
            symbol = "BTCPHP",
            side = "BUY",
            price = 5000000.0,
            quantity = 0.0002,
            amount = 1000.0,
            fee = 1.0,
            feeCurrency = "PHP",
            status = "FILLED",
            message = "Order filled"
        )

        val filledLogs = listOf(filledBuyLog) + nonFilledLogs

        val rejectedResult = riskManager.validateOrder(
            signal = signal,
            settings = defaultSettings,
            ticker = defaultTicker.copy(lastUpdated = now),
            openPositions = emptyList(),
            allocatedCapital = 0.0,
            dailyRealizedLoss = 0.0,
            recentTradeLogs = filledLogs
        )

        assertTrue("Order should be Rejected when recent log is a filled BUY", rejectedResult is RiskCheckResult.Rejected)
        assertTrue((rejectedResult as RiskCheckResult.Rejected).reason.contains("Duplicate order cooldown active"))
    }

    @Test
    fun dcaStrategy_breakEvenLock_triggersSellWhenProfitPullbackToPointThreePercent() {
        val strategy = DcaStrategy()
        val openPos = OpenPosition(
            id = "pos_be",
            symbol = "BTCPHP",
            entryPrice = 5000000.0,
            currentPrice = 5010000.0,
            quantity = 0.0002,
            amountInvested = 1000.0,
            unrealizedPnl = 2.0,
            pnlPercentage = 0.20,
            entryTimestamp = System.currentTimeMillis() - 60000
        )

        val settingsBE = defaultSettings.copy(
            breakEvenLockEnabled = true,
            breakEvenActivated = true
        )

        val signal = strategy.evaluate(
            settings = settingsBE,
            currentTicker = defaultTicker.copy(price = 5010000.0),
            priceHistory = emptyList(),
            openPositions = listOf(openPos),
            allocatedCapital = 1000.0
        )

        assertEquals(SignalAction.SELL, signal.action)
        assertTrue(signal.reason.contains("Break-Even Lock triggered"))
    }

    @Test
    fun dcaStrategy_dcaMultiplier_scalesSubsequentTrancheOrderAmount() {
        val strategy = DcaStrategy()
        val openPos1 = OpenPosition(
            id = "pos_1",
            symbol = "BTCPHP",
            entryPrice = 5000000.0,
            currentPrice = 4800000.0,
            quantity = 0.0002,
            amountInvested = 1000.0,
            unrealizedPnl = -40.0,
            pnlPercentage = -4.0,
            entryTimestamp = System.currentTimeMillis() - 60000
        )

        val settingsMulti = defaultSettings.copy(
            orderAmount = 1000.0,
            dipTriggerPercentage = 2.5,
            dcaMultiplier = 1.5,
            stopLossPercentage = 10.0,
            maxCapital = 10000.0,
            maxOrderValue = 5000.0
        )

        val dippedTicker = defaultTicker.copy(price = 4800000.0)
        val signal = strategy.evaluate(
            settings = settingsMulti,
            currentTicker = dippedTicker,
            priceHistory = emptyList(),
            openPositions = listOf(openPos1),
            allocatedCapital = 1000.0
        )

        assertEquals(SignalAction.BUY, signal.action)
        assertEquals(1500.0, signal.suggestedAmount!!, 0.01)
    }

    @Test
    fun bollingerBandsStrategy_triggersBuyOnLowerBandBounceAndSellOnMeanReversion() {
        val strategy = com.example.domain.strategy.BollingerBandsStrategy()
        val prices = (1..20).map { 5000000.0 }
        val pricePoints = prices.mapIndexed { i, pr -> PricePoint(i.toLong(), pr) }

        val lowTicker = defaultTicker.copy(price = 4800000.0)
        val buySignal = strategy.evaluate(
            settings = defaultSettings.copy(strategyType = StrategyType.BOLLINGER),
            currentTicker = lowTicker,
            priceHistory = pricePoints,
            openPositions = emptyList(),
            allocatedCapital = 0.0
        )
        assertEquals(SignalAction.BUY, buySignal.action)
        assertTrue(buySignal.reason.contains("Bollinger Lower Band bounce"))

        val openPos = OpenPosition(
            id = "pos_boll",
            symbol = "BTCPHP",
            entryPrice = 4800000.0,
            currentPrice = 5020000.0,
            quantity = 0.0002,
            amountInvested = 960.0,
            unrealizedPnl = 44.0,
            pnlPercentage = 4.58,
            entryTimestamp = System.currentTimeMillis() - 60000
        )

        val highTicker = defaultTicker.copy(price = 5020000.0)
        val sellSignal = strategy.evaluate(
            settings = defaultSettings.copy(strategyType = StrategyType.BOLLINGER),
            currentTicker = highTicker,
            priceHistory = pricePoints,
            openPositions = listOf(openPos),
            allocatedCapital = 960.0
        )
        assertEquals(SignalAction.SELL, sellSignal.action)
        assertTrue(sellSignal.reason.contains("Bollinger Mean-Reversion exit"))
    }

    @Test
    fun dcaStrategy_neverSellAtLoss_suppressesStopLossDuringDrawdown() {
        val strategy = DcaStrategy()
        val openPos = OpenPosition(
            id = "pos_drawdown",
            symbol = "BTCPHP",
            entryPrice = 5000000.0,
            currentPrice = 4500000.0,
            quantity = 0.0002,
            amountInvested = 1000.0,
            unrealizedPnl = -100.0,
            pnlPercentage = -10.0,
            entryTimestamp = System.currentTimeMillis() - 60000
        )

        val settingsHodl = defaultSettings.copy(
            stopLossPercentage = 3.5,
            neverSellAtLoss = true
        )

        val crashedTicker = defaultTicker.copy(price = 4500000.0)
        val signal = strategy.evaluate(
            settings = settingsHodl,
            currentTicker = crashedTicker,
            priceHistory = emptyList(),
            openPositions = listOf(openPos),
            allocatedCapital = 1000.0
        )

        assertEquals("Spot HODL Guard should suppress Stop-Loss and HOLD", SignalAction.HOLD, signal.action)
        assertTrue(signal.reason.contains("Spot HODL Guard active"))
    }

    @Test
    fun spreadGuard_blocksBuyWhenSpreadExceedsMaxSpreadPercentage() {
        val strategy = DcaStrategy()
        val highSpreadTicker = defaultTicker.copy(
            price = 5000000.0,
            bidPrice = 5000000.0,
            askPrice = 5050000.0
        )

        val settingsSpread = defaultSettings.copy(
            spreadGuardEnabled = true,
            maxSpreadPercentage = 0.60
        )

        val signal = strategy.evaluate(
            settings = settingsSpread,
            currentTicker = highSpreadTicker,
            priceHistory = emptyList(),
            openPositions = emptyList(),
            allocatedCapital = 0.0
        )

        assertEquals(SignalAction.HOLD, signal.action)
        assertTrue(signal.reason.contains("Spread Guard blocked BUY"))
    }

    @Test
    fun turtleBreakoutStrategy_triggersBuyOn20PeriodHighBreakoutWithVolume() {
        val strategy = com.example.domain.strategy.TurtleBreakoutStrategy()
        val candles = (0..20).map { i ->
            CandleStick(
                openTime = 1700000000000L + i * 300000L,
                open = 5000000.0,
                high = 5100000.0,
                low = 4950000.0,
                close = 5000000.0,
                volume = if (i == 20) 20.0 else 10.0,
                closeTime = 1700000300000L + i * 300000L
            )
        }

        val breakoutTicker = defaultTicker.copy(price = 5200000.0)
        val signal = strategy.evaluate(
            settings = defaultSettings.copy(strategyType = StrategyType.TURTLE_BREAKOUT),
            currentTicker = breakoutTicker,
            priceHistory = emptyList(),
            openPositions = emptyList(),
            allocatedCapital = 0.0,
            candles = candles
        )

        assertEquals(SignalAction.BUY, signal.action)
        assertTrue(signal.reason.contains("Turtle Donchian Breakout"))
    }

    @Test
    fun vwapPinbarStrategy_triggersBuyOnBelowVwapHammerRejection() {
        val strategy = com.example.domain.strategy.VwapPinbarStrategy()
        val candles = (0..20).map { i ->
            CandleStick(
                openTime = 1700000000000L + i * 300000L,
                open = 5000000.0,
                high = 5050000.0,
                low = 4800000.0,
                close = 4980000.0,
                volume = 20.0,
                closeTime = 1700000300000L + i * 300000L
            )
        }

        val discountedTicker = defaultTicker.copy(price = 4850000.0)
        val signal = strategy.evaluate(
            settings = defaultSettings.copy(strategyType = StrategyType.VWAP_REVERSAL),
            currentTicker = discountedTicker,
            priceHistory = emptyList(),
            openPositions = emptyList(),
            allocatedCapital = 0.0,
            candles = candles
        )

        assertEquals(SignalAction.BUY, signal.action)
        assertTrue(signal.reason.contains("Whale VWAP Discount"))
    }
}
