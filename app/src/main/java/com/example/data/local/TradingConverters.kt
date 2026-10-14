package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.StrategyType
import com.example.data.model.TradingMode

class TradingConverters {
    @TypeConverter
    fun fromTradingMode(mode: TradingMode): String {
        return mode.name
    }

    @TypeConverter
    fun toTradingMode(name: String): TradingMode {
        return try {
            TradingMode.valueOf(name)
        } catch (e: Exception) {
            TradingMode.PAPER
        }
    }

    @TypeConverter
    fun fromStrategyType(type: StrategyType): String {
        return type.name
    }

    @TypeConverter
    fun toStrategyType(name: String): StrategyType {
        return try {
            StrategyType.valueOf(name)
        } catch (e: Exception) {
            StrategyType.DCA
        }
    }
}
