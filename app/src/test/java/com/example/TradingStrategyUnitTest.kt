package com.example

import com.example.data.model.BotSettings
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
}
