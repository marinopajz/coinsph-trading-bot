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
    @Json(name = "isSpotTradingAllowed") val isSpotTradingAllowed: Boolean? = true,
    val lastPrice: Double = 0.0,
    val priceChangePercent: Double = 0.0,
    val volume24h: Double = 0.0
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
    @Json(name = "volume") val volume: String?,
    @Json(name = "bidPrice") val bidPrice: String? = null,
    @Json(name = "askPrice") val askPrice: String? = null
)

@JsonClass(generateAdapter = true)
data class CoinsOrderBookResponse(
    @Json(name = "bids") val bids: List<List<String>> = emptyList(),
    @Json(name = "asks") val asks: List<List<String>> = emptyList()
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
    @Json(name = "canTrade") val canTrade: Boolean? = true,
    @Json(name = "canWithdraw") val canWithdraw: Boolean? = true,
    @Json(name = "canDeposit") val canDeposit: Boolean? = true,
    @Json(name = "balances") val rawBalances: List<CoinsBalance>? = null,
    @Json(name = "assets") val rawAssets: List<CoinsBalance>? = null,
    @Json(name = "data") val rawData: List<CoinsBalance>? = null
) {
    constructor(
        canTrade: Boolean?,
        canWithdraw: Boolean?,
        canDeposit: Boolean?,
        balances: List<CoinsBalance>?
    ) : this(
        canTrade = canTrade,
        canWithdraw = canWithdraw,
        canDeposit = canDeposit,
        rawBalances = balances
    )

    val balances: List<CoinsBalance>
        get() = rawBalances ?: rawAssets ?: rawData ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class CoinsBalance(
    @Json(name = "asset") val rawAsset: String? = null,
    @Json(name = "coin") val rawCoin: String? = null,
    @Json(name = "currency") val rawCurrency: String? = null,
    @Json(name = "symbol") val rawSymbol: String? = null,

    @Json(name = "free") val rawFree: Any? = null,
    @Json(name = "available") val rawAvailable: Any? = null,
    @Json(name = "balance") val rawBalance: Any? = null,

    @Json(name = "locked") val rawLocked: Any? = null,
    @Json(name = "frozen") val rawFrozen: Any? = null,
    @Json(name = "reserved") val rawReserved: Any? = null
) {
    constructor(asset: String, free: String, locked: String) : this(
        rawAsset = asset,
        rawFree = free,
        rawLocked = locked
    )

    val asset: String
        get() = rawAsset ?: rawCoin ?: rawCurrency ?: rawSymbol ?: ""

    val free: String
        get() {
            val v = rawFree ?: rawAvailable ?: rawBalance ?: "0.0"
            return v.toString()
        }

    val locked: String
        get() {
            val v = rawLocked ?: rawFrozen ?: rawReserved ?: "0.0"
            return v.toString()
        }
}
