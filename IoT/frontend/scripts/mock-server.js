/**
 * Unified Mock Server for IoT Frontend Testing
 * Merged from mock-wifi-server.js and mock-sse-server.js
 * Port: 8080
 */

import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { randomUUID } from 'crypto';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PORT = 8080;

// Path to test images
const LOCAL_IMAGES_DIR = path.join(__dirname, '..', 'test-images');
if (!fs.existsSync(LOCAL_IMAGES_DIR)) {
    fs.mkdirSync(LOCAL_IMAGES_DIR, { recursive: true });
}

// --- Global States ---
let wifiState = {
    activeSSID: 'MyHomeWiFi',
    lastPing: Date.now() / 1000,
    busy: false,
    aps: [
        { ssid: 'MyHomeWiFi', signal: 85, security: 'WPA2', in_use: true },
        { ssid: 'Neighbor_WiFi', signal: 65, security: 'WPA2', in_use: false },
        { ssid: 'CoffeeShop_Free', signal: 45, security: 'Open', in_use: false },
        { ssid: 'Office_Network', signal: 72, security: 'WPA2-Enterprise', in_use: false },
        { ssid: 'Guest_WiFi', signal: 55, security: 'WPA2', in_use: false },
        { ssid: 'Public_Guest_WiFi', signal: 40, security: 'Open', in_use: false },
        { ssid: 'Fail_Test_WiFi', signal: 60, security: 'WPA2', in_use: false },
        { ssid: 'Success_Test_WiFi', signal: 90, security: 'WPA2', in_use: false },
    ],
    profiles: [
        { name: 'MyHomeWiFi', ssid: 'MyHomeWiFi', iface: 'wlan0', autoconnect: true, active_device: 'wlan0' },
        { name: 'Office_Network', ssid: 'Office_Network', iface: 'wlan0', autoconnect: false, active_device: null },
        { name: 'Old_Home_WiFi', ssid: 'Old_Home_WiFi', iface: 'wlan0', autoconnect: false, active_device: null },
        { name: 'Fail_Test_WiFi', ssid: 'Fail_Test_WiFi', iface: 'wlan0', autoconnect: false, active_device: null },
    ],
    lastScan: Date.now() / 1000
};

const today = new Date();
const yyyy = today.getFullYear();
const mm = String(today.getMonth() + 1).padStart(2, '0');
const dd = String(today.getDate()).padStart(2, '0');
const todayStr = `${yyyy}-${mm}-${dd}`;

let scheduleEvents = [];
let chatHistory = [];

// --- Helpers ---
function sendJSON(res, statusCode, data) {
    res.writeHead(statusCode, {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS, DELETE, PATCH',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    res.end(JSON.stringify(data));
}

function parseBody(req, callback) {
    let body = '';
    req.on('data', chunk => body += chunk.toString());
    req.on('end', () => {
        try {
            callback(null, body ? JSON.parse(body) : {});
        } catch (e) {
            callback(e);
        }
    });
}

const server = http.createServer((req, res) => {
    // CORS Preflight
    if (req.method === 'OPTIONS') {
        res.writeHead(204, {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS, DELETE, PATCH',
            'Access-Control-Allow-Headers': 'Content-Type, Authorization'
        });
        res.end();
        return;
    }

    const url = new URL(req.url, `http://${req.headers.host}`);
    const pathName = url.pathname;
    const query = url.searchParams;

    console.log(`${req.method} ${pathName}`);

    // --- 1. WiFi APIs ---
    if (pathName === '/api/wifi/ui/ping' && req.method === 'POST') {
        wifiState.lastPing = Date.now() / 1000;
        return sendJSON(res, 200, { ok: true, ts: wifiState.lastPing });
    }
    if (pathName === '/api/wifi/scan' && req.method === 'GET') {
        const forceScan = query.get('scan') === 'true';
        if (forceScan) wifiState.lastScan = Date.now() / 1000;
        return sendJSON(res, 200, { ok: true, ssid: wifiState.activeSSID, aps: wifiState.aps, ts: wifiState.lastScan });
    }
    if (pathName === '/api/wifi/active' && req.method === 'GET') {
        return sendJSON(res, 200, { ssid: wifiState.activeSSID, ts: Date.now() / 1000 });
    }
    if (pathName === '/api/wifi/profiles' && req.method === 'GET') {
        return sendJSON(res, 200, { ok: true, profiles: wifiState.profiles });
    }
    if (pathName === '/api/wifi/connect' && req.method === 'POST') {
        return parseBody(req, (err, body) => {
            const { ssid, password } = body;

            if (wifiState.activeSSID === ssid) {
                return sendJSON(res, 200, { ok: true, skipped: true, ssid, message: 'already connected' });
            }

            wifiState.busy = true;
            setTimeout(() => {
                const shouldFail = (password && password.toLowerCase().startsWith('fail')) || Math.random() < 0.1;

                if (!shouldFail) {
                    wifiState.activeSSID = ssid;
                    wifiState.aps = wifiState.aps.map(ap => ({ ...ap, in_use: ap.ssid === ssid }));

                    if (!wifiState.profiles.find(p => p.ssid === ssid)) {
                        wifiState.profiles.push({
                            name: ssid, ssid, iface: 'wlan0', autoconnect: true, active_device: 'wlan0'
                        });
                    }
                    console.log(`✅ [Mock] Connected to ${ssid}`);
                } else {
                    console.log(`❌ [Mock] Connection to ${ssid} failed (Simulated)`);
                }
                wifiState.busy = false;
            }, 2000);

            sendJSON(res, 200, { ok: true, message: 'connecting' });
        });
    }

    if (pathName === '/api/wifi/profile/connect' && req.method === 'POST') {
        return parseBody(req, (err, body) => {
            const { name } = body;
            const profile = wifiState.profiles.find(p => p.name === name);
            if (!profile) return sendJSON(res, 404, { ok: false, message: 'Profile not found' });

            if (wifiState.activeSSID === profile.ssid) {
                return sendJSON(res, 200, { ok: true, skipped: true, message: 'already connected' });
            }

            wifiState.busy = true;
            setTimeout(() => {
                const shouldFail = name.toLowerCase().includes('fail') || Math.random() < 0.1;
                if (!shouldFail) {
                    wifiState.activeSSID = profile.ssid;
                    wifiState.aps = wifiState.aps.map(ap => ({ ...ap, in_use: ap.ssid === profile.ssid }));
                    console.log(`✅ [Mock] Profile connected to ${profile.ssid}`);
                } else {
                    console.log(`❌ [Mock] Profile connection to ${profile.ssid} failed (Simulated)`);
                }
                wifiState.busy = false;
            }, 2000);

            sendJSON(res, 200, { ok: true, message: 'connect requested' });
        });
    }

    if (pathName === '/api/wifi/profile/delete' && req.method === 'POST') {
        return parseBody(req, (err, body) => {
            const { name } = body;
            const index = wifiState.profiles.findIndex(p => p.name === name);
            if (index === -1) return sendJSON(res, 404, { ok: false, message: 'Profile not found' });

            wifiState.profiles.splice(index, 1);
            sendJSON(res, 200, { ok: true, deleted: name });
        });
    }

    // --- 2. SSE Streams ---
    // Track active SSE connections for broadcasting
    if (!global.sseClients) global.sseClients = new Set();

    if (pathName === '/api/alerts/stream') {
        console.log('[SSE] Alert stream connected');
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            'Access-Control-Allow-Origin': '*'
        });
        res.write('event: ping\ndata: connected\n\n');
        global.sseClients.add(res);
        req.on('close', () => {
            global.sseClients.delete(res);
        });
        return;
    }

    if (pathName === '/api/voice/stream') {
        console.log('[SSE] Voice stream connected');
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            'Access-Control-Allow-Origin': '*'
        });
        res.write('event: connected\ndata: {}\n\n');

        // We can reuse global.sseClients or create a new set. 
        // For simplicity, let's track voice clients separately to send specific 'voice' events.
        if (!global.voiceClients) global.voiceClients = new Set();
        global.voiceClients.add(res);

        req.on('close', () => {
            console.log('[SSE] Voice stream disconnected');
            global.voiceClients.delete(res);
        });
        return;
    }

    // Manual Trigger Endpoint
    if (pathName === '/api/mock/trigger' && req.method === 'POST') {
        return parseBody(req, (err, body) => {
            if (err) return sendJSON(res, 400, { ok: false, message: 'Invalid JSON' });

            const payload = {
                msg_id: randomUUID(),
                kind: body.kind || 'medication',
                title: body.title || '알림',
                content: body.content || (body.data?.text_message) || '테스트 내용입니다.',
                type: body.type || 'alert',
                data: {
                    medication_list: body.data?.medication_list || body.medication_list || [],
                    text_message: body.data?.text_message || body.text_message || body.content || '',
                    ...(body.data || {})
                }
            };

            console.log('[Mock] Triggering alert:', payload.kind);

            // Sync schedule data if kind is schedule
            if (payload.kind === 'schedule' && payload.data?.events_for_today) {
                console.log('[Mock] Syncing schedule data');
                scheduleEvents = [{
                    id: Date.now(),
                    title: payload.title,
                    description: payload.content,
                    startAt: new Date().toISOString(),
                    type: "SCHEDULE",
                    data: payload.data
                }, ...scheduleEvents.slice(0, 10)]; // Keep last 10
            }

            // Sync voice data if kind is voice
            if (payload.kind === 'voice') {
                console.log('[Mock] Syncing voice/chat data');

                // Mock profile lookup
                const senders = [
                    { name: "아들 철수", img: "https://randomuser.me/api/portraits/men/32.jpg" },
                    { name: "딸 영희", img: "https://randomuser.me/api/portraits/women/44.jpg" },
                    { name: "며느리 수진", img: "https://randomuser.me/api/portraits/women/68.jpg" }
                ];
                const senderInfo = senders.find(s => s.name === payload.title) || {};

                const voiceMsg = {
                    id: Date.now(),
                    sender: payload.title,
                    content: payload.content,
                    timestamp: new Date().toISOString()
                };
                chatHistory = [voiceMsg, ...chatHistory.slice(0, 20)];

                // Broadcast to Voice Stream Clients
                if (global.voiceClients) {
                    const voicePayload = {
                        id: voiceMsg.id,
                        description: voiceMsg.content,
                        created_at: Math.floor(Date.now() / 1000),
                        sender: {
                            user_id: 1,
                            name: voiceMsg.sender,
                            profile_image_url: senderInfo.img || null
                        },
                        download: {
                            status: "done",
                            ready: true,
                            retry_count: 0
                        }
                    };
                    global.voiceClients.forEach(client => {
                        client.write(`event: voice\ndata: ${JSON.stringify(voicePayload)}\n\n`);
                    });
                }

                // Inject profile_image into payload for alert stream too
                payload.profile_image = senderInfo.img || null;
            }

            let count = 0;
            global.sseClients.forEach(client => {
                client.write(`event: alert\ndata: ${JSON.stringify(payload)}\n\n`);
                count++;
            });

            sendJSON(res, 200, { ok: true, sent_to: count, payload });
        });
    }

    if (pathName === '/api/slideshow/stream') {
        console.log('[SSE] Slideshow stream connected');
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            'Access-Control-Allow-Origin': '*'
        });
        const interval = setInterval(() => {
            const data = {
                seq: Date.now(),
                item: {
                    id: Date.now(),
                    url: `https://picsum.photos/1920/1080?random=${Date.now()}`,
                    description: "가족의 소중한 추억"
                }
            };
            res.write(`event: slide\ndata: ${JSON.stringify(data)}\n\n`);
        }, 15000); // Slooooow down slideshow for better testing
        req.on('close', () => clearInterval(interval));
        return;
    }

    // --- 3. Additional APIs (Schedules, Voice sync, etc.) ---
    if (pathName === '/api/iot/device/schedules') {
        return sendJSON(res, 200, {
            status: 200,
            data: scheduleEvents
        });
    }
    if (pathName.startsWith('/api/iot/device/sync/voice')) {
        if (req.method === 'DELETE') {
            const id = parseInt(pathName.split('/').pop());
            chatHistory = chatHistory.filter(m => m.id !== id);
            return sendJSON(res, 200, { ok: true });
        }

        // Transform chatHistory to new format
        const items = chatHistory.map(msg => ({
            id: msg.id,
            description: msg.content,
            created_at: new Date(msg.timestamp).getTime() / 1000,
            sender: {
                user_id: 1, // Mock ID
                name: msg.sender,
                profile_image_url: null
            }
        }));

        return sendJSON(res, 200, {
            ok: true,
            reason: null,
            data: {
                items: items,
                limit: 100,
                offset: 0
            }
        });
    }

    if (pathName === '/api/voice/pending' && req.method === 'GET') {
        const pendingItems = [
            {
                id: 9991,
                description: "부재중 음성 메시지가 있습니다 (Pending Test)",
                created_at: Math.floor(Date.now() / 1000) - 3600, // 1 hour ago
                sender: {
                    user_id: 10,
                    name: "홍길동",
                    profile_image_url: "https://randomuser.me/api/portraits/men/10.jpg"
                },
                download: {
                    status: "done",
                    ready: true,
                    retry_count: 0,
                    next_try_at: null,
                    last_error: "",
                    updated_at: Math.floor(Date.now() / 1000)
                }
            }
        ];

        return sendJSON(res, 200, {
            ok: true,
            reason: null,
            data: {
                items: pendingItems,
                limit: 100,
                offset: 0
            }
        });
    }

    // --- 4. Image Serving ---
    if (pathName.startsWith('/images/')) {
        const fileName = decodeURIComponent(pathName.replace('/images/', ''));
        const filePath = path.join(LOCAL_IMAGES_DIR, fileName);
        if (fs.existsSync(filePath)) {
            res.writeHead(200, { 'Content-Type': 'image/jpeg' });
            return fs.createReadStream(filePath).pipe(res);
        }
    }

    // 404
    sendJSON(res, 404, { ok: false, message: 'Not Found' });
});

server.listen(PORT, () => {
    console.log(`\n🚀 Unified Mock Server running at http://localhost:${PORT}`);
    console.log(`   - WiFi & SSE APIs Integrated`);
    console.log(`   - Port: ${PORT}\n`);
});
