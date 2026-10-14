package com.example.data.model

enum class TradingMode(val displayName: String) {
    PAPER("Paper Trading"),
    LIVE("Live Trading")
}

enum class StrategyType(val label: String, val description: String) {
    DCA("DCA (Dollar-Cost Average)", "Accumulates asset on price pullbacks with maximum safety limits."),
    RSI_SWING("RSI Swing Trader", "Enters on oversold signals (RSI < 30) and exits on overbought signals (RSI > 70)."),
    GRID("Grid Trading", "Places automated buy and sell limit grids around the current market price range.")
}

enum class BotState(val label: String) {
    RUNNING("Running"),
    PAUSED("Paused"),
    STOPPED("Stopped"),
    EMERGENCY_STOP("Emergency Stop"),
    ERROR("Error")
}

data class BotSettings(
    val symbol: String = "BTCPHP",
    val strategyType: StrategyType = StrategyType.DCA,
    val orderAmount: Double = 500.0, // in quote currency (PHP or USDT)
    val quoteCurrency: String = "PHP",
    val baseCurrency: String = "BTC",
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
    val lastUpdated: Long = System.currentTimeMillis()
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
    val lastUpdated: Long = System.currentTimeMillis()
)

data class PortfolioSnapshot(
    val quoteBalance: Double = 25000.0, // Default virtual paper quote balance
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
    val entryTimestamp: Long
)
