package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.TradingDao
import com.example.data.local.entity.BotSettingsEntity
import com.example.data.local.entity.OpenPositionEntity
import com.example.data.local.entity.PaperBalanceEntity
import com.example.data.local.entity.PortfolioSnapshotEntity
import com.example.data.local.entity.TradeLogEntity

@Database(
    entities = [
        PortfolioSnapshotEntity::class,
        BotSettingsEntity::class,
        TradeLogEntity::class,
        OpenPositionEntity::class,
        PaperBalanceEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(TradingConverters::class)
abstract class TradingDatabase : RoomDatabase() {
    abstract fun tradingDao(): TradingDao

    companion object {
        @Volatile
        private var INSTANCE: TradingDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `paper_balances` (`asset` TEXT NOT NULL, `freeAmount` REAL NOT NULL, PRIMARY KEY(`asset`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `portfolio_snapshot` ADD COLUMN `winningTrades` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `portfolio_snapshot` SET `winningTrades` = CAST(ROUND((winRate / 100.0) * totalTrades) AS INTEGER)")
            }
        }

        fun getDatabase(context: Context): TradingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TradingDatabase::class.java,
                    "trading_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
