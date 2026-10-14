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

        override suspend fun getKlines(
            symbol: String,
            interval: String,
            limit: Int
        ): Response<List<List<Any>>> {
            val sampleKlines = (0 until limit.coerceAtMost(20)).map { i ->
                listOf(1700000000000L + i * 300000L, "5000000.0", "5100000.0", "4950000.0", "5050000.0", "12.5", 1700000300000L + i * 300000L)
            }
            return Response.success(sampleKlines)
        }

        override suspend fun getOrderBook(
            symbol: String,
            limit: Int
        ): Response<com.example.data.model.CoinsOrderBookResponse> {
            return Response.success(com.example.data.model.CoinsOrderBookResponse())
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

    @Test
    fun switchSymbol_clearsPriceHistoryAndSyncsPortfolioBalances() = runTest {
        val fakeDao = FakeTradingDao()
        fakeDao.savePaperBalance(PaperBalanceEntity("PHP", 50000.0))
        fakeDao.savePaperBalance(PaperBalanceEntity("USDT", 1000.0))

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

        // Switch symbol from BTCPHP to BTCUSDT
        val switchRes = repo.switchSymbol("BTCUSDT")
        assertTrue("Switch symbol should succeed", switchRes.isSuccess)

        val priceHistory = repo.priceHistoryFlow.first()
        assertTrue("Price history should be cleared on symbol switch", priceHistory.isEmpty())

        val settings = repo.botSettingsFlow.first()
        assertEquals("BTCUSDT", settings.symbol)
        assertEquals("USDT", settings.quoteCurrency)
        assertEquals("BTC", settings.baseCurrency)

        val portfolio = repo.portfolioFlow.first()
        assertEquals(1000.0, portfolio.quoteBalance, 0.01)
        assertEquals("USDT", portfolio.quoteCurrency)
    }

    @Test
    fun closePosition_updatesPortfolioQuoteBalanceAccurately() = runTest {
        val fakeDao = FakeTradingDao()
        fakeDao.savePaperBalance(PaperBalanceEntity("PHP", 45000.0))
        fakeDao.savePaperBalance(PaperBalanceEntity("BTC", 0.001))

        val position = com.example.data.model.OpenPosition(
            id = "pos_close_bal",
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
            initialPhpBalance = 45000.0,
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

        val closeRes = repo.closePosition("pos_close_bal")
        assertTrue("Close position should succeed", closeRes.isSuccess)

        // After close, paper client balance becomes 45000 + 4995 = 49995 PHP
        val portfolio = repo.portfolioFlow.first()
        assertEquals(49995.0, portfolio.quoteBalance, 0.01)
    }

    @Test
    fun switchSymbol_withUsdcPair_detectsUsdcQuoteCurrency() = runTest {
        val fakeDao = FakeTradingDao()
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

        val switchRes = repo.switchSymbol("BTCUSDC")
        assertTrue("Switch to BTCUSDC should succeed", switchRes.isSuccess)

        val settings = repo.botSettingsFlow.first()
        assertEquals("BTCUSDC", settings.symbol)
        assertEquals("USDC", settings.quoteCurrency)
        assertEquals("BTC", settings.baseCurrency)
    }

    @Test
    fun setTradingMode_separatesPaperAndLivePnlAndCounters() = runTest {
        val fakeDao = FakeTradingDao()
        fakeDao.savePaperBalance(PaperBalanceEntity("PHP", 50000.0))

        val paperLog = TradeLogEntity(
            id = "log_paper_1",
            timestamp = System.currentTimeMillis(),
            type = "MARKET",
            symbol = "BTCPHP",
            side = "SELL",
            price = 5000000.0,
            quantity = 0.001,
            amount = 5000.0,
            fee = 5.0,
            feeCurrency = "PHP",
            realizedPnl = -100.0,
            status = "FILLED",
            orderId = "ord_p1",
            message = "Paper sell",
            tradingMode = com.example.data.model.TradingMode.PAPER
        )
        fakeDao.insertTradeLog(paperLog)

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

        val paperPortfolio = repo.portfolioFlow.first()
        assertEquals(-100.0, paperPortfolio.totalRealizedPnl, 0.01)
        assertEquals(1, paperPortfolio.totalTrades)

        val modeRes = repo.setTradingMode(com.example.data.model.TradingMode.LIVE)
        assertTrue("Set trading mode to LIVE should succeed", modeRes.isSuccess)

        val livePortfolio = repo.portfolioFlow.first()
        assertEquals("Live mode portfolio should reset realized PnL from paper logs", 0.0, livePortfolio.totalRealizedPnl, 0.01)
        assertEquals("Live mode portfolio should have 0 total trades", 0, livePortfolio.totalTrades)
    }

    @Test
    fun setChartInterval_updatesIntervalFlowAndFetchesKlines() = runTest {
        val fakeDao = FakeTradingDao()
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

        repo.setChartInterval(com.example.data.model.ChartInterval.FIFTEEN_MIN)
        testScheduler.runCurrent()

        val selectedInterval = repo.selectedIntervalFlow.first()
        assertEquals(com.example.data.model.ChartInterval.FIFTEEN_MIN, selectedInterval)

        val candles = repo.candlesFlow.first()
        assertTrue("Candles flow should contain parsed candlestick data", candles.isNotEmpty())
    }

    @Test
    fun switchExchangePlatform_switchesToBinanceAndUpdatesDefaultSymbol() = runTest {
        val fakeDao = FakeTradingDao()
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

        assertEquals("BTCPHP", repo.botSettingsFlow.first().symbol)
        assertEquals(com.example.data.model.ExchangePlatform.COINS_PH, repo.botSettingsFlow.first().exchangePlatform)

        val switchRes = repo.switchExchangePlatform(com.example.data.model.ExchangePlatform.BINANCE)
        testScheduler.runCurrent()

        assertTrue(switchRes.isSuccess)
        val updatedSettings = repo.botSettingsFlow.first()
        assertEquals(com.example.data.model.ExchangePlatform.BINANCE, updatedSettings.exchangePlatform)
        assertEquals("BTCUSDT", updatedSettings.symbol)
        assertEquals("USDT", updatedSettings.quoteCurrency)
    }

    @Test
    fun manualBuyNow_and_closeAllPositions_executeAccurately() = runTest {
        val fakeDao = FakeTradingDao()
        fakeDao.savePaperBalance(PaperBalanceEntity("PHP", 50000.0))

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

        // 1. Manual Buy
        val buyRes = repo.manualBuyNow()
        assertTrue("manualBuyNow should succeed", buyRes.isSuccess)

        val positions = repo.openPositionsFlow.first()
        assertEquals(1, positions.size)
        assertEquals("BTCPHP", positions.first().symbol)

        // 2. Close All Positions
        val closeAllRes = repo.closeAllPositions()
        assertTrue("closeAllPositions should succeed", closeAllRes.isSuccess)

        val updatedPositions = repo.openPositionsFlow.first()
        assertTrue("Open positions should be empty after closeAllPositions", updatedPositions.isEmpty())
    }

    @Test
    fun runQuickBacktest_executesAndReturnsValidBacktestResult() = runTest {
        val fakeDao = FakeTradingDao()
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

        val backtestRes = repo.runQuickBacktest()
        assertTrue("runQuickBacktest should succeed", backtestRes.isSuccess)

        val result = backtestRes.getOrThrow()
        assertEquals("BTCPHP", result.symbol)
        assertTrue(result.candlesTested > 0)
        assertTrue(result.summary.contains("candles"))
    }

    @Test
    fun autoOptimizeStrategy_evaluatesCandidatesAndReturnsRecommendedSettings() = runTest {
        val fakeDao = FakeTradingDao()
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

        val optimizeRes = repo.autoOptimizeStrategy()
        assertTrue("autoOptimizeStrategy should succeed", optimizeRes.isSuccess)

        val result = optimizeRes.getOrThrow()
        assertEquals("BTCPHP", result.symbol)
        assertTrue("candlesTested should be greater than 0", result.candlesTested > 0)
        org.junit.Assert.assertNotNull("recommendedSettings should be populated", result.recommendedSettings)
        assertTrue(result.summary.contains("Optimal"))
    }
}