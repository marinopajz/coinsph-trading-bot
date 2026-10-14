package com.example

import com.example.data.model.Coins24HrTicker
import com.example.data.model.CoinsAccountInfo
import com.example.data.model.CoinsBalance
import com.example.data.model.CoinsExchangeInfo
import com.example.data.model.CoinsOrderResponse
import com.example.data.model.CoinsServerTime
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.CoinsTickerPrice
import com.example.data.model.TradingMode
import com.example.data.network.CoinsPhApiService
import com.example.domain.exchange.CoinsPhExchangeClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CoinsPhExchangeClientTest {

    private lateinit var fakeApiService: FakeCoinsPhApiService
    private lateinit var client: CoinsPhExchangeClient

    private class FakeCoinsPhApiService : CoinsPhApiService {
        var lastApiKey: String? = null
        var lastSymbol: String? = null
        var lastSide: String? = null
        var lastType: String? = null
        var lastQuantity: String? = null
        var lastQuoteOrderQty: String? = null
        var lastPrice: String? = null
        var lastTimestamp: Long? = null
        var lastSignature: String? = null

        override suspend fun getServerTime(): Response<CoinsServerTime> =
            Response.success(CoinsServerTime(1700000000000L))

        override suspend fun getExchangeInfo(): Response<CoinsExchangeInfo> =
            Response.success(
                CoinsExchangeInfo(
                    timezone = "UTC",
                    serverTime = 1700000000000L,
                    symbols = listOf(
                        CoinsSymbolInfo("BTCPHP", "TRADING", "BTC", "PHP", true)
                    )
                )
            )

        override suspend fun getTickerPrice(symbol: String?): Response<CoinsTickerPrice> =
            Response.success(CoinsTickerPrice(symbol ?: "BTCPHP", "5000000.0"))

        override suspend fun get24HrTicker(symbol: String?): Response<Coins24HrTicker> =
            Response.success(
                Coins24HrTicker(
                    symbol = symbol ?: "BTCPHP",
                    lastPrice = "5000000.0",
                    priceChangePercent = "2.5",
                    highPrice = "5100000.0",
                    lowPrice = "4900000.0",
                    volume = "100.0"
                )
            )

        override suspend fun getKlines(
            symbol: String,
            interval: String,
            limit: Int
        ): Response<List<List<Any>>> {
            val sampleKlines = listOf(
                listOf(1700000000000L, "5000000.0", "5100000.0", "4950000.0", "5050000.0", "12.5", 1700000300000L)
            )
            return Response.success(sampleKlines)
        }

        override suspend fun testOrder(
            apiKey: String, symbol: String, side: String, type: String,
            quantity: String?, quoteOrderQty: String?, price: String?,
            timestamp: Long, signature: String
        ): Response<Unit> = Response.success(Unit)

        override suspend fun createOrder(
            apiKey: String, symbol: String, side: String, type: String,
            quantity: String?, quoteOrderQty: String?, price: String?,
            timestamp: Long, signature: String
        ): Response<CoinsOrderResponse> {
            lastApiKey = apiKey
            lastSymbol = symbol
            lastSide = side
            lastType = type
            lastQuantity = quantity
            lastQuoteOrderQty = quoteOrderQty
            lastPrice = price
            lastTimestamp = timestamp
            lastSignature = signature

            val execQty = quantity ?: "0.0001"
            val cumQuote = quoteOrderQty ?: "500.00"

            return Response.success(
                CoinsOrderResponse(
                    symbol = symbol,
                    orderId = 987654321L,
                    clientOrderId = "client_ord_1",
                    transactTime = System.currentTimeMillis(),
                    price = "0.00000000",
                    origQty = execQty,
                    executedQty = execQty,
                    cummulativeQuoteQty = cumQuote,
                    status = "FILLED",
                    type = type,
                    side = side
                )
            )
        }

        override suspend fun getOpenOrders(
            apiKey: String, symbol: String, timestamp: Long, signature: String
        ): Response<List<CoinsOrderResponse>> = Response.success(emptyList())

        override suspend fun cancelOrder(
            apiKey: String, symbol: String, orderId: Long, timestamp: Long, signature: String
        ): Response<CoinsOrderResponse> = Response.success(
            CoinsOrderResponse(
                symbol = symbol,
                orderId = orderId,
                clientOrderId = "c_1",
                transactTime = System.currentTimeMillis(),
                price = "0.0",
                origQty = "0.001",
                executedQty = "0.0",
                cummulativeQuoteQty = "0.0",
                status = "CANCELED",
                type = "LIMIT",
                side = "BUY"
            )
        )

        override suspend fun getAccountInfo(
            apiKey: String, timestamp: Long, signature: String
        ): Response<CoinsAccountInfo> {
            lastApiKey = apiKey
            lastTimestamp = timestamp
            lastSignature = signature
            return Response.success(
                CoinsAccountInfo(
                    canTrade = true,
                    canWithdraw = true,
                    canDeposit = true,
                    balances = listOf(
                        CoinsBalance("PHP", "50000.0", "0.0"),
                        CoinsBalance("BTC", "0.5", "0.0")
                    )
                )
            )
        }
    }

    @Before
    fun setUp() {
        fakeApiService = FakeCoinsPhApiService()
        client = CoinsPhExchangeClient(apiService = fakeApiService)
    }

    @Test
    fun submitOrder_withoutCredentials_rejectsWithSecurityException() = runTest {
        val result = client.submitOrder(
            symbol = "BTCPHP",
            side = "BUY",
            type = "MARKET",
            quantity = 0.0001,
            price = null,
            quoteOrderAmount = 500.0
        )

        assertTrue("Order should fail without API credentials", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(
            (result.exceptionOrNull() as SecurityException).message?.contains("credentials") == true
        )
    }

    @Test
    fun submitOrder_liveMarketBuyWithCredentials_signsAndComputesAccounting() = runTest {
        client.setCredentials("test_key_123", "test_secret_456")
        assertTrue(client.hasCredentials())

        val result = client.submitOrder(
            symbol = "BTCPHP",
            side = "BUY",
            type = "MARKET",
            quantity = 0.0001,
            price = null,
            quoteOrderAmount = 500.0
        )

        assertTrue("Live market buy order should succeed with credentials", result.isSuccess)
        val log = result.getOrThrow()

        assertEquals("BTCPHP", log.symbol)
        assertEquals("BUY", log.side)
        assertEquals(500.0, log.amount, 0.01)
        assertEquals(0.5, log.fee, 0.001) // 500 * 0.001 = 0.5 PHP
        assertEquals("PHP", log.feeCurrency)
        assertEquals(5000000.0, log.price, 0.01) // 500.0 / 0.0001 = 5,000,000.0
        assertEquals("FILLED", log.status)
        assertEquals(TradingMode.LIVE, log.tradingMode)

        assertEquals("test_key_123", fakeApiService.lastApiKey)
        assertEquals("500.00", fakeApiService.lastQuoteOrderQty)
        assertNotNull(fakeApiService.lastSignature)
    }

    @Test
    fun submitOrder_liveMarketSell_formatsQuantityForSymbolStepSize() = runTest {
        client.setCredentials("test_key_123", "test_secret_456")

        val result = client.submitOrder(
            symbol = "BTCPHP",
            side = "SELL",
            type = "MARKET",
            quantity = 0.0001234567, // Should floor to 5 decimals for BTC -> 0.00012
            price = 5000000.0,
            quoteOrderAmount = null
        )

        assertTrue("Live market sell order should succeed", result.isSuccess)
        assertEquals("0.00012", fakeApiService.lastQuantity)
    }

    @Test
    fun getBalances_withCredentials_fetchesExchangeAccountBalances() = runTest {
        client.setCredentials("test_key_123", "test_secret_456")

        val result = client.getBalances()
        assertTrue("getBalances should succeed with credentials", result.isSuccess)

        val balances = result.getOrThrow()
        val phpBal = balances.find { it.asset == "PHP" }
        assertNotNull(phpBal)
        assertEquals("50000.0", phpBal?.free)
    }

    @Test
    fun checkConnectivity_withoutCredentials_fails() = runTest {
        val res = client.checkConnectivity()
        assertTrue("checkConnectivity should fail when credentials are not configured", res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
    }

    @Test
    fun checkConnectivity_withCredentials_succeeds() = runTest {
        client.setCredentials("test_key_123", "test_secret_456")
        val res = client.checkConnectivity()
        assertTrue("checkConnectivity should succeed when credentials are set and verified", res.isSuccess)
        assertEquals(1700000000000L, res.getOrThrow())
    }

    @Test
    fun submitOrder_liveSell_capsQuantityToAvailableFreeBaseBalance() = runTest {
        client.setCredentials("test_key_123", "test_secret_456")

        // In FakeCoinsPhApiService, free BTC balance is 0.5 BTC.
        // If we request to sell 0.6 BTC (exceeding free balance), it should cap at 0.5 BTC (or formatted "0.50000").
        val result = client.submitOrder(
            symbol = "BTCPHP",
            side = "SELL",
            type = "MARKET",
            quantity = 0.6,
            price = 5000000.0,
            quoteOrderAmount = null
        )

        assertTrue("Live sell order should succeed by capping to free balance", result.isSuccess)
        assertEquals("0.50000", fakeApiService.lastQuantity)
    }

    @Test
    fun getKlines_parsesCandleSticksAccurately() = runTest {
        val res = client.getKlines("BTCPHP", "5m", 10)
        assertTrue("getKlines should succeed", res.isSuccess)

        val candles = res.getOrThrow()
        assertEquals(1, candles.size)

        val c = candles.first()
        assertEquals(1700000000000L, c.openTime)
        assertEquals(5000000.0, c.open, 0.01)
        assertEquals(5100000.0, c.high, 0.01)
        assertEquals(4950000.0, c.low, 0.01)
        assertEquals(5050000.0, c.close, 0.01)
        assertEquals(12.5, c.volume, 0.01)
        assertEquals(1700000300000L, c.closeTime)
    }
}
