package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BotSettingsEntity
import com.example.data.local.entity.OpenPositionEntity
import com.example.data.local.entity.PaperBalanceEntity
import com.example.data.local.entity.PortfolioSnapshotEntity
import com.example.data.local.entity.TradeLogEntity

@Dao
interface TradingDao {
    @Query("SELECT * FROM paper_balances")
    suspend fun getAllPaperBalances(): List<PaperBalanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePaperBalance(balance: PaperBalanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePaperBalances(balances: List<PaperBalanceEntity>)

    @Query("SELECT * FROM portfolio_snapshot WHERE id = 1")
    suspend fun getPortfolioSnapshot(): PortfolioSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePortfolioSnapshot(portfolio: PortfolioSnapshotEntity)

    @Query("SELECT * FROM bot_settings WHERE id = 1")
    suspend fun getBotSettings(): BotSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBotSettings(settings: BotSettingsEntity)

    @Query("SELECT * FROM trade_logs ORDER BY timestamp DESC")
    suspend fun getAllTradeLogs(): List<TradeLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTradeLog(tradeLog: TradeLogEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTradeLogs(tradeLogs: List<TradeLogEntity>)

    @Query("SELECT * FROM open_positions")
    suspend fun getAllOpenPositions(): List<OpenPositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpenPosition(position: OpenPositionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpenPositions(positions: List<OpenPositionEntity>)

    @Query("DELETE FROM open_positions WHERE id = :id")
    suspend fun deleteOpenPosition(id: String)
    
    @Query("DELETE FROM open_positions")
    suspend fun deleteAllOpenPositions()

    @Query("DELETE FROM trade_logs")
    suspend fun deleteAllTradeLogs()
}
