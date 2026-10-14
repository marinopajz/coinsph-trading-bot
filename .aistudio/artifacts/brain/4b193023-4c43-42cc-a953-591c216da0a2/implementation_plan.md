# Coins.ph AI Crypto Trading Bot — Dashboard & Engine Architecture Plan

A production-grade Android Jetpack Compose dashboard and autonomous cloud backend controller for crypto spot trading on Coins.ph. The system operates by default in **Paper Trading** mode (zero-risk simulation using live market prices) and supports authenticated **Live Trading** via an isolated, authorized backend with strict risk management, automated DCA/RSI/Grid strategies, and real-time Firestore synchronization.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following key decisions have been confirmed and incorporated into the design:

- **Trading Pair Support**: Primary default support for **both Philippine Peso (PHP) pairs** (e.g., `BTCPHP`, `ETHPHP`) and **Tether (USDT) pairs** (e.g., `BTCUSDT`, `ETHUSDT`), dynamically queried and verified against the official Coins.ph exchange information endpoint (`/openapi/v1/exchangeInfo`).
- **Default Strategy**: **Dollar-Cost Averaging (DCA)** with dip triggers configured as the primary strategy on launch, with seamless configuration switching to **RSI Swing** and **Grid Trading**.
- **Backend Architecture**: **Autonomous Cloud Engine (Cloud Run / Cloud Functions with Firestore state sync)** acting as the authoritative trading worker, ensuring trading strategies execute continuously regardless of whether the Android app is foregrounded, backgrounded, or offline.
- **Strict Safety Isolation**: Paper Trading and Live Trading are strictly isolated execution paths. Mobile clients never hold Coins.ph API secret keys and cannot sign live orders. Live trading requires explicit user opt-in, backend credential verification, balance verification, and strict risk limits (max capital, max daily loss, take-profit, stop-loss).

---

## 1. Overview & Core Concept

- **What It Does**: Serves as a full-featured mission control dashboard on Android for monitoring crypto spot markets on Coins.ph, configuring automated trading strategies (DCA, RSI Swing, Grid), running simulated paper trades, tracking real-time portfolio performance (PnL, win rate, balances), and supervising live order execution through a secure cloud backend.
- **Target Audience / Persona**: Retail and quantitative crypto traders in the Philippines and Southeast Asia seeking disciplined, algorithmic spot execution on Coins.ph without emotional trading pitfalls.
- **Key Value**: Hands-off, risk-controlled algorithmic execution backed by verified exchange market rules, zero-risk paper trading simulation, and zero client-side secret exposure.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Launch & Authentication Gate**: User authenticates via Google Sign-In (Credential Manager). App verifies session and connects to the user's Firestore state and live exchange market tickers.
2. **Dashboard Overview**: User views live ticker updates for PHP and USDT pairs (`BTCPHP`, `BTCUSDT`, `ETHPHP`, etc.), portfolio balance cards (Simulated vs Real), open positions, realized/unrealized PnL, win rate, and an interactive Compose Canvas price/trend chart.
3. **Strategy Configuration**: User selects active pair, trading strategy (DCA / RSI / Grid), order amount with quote currency awareness (PHP or USDT), dip trigger percentage, take-profit/stop-loss, max capital limit, and daily loss stop.
4. **Paper Trading Simulation**: Bot starts in Paper mode with authoritative status `RUNNING (PAPER)`. Tickers trigger simulated orders with recorded fees, slippage, and position tracking in Firestore `trade_logs`.
5. **Live Trading Verification & Enablement**: If the user toggles Live Trading, a strict confirmation modal requires acknowledging risks, verifying backend exchange connectivity (`/openapi/v1/time`, account check), and validating risk limits before live order routing is enabled.
6. **Activity & Audit Feed**: Real-time stream of order events, partial fills, completed executions, strategy evaluation decisions, and risk guard alerts.
7. **Emergency Stop & Position Management**: Instant one-tap Emergency Stop pauses new entries, with separate explicit confirmation options to cancel pending open orders or market-close positions.

### Visual Identity & Theme
- **Aesthetic Direction**: High-density financial terminal aesthetic with sleek Material 3 dark theming, high-contrast status pills, and precision typography.
- **Color Palette**:
  - Background: Deep Void (`#0B0E14`)
  - Surface & Cards: Slate Glass (`#141923`), elevated card strokes (`#232B3B`)
  - Primary Accent: Coins Emerald (`#00D182`) for profit, active states, and buy orders
  - Secondary Accent: Electric Cyan (`#00B8D9`) for paper trading badges and market indicators
  - Danger / Sell: Coral Red (`#FF4D4D`) for losses, stop-loss triggers, sell orders, and emergency stop
  - Warning: Amber Glow (`#FFAB00`) for paused states and risk-limit warnings
  - Text: High-contrast Off-White (`#F0F4F8`) and Muted Gray (`#8A94A6`)
- **Typography & Layout**: Clean monospace numeric styling for prices and PnL metrics, distinct display headers for market pairs, and compact 48dp+ interactive touch targets with Material ripples.
- **Interactive Feedback**: Instant optimistic UI transitions backed by authoritative Firestore state sync, spring animations on card expansion, and distinct badges indicating `[PAPER]` vs `[LIVE]`.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Cloud-First Autonomous Engine vs. Mobile Local Runner**
  - *Chosen Approach*: Cloud-native engine (Cloud Run / scheduled worker) updating Firestore; Android app acts as the Dashboard and Controller.
  - *Why*: Mobile OS lifecycle (Doze mode, process death, background limits) renders 24/7 autonomous trading on-device unreliable and dangerous. Cloud execution guarantees continuous strategy checks.
  - *Trade-off*: Requires backend deployment; we provide complete server-side code, Dockerfile, and setup instructions alongside a local testing mode for development verification.
- **Decision 2: Strict Isolation of Paper vs. Live Trading**
  - *Chosen Approach*: Distinct execution pipelines and separate Firestore document collections/subcollections (`paper_trades` vs `live_trades`). Paper trades never interact with exchange order endpoints.
  - *Why*: Prevents accidental real fund allocation and ensures regulatory and policy compliance.
- **Decision 3: Zero Client-Side Exchange Secrets**
  - *Chosen Approach*: Coins.ph API secret keys and HMAC-SHA256 request signing reside exclusively in the backend environment.
  - *Why*: Embedding exchange API secrets in an Android APK or client Firestore document creates severe security risks of fund loss.
- **Decision 4: Quote-Asset-Aware Decimal Precision**
  - *Chosen Approach*: Financial math implemented with `BigDecimal` / high-precision scaling conforming to Coins.ph `exchangeInfo` lot-size and price-filter filters.
  - *Why*: Prevents floating-point rounding errors and rejects invalid exchange order steps (e.g., minimum order size, tick sizes).

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Android Dashboard (Client)                      │
│                                                                        │
│   ┌─────────────────────┐   ┌───────────────────┐   ┌──────────────┐   │
│   │  Header & Bot Status│   │   Market Tickers  │   │ Price Chart  │   │
│   └─────────────────────┘   └───────────────────┘   └──────────────┘   │
│   ┌─────────────────────┐   ┌───────────────────┐   ┌──────────────┐   │
│   │ Quick Stats (PnL)   │   │ Strategy Controls │   │ Activity Log │   │
│   └─────────────────────┘   └───────────────────┘   └──────────────┘   │
│              ▲                        ▲                    ▲           │
│              │ StateFlow              │ User Commands      │ Live Flow │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │                 BotDashboardViewModel                        │     │
│   └──────────────────────────────────────────────────────────────┘     │
│              ▲                                                         │
│              │ (Google Auth / Repository)                              │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │                 TradingBotRepository                         │     │
│   └──────────────────────────────────────────────────────────────┘     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Firestore SDK (Zero-Trust Rules)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Cloud Firestore & Auth Database                   │
│                                                                        │
│   users/{userId}/bot_settings (Client: Read/Write validated)           │
│   users/{userId}/bot_status   (Client: Read-Only, Server: Authoritative)│
│   users/{userId}/trade_logs   (Client: Read-Only, Server: Authoritative)│
│   users/{userId}/portfolio    (Client: Read-Only, Server: Authoritative)│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Sync & Event Stream
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                Secure Autonomous Backend (Cloud Run / Node/Go)          │
│                                                                        │
│   ┌─────────────────────┐   ┌───────────────────┐   ┌──────────────┐   │
│   │ Credential Vault    │   │ Strategy Engine   │   │ Risk Guard   │   │
│   │ (Secret Manager)    │   │ (DCA, RSI, Grid)  │   │ (Max Loss/TP)│   │
│   └─────────────────────┘   └───────────────────┘   └──────────────┘   │
│   ┌─────────────────────┐   ┌───────────────────┐   ┌──────────────┐   │
│   │ HMAC-SHA256 Signer  │   │ Paper Simulator   │   │ FCM Notifier │   │
│   └─────────────────────┘   └───────────────────┘   └──────────────┘   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Signed HTTPS REST
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     Coins.ph Exchange API (Spot)                       │
│                     Base URL: https://api.pro.coins.ph                 │
│                                                                        │
│   GET  /openapi/v1/time           - Server time check                  │
│   GET  /openapi/v1/exchangeInfo   - Trading pair filters & precision   │
│   GET  /openapi/quote/v1/ticker/price - Spot ticker prices             │
│   POST /openapi/v1/order/test     - Test order validation              │
│   POST /openapi/v1/order          - Live order placement (Signed)      │
│   GET  /openapi/v1/openOrders     - Query open orders (Signed)         │
│   DELETE /openapi/v1/order        - Cancel order (Signed)              │
│   GET  /openapi/v1/account        - Balances & permissions (Signed)    │
└────────────────────────────────────────────────────────────────────────┘
```

### Core Data Models
1. **BotSettings**:
   - `symbol`: Trading pair (e.g., `"BTCPHP"`, `"BTCUSDT"`, `"ETHPHP"`)
   - `strategyType`: `"DCA"`, `"RSI_SWING"`, `"GRID"`
   - `orderAmount`: Double (in quote currency, PHP or USDT)
   - `dipTriggerPercentage`: Double (e.g. `2.5`%)
   - `takeProfitPercentage`: Double (e.g. `5.0`%)
   - `stopLossPercentage`: Double (e.g. `3.0`%)
   - `checkIntervalMinutes`: Int
   - `tradingMode`: `"PAPER"` (default) or `"LIVE"`
   - `maxCapital`: Double
   - `maxOrderValue`: Double
   - `maxDailyLoss`: Double
   - `lastUpdated`: Long
2. **TradeLog**:
   - `id`, `timestamp`, `symbol`, `side` (`"BUY"` / `"SELL"`), `price`, `quantity`, `amount`, `fee`, `feeCurrency`, `realizedPnl`, `status` (`"FILLED"`, `"PARTIALLY_FILLED"`, `"CANCELLED"`), `orderId`, `message`, `tradingMode`.
3. **BotRuntimeStatus**:
   - `state`: `"RUNNING"`, `"PAUSED"`, `"STARTING"`, `"STOPPING"`, `"ERROR"`, `"EMERGENCY_STOP"`
   - `mode`: `"PAPER"`, `"LIVE"`
   - `activeSymbol`: String
   - `lastCheckTime`: Long
   - `backendHealthy`: Boolean
   - `exchangeConnected`: Boolean
   - `lastErrorMessage`: String?
4. **PortfolioSnapshot**:
   - `availableQuoteBalance`: Double
   - `allocatedCapital`: Double
   - `totalRealizedPnl`: Double
   - `unrealizedPnl`: Double
   - `winRate`: Double
   - `totalTrades`: Int
   - `quoteCurrency`: String (`"PHP"` or `"USDT"`)
5. **MarketTicker**:
   - `symbol`, `price`, `priceChange24h`, `high24h`, `low24h`, `volume24h`, `timestamp`.

### Security & Firestore Access Rules
- **Authentication**: Google Sign-In via Credential Manager (`GetSignInWithGoogleOption`).
- **Authorization**:
  - `users/{userId}/bot_settings`: User read/write (strict schema validation).
  - `users/{userId}/bot_status`: User read-only (writable only by verified backend service account).
  - `users/{userId}/trade_logs`: User read-only (writable only by verified backend service account).
  - `users/{userId}/portfolio`: User read-only (writable only by verified backend service account).

---

## 5. Phased Implementation Plan

- **Phase 1: Project Setup & Dependencies**
  - Update `metadata.json` and `strings.xml` with app branding: **Coins.ph Trading Bot**.
  - Configure `app/build.gradle.kts` with Firebase Auth, Firestore, Navigation Compose, Coroutines, Moshi, Retrofit, and testing libraries.
  - Add Google Play policy and AndroidManifest permissions (`INTERNET`, `POST_NOTIFICATIONS`).
- **Phase 2: Exchange & Domain Architecture**
  - Define `ExchangeClient` interface abstraction (`CoinsPhExchangeClient`, `PaperTradingExchangeClient`).
  - Implement Coins.ph REST data models and API service for public market data (`exchangeInfo`, ticker prices, server time).
  - Implement mathematical models for DCA, RSI, Grid calculations, fee deduction, and precision rounding.
- **Phase 3: Repository & Data Layer**
  - Implement `TradingBotRepository` with real-time Firestore flows and offline caching.
  - Implement Paper Trading simulator with order matching and PnL calculation.
  - Provide Firestore security rules (`firestore.rules`) and blueprint.
- **Phase 4: Dashboard ViewModel & State Management**
  - Implement `BotDashboardViewModel` exposing immutable `DashboardUiState`.
  - Handle settings persistence, live ticker polling, start/stop commands, and emergency stop actions.
- **Phase 5: Jetpack Compose UI (Material 3 Dark Theme)**
  - Build header status card (exchange health, engine state, active mode badge).
  - Build quick statistics grid (PnL, balance, win rate).
  - Build interactive Compose Canvas price/trend chart.
  - Build strategy configuration panel with numeric validation and quote currency selectors.
  - Build bot control buttons (Start, Stop, Live toggle modal, Emergency Stop).
  - Build real-time activity feed LazyColumn.
- **Phase 6: Notifications & Background Alerting**
  - Configure `MyFirebaseMessagingService` with `trading_alerts` notification channel.
  - Request `POST_NOTIFICATIONS` runtime permission with rationale dialog.
- **Phase 7: Backend Engine & Coins.ph Credentials Guide**
  - Deliver complete, production-ready backend engine code for Cloud Run / Node.js worker.
  - Document HMAC-SHA256 request signing, Coins.ph API key generation, IP whitelisting, and secret management.
- **Phase 8: Verification & Automated Tests**
  - Write Robolectric JVM tests for DCA strategy logic, RSI calculations, Grid bounds, risk limit enforcement, and ViewModel state transitions.
  - Run `compile_applet` and test suite to ensure clean, error-free compilation.

---

## 6. Implementation Tracking Checklist

- [x] **Phase 1: Setup & Metadata** (Completed — Application branding, adaptive icon, permissions)
- [x] **Phase 2: Domain & Exchange Abstraction** (Completed — ExchangeClient, CoinsPhApiService, Moshi models)
- [x] **Phase 3: Repository & Paper Engine** (Completed — TradingBotRepository, DCA, RSI, Grid strategies, RiskManager)
- [x] **Phase 4: ViewModel & State Flows** (Completed — BotDashboardViewModel, StateFlows, event channels)
- [x] **Phase 5: Compose Terminal UI** (Completed — Status pill, Canvas PriceChart, StatsGrid, Controls, Logs)
- [x] **Phase 6: FCM Notification Service** (Completed — trading_alerts notification channel & alert service)
- [x] **Phase 7: Cloud Run Engine & Coins.ph Setup Guide** (Completed — Node.js engine, Dockerfile, HMAC signing, README)
- [x] **Phase 8: Tests & Compilation Verification** (Completed — Strategy & Robolectric tests, Clean Gradle build)
