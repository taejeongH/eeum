/**
 * Mock WiFi API Server for Testing
 * Run with: node scripts/mock-wifi-server.js
 */

import http from 'http';

// Mock data
let wifiState = {
    activeSSID: 'MyHomeWiFi',
    lastPing: Date.now(),
    busy: false,
    aps: [
        { ssid: 'MyHomeWiFi', signal: 85, security: 'WPA2', in_use: true },
        { ssid: 'Neighbor_WiFi', signal: 65, security: 'WPA2', in_use: false },
        { ssid: 'CoffeeShop_Free', signal: 45, security: 'Open', in_use: false },
        { ssid: 'Office_Network', signal: 72, security: 'WPA2-Enterprise', in_use: false },
        { ssid: 'Guest_WiFi', signal: 55, security: 'WPA2', in_use: false },
    ],
    profiles: [
        { name: 'MyHomeWiFi', ssid: 'MyHomeWiFi', iface: 'wlan0', autoconnect: true, active_device: 'wlan0' },
        { name: 'Office_Network', ssid: 'Office_Network', iface: 'wlan0', autoconnect: false, active_device: null },
    ],
    lastScan: Date.now()
};

// Helper to send JSON response
function sendJSON(res, statusCode, data) {
    res.writeHead(statusCode, {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end(JSON.stringify(data));
}

// Helper to parse request body
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
    // Handle CORS preflight
    if (req.method === 'OPTIONS') {
        res.writeHead(200, {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
        });
        res.end();
        return;
    }

    const url = new URL(req.url, `http://${req.headers.host}`);
    const path = url.pathname;
    const query = url.searchParams;

    console.log(`${req.method} ${path}`);

    // 1. WiFi UI Ping
    if (path === '/api/wifi/ui/ping' && req.method === 'POST') {
        wifiState.lastPing = Date.now() / 1000;
        sendJSON(res, 200, {
            ok: true,
            ts: wifiState.lastPing
        });
        return;
    }

    // 2. WiFi Scan
    if (path === '/api/wifi/scan' && req.method === 'GET') {
        const forceScan = query.get('scan') === 'true';

        if (forceScan && wifiState.busy) {
            sendJSON(res, 200, {
                ok: true,
                iface: 'wlan0',
                active_ssid: wifiState.activeSSID,
                aps: wifiState.aps,
                ts: Date.now() / 1000,
                skipped: true,
                message: 'wifi busy'
            });
            return;
        }

        if (forceScan) {
            // Simulate scan delay
            wifiState.lastScan = Date.now() / 1000;
            // Randomize signal strengths slightly
            wifiState.aps = wifiState.aps.map(ap => ({
                ...ap,
                signal: Math.max(20, Math.min(100, ap.signal + Math.floor(Math.random() * 10 - 5)))
            }));
        }

        sendJSON(res, 200, {
            ok: true,
            iface: 'wlan0',
            active_ssid: wifiState.activeSSID,
            aps: wifiState.aps,
            ts: wifiState.lastScan
        });
        return;
    }

    // 3. Active WiFi Status
    if (path === '/api/wifi/active' && req.method === 'GET') {
        sendJSON(res, 200, {
            iface: 'wlan0',
            ssid: wifiState.activeSSID,
            ts: Date.now() / 1000
        });
        return;
    }

    // 4. WiFi Profiles
    if (path === '/api/wifi/profiles' && req.method === 'GET') {
        const refresh = query.get('refresh') === 'true';

        if (refresh && wifiState.busy) {
            sendJSON(res, 200, {
                ok: true,
                iface: 'wlan0',
                active_ssid: wifiState.activeSSID,
                profiles: wifiState.profiles,
                ts: Date.now() / 1000,
                skipped: true,
                message: 'wifi busy'
            });
            return;
        }

        sendJSON(res, 200, {
            ok: true,
            iface: 'wlan0',
            active_ssid: wifiState.activeSSID,
            profiles: wifiState.profiles,
            ts: Date.now() / 1000
        });
        return;
    }

    // 5. Connect to WiFi
    if (path === '/api/wifi/connect' && req.method === 'POST') {
        parseBody(req, (err, body) => {
            if (err) {
                sendJSON(res, 400, { ok: false, code: 'invalid_json', message: 'Invalid JSON' });
                return;
            }

            const { ssid, password } = body;

            if (!ssid || !password) {
                sendJSON(res, 400, { ok: false, code: 'missing_params', message: 'Missing ssid or password' });
                return;
            }

            // Check if already connected
            if (wifiState.activeSSID === ssid) {
                sendJSON(res, 200, {
                    ok: true,
                    skipped: true,
                    ssid: ssid,
                    message: 'already connected'
                });
                return;
            }

            // Simulate connection
            wifiState.busy = true;
            setTimeout(() => {
                // Simulate successful connection (90% success rate)
                if (Math.random() > 0.1) {
                    wifiState.activeSSID = ssid;
                    wifiState.aps = wifiState.aps.map(ap => ({
                        ...ap,
                        in_use: ap.ssid === ssid
                    }));

                    // Add to profiles if not exists
                    if (!wifiState.profiles.find(p => p.ssid === ssid)) {
                        wifiState.profiles.push({
                            name: ssid,
                            ssid: ssid,
                            iface: 'wlan0',
                            autoconnect: true,
                            active_device: 'wlan0'
                        });
                    }

                    wifiState.busy = false;
                } else {
                    wifiState.busy = false;
                }
            }, 2000);

            sendJSON(res, 200, {
                ok: true,
                iface: 'wlan0',
                ssid: ssid,
                message: 'connected'
            });
        });
        return;
    }

    // 6. Connect to Profile
    if (path === '/api/wifi/profile/connect' && req.method === 'POST') {
        parseBody(req, (err, body) => {
            if (err) {
                sendJSON(res, 400, { ok: false, code: 'invalid_json', message: 'Invalid JSON' });
                return;
            }

            const { name } = body;
            const profile = wifiState.profiles.find(p => p.name === name);

            if (!profile) {
                sendJSON(res, 404, { ok: false, code: 'profile_not_found', message: 'Profile not found' });
                return;
            }

            if (wifiState.activeSSID === profile.ssid) {
                sendJSON(res, 200, {
                    ok: true,
                    skipped: true,
                    requested: name,
                    message: 'already connected'
                });
                return;
            }

            // Simulate connection
            wifiState.busy = true;
            setTimeout(() => {
                wifiState.activeSSID = profile.ssid;
                wifiState.aps = wifiState.aps.map(ap => ({
                    ...ap,
                    in_use: ap.ssid === profile.ssid
                }));
                wifiState.busy = false;
            }, 2000);

            sendJSON(res, 200, {
                ok: true,
                requested: name,
                message: 'connect requested'
            });
        });
        return;
    }

    // 7. Delete Profile
    if (path === '/api/wifi/profile/delete' && req.method === 'POST') {
        parseBody(req, (err, body) => {
            if (err) {
                sendJSON(res, 400, { ok: false, code: 'invalid_json', message: 'Invalid JSON' });
                return;
            }

            const { name } = body;
            const index = wifiState.profiles.findIndex(p => p.name === name);

            if (index === -1) {
                sendJSON(res, 404, { ok: false, code: 'profile_not_found', message: 'Profile not found' });
                return;
            }

            wifiState.profiles.splice(index, 1);

            sendJSON(res, 200, {
                ok: true,
                deleted: name,
                message: 'deleted'
            });
        });
        return;
    }

    // 404 for unknown routes
    sendJSON(res, 404, { ok: false, code: 'not_found', message: 'Endpoint not found' });
});

const PORT = 8080;
server.listen(PORT, () => {
    console.log(`🚀 Mock WiFi API Server running on http://localhost:${PORT}`);
    console.log('');
    console.log('Available endpoints:');
    console.log('  POST /api/wifi/ui/ping');
    console.log('  GET  /api/wifi/scan?scan=true');
    console.log('  GET  /api/wifi/active');
    console.log('  GET  /api/wifi/profiles?refresh=true');
    console.log('  POST /api/wifi/connect');
    console.log('  POST /api/wifi/profile/connect');
    console.log('  POST /api/wifi/profile/delete');
    console.log('');
    console.log('Press Ctrl+C to stop');
});
