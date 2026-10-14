package com.example

import com.example.data.local.dao.TradingDao
import com.example.data.local.entity.BotSettingsEntity
import com.example.data.local.entity.OpenPositionEntity
import com.example.data.local.entity.PaperBalanceEntity
import com.example.data.local.entity.PortfolioSnapshotEntity
import com.example.data.local.entity.TradeLogEntity
import com.example.data.model.Coins24HrTicker
import com.example.data.model.CoinsAccountInfo
import com.example.data.model.CoinsExchangeInfo
import com.example.data.model.CoinsOrderResponse
import com.example.data.model.CoinsServerTime
import com.example.data.model.CoinsSymbolInfo
import com.example.data.model.CoinsTickerPrice
import com.example.data.network.CoinsPhApiService
import com.example.domain.exchange.PaperTradingExchangeClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class FakeTradingDao : TradingDao {
    private val balances = mutableMapOf<String, PaperBalanceEntity>()
    private val tradeLogs = mutableListOf<TradeLogEntity>()
    private val openPositions = mutableListOf<OpenPositionEntity>()
    private var portfolio: PortfolioSnapshotEntity? = null
    private var settings: BotSettingsEntity? = null

    override suspend fun getAllPaperBalances(): List<PaperBalanceEntity> {
        return balances.values.toList()
    }

    override suspend fun savePaperBalance(balance: PaperBalanceEntity) {
        balances[balance.asset] = balance
    }

    override suspend fun savePaperBalances(balancesList: List<PaperBalanceEntity>) {
        balancesList.forEach { balances[it.asset] = it }
    }

    override suspend fun getPortfolioSnapshot(): PortfolioSnapshotEntity? = portfolio
    override suspend fun savePortfolioSnapshot(p: PortfolioSnapshotEntity) { portfolio = p }
    override suspend fun getBotSettings(): BotSettingsEntity? = settings
    override suspend fun saveBotSettings(s: BotSettingsEntity) { settings = s }
    override suspend fun getAllTradeLogs(): List<TradeLogEntity> = tradeLogs
    override suspend fun insertTradeLog(tradeLog: TradeLogEntity) { tradeLogs.add(0, tradeLog) }
    override suspend fun insertTradeLogs(logs: List<TradeLogEntity>) { tradeLogs.addAll(0, logs) }
    override suspend fun getAllOpenPositions(): List<OpenPositionEntity> = openPositions
    override suspend fun insertOpenPosition(position: OpenPositionEntity) { openPositions.add(position) }
    override suspend fun insertOpenPositions(positions: List<OpenPositionEntity>) { openPositions.addAll(positions) }
    override suspend fun deleteOpenPosition(id: String) { openPositions.removeAll { it.id == id } }
    override suspend fun deleteAllOpenPositions() { openPositions.clear() }
    override suspend fun deleteAllTradeLogs() { tradeLogs.clear() }
}

class FakeCoinsPhApiService : CoinsPhApiService {
    var serverTimeResponse: Response<CoinsServerTime>? = null
    var exchangeInfoResponse: Response<CoinsExchangeInfo>? = null
    var tickerPriceResponse: Response<CoinsTickerPrice>? = null
    var ticker24HrResponse: Response<Coins24HrTicker>? = null

    override suspend fun getServerTime(): Response<CoinsServerTime> {
        return serverTimeResponse ?: Response.success(CoinsServerTime(1700000000000L))
    }

    override suspend fun getExchangeInfo(): Response<CoinsExchangeInfo> {
        return exchangeInfoResponse ?: Response.success(
            CoinsExchangeInfo(
                timezone = "UTC",
                serverTime = 1700000000000L,
                symbols = listOf(
                    CoinsSymbolInfo("BTCPHP", "TRADING", "BTC", "PHP", true),
                    CoinsSymbolInfo("BTCUSDT", "TRADING", "BTC", "USDT", true),
                    CoinsSymbolInfo("ETHPHP", "TRADING", "ETH", "PHP", true),
                    CoinsSymbolInfo("SOLPHP", "TRADING", "SOL", "PHP", true),
                    CoinsSymbolInfo("XRPPHP", "TRADING", "XRP", "PHP", true)
                )
            )
        )
    }

    override suspend fun getTickerPrice(symbol: String?): Response<CoinsTickerPrice> {
        return tickerPriceResponse ?: Response.success(CoinsTickerPrice(symbol ?: "BTCPHP", "5000000.0"))
    }

    override suspend fun get24HrTicker(symbol: String?): Response<Coins24HrTicker> {
        return ticker24HrResponse ?: Response.success(
            Coins24HrTicker(
                symbol = symbol ?: "BTCPHP",
                lastPrice = "5000000.0",
                priceChangePercent = "2.5",
                highPrice = "5100000.0",
                lowPrice = "4900000.0",
                volume = "100.0"
            )
        )
    }

    override suspend fun testOrder(
        apiKey: String,
        symbol: String,
        side: String,
        type: String,
        quantity: String?,
        quoteOrderAmount: String?,
        price: String?,
        timestamp: Long,
        signature: String
    ): Response<Unit> {
        return Response.success(Unit)
    }

    override suspend fun createOrder(
        apiKey: String,
        symbol: String,
        side: String,
        type: String,
        quantity: String?,
        quoteOrderAmount: String?,
        price: String?,
        timestamp: Long,
        signature: String
    ): Response<CoinsOrderResponse> {
        return Response.success(
            CoinsOrderResponse(
                symbol = symbol,
                orderId = 12345L,
                clientOrderId = "test_client_id",
                transactTime = System.currentTimeMillis(),
                price = price ?: "5000000.0",
                origQty = quantity ?: "0.001",
                executedQty = quantity ?: "0.001",
                status = "FILLED",
                type = type,
                side = side
            )
        )
    }

    override suspend fun getOpenOrders(
        apiKey: String,
        symbol: String,
        timestamp: Long,
        signature: String
    ): Response<List<CoinsOrderResponse>> {
        return Response.success(emptyList())
    }

    override suspend fun cancelOrder(
        apiKey: String,
        symbol: String,
        orderId: Long,
        timestamp: Long,
        signature: String
    ): Response<CoinsOrderResponse> {
        return Response.success(
            CoinsOrderResponse(
                symbol = symbol,
                orderId = orderId,
                clientOrderId = "test_cancel",
                transactTime = System.currentTimeMillis(),
                price = "5000000.0",
                origQty = "0.001",
                executedQty = "0.001",
                status = "CANCELED",
                type = "LIMIT",
                side = "BUY"
            )
        )
    }

    override suspend fun getAccountInfo(
        apiKey: String,
        timestamp: Long,
        signature: String
    ): Response<CoinsAccountInfo> {
        return Response.success(
            CoinsAccountInfo(
                canTrade = true,
                canWithdraw = true,
                canDeposit = true,
                balances = emptyList()
            )
        )
    }
}

class PaperTradingExchangeClientTest {

    private lateinit var fakeApiService: FakeCoinsPhApiService
    private lateinit var fakeDao: FakeTradingDao
    private lateinit var client: PaperTradingExchangeClient

    @Before
    fun setUp() {
        fakeApiService = FakeCoinsPhApiService()
        fakeDao = FakeTradingDao()
        client = PaperTradingExchangeClient(
            apiService = fakeApiService,
            initialPhpBalance = 50000.0,
            initialUsdtBalance = 1000.0,
            tradingDao = fakeDao
        )
    }

    @Test
    fun checkConnectivity_success() = runTest {
        val result = client.checkConnectivity()
        assertTrue(result.isSuccess)
        assertEquals(1700000000000L, result.getOrNull())
    }

    @Test
    fun submitOrder_rejectsMalformedSymbol() = runTest {
        val result = client.submitOrder(
            symbol = "PHP",
            side = "BUY",
            type = "MARKET",
            quantity = 1.0,
            price = 500.0
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun submitOrder_rejectsIdenticalBaseAndQuote() = runTest {
        val result = client.submitOrder(
            symbol = "PHPPHP",
            side = "BUY",
            type = "MARKET",
            quantity = 1.0,
            price = 500.0
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun submitOrder_rejectsUnsupportedQuote() = runTest {
        val result = client.submitOrder(
            symbol = "BTCEUR",
            side = "BUY",
            type = "MARKET",
            quantity = 1.0,
            price = 500.0
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun submitOrder_rejectsUnsupportedBase() = runTest {
        val result = client.submitOrder(
            symbol = "DOGEPHP",
            side = "BUY",
            type = "MARKET",
            quantity = 1.0,
            price = 500.0
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun submitOrder_rejectsInvalidSide() = runTest {
        val result = client.submitOrder(
            symbol = "BTCPHP",
            side = "HOLD",
            type = "MARKET",
            quantity = 1.0,
            price = 5000000.0
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun submitOrder_successfulBuyAndSell() = runTest {
        // 1. Execute BUY
        val buyResult = client.submitOrder(
            symbol = "BTCPHP",
            side = "BUY",
            type = "MARKET",
            quantity = 0.001,
            price = 5000000.0,
            quoteOrderAmount = 5000.0
        )
        assertTrue(buyResult.isSuccess)
        val buyTrade = buyResult.getOrThrow()
        assertEquals("FILLED", buyTrade.status)
        assertEquals(5000.0, buyTrade.amount, 0.01)
        assertEquals(5.0, buyTrade.fee, 0.01) // 0.1% fee on 5000 = 5

        // Check balances: Initial PHP 50000 - 5005 (amount + fee) = 44995.0 PHP
        val balancesAfterBuy = client.getBalances().getOrThrow()
        val phpBalance = balancesAfterBuy.first { it.asset == "PHP" }.free.toDouble()
        assertEquals(44995.0, phpBalance, 0.01)

        // 2. Execute SELL
        val sellResult = client.submitOrder(
            symbol = "BTCPHP",
            side = "SELL",
            type = "MARKET",
            quantity = 0.001,
            price = 6000000.0 // Price went up to 6M
        )
        assertTrue(sellResult.isSuccess)
        val sellTrade = sellResult.getOrThrow()
        assertEquals("FILLED", sellTrade.status)
        assertEquals(6000.0, sellTrade.amount, 0.01)
        assertEquals(6.0, sellTrade.fee, 0.01) // 0.1% fee on 6000 = 6

        // Check balances: PHP 44995 + 5994 (amount - fee) = 50989.0 PHP
        val balancesAfterSell = client.getBalances().getOrThrow()
        val finalPhpBalance = balancesAfterSell.first { it.asset == "PHP" }.free.toDouble()
        assertEquals(50989.0, finalPhpBalance, 0.01)
    }

    @Test
    fun submitOrder_insufficientFunds_fails() = runTest {
        val result = client.submitOrder(
            symbol = "BTCPHP",
            side = "BUY",
            type = "MARKET",
            quantity = 1.0,
            price = 5000000.0,
            quoteOrderAmount = 100000.0 // Requires 100k PHP, but only 50k available
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun submitOrder_failsWhenDatabaseRestoreThrows() = runTest {
        val failingDao = object : TradingDao by fakeDao {
            override suspend fun getAllPaperBalances(): List<PaperBalanceEntity> {
                throw RuntimeException("Database read error")
            }
        }
        val clientWithFailingDao = PaperTradingExchangeClient(
            apiService = fakeApiService,
            initialPhpBalance = 50000.0,
            initialUsdtBalance = 1000.0,
            tradingDao = failingDao
        )
        val result = clientWithFailingDao.submitOrder(
            symbol = "BTCPHP",
            side = "BUY",
            type = "MARKET",
            quantity = 0.001,
            price = 5000000.0
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to restore paper balances") == true)
    }

    @Test
    fun paperWallet_persistsAndRestoresAcrossRestarts() = runTest {
        // Execute BUY on client 1
        val buyResult = client.submitOrder(
            symbol = "BTCPHP",
            side = "BUY",
            type = "MARKET",
            quantity = 0.001,
            price = 5000000.0,
            quoteOrderAmount = 5000.0
        )
        assertTrue(buyResult.isSuccess)

        // Verify DAO contains persisted PHP balance
        val savedBalances = fakeDao.getAllPaperBalances()
        val savedPhp = savedBalances.first { it.asset == "PHP" }.freeAmount
        assertEquals(44995.0, savedPhp, 0.01)

        // Instantiate a NEW client (simulating app restart) using the SAME DAO
        val restartedClient = PaperTradingExchangeClient(
            apiService = fakeApiService,
            initialPhpBalance = 50000.0, // Default constructor values
            initialUsdtBalance = 1000.0,
            tradingDao = fakeDao
        )

        // Verify that restarted client restores the EXACT persisted balance (44995.0 PHP) instead of resetting to 50000.0 PHP
        val restoredBalances = restartedClient.getBalances().getOrThrow()
        val restoredPhp = restoredBalances.first { it.asset == "PHP" }.free.toDouble()
        assertEquals(44995.0, restoredPhp, 0.01)
    }

    @Test
    fun closePosition_succeedsAfterAppRestartWithPersistedBalances() = runTest {
        // 1. Execute BUY on client
        val buyResult = client.submitOrder(
            symbol = "BTCPHP",
            side = "BUY",
            type = "MARKET",
            quantity = 0.001,
            price = 5000000.0,
            quoteOrderAmount = 5000.0
        )
        assertTrue(buyResult.isSuccess)

        // 2. Persist matching open position to DAO
        fakeDao.insertOpenPosition(
            OpenPositionEntity(
                id = "pos_101",
                symbol = "BTCPHP",
                side = "BUY",
                entryPrice = 5000000.0,
                currentPrice = 5000000.0,
                quantity = 0.001,
                amountInvested = 5000.0,
                unrealizedPnl = 0.0,
                pnlPercentage = 0.0,
                entryTimestamp = System.currentTimeMillis()
            )
        )

        // 3. Instantiate NEW client (simulating app restart) using same DAO
        val restartedClient = PaperTradingExchangeClient(
            apiService = fakeApiService,
            initialPhpBalance = 50000.0,
            initialUsdtBalance = 1000.0,
            tradingDao = fakeDao
        )

        // 4. Close position (SELL) on restarted client
        val sellResult = restartedClient.submitOrder(
            symbol = "BTCPHP",
            side = "SELL",
            type = "MARKET",
            quantity = 0.001,
            price = 5000000.0
        )

        assertTrue("Sell order should succeed after app restart", sellResult.isSuccess)
        val sellTrade = sellResult.getOrThrow()
        assertEquals("FILLED", sellTrade.status)
        assertEquals(0.001, sellTrade.quantity, 0.000001)
    }

    @Test
    fun closePosition_failsSafelyWhenWalletLacksAsset() = runTest {
        // Attempt SELL without any prior BUY or credited BTC balance
        val sellResult = client.submitOrder(
            symbol = "BTCPHP",
            side = "SELL",
            type = "MARKET",
            quantity = 0.001,
            price = 5000000.0
        )

        assertTrue(sellResult.isFailure)
        val ex = sellResult.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
        assertTrue(ex?.message?.contains("Insufficient paper asset") == true)
    }
}
