package com.example.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class BackendHealthResponse(
    val status: String,
    val exchangeConnected: Boolean,
    val liveTradingEnabled: Boolean,
    val timestamp: Long
)

data class BackendAccountResponse(
    val canTrade: Boolean,
    val canWithdraw: Boolean,
    val canDeposit: Boolean,
    val balances: List<BackendBalanceItem>,
    val serverTime: Long
)

data class BackendBalanceItem(
    val asset: String,
    val free: String,
    val locked: String
)

data class BackendOrderRequest(
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: Double? = null,
    val quoteOrderQty: Double? = null,
    val price: Double? = null,
    val clientOrderId: String? = null,
    val isTestOrder: Boolean = false
)

data class BackendOrderResponse(
    val success: Boolean = true,
    val orderId: String? = null,
    val status: String? = null,
    val message: String? = null
)

interface BackendApiService {
    @GET("/health")
    suspend fun getHealth(): Response<BackendHealthResponse>

    @GET("/api/account")
    suspend fun getAccount(
        @Header("Authorization") bearerToken: String
    ): Response<BackendAccountResponse>

    @GET("/api/openOrders")
    suspend fun getOpenOrders(
        @Header("Authorization") bearerToken: String,
        @Query("symbol") symbol: String
    ): Response<List<BackendOrderResponse>>

    @POST("/api/order")
    suspend fun submitOrder(
        @Header("Authorization") bearerToken: String,
        @Body request: BackendOrderRequest
    ): Response<BackendOrderResponse>
}
