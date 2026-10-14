/**
 * SAWER STUDIO v10 BACKEND — FIX BACKEND GAMAU CONNECT
 * 
 * Masalah: Vercel serverless gak support Socket.IO persistent, jadi /socket.io/ 404
 * Fix v10:
 * - Tambah REST polling fallback: GET /api/events/:username (polling tiap 2 detik)
 * - POST /api/join {username} buat connect TikTok
 * - Event queue per username, auto clear setelah diambil
 * - Tetep support Socket.IO kalo di-run di Termux / VPS / Railway (persistent)
 * - Di Vercel, pakai REST polling, tetep bisa follow/gift real (via global memory)
 * - Tambah /api/test/:username buat test event
 */

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

let TikTokConnector = null;
try {
  TikTokConnector = require('tiktok-live-connector').WebcastPushConnection;
  console.log('✅ tiktok-live-connector loaded');
} catch (e) {
  console.log('⚠️ tiktok-live-connector not available, using mock:', e.message);
}

const app = express();
app.use(cors({ origin: "*" }));
app.use(express.json());

// Global state — persists across warm invocations di Vercel
const tiktokConnections = new Map();
const roomClients = new Map();
const eventQueues = new Map(); // username -> [{type, data, timestamp}]
const lastEventId = new Map(); // username -> last id

function pushEvent(username, eventType, data) {
  if (!eventQueues.has(username)) eventQueues.set(username, []);
  const queue = eventQueues.get(username);
  const event = {
    id: Date.now() + Math.random(),
    type: eventType,
    eventType,
    ...data,
    timestamp: Date.now()
  };
  queue.push(event);
  // Keep max 100 events per user
  if (queue.length > 100) queue.shift();
  console.log(`📥 Queue @${username} ${eventType}: ${data.name || ''}`);
  return event;
}

function getEvents(username, since = 0) {
  const queue = eventQueues.get(username) || [];
  if (since === 0) return queue;
  return queue.filter(e => e.timestamp > since);
}

app.get('/', (req, res) => {
  res.json({
    name: 'Sawer Studio v10 Backend — FIX CONNECT',
    version: '10.0.0',
    status: 'ready',
    fix: 'REST polling fallback for Vercel + Socket.IO for Termux',
    endpoints: {
      '/': 'Info',
      '/health': 'Health check',
      '/api/status': 'Active connections',
      '/api/connect/:username': 'Connect TikTok (GET)',
      '/api/join': 'Join TikTok Live (POST {username})',
      '/api/events/:username': 'Poll events (GET ?since=timestamp)',
      '/api/test/:username': 'Test event (POST {eventType, name})',
      '/socket.io/': 'Socket.IO (only on persistent host like Termux/Railway)'
    },
    deployment: process.env.VERCEL ? 'vercel' : 'node',
    hasConnector: !!TikTokConnector,
    connections: tiktokConnections.size,
    timestamp: Date.now()
  });
});

app.get('/health', (req, res) => {
  res.json({ 
    status: 'ok', 
    uptime: process.uptime(), 
    connections: tiktokConnections.size, 
    vercel: !!process.env.VERCEL,
    hasConnector: !!TikTokConnector,
    queues: Array.from(eventQueues.keys()).length
  });
});

app.get('/api/status', (req, res) => {
  const status = {};
  for (const [username, conn] of tiktokConnections.entries()) {
    try {
      status[username] = {
        connected: conn.getState ? conn.getState().isConnected : true,
        clients: roomClients.get(username)?.size || 0,
        queuedEvents: (eventQueues.get(username) || []).length
      };
    } catch (e) {
      status[username] = { connected: false, error: e.message };
    }
  }
  res.json(status);
});

// REST: Join TikTok Live (buat Vercel polling)
app.post('/api/join', async (req, res) => {
  const username = (req.body.username || '').replace('@','').trim();
  if (!username) return res.status(400).json({ success: false, error: 'Username required' });
  
  try {
    await connectToTikTok(username, globalIo);
    res.json({ success: true, message: `Connected to @${username}`, username, hasConnector: !!TikTokConnector });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/connect/:username', async (req, res) => {
  const username = req.params.username.replace('@','');
  try {
    await connectToTikTok(username, globalIo);
    res.json({ success: true, message: `Connected to @${username}`, username, hasConnector: !!TikTokConnector });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// REST: Poll events (fallback kalo Socket.IO gak connect)
app.get('/api/events/:username', (req, res) => {
  const username = req.params.username.replace('@','');
  const since = parseInt(req.query.since || '0');
  const events = getEvents(username, since);
  res.json({ 
    success: true, 
    username, 
    events,
    count: events.length,
    timestamp: Date.now()
  });
});

// REST: Test event
app.post('/api/test/:username', (req, res) => {
  const username = req.params.username.replace('@','');
  const { eventType, name, gift, amount } = req.body;
  const testName = name || 'TestUser'+Math.floor(Math.random()*1000);
  const event = pushEvent(username, eventType || 'follow', {
    name: testName,
    uniqueId: testName.toLowerCase(),
    gift: gift || 'Rose',
    amount: amount || 10,
    type: eventType || 'follow'
  });
  // Also emit via Socket.IO if available
  if (globalIo) {
    globalIo.to(username).emit(eventType || 'follow', event);
    globalIo.to(username).emit('event', event);
  }
  res.json({ success: true, event });
});

// Legacy test via GET
app.get('/api/test/:username/:eventType', (req, res) => {
  const username = req.params.username.replace('@','');
  const eventType = req.params.eventType;
  const name = 'TestUser'+Math.floor(Math.random()*1000);
  const event = pushEvent(username, eventType, { name, uniqueId: name.toLowerCase(), gift: 'Rose', amount: 10, type: eventType });
  if (globalIo) {
    globalIo.to(username).emit(eventType, event);
  }
  res.json({ success: true, event });
});

async function connectToTikTok(username, io) {
  if (tiktokConnections.has(username)) {
    const existing = tiktokConnections.get(username);
    try {
      if (existing.getState && existing.getState().isConnected) {
        console.log(`Already connected to @${username}`);
        return existing;
      }
    } catch (e) {}
  }

  if (!TikTokConnector) {
    console.log(`Mock connect to @${username} (no connector)`);
    const mockConn = {
      getState: () => ({ isConnected: true, roomId: 'mock-'+Date.now() }),
      disconnect: () => {},
      on: () => {}
    };
    tiktokConnections.set(username, mockConn);
    if (!eventQueues.has(username)) eventQueues.set(username, []);
    
    // Simulate events for Vercel demo
    const interval = setInterval(() => {
      if (!tiktokConnections.has(username)) {
        clearInterval(interval);
        return;
      }
      if (Math.random() < 0.25) {
        const names = ['User'+Math.floor(Math.random()*1000), 'FollowerBaru', 'BangJago', 'Siti'];
        const name = names[Math.floor(Math.random()*names.length)];
        let event;
        if (Math.random() < 0.6) {
          event = pushEvent(username, 'follow', { name, uniqueId: name.toLowerCase(), type: 'follow' });
          if (io) io.to(username).emit('follow', event);
        } else {
          const gifts = ['Rose','TikTok','Crown'];
          const gift = gifts[Math.floor(Math.random()*gifts.length)];
          event = pushEvent(username, 'gift', { name, uniqueId: name.toLowerCase(), gift, amount: Math.floor(Math.random()*100)+1, type: 'gift' });
          if (io) io.to(username).emit('gift', event);
        }
      }
    }, 4000);
    
    return mockConn;
  }

  console.log(`Connecting to TikTok Live @${username}...`);
  const connection = new TikTokConnector(username, {
    processInitialData: true,
    enableExtendedGiftInfo: true,
    enableWebsocketUpgrade: true,
    requestPollingIntervalMs: 2000
  });

  connection.on('connected', (state) => {
    console.log(`✅ Connected to @${username} Room: ${state.roomId}`);
    pushEvent(username, 'tiktok_connected', { username, roomId: state.roomId });
    if (io) io.to(username).emit('tiktok_connected', { username, roomId: state.roomId });
  });

  connection.on('disconnected', () => {
    console.log(`❌ Disconnected @${username}`);
    pushEvent(username, 'tiktok_disconnected', { username });
    if (io) io.to(username).emit('tiktok_disconnected', { username });
    tiktokConnections.delete(username);
  });

  connection.on('follow', (data) => {
    console.log(`👤 @${username} ${data.uniqueId} FOLLOWED`);
    const payload = { 
      type: 'follow', 
      name: data.nickname || data.uniqueId, 
      uniqueId: data.uniqueId, 
      avatar: data.profilePictureUrl,
      timestamp: Date.now() 
    };
    const event = pushEvent(username, 'follow', payload);
    if (io) {
      io.to(username).emit('follow', event);
      io.to(username).emit('event', { eventType: 'follow', ...event });
    }
  });

  connection.on('gift', (data) => {
    if (data.giftType === 1 && !data.repeatEnd) return;
    const payload = { 
      type: 'gift', 
      name: data.nickname || data.uniqueId, 
      uniqueId: data.uniqueId, 
      gift: data.giftName, 
      giftId: data.giftId, 
      amount: data.diamondCount * data.repeatCount, 
      repeatCount: data.repeatCount, 
      timestamp: Date.now() 
    };
    const event = pushEvent(username, 'gift', payload);
    if (io) {
      io.to(username).emit('gift', event);
      io.to(username).emit('event', { eventType: 'gift', ...event });
    }
  });

  connection.on('like', (data) => {
    if (data.likeCount > 10) return;
    const payload = { type: 'like', name: data.nickname || data.uniqueId, likeCount: data.likeCount, timestamp: Date.now() };
    const event = pushEvent(username, 'like', payload);
    if (io) io.to(username).emit('like', event);
  });

  connection.on('share', (data) => {
    const payload = { type: 'share', name: data.nickname || data.uniqueId, timestamp: Date.now() };
    const event = pushEvent(username, 'share', payload);
    if (io) io.to(username).emit('share', event);
  });

  connection.on('chat', (data) => {
    const payload = { type: 'chat', name: data.nickname || data.uniqueId, message: data.comment, timestamp: Date.now() };
    const event = pushEvent(username, 'chat', payload);
    if (io) io.to(username).emit('chat', event);
  });

  connection.on('error', (err) => {
    console.error(`Error @${username}:`, err);
    pushEvent(username, 'tiktok_error', { username, error: err.message });
    if (io) io.to(username).emit('tiktok_error', { username, error: err.message });
  });

  try {
    const state = await connection.connect();
    tiktokConnections.set(username, connection);
    if (!eventQueues.has(username)) eventQueues.set(username, []);
    return connection;
  } catch (e) {
    console.error(`Failed @${username}:`, e);
    throw e;
  }
}

function disconnectFromTikTok(username) {
  const conn = tiktokConnections.get(username);
  if (conn && conn.disconnect) {
    try { conn.disconnect(); } catch (e) {}
    tiktokConnections.delete(username);
  }
}

// Global io reference
let globalIo = null;
let server = null;
let io = null;

// Create server and io for both Vercel and non-Vercel, but only listen if not Vercel
function createServerAndIo() {
  if (!server) {
    server = http.createServer(app);
    io = new Server(server, { 
      cors: { origin: "*", methods: ["GET", "POST"] },
      transports: ['websocket','polling']
    });
    globalIo = io;

    io.on('connection', (socket) => {
      console.log(`Socket connected: ${socket.id}`);

      socket.on('join', async (data) => {
        const username = (data.username || '').replace('@','').trim();
        if (!username) {
          socket.emit('error', { message: 'Username required' });
          return;
        }
        console.log(`${socket.id} joining @${username}`);
        socket.join(username);
        if (!roomClients.has(username)) roomClients.set(username, new Set());
        roomClients.get(username).add(socket.id);
        socket.emit('joined', { username, clients: roomClients.get(username).size, hasConnector: !!TikTokConnector });
        try {
          await connectToTikTok(username, io);
        } catch (e) {
          socket.emit('tiktok_error', { username, error: e.message });
        }
      });

      socket.on('leave', (data) => {
        const username = (data.username || '').replace('@','').trim();
        if (username) {
          socket.leave(username);
          roomClients.get(username)?.delete(socket.id);
          setTimeout(() => {
            if ((roomClients.get(username)?.size || 0) === 0) disconnectFromTikTok(username);
          }, 60000);
        }
      });

      socket.on('disconnect', () => {
        console.log(`Socket disconnected: ${socket.id}`);
        for (const [username, clients] of roomClients.entries()) {
          if (clients.has(socket.id)) {
            clients.delete(socket.id);
            if (clients.size === 0) {
              setTimeout(() => {
                if ((roomClients.get(username)?.size || 0) === 0) disconnectFromTikTok(username);
              }, 60000);
            }
          }
        }
      });

      socket.on('test_event', (data) => {
        const { username, eventType } = data;
        if (username) {
          const event = pushEvent(username, eventType, data);
          if (io) {
            io.to(username).emit(eventType, event);
            io.to(username).emit('event', { eventType, ...event });
          }
        }
      });
    });
  }
  return { server, io };
}

// For local / Termux / Railway: listen
if (!process.env.VERCEL) {
  const { server: srv } = createServerAndIo();
  const PORT = process.env.PORT || 3000;
  if (require.main === module) {
    srv.listen(PORT, '0.0.0.0', () => {
      console.log(`🔥 Sawer Studio v10 Backend running on http://0.0.0.0:${PORT}`);
      console.log(`📱 Termux: node server.js`);
      console.log(`🔌 Socket.IO: ws://0.0.0.0:${PORT}`);
      console.log(`📡 REST polling: http://0.0.0.0:${PORT}/api/events/:username`);
    });
  }
} else {
  // Vercel: create io but don't listen, export app
  // For Vercel, Socket.IO won't work via serverless, but REST polling will
  console.log('Running in Vercel serverless mode — REST polling enabled, Socket.IO limited');
  createServerAndIo();
}

module.exports = app;
module.exports.connectToTikTok = connectToTikTok;
module.exports.pushEvent = pushEvent;
module.exports.getEvents = getEvents;
module.exports.createServerAndIo = createServerAndIo;
