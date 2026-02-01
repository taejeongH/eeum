import http from 'http';

const PORT = 8080;

const server = http.createServer((req, res) => {
    // CORS Header Setting
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    if (req.url === '/api/alerts/stream') {
        console.log('Client connected to SSE stream');

        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
        });

        // Send initial ping
        res.write(`event: ping\ndata: connected\n\n`);

        // Helper to send events
        const sendEvent = (event, data) => {
            res.write(`event: ${event}\n`);
            res.write(`data: ${JSON.stringify(data)}\n\n`);
        };

        // Simulate Heartbeat every 3 seconds
        const pingInterval = setInterval(() => {
            console.log('Sending ping');
            res.write('event: ping\ndata: heartbeat\n\n');
        }, 3000);

        // Simulate Alerts every 10 seconds
        const alertInterval = setInterval(() => {
            const isMedication = Math.random() > 0.5;
            const msgId = crypto.randomUUID();
            const now = Date.now() / 1000;

            if (isMedication) {
                console.log('Sending medication alert');
                sendEvent('alert', {
                    "msg_id": msgId,
                    "kind": "medication",
                    "sent_at": now,
                    "content": "복약 시간입니다. 고혈압약 외 1개 약을 드세요.",
                    "data": {
                        "medication_list": ["고혈압약", "비타민"],
                        "text_message": "고혈압약 외 1개"
                    }
                });
            } else {
                console.log('Sending schedule alert');
                sendEvent('alert', {
                    "msg_id": msgId,
                    "kind": "schedule",
                    "sent_at": now,
                    "content": "오늘 총 2개의 일정이 있습니다. 14:00, 치과 예약. 18:00, 동창 모임.",
                    "data": {
                        "events_for_today": [
                            { "title": "치과 예약", "time": "14:00" },
                            { "title": "동창 모임", "time": "18:00" }
                        ]
                    }
                });
            }
        }, 10000);

        req.on('close', () => {
            console.log('Client disconnected');
            clearInterval(pingInterval);
            clearInterval(alertInterval);
        });
    } else {
        res.writeHead(404);
        res.end('Not Found');
    }
});

server.listen(PORT, () => {
    console.log(`SSE Mock Server running at http://localhost:${PORT}`);
    console.log(`Stream Endpoint: http://localhost:${PORT}/api/alerts/stream`);
});
