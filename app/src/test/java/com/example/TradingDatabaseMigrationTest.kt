package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TradingDatabase
import com.example.data.local.entity.BotSettingsEntity
import com.example.data.local.entity.PaperBalanceEntity
import com.example.data.local.entity.PortfolioSnapshotEntity
import com.example.data.local.entity.TradeLogEntity
import com.example.data.model.BotSettings
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.StrategyType
import com.example.data.model.TradeLog
import com.example.data.model.TradingMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TradingDatabaseMigrationTest {

    private lateinit var database: TradingDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, TradingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun databaseOperations_preserveDataAndEntities() = runTest {
        val dao = database.tradingDao()

        // 1. Save and verify portfolio snapshot
        val portfolio = PortfolioSnapshot(quoteBalance = 45000.0, baseBalance = 0.001)
        dao.savePortfolioSnapshot(PortfolioSnapshotEntity.fromDomain(portfolio))

        val loadedPortfolio = dao.getPortfolioSnapshot()?.toDomain()
        assertNotNull(loadedPortfolio)
        assertEquals(45000.0, loadedPortfolio!!.quoteBalance, 0.01)

        // 2. Save and verify bot settings
        val settings = BotSettings(symbol = "BTCUSDT", tradingMode = TradingMode.PAPER)
        dao.saveBotSettings(BotSettingsEntity.fromDomain(settings))

        val loadedSettings = dao.getBotSettings()?.toDomain()
        assertNotNull(loadedSettings)
        assertEquals("BTCUSDT", loadedSettings!!.symbol)

        // 3. Save and verify paper balances
        val balances = listOf(
            PaperBalanceEntity("PHP", 44995.0),
            PaperBalanceEntity("BTC", 0.001)
        )
        dao.savePaperBalances(balances)

        val loadedBalances = dao.getAllPaperBalances()
        assertEquals(2, loadedBalances.size)
        assertEquals(44995.0, loadedBalances.first { it.asset == "PHP" }.freeAmount, 0.01)

        // 4. Save and verify trade logs
        val log = TradeLog(
            id = "test_log_1",
            type = "MARKET",
            symbol = "BTCPHP",
            side = "BUY",
            price = 5000000.0,
            quantity = 0.001,
            amount = 5000.0,
            fee = 5.0,
            feeCurrency = "PHP",
            status = "FILLED"
        )
        dao.insertTradeLog(TradeLogEntity.fromDomain(log))

        val loadedLogs = dao.getAllTradeLogs().map { it.toDomain() }
        assertEquals(1, loadedLogs.size)
        assertEquals("test_log_1", loadedLogs[0].id)
    }
}
