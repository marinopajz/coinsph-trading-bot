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
import com.example.data.repository.TradingBotRepositoryImpl
import com.example.domain.exchange.PaperTradingExchangeClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TradingBotRepositoryLifecycleTest {

    private lateinit var fakeApiService: CountingFakeApiService
    private lateinit var paperClient: PaperTradingExchangeClient
    private lateinit var repository: TradingBotRepositoryImpl

    private class CountingFakeApiService : CoinsPhApiService {
        val tickerCallCount = AtomicInteger(0)

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

        override suspend fun get24HrTicker(symbol: String?): Response<Coins24HrTicker> {
            tickerCallCount.incrementAndGet()
            return Response.success(
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
            apiKey: String, symbol: String, side: String, type: String,
            quantity: String?, quoteOrderAmount: String?, price: String?,
            timestamp: Long, signature: String
        ): Response<Unit> = Response.success(Unit)

        override suspend fun createOrder(
            apiKey: String, symbol: String, side: String, type: String,
            quantity: String?, quoteOrderAmount: String?, price: String?,
            timestamp: Long, signature: String
        ): Response<CoinsOrderResponse> = Response.error(400, okhttp3.ResponseBody.create(null, ""))

        override suspend fun getOpenOrders(
            apiKey: String, symbol: String, timestamp: Long, signature: String
        ): Response<List<CoinsOrderResponse>> = Response.success(emptyList())

        override suspend fun cancelOrder(
            apiKey: String, symbol: String, orderId: Long, timestamp: Long, signature: String
        ): Response<CoinsOrderResponse> = Response.error(400, okhttp3.ResponseBody.create(null, ""))

        override suspend fun getAccountInfo(
            apiKey: String, timestamp: Long, signature: String
        ): Response<CoinsAccountInfo> = Response.success(CoinsAccountInfo(true, true, true, emptyList()))
    }

    @Before
    fun setUp() {
        fakeApiService = CountingFakeApiService()
        paperClient = PaperTradingExchangeClient(
            apiService = fakeApiService,
            initialPhpBalance = 50000.0,
            initialUsdtBalance = 1000.0
        )
    }

    @Test
    fun repositoryLifecycle_stopsPollingOnBackgroundAndResumesOnForeground() = runTest {
        repository = TradingBotRepositoryImpl(
            paperExchangeClient = paperClient,
            liveExchangeClient = paperClient,
            coroutineScope = backgroundScope
        )

        testScheduler.runCurrent()

        // 1. Initial ticker polling on startup
        advanceTimeBy(3500)
        val countAfterStart = fakeApiService.tickerCallCount.get()
        assertTrue("Ticker polling should execute when active", countAfterStart > 0)

        // 2. Simulate app going to background
        repository.onAppBackgrounded()
        val countOnBackground = fakeApiService.tickerCallCount.get()

        // Advance time while in background
        advanceTimeBy(10000)
        assertEquals("Ticker polling should NOT execute while backgrounded", countOnBackground, fakeApiService.tickerCallCount.get())

        // 3. Simulate app returning to foreground
        repository.onAppForegrounded()
        val countBeforeResume = fakeApiService.tickerCallCount.get()
        testScheduler.runCurrent()
        advanceTimeBy(3500)
        val countAfterResume = fakeApiService.tickerCallCount.get()
        println("DEBUG: countAfterStart=$countAfterStart, countOnBackground=$countOnBackground, countBeforeResume=$countBeforeResume, countAfterResume=$countAfterResume")
        assertTrue("Ticker polling should resume after foregrounding. countOnBackground=$countOnBackground, countAfterResume=$countAfterResume", countAfterResume > countOnBackground)
    }

    @Test
    fun closePosition_samePriceTradeResultInFeeLossAndZeroWinRate() = runTest {
        val fakeDao = FakeTradingDao()
        fakeDao.savePaperBalance(PaperBalanceEntity("PHP", 50000.0))
        fakeDao.savePaperBalance(PaperBalanceEntity("BTC", 1.0))

        val position = com.example.data.model.OpenPosition(
            id = "pos1",
            symbol = "BTCPHP",
            side = "BUY",
            entryPrice = 5000000.0,
            currentPrice = 5000000.0,
            quantity = 0.001,
            amountInvested = 5005.0, // 5000 + 5 fee
            unrealizedPnl = 0.0,
            pnlPercentage = 0.0,
            entryTimestamp = System.currentTimeMillis()
        )
        fakeDao.insertOpenPosition(OpenPositionEntity.fromDomain(position))

        val client = PaperTradingExchangeClient(
            apiService = fakeApiService,
            initialPhpBalance = 50000.0,
            initialUsdtBalance = 1000.0,
            tradingDao = fakeDao
        )
        val repo = TradingBotRepositoryImpl(
            paperExchangeClient = client,
            liveExchangeClient = client,
            tradingDao = fakeDao,
            coroutineScope = backgroundScope
        )

        testScheduler.runCurrent()

        // Close position at same price (5,000,000 PHP)
        // Gross proceeds = 5000 PHP, Sell Fee = 5 PHP -> Net proceeds = 4995 PHP
        // Realized PnL = 4995 - 5005 = -10.0 PHP (loss due to fees)
        val closeRes = repo.closePosition("pos1")
        assertTrue("Close position should succeed", closeRes.isSuccess)

        val portfolio = repo.portfolioFlow.first()
        assertEquals(-10.0, portfolio.totalRealizedPnl, 0.01)
        assertEquals(1, portfolio.totalTrades)
        assertEquals(0.0, portfolio.winRate, 0.01)
    }

    @Test
    fun closePosition_losingTradeUpdatesWinRateCorrectly() = runTest {
        val fakeDao = FakeTradingDao()
        fakeDao.savePaperBalance(PaperBalanceEntity("PHP", 50000.0))
        fakeDao.savePaperBalance(PaperBalanceEntity("BTC", 1.0))

        // Seed 1 win out of 1 trade (100% win rate) BEFORE initializing repo
        fakeDao.savePortfolioSnapshot(
            PortfolioSnapshotEntity(
                id = 1,
                quoteBalance = 50000.0,
                baseBalance = 0.0,
                quoteCurrency = "PHP",
                allocatedCapital = 0.0,
                totalRealizedPnl = 100.0,
                unrealizedPnl = 0.0,
                winRate = 100.0,
                totalTrades = 1,
                dailyPnl = 100.0
            )
        )

        val position = com.example.data.model.OpenPosition(
            id = "pos2",
            symbol = "BTCPHP",
            side = "BUY",
            entryPrice = 5000000.0,
            currentPrice = 5000000.0,
            quantity = 0.001,
            amountInvested = 5005.0,
            unrealizedPnl = 0.0,
            pnlPercentage = 0.0,
            entryTimestamp = System.currentTimeMillis()
        )
        fakeDao.insertOpenPosition(OpenPositionEntity.fromDomain(position))

        val client = PaperTradingExchangeClient(
            apiService = fakeApiService,
            initialPhpBalance = 50000.0,
            initialUsdtBalance = 1000.0,
            tradingDao = fakeDao
        )
        val repo = TradingBotRepositoryImpl(
            paperExchangeClient = client,
            liveExchangeClient = client,
            tradingDao = fakeDao,
            coroutineScope = backgroundScope
        )

        testScheduler.runCurrent()

        // Close position at same price (5,000,000 PHP) -> Loss of 10 PHP due to fees
        val closeRes = repo.closePosition("pos2")
        assertTrue("Close position should succeed", closeRes.isSuccess)

        val portfolio = repo.portfolioFlow.first()
        assertEquals(90.0, portfolio.totalRealizedPnl, 0.01) // 100.0 - 10.0 = 90.0
        assertEquals(90.0, portfolio.dailyPnl, 0.01) // 100.0 - 10.0 = 90.0 daily PnL
        assertEquals(2, portfolio.totalTrades)
        assertEquals(1, portfolio.winningTrades)
        assertEquals(50.0, portfolio.winRate, 0.01) // 1 win out of 2 trades = 50%
    }
}