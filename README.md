# Coins.ph AI Crypto Trading Bot — Android & Cloud Engine

A production-grade algorithmic spot trading dashboard and autonomous backend engine for [Coins.ph](https://coins.ph) Spot Trading (`https://api.pro.coins.ph`).

## Architecture & Security Model

The system follows a strict zero-trust financial architecture:

1. **Android Dashboard (Client)**:
   - Built with modern Kotlin & Jetpack Compose (Material 3).
   - Allows users to monitor live market prices, configure trading strategies, view portfolio metrics (PnL, win rate), supervise order logs, and issue Start/Pause/Stop/Emergency Stop commands.
   - **Never stores exchange API secrets or private keys.**
   - Defaults strictly to **Paper Trading** (simulated orders with zero financial risk).
2. **Cloud Firestore**:
   - Stores user bot settings, authoritative runtime status, trade audit logs, and portfolio snapshots.
   - Guarded by zero-trust Firestore Security Rules (`firestore.rules`): Users can edit strategy settings, while trade execution results and balances are strictly write-restricted to backend service accounts.
3. **Autonomous Cloud Engine (Cloud Run / Node.js Worker)**:
   - Runs independently 24/7 in a secure server container (`backend/`).
   - Securely accesses Coins.ph credentials stored in Google Secret Manager or server environment variables.
   - Computes HMAC-SHA256 request signatures server-side.
   - Periodically evaluates DCA, RSI Swing, and Grid algorithms, enforces server-side risk controls (max daily loss, capital caps, stale-price rejection), submits spot orders, and synchronizes status back to Firestore.

---

## Trading Modes

| Feature | Paper Trading (Default) | Live Trading |
| :--- | :--- | :--- |
| **Real Funds Used** | **No** (Simulated virtual balances) | **Yes** (Real account balance) |
| **Market Data** | Real-time Coins.ph spot tickers | Real-time Coins.ph spot tickers |
| **Order Routing** | Local simulation matching live prices | Authenticated backend REST API |
| **Fee Deduction** | 0.10% simulated spot fee | Official Coins.ph maker/taker fee |
| **Safety Barrier** | None needed | Explicit confirmation modal & risk gate |

---

## Supported Trading Strategies

1. **Dollar-Cost Averaging (DCA)**:
   - Configurable purchase amount (in quote currency: PHP or USDT).
   - Dip trigger percentage (e.g. 2.5% drop from previous tranche).
   - Automated Take-Profit (+5%) and Stop-Loss (-3.5%) targets.
   - Enforces maximum capital allocation and minimum entry intervals.
2. **RSI Swing Trader**:
   - Configurable RSI calculation period (default 14).
   - Enters on Oversold conditions (RSI <= 30).
   - Exits on Overbought conditions (RSI >= 70) or Stop-Loss threshold.
3. **Grid Trading**:
   - Establishes automated ladder buy and sell boundaries between upper and lower prices.
   - Places automated limit orders to capture intraday volatility.

---

## Coins.ph API Setup Guide

To connect a live account:
1. Log in to your verified account on [Coins.ph](https://coins.ph).
2. Ensure **ID verification** and **Two-Factor Authentication (2FA)** are active.
3. Navigate to **Account Settings > API Management > Create API Key**.
4. Set permissions to **Spot Trade Only**.
5. **DISABLE** all withdrawal permissions.
6. Enable **IP Access Restriction** and whitelist your Cloud Run backend egress IP address.
7. Save the **API Key** and **Secret Key** securely into Google Secret Manager or your backend environment.

> **CRITICAL SECURITY RULE**: Never paste your Coins.ph API Secret Key into the Android client, local preferences, or git repositories.

---

## Deploying the Backend to Google Cloud Run

```bash
cd backend
# Build and deploy container to Cloud Run
gcloud run deploy coinsph-trading-engine \
  --source . \
  --region asia-east1 \
  --platform managed \
  --set-env-vars COINS_API_KEY="YOUR_API_KEY",COINS_SECRET_KEY="YOUR_SECRET_KEY" \
  --allow-unauthenticated
```
