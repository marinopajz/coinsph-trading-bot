package com.example.data.model

enum class TradingMode(val displayName: String) {
    PAPER("Paper Trading"),
    LIVE("Live Trading")
}

enum class ExchangePlatform(val displayName: String, val defaultSymbol: String, val defaultQuote: String) {
    COINS_PH("Coins.ph", "BTCPHP", "PHP"),
    BINANCE("Binance", "BTCUSDT", "USDT")
}

enum class StrategyType(val label: String, val description: String) {
    DCA("DCA (Dollar-Cost Average)", "Accumulates asset on price pullbacks with maximum safety limits."),
    RSI_SWING("RSI Swing Trader", "Enters on oversold signals (RSI < 30) and exits on overbought signals (RSI > 70)."),
    GRID("Grid Trading", "Places automated buy and sell limit grids around the current market price range."),
    BOLLINGER("Bollinger Mean-Rev", "Buys statistical oversold bounces off the Lower Bollinger Band (20, 2) and exits at the Middle/Upper Band."),
    TURTLE_BREAKOUT("Turtle Breakout", "Richard Dennis' Donchian Channel (20) high breakout confirmed by a >1.2x institutional volume surge."),
    VWAP_REVERSAL("Whale VWAP + Wick", "Institutional Volume-Weighted Average Price (VWAP) discount entry with bullish hammer/pinbar wick rejection."),
    AI_GOD_MODE("⚡ AI God Mode", "Autonomous Quant Ensemble: detects market regime (Trending vs. Ranging vs. Dip) and combines Multi-Strategy Consensus Voting (DCA, RSI, Bollinger, VWAP, Breakout).")
}

enum class BotState(val label: String) {
    RUNNING("Running"),
    PAUSED("Paused"),
    STOPPED("Stopped"),
    EMERGENCY_STOP("Emergency Stop"),
    ERROR("Error")
}

enum class ChartType(val label: String) {
    LINE("Line"),
    CANDLE("Candles"),
    AREA("Area + MA")
}

enum class ChartInterval(val code: String, val label: String) {
    ONE_MIN("1m", "1m"),
    FIVE_MIN("5m", "5m"),
    FIFTEEN_MIN("15m", "15m"),
    ONE_HOUR("1h", "1H"),
    FOUR_HOUR("4h", "4H"),
    ONE_DAY("1d", "1D")
}

data class CandleStick(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long
)

data class BotSettings(
    val symbol: String = "BTCPHP",
    val strategyType: StrategyType = StrategyType.DCA,
    val orderAmount: Double = 500.0, // in quote currency (PHP or USDT)
    val quoteCurrency: String = "PHP",
    val baseCurrency: String = "BTC",
    val exchangePlatform: ExchangePlatform = ExchangePlatform.COINS_PH,
    val dipTriggerPercentage: Double = 2.5,
    val takeProfitPercentage: Double = 5.0,
    val stopLossPercentage: Double = 3.5,
    val checkIntervalMinutes: Int = 5,
    val tradingMode: TradingMode = TradingMode.PAPER,
    val maxCapital: Double = 10000.0,
    val maxOrderValue: Double = 2500.0,
    val maxDailyLoss: Double = 1500.0,
    val gridLevels: Int = 5,
    val gridUpperPrice: Double = 6000000.0,
    val gridLowerPrice: Double = 5000000.0,
    val rsiPeriod: Int = 14,
    val trailingTakeProfitEnabled: Boolean = false,
    val trailingStopPercentage: Double = 1.0,
    val peakPrice: Double = 0.0,
    val smartEntryEnabled: Boolean = false,
    val breakEvenLockEnabled: Boolean = false,
    val breakEvenActivated: Boolean = false,
    val greenCandleConfirmation: Boolean = false,
    val dcaMultiplier: Double = 1.0,
    val neverSellAtLoss: Boolean = false,
    val spreadGuardEnabled: Boolean = false,
    val maxSpreadPercentage: Double = 0.60,
    val crashTrendFilterEnabled: Boolean = false,
    val partialTakeProfitEnabled: Boolean = false,
    val partialTakeProfitPercentage: Double = 1.8,
    val atrDynamicTargetsEnabled: Boolean = false,
    val orderBookWallFilterEnabled: Boolean = false,
    val kellyDynamicSizingEnabled: Boolean = false,
    val seafarerLowDataMode: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class BacktestResult(
    val symbol: String,
    val strategy: StrategyType,
    val interval: String,
    val candlesTested: Int,
    val totalTrades: Int,
    val winRate: Double,
    val netPnl: Double,
    val summary: String,
    val openPositionsCount: Int = 0,
    val unrealizedPnl: Double = 0.0,
    val recommendedSettings: BotSettings? = null
)

data class TradeLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "MARKET", "LIMIT"
    val symbol: String,
    val side: String, // "BUY" or "SELL"
    val price: Double,
    val quantity: Double,
    val amount: Double,
    val fee: Double,
    val feeCurrency: String,
    val realizedPnl: Double = 0.0,
    val status: String, // "FILLED", "PENDING", "CANCELLED", "REJECTED"
    val orderId: String? = null,
    val message: String = "",
    val tradingMode: TradingMode = TradingMode.PAPER
)

data class PricePoint(
    val timestamp: Long,
    val price: Double
)

data class MarketTicker(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val price: Double,
    val change24h: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24h: Double,
    val bidPrice: Double = price,
    val askPrice: Double = price,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class OrderBookEntry(val price: Double, val quantity: Double)

data class OrderBookSnapshot(
    val symbol: String = "BTCPHP",
    val bids: List<OrderBookEntry> = emptyList(),
    val asks: List<OrderBookEntry> = emptyList(),
    val buyPressurePercent: Double = 50.0,
    val spreadPercent: Double = 0.0
)

data class PortfolioSnapshot(
    val quoteBalance: Double = 50000.0, // Default virtual paper quote balance
    val baseBalance: Double = 0.0,
    val quoteCurrency: String = "PHP",
    val allocatedCapital: Double = 0.0,
    val totalRealizedPnl: Double = 0.0,
    val unrealizedPnl: Double = 0.0,
    val winRate: Double = 0.0,
    val totalTrades: Int = 0,
    val winningTrades: Int = kotlin.math.round((winRate / 100.0) * totalTrades).toInt(),
    val dailyPnl: Double = 0.0
)

data class BotRuntimeStatus(
    val state: BotState = BotState.STOPPED,
    val mode: TradingMode = TradingMode.PAPER,
    val activeSymbol: String = "BTCPHP",
    val lastCheckTime: Long = 0L,
    val backendHealthy: Boolean = true,
    val exchangeConnected: Boolean = true,
    val lastErrorMessage: String? = null,
    val openPositionsCount: Int = 0
)

data class OpenPosition(
    val id: String,
    val symbol: String,
    val side: String = "BUY",
    val entryPrice: Double,
    val currentPrice: Double,
    val quantity: Double,
    val amountInvested: Double,
    val unrealizedPnl: Double,
    val pnlPercentage: Double,
    val entryTimestamp: Long,
    val partialTpTaken: Boolean = false
)
