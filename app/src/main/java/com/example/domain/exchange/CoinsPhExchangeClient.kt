package com.example.domain.exchange

import com.example.data.model.CoinsBalance
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.MarketTicker
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import com.example.data.network.BackendApiService
import com.example.data.network.BackendOrderRequest
import com.example.data.network.CoinsPhApiService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * CoinsPhExchangeClient connects to the Coins.ph Spot API for public market data,
 * and delegates all private live trading requests (orders, balances, cancellations)
 * to a secure backend running in Cloud Run/Firebase Cloud Functions.
 */
class CoinsPhExchangeClient(
    private val apiService: CoinsPhApiService,
    private val backendBaseUrl: String = "https://backend.tradingbot.internal",
    private val backendApiService: BackendApiService? = null
) : ExchangeClient {

    override val mode: TradingMode = TradingMode.LIVE

    private fun isBackendConfigured(): Boolean {
        return backendApiService != null &&
               backendBaseUrl.isNotBlank() &&
               !backendBaseUrl.contains("backend.tradingbot.internal")
    }

    private suspend fun fetchFirebaseToken(): String? {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            val tokenResult = currentUser.getIdToken(false).await()
            tokenResult.token
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun checkConnectivity(): Result<Long> {
        return try {
            val response = apiService.getServerTime()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.serverTime)
            } else {
                Result.failure(Exception("Coins.ph server time check failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableSymbols(): Result<List<CoinsSymbolInfo>> {
        return try {
            val response = apiService.getExchangeInfo()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.symbols.filter {
                    it.status == "TRADING" || it.isSpotTradingAllowed == true
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to fetch exchangeInfo: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTicker(symbol: String): Result<MarketTicker> {
        return try {
            val tickerRes = apiService.get24HrTicker(symbol)
            if (tickerRes.isSuccessful && tickerRes.body() != null) {
                val t = tickerRes.body()!!
                val price = t.lastPrice?.toDoubleOrNull() ?: 0.0
                val change = t.priceChangePercent?.toDoubleOrNull() ?: 0.0
                val high = t.highPrice?.toDoubleOrNull() ?: price
                val low = t.lowPrice?.toDoubleOrNull() ?: price
                val vol = t.volume?.toDoubleOrNull() ?: 0.0
                val (base, quote) = splitSymbol(symbol)

                Result.success(
                    MarketTicker(
                        symbol = symbol,
                        baseAsset = base,
                        quoteAsset = quote,
                        price = price,
                        change24h = change,
                        high24h = high,
                        low24h = low,
                        volume24h = vol
                    )
                )
            } else {
                Result.failure(Exception("Failed to fetch ticker for $symbol: HTTP ${tickerRes.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun submitOrder(
        symbol: String,
        side: String,
        type: String,
        quantity: Double,
        price: Double?,
        quoteOrderAmount: Double?
    ): Result<TradeLog> {
        if (!isBackendConfigured()) {
            return Result.failure(
                SecurityException(
                    "Live trading disabled: Secure backend service is unconfigured or using placeholder URL ($backendBaseUrl). " +
                    "To enable live trading, deploy the backend and configure a verified HTTPS backend URL."
                )
            )
        }

        val token = fetchFirebaseToken()
            ?: return Result.failure(
                SecurityException("Live trading authorization failed: User must be signed in with Firebase Auth.")
            )

        val clientOrderId = "ord_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        return try {
            val request = BackendOrderRequest(
                symbol = symbol,
                side = side,
                type = type,
                quantity = quantity,
                quoteOrderQty = quoteOrderAmount,
                price = price,
                clientOrderId = clientOrderId,
                isTestOrder = false
            )
            val response = backendApiService!!.submitOrder("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success || body.status == "EXECUTED" || body.status == "RECONCILED_OPEN") {
                    val log = TradeLog(
                        id = body.orderId ?: clientOrderId,
                        timestamp = System.currentTimeMillis(),
                        type = type,
                        symbol = symbol,
                        side = side.uppercase(),
                        price = price ?: 0.0,
                        quantity = quantity,
                        amount = quoteOrderAmount ?: (quantity * (price ?: 0.0)),
                        fee = 0.0,
                        feeCurrency = splitSymbol(symbol).second,
                        status = body.status ?: "FILLED",
                        orderId = body.orderId ?: clientOrderId,
                        message = body.message ?: "Live order executed via secure backend",
                        tradingMode = TradingMode.LIVE
                    )
                    Result.success(log)
                } else {
                    Result.failure(Exception("Live order rejected by backend: ${body.message}"))
                }
            } else {
                Result.failure(Exception("Backend order submission failed: HTTP ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            // Reconcile order status via getOpenOrders on network timeout or exception
            val openOrdersRes = getOpenOrders(symbol)
            if (openOrdersRes.isSuccess) {
                val reconciled = openOrdersRes.getOrDefault(emptyList()).firstOrNull { it.symbol == symbol }
                if (reconciled != null) {
                    return Result.success(
                        reconciled.copy(
                            message = "Order encountered network timeout but was reconciled on exchange open orders."
                        )
                    )
                }
            }
            Result.failure(e)
        }
    }

    override suspend fun cancelOrder(symbol: String, orderId: String): Result<Boolean> {
        if (!isBackendConfigured()) {
            return Result.failure(
                SecurityException("Live order cancellation disabled: Backend URL unconfigured.")
            )
        }
        return Result.failure(
            SecurityException("Live order cancellation endpoint unavailable.")
        )
    }

    override suspend fun getOpenOrders(symbol: String): Result<List<TradeLog>> {
        if (!isBackendConfigured()) {
            return Result.success(emptyList())
        }
        val token = fetchFirebaseToken() ?: return Result.success(emptyList())
        return try {
            val response = backendApiService!!.getOpenOrders("Bearer $token", symbol)
            if (response.isSuccessful && response.body() != null) {
                val logs = response.body()!!.map { item ->
                    TradeLog(
                        id = item.orderId ?: UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        type = "LIMIT",
                        symbol = symbol,
                        side = "BUY",
                        price = 0.0,
                        quantity = 0.0,
                        amount = 0.0,
                        fee = 0.0,
                        feeCurrency = splitSymbol(symbol).second,
                        status = item.status ?: "NEW",
                        orderId = item.orderId,
                        message = item.message ?: "",
                        tradingMode = TradingMode.LIVE
                    )
                }
                Result.success(logs)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun getBalances(): Result<List<CoinsBalance>> {
        if (!isBackendConfigured()) {
            return Result.success(emptyList())
        }
        val token = fetchFirebaseToken() ?: return Result.success(emptyList())
        return try {
            val response = backendApiService!!.getAccount("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val coinsBalances = response.body()!!.balances.map { item ->
                    CoinsBalance(
                        asset = item.asset,
                        free = item.free,
                        locked = item.locked
                    )
                }
                Result.success(coinsBalances)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    private fun splitSymbol(symbol: String): Pair<String, String> {
        val upper = symbol.uppercase()
        return when {
            upper.endsWith("PHP") -> upper.removeSuffix("PHP") to "PHP"
            upper.endsWith("USDT") -> upper.removeSuffix("USDT") to "USDT"
            upper.endsWith("USDC") -> upper.removeSuffix("USDC") to "USDC"
            else -> upper.take(3) to upper.drop(3)
        }
    }
}
