/**
 * Coins.ph Spot Trading Autonomous Backend Engine & Controller
 *
 * Implements server-side algorithmic strategy evaluation (DCA, RSI Swing, Grid),
 * zero-trust risk enforcement, paper trading simulation, HMAC-SHA256 request signing,
 * Firebase ID token authentication middleware, authenticated REST proxy routes,
 * order reconciliation, and real-time Firestore synchronization.
 */

const express = require('express');
const crypto = require('crypto');
const axios = require('axios');
const admin = require('firebase-admin');

// 1. Firebase Admin Initialization
if (!admin.apps.length) {
  if (process.env.FIREBASE_CONFIG || process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    admin.initializeApp();
  } else {
    admin.initializeApp({
      projectId: process.env.FIREBASE_PROJECT_ID || 'coinsph-trading-bot'
    });
  }
}

const db = admin.firestore();
const app = express();
app.use(express.json());

const PORT = process.env.PORT || 8080;
const COINS_PH_BASE_URL = 'https://api.pro.coins.ph';

// Server-side environment credentials (never exposed to client)
const COINS_API_KEY = process.env.COINS_API_KEY || '';
const COINS_SECRET_KEY = process.env.COINS_SECRET_KEY || '';

// Strict server-side authorization gate for live order execution
// Live orders will NEVER execute unless this variable is explicitly set to 'true'
const SERVER_LIVE_TRADING_ENABLED = (process.env.SERVER_LIVE_TRADING_ENABLED || 'false').toLowerCase() === 'true';

// Concurrency lock to prevent overlapping autonomous cycles
let isCycleRunning = false;

// In-memory price history buffer for indicator calculations (per symbol)
const priceHistoryBuffer = new Map();

/**
 * Generates an HMAC-SHA256 signature for Coins.ph Spot API requests.
 */
function generateSignature(queryStringOrPayload, secretKey) {
  return crypto
    .createHmac('sha256', secretKey)
    .update(queryStringOrPayload)
    .digest('hex');
}

/**
 * Builds signed request parameters adhering to official Coins.ph REST specifications.
 */
function buildSignedParams(params, secretKey) {
  const timestamp = Date.now();
  const fullParams = { ...params, timestamp };
  const queryString = Object.keys(fullParams)
    .sort()
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(fullParams[key])}`)
    .join('&');
  const signature = generateSignature(queryString, secretKey);
  return `${queryString}&signature=${signature}`;
}

/**
 * Helper to call Coins.ph REST API endpoints.
 */
async function coinsRequest(method, endpoint, params = {}, isSigned = false) {
  const headers = {};
  let url = `${COINS_PH_BASE_URL}${endpoint}`;

  if (isSigned) {
    if (!COINS_API_KEY || !COINS_SECRET_KEY) {
      throw new Error('Exchange credentials unconfigured on backend server.');
    }
    headers['X-COINS-APIKEY'] = COINS_API_KEY;
    const signedQuery = buildSignedParams(params, COINS_SECRET_KEY);
    url = `${url}?${signedQuery}`;
  } else if (Object.keys(params).length > 0) {
    const query = new URLSearchParams(params).toString();
    url = `${url}?${query}`;
  }

  const response = await axios({
    method,
    url,
    headers,
    timeout: 10000
  });

  return response.data;
}

/**
 * Firebase ID Token verification middleware.
 * Verifies Authorization: Bearer <idToken> using admin.auth().verifyIdToken.
 */
async function authenticateUser(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({
      error: 'Unauthorized: Missing or malformed Authorization header. Expected "Bearer <Firebase_ID_Token>".'
    });
  }

  const token = authHeader.split('Bearer ')[1].trim();
  if (!token) {
    return res.status(401).json({ error: 'Unauthorized: Empty Bearer token.' });
  }

  try {
    const decodedToken = await admin.auth().verifyIdToken(token);
    req.user = {
      uid: decodedToken.uid,
      email: decodedToken.email || null
    };
    next();
  } catch (err) {
    return res.status(401).json({
      error: 'Unauthorized: Invalid or expired Firebase ID token.',
      code: err.code || 'AUTH_TOKEN_INVALID'
    });
  }
}

// -----------------------------------------------------------------------------
// Strategy Evaluation Engine
// -----------------------------------------------------------------------------

function calculateRsi(prices, period = 14) {
  if (prices.length <= period) return 50.0;
  let gains = 0.0;
  let losses = 0.0;

  for (let i = prices.length - period; i < prices.length; i++) {
    const diff = prices[i] - prices[i - 1];
    if (diff >= 0) {
      gains += diff;
    } else {
      losses += -diff;
    }
  }

  if (losses === 0.0) return 100.0;
  if (gains === 0.0) return 0.0;

  const avgGain = gains / period;
  const avgLoss = losses / period;
  const rs = avgGain / avgLoss;
  return 100.0 - 100.0 / (1.0 + rs);
}

function evaluateDcaStrategy(settings, currentPrice, openPositions, allocatedCapital) {
  // 1. Take Profit & Stop Loss exit check
  if (openPositions.length > 0) {
    const totalQty = openPositions.reduce((acc, p) => acc + (p.quantity || 0), 0);
    const totalCost = openPositions.reduce((acc, p) => acc + (p.amountInvested || 0), 0);

    if (totalQty > 0 && totalCost > 0) {
      const avgPrice = totalCost / totalQty;
      const profitPct = ((currentPrice - avgPrice) / avgPrice) * 100.0;

      if (profitPct >= (settings.takeProfitPercentage || 5.0)) {
        return {
          action: 'SELL',
          reason: `Take Profit triggered: +${profitPct.toFixed(2)}% (Target: +${settings.takeProfitPercentage}%)`,
          suggestedPrice: currentPrice
        };
      }

      if (profitPct <= -(settings.stopLossPercentage || 3.5)) {
        return {
          action: 'SELL',
          reason: `Stop Loss triggered: ${profitPct.toFixed(2)}% (Limit: -${settings.stopLossPercentage}%)`,
          suggestedPrice: currentPrice
        };
      }
    }
  }

  // 2. Capital check
  const remainingCapital = (settings.maxCapital || 10000.0) - allocatedCapital;
  if (remainingCapital < (settings.orderAmount || 500.0)) {
    return { action: 'HOLD', reason: 'Max capital allocation reached.' };
  }

  // 3. Entry check
  if (openPositions.length === 0) {
    return {
      action: 'BUY',
      reason: `Initial DCA tranche entry at spot ${currentPrice}`,
      suggestedAmount: settings.orderAmount || 500.0,
      suggestedPrice: currentPrice
    };
  }

  const lastEntry = openPositions.reduce((latest, pos) => {
    return (pos.entryTimestamp || 0) > (latest.entryTimestamp || 0) ? pos : latest;
  }, openPositions[0]);

  const lastEntryPrice = lastEntry.entryPrice || currentPrice;
  const priceDropPct = ((lastEntryPrice - currentPrice) / lastEntryPrice) * 100.0;

  if (priceDropPct >= (settings.dipTriggerPercentage || 2.5)) {
    return {
      action: 'BUY',
      reason: `DCA dip trigger satisfied: -${priceDropPct.toFixed(2)}% from last tranche`,
      suggestedAmount: settings.orderAmount || 500.0,
      suggestedPrice: currentPrice
    };
  }

  return { action: 'HOLD', reason: `Pullback ${priceDropPct.toFixed(2)}% below threshold.` };
}

function evaluateRsiStrategy(settings, currentPrice, priceHistory, openPositions, allocatedCapital) {
  const period = settings.rsiPeriod || 14;
  if (priceHistory.length < period + 1) {
    return { action: 'HOLD', reason: `Awaiting price samples (${priceHistory.length}/${period + 1}).` };
  }

  const rsi = calculateRsi(priceHistory, period);

  // Exit check
  if (openPositions.length > 0) {
    const totalQty = openPositions.reduce((acc, p) => acc + (p.quantity || 0), 0);
    const totalCost = openPositions.reduce((acc, p) => acc + (p.amountInvested || 0), 0);
    if (totalQty > 0 && totalCost > 0) {
      const avgPrice = totalCost / totalQty;
      const profitPct = ((currentPrice - avgPrice) / avgPrice) * 100.0;

      if (profitPct <= -(settings.stopLossPercentage || 3.5)) {
        return { action: 'SELL', reason: `RSI Stop Loss triggered: ${profitPct.toFixed(2)}%`, suggestedPrice: currentPrice };
      }

      if (rsi >= 70.0 || profitPct >= (settings.takeProfitPercentage || 5.0)) {
        return { action: 'SELL', reason: `RSI Overbought exit: RSI ${rsi.toFixed(1)}`, suggestedPrice: currentPrice };
      }
    }
  }

  // Entry check
  if (rsi <= 30.0) {
    const remainingCapital = (settings.maxCapital || 10000.0) - allocatedCapital;
    if (remainingCapital >= (settings.orderAmount || 500.0)) {
      return {
        action: 'BUY',
        reason: `RSI Oversold entry signal: RSI ${rsi.toFixed(1)} <= 30.0`,
        suggestedAmount: settings.orderAmount || 500.0,
        suggestedPrice: currentPrice
      };
    }
  }

  return { action: 'HOLD', reason: `RSI neutral at ${rsi.toFixed(1)}` };
}

function evaluateGridStrategy(settings, currentPrice, openPositions, allocatedCapital) {
  const upper = settings.gridUpperPrice || currentPrice * 1.15;
  const lower = settings.gridLowerPrice || currentPrice * 0.85;

  if (currentPrice > upper) {
    if (openPositions.length > 0) {
      return { action: 'SELL', reason: 'Price exceeded upper grid boundary. Taking profit.', suggestedPrice: currentPrice };
    }
    return { action: 'HOLD', reason: 'Price above upper grid limit.' };
  }

  if (currentPrice < lower) {
    return { action: 'HOLD', reason: 'Price below lower grid boundary. New entries suspended.' };
  }

  const levels = Math.max(2, settings.gridLevels || 5);
  const step = (upper - lower) / levels;
  const currentLevel = Math.floor((currentPrice - lower) / step);

  // Check if position can be sold at profit
  const eligibleSell = openPositions.find(pos => currentPrice >= pos.entryPrice + step * 0.9);
  if (eligibleSell) {
    return {
      action: 'SELL',
      reason: `Grid level ${currentLevel} reached. Selling position from ${eligibleSell.entryPrice}`,
      suggestedPrice: currentPrice
    };
  }

  // Check if grid level has no position
  const hasPosAtLevel = openPositions.some(pos => Math.abs(pos.entryPrice - currentPrice) < step * 0.5);
  if (!hasPosAtLevel && allocatedCapital + (settings.orderAmount || 500.0) <= (settings.maxCapital || 10000.0)) {
    return {
      action: 'BUY',
      reason: `Grid buy at level ${currentLevel} (Price: ${currentPrice})`,
      suggestedAmount: settings.orderAmount || 500.0,
      suggestedPrice: currentPrice
    };
  }

  return { action: 'HOLD', reason: 'Grid within boundaries. Holding positions.' };
}

// -----------------------------------------------------------------------------
// Server-Side Risk Controls
// -----------------------------------------------------------------------------

function evaluateRiskControls(signal, settings, tickerTime, openPositions, allocatedCapital, dailyRealizedLoss, lastOrderTimestamp) {
  if (signal.action === 'HOLD') {
    return { approved: true };
  }

  const now = Date.now();

  // 1. Stale-Price Rejection: Prices older than 60s rejected
  if (now - tickerTime > 60000) {
    return { approved: false, reason: `Stale market price: ${Math.round((now - tickerTime) / 1000)}s old (Limit: 60s).` };
  }

  // 2. Daily Loss Limit
  if (dailyRealizedLoss >= (settings.maxDailyLoss || 1500.0)) {
    return { approved: false, reason: `Daily loss limit breached: ${dailyRealizedLoss} >= ${settings.maxDailyLoss}` };
  }

  if (signal.action === 'BUY') {
    const orderAmt = signal.suggestedAmount || settings.orderAmount || 500.0;

    // 3. Max Order Value
    if (orderAmt > (settings.maxOrderValue || 2500.0)) {
      return { approved: false, reason: `Order value (${orderAmt}) exceeds max allowable (${settings.maxOrderValue}).` };
    }

    // 4. Max Capital Allocation
    if (allocatedCapital + orderAmt > (settings.maxCapital || 10000.0)) {
      return { approved: false, reason: `Max capital reached: ${allocatedCapital + orderAmt} > ${settings.maxCapital}` };
    }

    // 5. Max Position Exposure
    if (openPositions.length >= 10) {
      return { approved: false, reason: 'Maximum open position count reached (10 tranches).' };
    }

    // 6. Duplicate-Order Cooldown (< 15 seconds)
    if (lastOrderTimestamp && now - lastOrderTimestamp < 15000) {
      return { approved: false, reason: `Duplicate order cooldown active: ${Math.round((now - lastOrderTimestamp) / 1000)}s ago.` };
    }
  }

  return { approved: true };
}

/**
 * Returns today's date key in Asia/Manila timezone (UTC+8) for daily loss accounting.
 */
function getPhtDateKey() {
  const d = new Date();
  const utc = d.getTime() + d.getTimezoneOffset() * 60000;
  const phtDate = new Date(utc + 3600000 * 8);
  return phtDate.toISOString().slice(0, 10);
}

// -----------------------------------------------------------------------------
// Autonomous Engine Cycle
// -----------------------------------------------------------------------------

async function runAutonomousEngineCycle() {
  if (isCycleRunning) return;
  isCycleRunning = true;

  try {
    const usersSnapshot = await db.collection('users').get();

    for (const userDoc of usersSnapshot.docs) {
      const userId = userDoc.id;
      const settingsRef = userDoc.ref.collection('bot_settings').doc('current');
      const statusRef = userDoc.ref.collection('bot_status').doc('current');
      const portfolioRef = userDoc.ref.collection('portfolio').doc('current');
      const tradeLogsRef = userDoc.ref.collection('trade_logs');
      const positionsRef = userDoc.ref.collection('open_positions');

      const settingsSnap = await settingsRef.get();
      if (!settingsSnap.exists) continue;
      const settings = settingsSnap.data();

      // Check if user turned on bot
      if (!settings.isActive) continue;

      const symbol = settings.symbol || 'BTCPHP';
      const quote = symbol.endsWith('USDT') ? 'USDT' : 'PHP';

      // 1. Fetch live market price from Coins.ph
      let currentPrice = 0.0;
      let tickerTimestamp = Date.now();
      try {
        const tickerRes = await coinsRequest('GET', '/openapi/quote/v1/ticker/price', { symbol }, false);
        currentPrice = parseFloat(tickerRes.price);
        tickerTimestamp = Date.now();
      } catch (err) {
        console.error(`Failed to fetch ticker for ${symbol}:`, err.message);
        await statusRef.set({
          backendHealthy: false,
          exchangeConnected: false,
          lastErrorMessage: `Market ticker error: ${err.message}`,
          lastCheckTime: Date.now()
        }, { merge: true });
        continue;
      }

      // Update in-memory price history buffer
      if (!priceHistoryBuffer.has(symbol)) {
        priceHistoryBuffer.set(symbol, []);
      }
      const history = priceHistoryBuffer.get(symbol);
      history.push(currentPrice);
      if (history.length > 60) history.shift();

      // 2. Fetch current user state
      const positionsSnap = await positionsRef.get();
      const openPositions = positionsSnap.docs.map(doc => ({ id: doc.id, ...doc.data() }));

      const portfolioSnap = await portfolioRef.get();
      const portfolio = portfolioSnap.exists ? portfolioSnap.data() : {
        quoteBalance: 50000.0,
        allocatedCapital: 0.0,
        totalRealizedPnl: 0.0,
        dailyPnl: 0.0,
        dailyPnlDate: getPhtDateKey(),
        totalTrades: 0
      };

      // Daily loss reset check (midnight PHT)
      const todayPht = getPhtDateKey();
      if (portfolio.dailyPnlDate !== todayPht) {
        portfolio.dailyPnl = 0.0;
        portfolio.dailyPnlDate = todayPht;
      }

      const allocatedCapital = openPositions.reduce((acc, p) => acc + (p.amountInvested || 0), 0);
      const dailyRealizedLoss = Math.max(0.0, -portfolio.dailyPnl);

      // Fetch last order timestamp for duplicate cooldown check
      const recentLogsSnap = await tradeLogsRef.orderBy('timestamp', 'desc').limit(1).get();
      const lastOrderTime = recentLogsSnap.empty ? 0 : recentLogsSnap.docs[0].data().timestamp || 0;

      // 3. Evaluate Strategy
      const stratType = settings.strategyType || 'DCA';
      let signal;
      if (stratType === 'RSI_SWING') {
        signal = evaluateRsiStrategy(settings, currentPrice, history, openPositions, allocatedCapital);
      } else if (stratType === 'GRID') {
        signal = evaluateGridStrategy(settings, currentPrice, openPositions, allocatedCapital);
      } else {
        signal = evaluateDcaStrategy(settings, currentPrice, openPositions, allocatedCapital);
      }

      // 4. Server-Side Risk Validation
      const riskCheck = evaluateRiskControls(
        signal,
        settings,
        tickerTimestamp,
        openPositions,
        allocatedCapital,
        dailyRealizedLoss,
        lastOrderTime
      );

      if (!riskCheck.approved) {
        await tradeLogsRef.add({
          id: `risk_${Date.now()}`,
          timestamp: Date.now(),
          type: 'RISK_GUARD',
          symbol,
          side: 'BLOCKED',
          price: currentPrice,
          quantity: 0,
          amount: 0,
          fee: 0,
          feeCurrency: quote,
          status: 'REJECTED',
          message: `Server risk guard rejected order: ${riskCheck.reason}`,
          tradingMode: settings.tradingMode || 'PAPER'
        });
        continue;
      }

      // 5. Order Execution: Separate PAPER and LIVE execution paths
      const isLiveRequested = (settings.tradingMode === 'LIVE');

      if (isLiveRequested) {
        // --- LIVE TRADING PATH ---
        // Gated by server authorization flag and credential verification
        if (!SERVER_LIVE_TRADING_ENABLED || !COINS_API_KEY || !COINS_SECRET_KEY) {
          await statusRef.set({
            state: 'ERROR',
            mode: 'LIVE',
            lastErrorMessage: 'Live Trading requested but server-side authorization gate or exchange credentials are not enabled.',
            lastCheckTime: Date.now()
          }, { merge: true });
          continue;
        }

        // Live order execution
        if (signal.action === 'BUY') {
          const orderAmount = signal.suggestedAmount || settings.orderAmount;
          try {
            // Optional test-order validation before submitting
            await coinsRequest('POST', '/openapi/v1/order/test', {
              symbol,
              side: 'BUY',
              type: 'MARKET',
              quoteOrderQty: String(orderAmount)
            }, true);

            // Execute real spot order
            const orderRes = await coinsRequest('POST', '/openapi/v1/order', {
              symbol,
              side: 'BUY',
              type: 'MARKET',
              quoteOrderQty: String(orderAmount)
            }, true);

            const execPrice = parseFloat(orderRes.price) || currentPrice;
            const execQty = parseFloat(orderRes.executedQty) || (orderAmount / execPrice);

            await tradeLogsRef.add({
              id: String(orderRes.orderId || Date.now()),
              timestamp: Date.now(),
              type: 'MARKET',
              symbol,
              side: 'BUY',
              price: execPrice,
              quantity: execQty,
              amount: orderAmount,
              fee: orderAmount * 0.001,
              feeCurrency: quote,
              status: orderRes.status || 'FILLED',
              orderId: String(orderRes.orderId || ''),
              message: `Live order filled: ${signal.reason}`,
              tradingMode: 'LIVE'
            });

            await positionsRef.add({
              symbol,
              side: 'BUY',
              entryPrice: execPrice,
              quantity: execQty,
              amountInvested: orderAmount,
              entryTimestamp: Date.now(),
              orderId: String(orderRes.orderId || '')
            });
          } catch (orderErr) {
            console.error('Live buy order failed:', orderErr.message);
            // Reconcile open orders to verify if order succeeded despite timeout
            try {
              const openOrders = await coinsRequest('GET', '/openapi/v1/openOrders', { symbol }, true);
              console.log(`Reconciled open orders after timeout: ${openOrders.length}`);
            } catch (recErr) {
              console.error('Reconciliation error:', recErr.message);
            }

            await statusRef.set({
              lastErrorMessage: `Live order failed: ${orderErr.message}`,
              lastCheckTime: Date.now()
            }, { merge: true });
          }
        } else if (signal.action === 'SELL' && openPositions.length > 0) {
          const totalQty = openPositions.reduce((acc, p) => acc + (p.quantity || 0), 0);
          try {
            const orderRes = await coinsRequest('POST', '/openapi/v1/order', {
              symbol,
              side: 'SELL',
              type: 'MARKET',
              quantity: String(totalQty)
            }, true);

            // Clear positions
            for (const pos of openPositions) {
              await positionsRef.doc(pos.id).delete();
            }

            await tradeLogsRef.add({
              id: String(orderRes.orderId || Date.now()),
              timestamp: Date.now(),
              type: 'MARKET',
              symbol,
              side: 'SELL',
              price: currentPrice,
              quantity: totalQty,
              amount: totalQty * currentPrice,
              fee: (totalQty * currentPrice) * 0.001,
              feeCurrency: quote,
              status: 'FILLED',
              orderId: String(orderRes.orderId || ''),
              message: `Live sell executed: ${signal.reason}`,
              tradingMode: 'LIVE'
            });
          } catch (sellErr) {
            console.error('Live sell order failed:', sellErr.message);
          }
        }
      } else {
        // --- PAPER TRADING PATH (DEFAULT) ---
        // Real market prices, zero exchange risk, simulated virtual book
        const feeRate = 0.001; // 0.10% standard Coins.ph spot fee

        if (signal.action === 'BUY') {
          const orderAmount = signal.suggestedAmount || settings.orderAmount || 500.0;
          const fee = orderAmount * feeRate;
          const execQty = orderAmount / currentPrice;
          const totalCost = orderAmount + fee;

          if (portfolio.quoteBalance >= totalCost) {
            portfolio.quoteBalance -= totalCost;
            portfolio.allocatedCapital += totalCost;

            await positionsRef.add({
              symbol,
              side: 'BUY',
              entryPrice: currentPrice,
              currentPrice,
              quantity: execQty,
              amountInvested: totalCost,
              entryTimestamp: Date.now()
            });

            await tradeLogsRef.add({
              id: `sim_${Date.now()}`,
              timestamp: Date.now(),
              type: 'MARKET',
              symbol,
              side: 'BUY',
              price: currentPrice,
              quantity: execQty,
              amount: orderAmount,
              fee,
              feeCurrency: quote,
              status: 'FILLED',
              orderId: `sim_ord_${Date.now() % 1000000}`,
              message: `[PAPER] ${signal.reason}`,
              tradingMode: 'PAPER'
            });
          }
        } else if (signal.action === 'SELL' && openPositions.length > 0) {
          const totalQty = openPositions.reduce((acc, p) => acc + (p.quantity || 0), 0);
          const totalCost = openPositions.reduce((acc, p) => acc + (p.amountInvested || 0), 0);
          const grossAmount = totalQty * currentPrice;
          const fee = grossAmount * feeRate;
          const netProceeds = grossAmount - fee;
          const realizedPnl = netProceeds - totalCost;

          portfolio.quoteBalance += netProceeds;
          portfolio.allocatedCapital = 0.0;
          portfolio.totalRealizedPnl = (portfolio.totalRealizedPnl || 0.0) + realizedPnl;
          portfolio.dailyPnl = (portfolio.dailyPnl || 0.0) + realizedPnl;
          portfolio.totalTrades = (portfolio.totalTrades || 0) + 1;
          portfolio.winningTrades = (portfolio.winningTrades || 0) + (realizedPnl > 0 ? 1 : 0);
          portfolio.winRate = portfolio.totalTrades > 0 ? (portfolio.winningTrades / portfolio.totalTrades) * 100.0 : 0.0;

          // Clear open positions
          for (const pos of openPositions) {
            await positionsRef.doc(pos.id).delete();
          }

          await tradeLogsRef.add({
            id: `sim_${Date.now()}`,
            timestamp: Date.now(),
            type: 'MARKET',
            symbol,
            side: 'SELL',
            price: currentPrice,
            quantity: totalQty,
            amount: grossAmount,
            fee,
            feeCurrency: quote,
            realizedPnl,
            status: 'FILLED',
            orderId: `sim_ord_${Date.now() % 1000000}`,
            message: `[PAPER] ${signal.reason} | Realized PnL: ${realizedPnl >= 0 ? '+' : ''}${realizedPnl.toFixed(2)} ${quote}`,
            tradingMode: 'PAPER'
          });
        }

        // Save updated portfolio snapshot
        await portfolioRef.set(portfolio, { merge: true });
      }

      // Update authoritative bot status heartbeat
      await statusRef.set({
        state: 'RUNNING',
        mode: settings.tradingMode || 'PAPER',
        activeSymbol: symbol,
        lastCheckTime: Date.now(),
        backendHealthy: true,
        exchangeConnected: true,
        lastErrorMessage: null
      }, { merge: true });
    }
  } catch (cycleErr) {
    console.error('Autonomous cycle exception:', cycleErr.message);
  } finally {
    isCycleRunning = false;
  }
}

// -----------------------------------------------------------------------------
// Cloud Run HTTP Routes (Public & Authenticated API)
// -----------------------------------------------------------------------------

// Public health check endpoint
app.get('/health', async (req, res) => {
  try {
    const timeResponse = await axios.get(`${COINS_PH_BASE_URL}/openapi/v1/time`, { timeout: 5000 });
    res.json({
      status: 'UP',
      exchangeConnected: true,
      coinsServerTime: timeResponse.data.serverTime,
      liveTradingEnabled: SERVER_LIVE_TRADING_ENABLED,
      timestamp: Date.now()
    });
  } catch (err) {
    res.status(500).json({
      status: 'DEGRADED',
      exchangeConnected: false,
      error: err.message
    });
  }
});

// Public exchange metadata endpoint
app.get('/api/exchangeInfo', async (req, res) => {
  try {
    const info = await coinsRequest('GET', '/openapi/v1/exchangeInfo');
    res.json(info);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Public ticker endpoint
app.get('/api/ticker', async (req, res) => {
  try {
    const symbol = req.query.symbol || 'BTCPHP';
    const ticker = await coinsRequest('GET', '/openapi/quote/v1/ticker/24hr', { symbol });
    res.json(ticker);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// -----------------------------------------------------------------------------
// Authenticated Protected Proxy Endpoints (Require Firebase Bearer Token)
// -----------------------------------------------------------------------------

/**
 * GET /api/account
 * Queries private account balances and permissions from Coins.ph Spot API (/openapi/v1/account).
 * Signed server-side.
 */
app.get('/api/account', authenticateUser, async (req, res) => {
  try {
    if (!COINS_API_KEY || !COINS_SECRET_KEY) {
      return res.status(503).json({
        error: 'Exchange credentials unconfigured on backend server. Live account access unavailable.'
      });
    }

    const accountData = await coinsRequest('GET', '/openapi/v1/account', {}, true);

    // Sanitize balances to assets with positive holdings
    const sanitizedBalances = (accountData.balances || [])
      .filter(b => parseFloat(b.free || 0) > 0 || parseFloat(b.locked || 0) > 0)
      .map(b => ({
        asset: b.asset,
        free: b.free,
        locked: b.locked
      }));

    res.json({
      canTrade: !!accountData.canTrade,
      canWithdraw: !!accountData.canWithdraw,
      canDeposit: !!accountData.canDeposit,
      balances: sanitizedBalances,
      serverTime: Date.now()
    });
  } catch (err) {
    res.status(err.response?.status || 500).json({
      error: 'Failed to retrieve Coins.ph account data',
      message: err.response?.data?.msg || err.message
    });
  }
});

/**
 * GET /api/openOrders
 * Queries active open orders for symbol from Coins.ph Spot API (/openapi/v1/openOrders).
 * Signed server-side.
 */
app.get('/api/openOrders', authenticateUser, async (req, res) => {
  try {
    const symbol = req.query.symbol;
    if (!symbol) {
      return res.status(400).json({ error: 'Bad Request: "symbol" query parameter is required (e.g. BTCPHP).' });
    }

    if (!COINS_API_KEY || !COINS_SECRET_KEY) {
      return res.status(503).json({
        error: 'Exchange credentials unconfigured on backend server.'
      });
    }

    const openOrders = await coinsRequest('GET', '/openapi/v1/openOrders', { symbol }, true);
    res.json(openOrders);
  } catch (err) {
    res.status(err.response?.status || 500).json({
      error: 'Failed to retrieve open orders from Coins.ph',
      message: err.response?.data?.msg || err.message
    });
  }
});

/**
 * POST /api/order
 * Submits an order through the authenticated backend.
 * Enforces server-side risk controls and separates test validation from live execution.
 */
app.post('/api/order', authenticateUser, async (req, res) => {
  const { symbol, side, type, quantity, quoteOrderQty, price, isTestOrder } = req.body;

  if (!symbol || !side || !type) {
    return res.status(400).json({
      error: 'Bad Request: "symbol", "side" (BUY/SELL), and "type" (MARKET/LIMIT) are required.'
    });
  }

  const normalizedSide = String(side).toUpperCase();
  const normalizedType = String(type).toUpperCase();

  if (!['BUY', 'SELL'].includes(normalizedSide)) {
    return res.status(400).json({ error: 'Bad Request: "side" must be BUY or SELL.' });
  }

  // 1. Fetch authenticated user's Firestore settings & portfolio to enforce server-side risk limits
  const userRef = db.collection('users').doc(req.user.uid);
  const settingsDoc = await userRef.collection('bot_settings').doc('current').get();
  const settings = settingsDoc.exists ? settingsDoc.data() : {};

  // Check emergency stop state
  const statusDoc = await userRef.collection('bot_status').doc('current').get();
  const currentBotState = statusDoc.exists ? statusDoc.data().state : 'STOPPED';
  if (currentBotState === 'EMERGENCY_STOP') {
    return res.status(403).json({
      error: 'Order Rejected: Bot is in EMERGENCY_STOP state. All order submissions are blocked.'
    });
  }

  // Fetch current market price to evaluate order value
  let currentSpotPrice = 0.0;
  let tickerTime = Date.now();
  try {
    const tickerRes = await coinsRequest('GET', '/openapi/quote/v1/ticker/price', { symbol }, false);
    currentSpotPrice = parseFloat(tickerRes.price);
  } catch (priceErr) {
    return res.status(503).json({
      error: 'Service Unavailable: Unable to retrieve fresh market price for risk evaluation.',
      details: priceErr.message
    });
  }

  const orderValue = parseFloat(quoteOrderQty) || (parseFloat(quantity || 0) * currentSpotPrice) || 0.0;
  const portfolioDoc = await userRef.collection('portfolio').doc('current').get();
  const portfolio = portfolioDoc.exists ? portfolioDoc.data() : { dailyPnl: 0.0, allocatedCapital: 0.0 };
  const dailyRealizedLoss = Math.max(0.0, -(portfolio.dailyPnl || 0.0));

  // Server-side risk validation
  const riskCheck = evaluateRiskControls(
    { action: normalizedSide, suggestedAmount: orderValue },
    settings,
    tickerTime,
    [],
    portfolio.allocatedCapital || 0.0,
    dailyRealizedLoss,
    0
  );

  if (!riskCheck.approved) {
    return res.status(400).json({
      error: 'Order Rejected by Server Risk Guard',
      reason: riskCheck.reason
    });
  }

  const orderParams = {
    symbol,
    side: normalizedSide,
    type: normalizedType
  };
  if (quantity) orderParams.quantity = String(quantity);
  if (quoteOrderQty) orderParams.quoteOrderQty = String(quoteOrderQty);
  if (price) orderParams.price = String(price);

  // Check whether this is a TEST order or LIVE order
  const shouldExecuteTestOrder = (isTestOrder === true || !SERVER_LIVE_TRADING_ENABLED);

  if (shouldExecuteTestOrder) {
    // --- TEST ORDER VALIDATION (Zero risk, validates parameters & signature) ---
    try {
      if (!COINS_API_KEY || !COINS_SECRET_KEY) {
        return res.json({
          status: 'TEST_VALIDATED_OFFLINE',
          isLiveExecuted: false,
          message: 'Order validated against server risk limits. Live credentials unconfigured on backend.',
          params: orderParams
        });
      }

      await coinsRequest('POST', '/openapi/v1/order/test', orderParams, true);
      return res.json({
        status: 'TEST_VALIDATED',
        isLiveExecuted: false,
        message: 'Order parameters and signature validated by Coins.ph test endpoint (/openapi/v1/order/test). No real funds were used.',
        params: orderParams
      });
    } catch (testErr) {
      return res.status(testErr.response?.status || 400).json({
        error: 'Coins.ph Test Order Validation Failed',
        message: testErr.response?.data?.msg || testErr.message
      });
    }
  }

  // --- LIVE ORDER EXECUTION PATH ---
  // Requires explicit SERVER_LIVE_TRADING_ENABLED=true
  if (!SERVER_LIVE_TRADING_ENABLED) {
    return res.status(403).json({
      error: 'Live Trading Disabled: SERVER_LIVE_TRADING_ENABLED is false on backend server.'
    });
  }

  try {
    const liveOrderRes = await coinsRequest('POST', '/openapi/v1/order', orderParams, true);

    // Record authoritative trade log in user's subcollection
    const quote = symbol.endsWith('USDT') ? 'USDT' : 'PHP';
    await userRef.collection('trade_logs').add({
      id: String(liveOrderRes.orderId || Date.now()),
      timestamp: Date.now(),
      type: normalizedType,
      symbol,
      side: normalizedSide,
      price: parseFloat(liveOrderRes.price) || currentSpotPrice,
      quantity: parseFloat(liveOrderRes.executedQty) || parseFloat(quantity) || 0.0,
      amount: orderValue,
      fee: orderValue * 0.001,
      feeCurrency: quote,
      status: liveOrderRes.status || 'FILLED',
      orderId: String(liveOrderRes.orderId || ''),
      message: 'Live order confirmed by Coins.ph.',
      tradingMode: 'LIVE'
    });

    res.json({
      status: 'EXECUTED',
      isLiveExecuted: true,
      order: liveOrderRes
    });
  } catch (liveErr) {
    console.error('Live order execution error:', liveErr.message);

    // Reconcile order status on timeout or network uncertainty
    let reconciledOrder = null;
    try {
      const openOrders = await coinsRequest('GET', '/openapi/v1/openOrders', { symbol }, true);
      reconciledOrder = openOrders.find(o => o.symbol === symbol && Math.abs(parseFloat(o.origQty) - parseFloat(quantity || 0)) < 0.0001);
    } catch (recErr) {
      console.error('Reconciliation query failed:', recErr.message);
    }

    if (reconciledOrder) {
      return res.json({
        status: 'RECONCILED_OPEN',
        isLiveExecuted: true,
        message: 'Order encountered network timeout but was verified active on Coins.ph order book.',
        order: reconciledOrder
      });
    }

    res.status(liveErr.response?.status || 500).json({
      error: 'Live Order Submission Failed',
      message: liveErr.response?.data?.msg || liveErr.message,
      reconciled: false
    });
  }
});

/**
 * DELETE /api/order
 * Cancels a single open order on Coins.ph Spot API (/openapi/v1/order).
 * Signed server-side.
 */
app.delete('/api/order', authenticateUser, async (req, res) => {
  const { symbol, orderId } = req.query;
  if (!symbol || !orderId) {
    return res.status(400).json({
      error: 'Bad Request: "symbol" and "orderId" query parameters are required to cancel an order.'
    });
  }

  if (!COINS_API_KEY || !COINS_SECRET_KEY) {
    return res.status(503).json({
      error: 'Exchange credentials unconfigured on backend server.'
    });
  }

  try {
    const cancelRes = await coinsRequest('DELETE', '/openapi/v1/order', {
      symbol,
      orderId: String(orderId)
    }, true);

    res.json({
      status: 'CANCELLED',
      orderId: cancelRes.orderId || orderId,
      symbol,
      details: cancelRes
    });
  } catch (err) {
    res.status(err.response?.status || 500).json({
      error: 'Failed to cancel order on Coins.ph',
      message: err.response?.data?.msg || err.message
    });
  }
});

// Run autonomous engine worker every 15 seconds
setInterval(runAutonomousEngineCycle, 15000);

app.listen(PORT, () => {
  console.log(`Coins.ph Autonomous Trading Engine running on port ${PORT}`);
});
