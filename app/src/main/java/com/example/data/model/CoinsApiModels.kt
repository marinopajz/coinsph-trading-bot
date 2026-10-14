package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CoinsServerTime(
    @Json(name = "serverTime") val serverTime: Long
)

@JsonClass(generateAdapter = true)
data class CoinsExchangeInfo(
    @Json(name = "timezone") val timezone: String?,
    @Json(name = "serverTime") val serverTime: Long?,
    @Json(name = "symbols") val symbols: List<CoinsSymbolInfo>
)

@JsonClass(generateAdapter = true)
data class CoinsSymbolInfo(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "status") val status: String,
    @Json(name = "baseAsset") val baseAsset: String,
    @Json(name = "quoteAsset") val quoteAsset: String,
    @Json(name = "isSpotTradingAllowed") val isSpotTradingAllowed: Boolean? = true
)

@JsonClass(generateAdapter = true)
data class CoinsTickerPrice(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "price") val price: String
)

@JsonClass(generateAdapter = true)
data class Coins24HrTicker(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "lastPrice") val lastPrice: String?,
    @Json(name = "priceChangePercent") val priceChangePercent: String?,
    @Json(name = "highPrice") val highPrice: String?,
    @Json(name = "lowPrice") val lowPrice: String?,
    @Json(name = "volume") val volume: String?
)

@JsonClass(generateAdapter = true)
data class CoinsOrderResponse(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "orderId") val orderId: Long?,
    @Json(name = "clientOrderId") val clientOrderId: String?,
    @Json(name = "transactTime") val transactTime: Long?,
    @Json(name = "price") val price: String?,
    @Json(name = "origQty") val origQty: String?,
    @Json(name = "executedQty") val executedQty: String?,
    @Json(name = "cummulativeQuoteQty") val cummulativeQuoteQty: String? = null,
    @Json(name = "status") val status: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "side") val side: String?
)

@JsonClass(generateAdapter = true)
data class CoinsAccountInfo(
    @Json(name = "canTrade") val canTrade: Boolean?,
    @Json(name = "canWithdraw") val canWithdraw: Boolean?,
    @Json(name = "canDeposit") val canDeposit: Boolean?,
    @Json(name = "balances") val balances: List<CoinsBalance>?
)

@JsonClass(generateAdapter = true)
data class CoinsBalance(
    @Json(name = "asset") val asset: String,
    @Json(name = "free") val free: String,
    @Json(name = "locked") val locked: String
)
