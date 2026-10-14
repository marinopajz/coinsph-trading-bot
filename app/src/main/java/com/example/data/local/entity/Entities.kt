package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.StrategyType
import com.example.data.model.TradingMode
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.BotSettings
import com.example.data.model.TradeLog
import com.example.data.model.OpenPosition

@Entity(tableName = "paper_balances")
data class PaperBalanceEntity(
    @PrimaryKey val asset: String,
    val freeAmount: Double
)

@Entity(tableName = "portfolio_snapshot")
data class PortfolioSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val quoteBalance: Double,
    val baseBalance: Double,
    val quoteCurrency: String,
    val allocatedCapital: Double,
    val totalRealizedPnl: Double,
    val unrealizedPnl: Double,
    val winRate: Double,
    val totalTrades: Int,
    val winningTrades: Int = kotlin.math.round((winRate / 100.0) * totalTrades).toInt(),
    val dailyPnl: Double
) {
    fun toDomain() = PortfolioSnapshot(
        quoteBalance = quoteBalance,
        baseBalance = baseBalance,
        quoteCurrency = quoteCurrency,
        allocatedCapital = allocatedCapital,
        totalRealizedPnl = totalRealizedPnl,
        unrealizedPnl = unrealizedPnl,
        winRate = winRate,
        totalTrades = totalTrades,
        winningTrades = winningTrades,
        dailyPnl = dailyPnl
    )

    companion object {
        fun fromDomain(domain: PortfolioSnapshot) = PortfolioSnapshotEntity(
            id = 1,
            quoteBalance = domain.quoteBalance,
            baseBalance = domain.baseBalance,
            quoteCurrency = domain.quoteCurrency,
            allocatedCapital = domain.allocatedCapital,
            totalRealizedPnl = domain.totalRealizedPnl,
            unrealizedPnl = domain.unrealizedPnl,
            winRate = domain.winRate,
            totalTrades = domain.totalTrades,
            winningTrades = domain.winningTrades,
            dailyPnl = domain.dailyPnl
        )
    }
}

@Entity(tableName = "bot_settings")
data class BotSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val symbol: String,
    val strategyType: StrategyType,
    val orderAmount: Double,
    val quoteCurrency: String,
    val baseCurrency: String,
    val dipTriggerPercentage: Double,
    val takeProfitPercentage: Double,
    val stopLossPercentage: Double,
    val checkIntervalMinutes: Int,
    val tradingMode: TradingMode,
    val maxCapital: Double,
    val maxOrderValue: Double,
    val maxDailyLoss: Double,
    val gridLevels: Int,
    val gridUpperPrice: Double,
    val gridLowerPrice: Double,
    val rsiPeriod: Int,
    val lastUpdated: Long
) {
    fun toDomain() = BotSettings(
        symbol = symbol,
        strategyType = strategyType,
        orderAmount = orderAmount,
        quoteCurrency = quoteCurrency,
        baseCurrency = baseCurrency,
        dipTriggerPercentage = dipTriggerPercentage,
        takeProfitPercentage = takeProfitPercentage,
        stopLossPercentage = stopLossPercentage,
        checkIntervalMinutes = checkIntervalMinutes,
        tradingMode = tradingMode,
        maxCapital = maxCapital,
        maxOrderValue = maxOrderValue,
        maxDailyLoss = maxDailyLoss,
        gridLevels = gridLevels,
        gridUpperPrice = gridUpperPrice,
        gridLowerPrice = gridLowerPrice,
        rsiPeriod = rsiPeriod,
        lastUpdated = lastUpdated
    )

    companion object {
        fun fromDomain(domain: BotSettings) = BotSettingsEntity(
            id = 1,
            symbol = domain.symbol,
            strategyType = domain.strategyType,
            orderAmount = domain.orderAmount,
            quoteCurrency = domain.quoteCurrency,
            baseCurrency = domain.baseCurrency,
            dipTriggerPercentage = domain.dipTriggerPercentage,
            takeProfitPercentage = domain.takeProfitPercentage,
            stopLossPercentage = domain.stopLossPercentage,
            checkIntervalMinutes = domain.checkIntervalMinutes,
            tradingMode = domain.tradingMode,
            maxCapital = domain.maxCapital,
            maxOrderValue = domain.maxOrderValue,
            maxDailyLoss = domain.maxDailyLoss,
            gridLevels = domain.gridLevels,
            gridUpperPrice = domain.gridUpperPrice,
            gridLowerPrice = domain.gridLowerPrice,
            rsiPeriod = domain.rsiPeriod,
            lastUpdated = domain.lastUpdated
        )
    }
}

@Entity(tableName = "trade_logs")
data class TradeLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val type: String,
    val symbol: String,
    val side: String,
    val price: Double,
    val quantity: Double,
    val amount: Double,
    val fee: Double,
    val feeCurrency: String,
    val realizedPnl: Double,
    val status: String,
    val orderId: String?,
    val message: String,
    val tradingMode: TradingMode
) {
    fun toDomain() = TradeLog(
        id = id,
        timestamp = timestamp,
        type = type,
        symbol = symbol,
        side = side,
        price = price,
        quantity = quantity,
        amount = amount,
        fee = fee,
        feeCurrency = feeCurrency,
        realizedPnl = realizedPnl,
        status = status,
        orderId = orderId,
        message = message,
        tradingMode = tradingMode
    )

    companion object {
        fun fromDomain(domain: TradeLog) = TradeLogEntity(
            id = domain.id,
            timestamp = domain.timestamp,
            type = domain.type,
            symbol = domain.symbol,
            side = domain.side,
            price = domain.price,
            quantity = domain.quantity,
            amount = domain.amount,
            fee = domain.fee,
            feeCurrency = domain.feeCurrency,
            realizedPnl = domain.realizedPnl,
            status = domain.status,
            orderId = domain.orderId,
            message = domain.message,
            tradingMode = domain.tradingMode
        )
    }
}

@Entity(tableName = "open_positions")
data class OpenPositionEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val side: String,
    val entryPrice: Double,
    val currentPrice: Double,
    val quantity: Double,
    val amountInvested: Double,
    val unrealizedPnl: Double,
    val pnlPercentage: Double,
    val entryTimestamp: Long
) {
    fun toDomain() = OpenPosition(
        id = id,
        symbol = symbol,
        side = side,
        entryPrice = entryPrice,
        currentPrice = currentPrice,
        quantity = quantity,
        amountInvested = amountInvested,
        unrealizedPnl = unrealizedPnl,
        pnlPercentage = pnlPercentage,
        entryTimestamp = entryTimestamp
    )

    companion object {
        fun fromDomain(domain: OpenPosition) = OpenPositionEntity(
            id = domain.id,
            symbol = domain.symbol,
            side = domain.side,
            entryPrice = domain.entryPrice,
            currentPrice = domain.currentPrice,
            quantity = domain.quantity,
            amountInvested = domain.amountInvested,
            unrealizedPnl = domain.unrealizedPnl,
            pnlPercentage = domain.pnlPercentage,
            entryTimestamp = domain.entryTimestamp
        )
    }
}
