package com.example.domain.exchange

import com.example.data.model.CandleStick
import com.example.data.model.CoinsBalance
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.MarketTicker
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode

interface ExchangeClient {
    val mode: TradingMode

    suspend fun checkConnectivity(): Result<Long>

    suspend fun getAvailableSymbols(): Result<List<CoinsSymbolInfo>>

    suspend fun getTicker(symbol: String): Result<MarketTicker>

    suspend fun getKlines(
        symbol: String,
        interval: String = "5m",
        limit: Int = 40
    ): Result<List<CandleStick>>

    suspend fun submitOrder(
        symbol: String,
        side: String, // "BUY" or "SELL"
        type: String, // "MARKET" or "LIMIT"
        quantity: Double,
        price: Double? = null,
        quoteOrderAmount: Double? = null
    ): Result<TradeLog>

    suspend fun cancelOrder(symbol: String, orderId: String): Result<Boolean>

    suspend fun getOpenOrders(symbol: String): Result<List<TradeLog>>

    suspend fun getBalances(): Result<List<CoinsBalance>>
}
