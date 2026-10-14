package com.example.data.network

import com.example.data.model.Coins24HrTicker
import com.example.data.model.CoinsAccountInfo
import com.example.data.model.CoinsExchangeInfo
import com.example.data.model.CoinsOrderResponse
import com.example.data.model.CoinsServerTime
import com.example.data.model.CoinsTickerPrice
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface CoinsPhApiService {

    @GET("/openapi/v1/time")
    suspend fun getServerTime(): Response<CoinsServerTime>

    @GET("/openapi/v1/exchangeInfo")
    suspend fun getExchangeInfo(): Response<CoinsExchangeInfo>

    @GET("/openapi/quote/v1/ticker/price")
    suspend fun getTickerPrice(
        @Query("symbol") symbol: String? = null
    ): Response<CoinsTickerPrice>

    @GET("/openapi/quote/v1/ticker/24hr")
    suspend fun get24HrTicker(
        @Query("symbol") symbol: String? = null
    ): Response<Coins24HrTicker>

    // Signed endpoints for Spot Trading & Account queries
    @POST("/openapi/v1/order/test")
    suspend fun testOrder(
        @Header("X-COINS-APIKEY") apiKey: String,
        @Query("symbol") symbol: String,
        @Query("side") side: String,
        @Query("type") type: String,
        @Query("quantity") quantity: String?,
        @Query("quoteOrderQty") quoteOrderQty: String?,
        @Query("price") price: String?,
        @Query("timestamp") timestamp: Long,
        @Query("signature") signature: String
    ): Response<Unit>

    @POST("/openapi/v1/order")
    suspend fun createOrder(
        @Header("X-COINS-APIKEY") apiKey: String,
        @Query("symbol") symbol: String,
        @Query("side") side: String,
        @Query("type") type: String,
        @Query("quantity") quantity: String?,
        @Query("quoteOrderQty") quoteOrderQty: String?,
        @Query("price") price: String?,
        @Query("timestamp") timestamp: Long,
        @Query("signature") signature: String
    ): Response<CoinsOrderResponse>

    @GET("/openapi/v1/openOrders")
    suspend fun getOpenOrders(
        @Header("X-COINS-APIKEY") apiKey: String,
        @Query("symbol") symbol: String,
        @Query("timestamp") timestamp: Long,
        @Query("signature") signature: String
    ): Response<List<CoinsOrderResponse>>

    @DELETE("/openapi/v1/order")
    suspend fun cancelOrder(
        @Header("X-COINS-APIKEY") apiKey: String,
        @Query("symbol") symbol: String,
        @Query("orderId") orderId: Long,
        @Query("timestamp") timestamp: Long,
        @Query("signature") signature: String
    ): Response<CoinsOrderResponse>

    @GET("/openapi/v1/account")
    suspend fun getAccountInfo(
        @Header("X-COINS-APIKEY") apiKey: String,
        @Query("timestamp") timestamp: Long,
        @Query("signature") signature: String
    ): Response<CoinsAccountInfo>
}
