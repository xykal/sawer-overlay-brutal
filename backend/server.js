/**
 * SAWER STUDIO v6 BACKEND — TIKTOK LIVE REAL-TIME
 * Vercel compatible version
 */

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

let TikTokConnector = null;
try {
  TikTokConnector = require('tiktok-live-connector').WebcastPushConnection;
} catch (e) {
  console.log('tiktok-live-connector not available in this env, using mock');
}

const app = express();
app.use(cors({ origin: "*" }));
app.use(express.json());

const tiktokConnections = new Map();
const roomClients = new Map();

app.get('/', (req, res) => {
  res.json({
    name: 'Sawer Studio v6 Backend — TikTok Live Real-time',
    version: '6.0.0',
    status: 'ready',
    endpoints: {
      '/': 'Info',
      '/health': 'Health',
      '/api/status': 'Active connections',
      '/api/connect/:username': 'Connect to TikTok Live'
    },
    deployment: process.env.VERCEL ? 'vercel' : 'node',
    timestamp: Date.now()
  });
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok', uptime: process.uptime(), connections: tiktokConnections.size, vercel: !!process.env.VERCEL });
});

app.get('/api/status', (req, res) => {
  const status = {};
  for (const [username, conn] of tiktokConnections.entries()) {
    try {
      status[username] = {
        connected: conn.getState ? conn.getState().isConnected : true,
        clients: roomClients.get(username)?.size || 0
      };
    } catch (e) {
      status[username] = { connected: false, error: e.message };
    }
  }
  res.json(status);
});

app.get('/api/connect/:username', async (req, res) => {
  const username = req.params.username.replace('@','');
  try {
    await connectToTikTok(username, null);
    res.json({ success: true, message: `Connected to @${username}`, username });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
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
    console.log(`Mock connect to @${username} (no connector in vercel)`);
    // Mock connection for Vercel demo
    const mockConn = {
      getState: () => ({ isConnected: true, roomId: 'mock-'+Date.now() }),
      disconnect: () => {},
      on: () => {}
    };
    tiktokConnections.set(username, mockConn);
    // Simulate events every 5s
    if (io) {
      setInterval(() => {
        if (Math.random() < 0.3) {
          const names = ['User'+Math.floor(Math.random()*1000), 'FollowerBaru'];
          const name = names[Math.floor(Math.random()*names.length)];
          if (Math.random() < 0.5) {
            io.to(username).emit('follow', { name, uniqueId: name.toLowerCase(), type: 'follow' });
          } else {
            io.to(username).emit('gift', { name, gift: 'Rose', amount: 10, type: 'gift' });
          }
        }
      }, 5000);
    }
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
    if (io) io.to(username).emit('tiktok_connected', { username, roomId: state.roomId });
  });

  connection.on('disconnected', () => {
    console.log(`❌ Disconnected @${username}`);
    if (io) io.to(username).emit('tiktok_disconnected', { username });
    tiktokConnections.delete(username);
  });

  connection.on('follow', (data) => {
    console.log(`👤 @${username} ${data.uniqueId} FOLLOWED`);
    const payload = { type: 'follow', name: data.nickname || data.uniqueId, uniqueId: data.uniqueId, avatar: data.profilePictureUrl, timestamp: Date.now() };
    if (io) {
      io.to(username).emit('follow', payload);
      io.to(username).emit('event', { eventType: 'follow', ...payload });
    }
  });

  connection.on('gift', (data) => {
    if (data.giftType === 1 && !data.repeatEnd) return;
    const payload = { type: 'gift', name: data.nickname || data.uniqueId, uniqueId: data.uniqueId, gift: data.giftName, giftId: data.giftId, amount: data.diamondCount * data.repeatCount, repeatCount: data.repeatCount, timestamp: Date.now() };
    if (io) {
      io.to(username).emit('gift', payload);
      io.to(username).emit('event', { eventType: 'gift', ...payload });
    }
  });

  connection.on('like', (data) => {
    if (data.likeCount > 10) return;
    if (io) io.to(username).emit('like', { type: 'like', name: data.nickname || data.uniqueId, likeCount: data.likeCount, timestamp: Date.now() });
  });

  connection.on('share', (data) => {
    if (io) io.to(username).emit('share', { type: 'share', name: data.nickname || data.uniqueId, timestamp: Date.now() });
  });

  connection.on('error', (err) => {
    console.error(`Error @${username}:`, err);
    if (io) io.to(username).emit('tiktok_error', { username, error: err.message });
  });

  try {
    const state = await connection.connect();
    tiktokConnections.set(username, connection);
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

// Create HTTP server and Socket.IO only if not in Vercel serverless build phase
let server;
let io;

if (!process.env.VERCEL || process.env.VERCEL_ENV === 'development') {
  server = http.createServer(app);
  io = new Server(server, { cors: { origin: "*", methods: ["GET", "POST"] } });

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
      socket.emit('joined', { username, clients: roomClients.get(username).size });
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
      if (username && io) {
        io.to(username).emit(eventType, data);
        io.to(username).emit('event', { eventType, ...data });
      }
    });
  });

  const PORT = process.env.PORT || 3000;
  if (require.main === module) {
    server.listen(PORT, '0.0.0.0', () => {
      console.log(`🔥 Sawer Studio v6 Backend running on http://0.0.0.0:${PORT}`);
      console.log(`📱 Termux: node server.js`);
    });
  }
} else {
  // Vercel serverless - export app
  console.log('Running in Vercel serverless mode');
}

// For Vercel serverless, export app
module.exports = app;
// Also export for socket.io if needed
module.exports.connectToTikTok = connectToTikTok;
