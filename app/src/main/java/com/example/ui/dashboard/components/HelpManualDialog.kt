package com.example.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.CoinsEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@Composable
fun HelpManualDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf("📌 Gabay") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141D2A)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Title
                Text(
                    text = "📘 OPISYAL NA TAGALOG MANUAL & PLAYBOOK",
                    color = CoinsEmerald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "100% Offline Guide — Strategies, Boosters & Seafarer Setup",
                    color = TextMuted,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Filter Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("📌 Gabay", "🤖 7 Strategies", "⚡ 11 Boosters", "🎮 3 Playbooks", "🚨 FAQ & Live").forEach { tab ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) CoinsEmerald.copy(alpha = 0.2f) else Color(0xFF1B2230),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("help_tab_$tab")
                        ) {
                            Text(
                                text = tab,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CoinsEmerald else TextMuted,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content Cards
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        "📌 Gabay" -> {
                            item {
                                HelpSectionCard(
                                    title = "💡 Paper Mode vs. Live Mode",
                                    content = "• PAPER TRADING MODE: Gamitin ito para mag-ensayo nang LIGTAS! Nagbibigay ang app ng ₱50,000 PHP (o $1,000 USDT) na LIBRENG virtual money. Ginagamit nito ang tunay at live na presyo mula sa Coins.ph o Binance nang walang panganib sa tunay mong pera.\n\n• LIVE TRADING MODE: Kumokonekta nang direkta sa iyong tunay na Coins.ph o Binance account gamit ang HMAC-SHA256 API Key. Ang bawat order ay tunay na binibili/ibinibenta sa spot market.\n\n• SPOT TRADING SAFETY: Spot trading lamang ang app — WALANG LEVERAGE o LIQUIDATION. Ang binibili mo ay tunay na crypto asset."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "📱 Ang 4 na Main Tabs ng App",
                                    content = "1. 📈 MARKET TAB: Dito makikita ang Exchange Switcher (Coins.ph / Binance), interactive Candlestick Chart, Order Book Depth, Live Watchlist, at ang button na '⚡ SCAN BEST COIN'.\n\n2. 🤖 BOT & STRATEGY TAB: Ang control center ng bot! Dito pipiliin ang isa sa 7 Strategy Engines, ang 11 Profit Boosters, at ang mga button para sa '🧪 BACKTEST CURRENT' at '⚡ AUTO-FIND BEST'.\n\n3. 💼 PORTFOLIO TAB: Makikita ang iyong PnL, Win Rate, Open Positions, visual Equity Curve Chart, at ang mga button na '🔋 UNLOCK 24/7 CABIN BACKGROUND MODE' at '🔄 RESET PAPER PORTFOLIO'.\n\n4. 📜 HISTORY TAB: Audit log ng lahat ng buy/sell transactions na may timestamp at dahilan ng order. May button din para mag-export ng CSV file."
                                )
                            }
                        }

                        "🤖 7 Strategies" -> {
                            item {
                                HelpSectionCard(
                                    title = "1. 💵 DCA (Dollar-Cost Average)",
                                    content = "• PAANO GUMAGANA: Bumibili sa paunang posisyon at nagdadagdag ng panibagong order sa bawat pagbagsak ng presyo batay sa 'Dip Trigger %' (halimbawa: -2.5%). Sa bawat dip, bumababa ang iyong average entry price.\n\n• KAILAN GAGAMITIN: Pinakamaganda para sa pangmatagalan o kapag ang merkado ay unti-unting bumababa ngunit tiyak na babangon."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "2. 📊 RSI Swing Trader",
                                    content = "• PAANO GUMAGANA: Sinusukat ang Relative Strength Index (14 period). Bumibili kapag Oversold (RSI <= 30) at nagbebenta kapag Overbought (RSI >= 70) o kapag naabot ang Take Profit %.\n\n• KAILAN GAGAMITIN: Epektibo sa mga nakikipag-swing trade sa mabilis na pagtaas at pagbaba ng presyo."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "3. 🕸️ Grid Trading",
                                    content = "• PAANO GUMAGANA: Gumagawa ng automated buy-low/sell-high ladder grid sa pagitan ng Lower Price at Upper Price limit. Nag-iipon ng maliliit na kita sa bawat pagtalbog ng presyo.\n\n• KAILAN GAGAMITIN: Pinakamahusay kapag ang presyo ay nagse-sideways sa isang malinaw na price channel."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "4. 🎯 Bollinger Mean-Reversion",
                                    content = "• PAANO GUMAGANA: Binabantayan ang 20-period SMA at standard deviation bands (2.0x). Bumibili kapag tumama sa Lower Band at nagbebenta kapag bumalik sa gitna (SMA20) o Upper Band.\n\n• KAILAN GAGAMITIN: Pinakamaganda sa oversold mean-reversion bounces."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "5. 🐢 Turtle Breakout",
                                    content = "• PAANO GUMAGANA: Batay sa sikat na Richard Dennis Turtle Trading rules. Bumibili kapag binasag ang 20-Candle High na may kasamang >1.15x Volume Surge.\n\n• KAILAN GAGAMITIN: Epektibo sa malalakas na breakout at bullish momentum surges."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "6. 🐋 Whale VWAP + Wick Reversal",
                                    content = "• PAANO GUMAGANA: Institutional Smart Money strategy! Bumibili kapag ang presyo ay nasa ibaba ng Volume-Weighted Average Price (VWAP <= 0.992) AT may Bullish Pinbar / Hammer rejection wick.\n\n• KAILAN GAGAMITIN: Pinakamaganda sa paghuli ng dip ng mga malalaking balyena (whales)."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "7. ⚡ AI God Mode (Autonomous Quant Ensemble)",
                                    content = "• PAANO GUMAGANA: Ang pinakamalakas na strategy! Kusa nitong binabasa ang Market Regime (BULL TREND, PULLBACK, RANGING) at pinagsasama ang boto (Votes >= 2/5) ng DCA, RSI, Bollinger, Turtle, at VWAP.\n\n• KAILAN GAGAMITIN: Recommended para sa 24/7 fully automated trading sa anumang uri ng merkado!"
                                )
                            }
                        }

                        "⚡ 11 Boosters" -> {
                            item {
                                HelpSectionCard(
                                    title = "🎯 ENTRY & SIZING BOOSTERS",
                                    content = "1. Smart Entry: Naghihintay ng pullback (RSI <= 52) bago pumasok para maiwasan ang pagbili sa tuktok.\n2. Green Candle Confirm: Sisiguraduhing may green reversal candle muna bago bumili sa dip.\n3. Half-Kelly Dynamic Sizing: Sinasukat ang lakas ng signal at win rate para ayusin ang order size sa pagitan ng 0.6x hanggang 1.5x.\n4. DCA Tranche Multiplier: Pinalalaki ang susunod na bili sa dip (1.0x, 1.25x, o 1.5x) para mas mabilis bumaba ang break-even point."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "💰 PROFIT LOCKING BOOSTERS",
                                    content = "5. Break-Even Lock: Kapag umakyat ang kita sa +1.5%, ililipat ang floor sa +0.3% para protektado ang puhunan at trading fees.\n6. 2-Stage Partial TP: Nagbebenta ng 50% sa +1.8% para mag-lock in ng kita, habang ang natitirang 50% (moonbag) ay pinatatakbo nang may break-even lock.\n7. ATR Dynamic Targets (Chandelier TP/SL): Ginagamit ang 14-period Average True Range para kusang i-adjust ang TP at SL batay sa galaw ng presyo."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "🛡️ RISK, WHALE & SEAFARER GUARDS",
                                    content = "8. Never Sell at a Loss (Spot HODL): Pipigilan ang stop-loss selling kapag bumaba ang presyo; hahawakan ang asset hanggang sa kumita.\n9. Bid-Ask Spread Guard: Pipigilan ang pagbili kapag ang bid-ask spread ay higit sa 0.60% para maiwasan ang lugi sa illiquid markets.\n10. Crash Trend Filter: Pipigilan ang pagbili kapag matarik ang pagbagsak ng MA7 kumpara sa MA20.\n11. Order Book Whale Wall Filter: Pipigilan ang pagbili kapag mahina ang buy wall (Bids < 45%).\n12. ⚓ Seafarer Satellite / Low-Data Mode: Nagtitipid ng 70%+ satellite data sa pamamagitan ng mas madalang na polling at 60s recvWindow timezone sync."
                                )
                            }
                        }

                        "🎮 3 Playbooks" -> {
                            item {
                                HelpSectionCard(
                                    title = "🎮 Playbook 1: 'No-Stress Auto-Pilot' (Para sa Baguhan)",
                                    content = "1. Pumunta sa Portfolio Tab -> I-tap ang '🔄 RESET PAPER PORTFOLIO'.\n2. Pumunta sa Market Tab -> I-tap ang '⚡ SCAN BEST COIN'.\n3. Pumunta sa Bot Tab -> Pipiliin ng app ang '⚡ AI God Mode'.\n4. I-set ang Order Size sa 1000, TP sa 2.5%, SL sa 3.0%.\n5. I-enable sa Boosters: Green Candle, Half-Kelly, Break-Even Lock, 2-Stage Partial TP, ATR Targets, at Spread Guard.\n6. Pindutin ang '▶ START BOT'. Pabayaan ang bot na kumita nang kusa!"
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "🎮 Playbook 2: 'Gold & Forex Safe Haven' (Binance Mode)",
                                    content = "1. Sa Header, ilipat ang Platform sa 'BINANCE'.\n2. I-tap ang Market Pair dropdown -> Piliin ang 'FX' tab -> Piliin ang 'PAXGUSDT' (Gold) o 'EURUSDT'.\n3. Sa Chart, piliin ang '15m' timeframe.\n4. Sa Bot Tab, i-tap ang '⚡ AUTO-FIND BEST'.\n5. I-enable ang 'Never Sell at a Loss (Spot HODL)' + 'Break-Even Lock'.\n6. Pindutin ang '▶ START BOT' para sa ligtas na forex/gold yield."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "🎮 Playbook 3: 'Seafarer Cabin 24/7 Mode' (Para sa Barko / Biyahe)",
                                    content = "1. Pumunta sa Portfolio Tab -> I-tap ang '🔋 UNLOCK 24/7 CABIN BACKGROUND MODE' para i-whitelist ang app sa battery settings.\n2. Sa Bot Tab -> I-expand ang Boosters -> I-enable ang '⚓ Seafarer Satellite / Low-Data Mode'.\n3. Pindutin ang '▶ START BOT'.\n4. Ligtas nang i-off ang screen ng telepono o ilagay sa bulsa sa cabin. Ang CPU WakeLock at 60s satellite sync ay magpapatakbo sa bot nang walang hinto!"
                                )
                            }
                        }

                        "🚨 FAQ & Live" -> {
                            item {
                                HelpSectionCard(
                                    title = "❓ Ano ang mga Instant Action Buttons?",
                                    content = "• ⚡ INSTANT BUY NOW: Agad na bumibili sa market price nang hindi naghihintay ng strategy trigger.\n• 🔥 SELL ALL POSITIONS: Agad na nagbebenta ng lahat ng hawak na posisyon para maging cash.\n• 🚫 EMERGENCY HALT ALL: Agad na nagpapatigil sa lahat ng automatic trading operations."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "❓ Bakit lalabas ang 'BLOCKED: Daily loss limit reached'?",
                                    content = "Ito ay proteksyon ng Risk Manager kapag naabot ang 'Daily Loss Stop'. Para ma-clear ito sa Paper Mode, i-tap ang '🔄 RESET PAPER PORTFOLIO' sa Portfolio tab, o taasan ang 'Daily Loss Stop' sa settings."
                                )
                            }

                            item {
                                HelpSectionCard(
                                    title = "🔒 Ligtas ba ang aking API Keys?",
                                    content = "OO! Ang API Keys ay nakaimbak lamang sa secure SharedPreferences ng iyong telepono. Kapag gumagawa ng API Key sa Coins.ph o Binance, i-enable LAMANG ang 'Read' at 'Spot Trading'. HUWAG KAILANMAN i-enable ang 'Withdrawal' permission!"
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CoinsEmerald, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("btn_close_help_manual")
                ) {
                    Text("ISARA ANG MANUAL (CLOSE)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HelpSectionCard(title: String, content: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2230), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = title,
                color = CoinsEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = content,
                color = TextPrimary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}
