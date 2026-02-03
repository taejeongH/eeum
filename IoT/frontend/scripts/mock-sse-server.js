import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { randomUUID } from 'crypto';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PORT = 8081;

// Path to your local test images (ProjectRoot/test-images)
const LOCAL_IMAGES_DIR = path.join(__dirname, '..', 'test-images');

// Global state to keep track of voice messages for consistency
let chatHistory = [
    { id: 101, sender: "딸 영희", content: "아빠, 오늘 저녁에 맛있는 거 사갈게요~", timestamp: new Date(Date.now() - 3600000 * 2).toISOString() },
    { id: 102, sender: "아들 철수", content: "이번 주말에 손주들이랑 같이 놀러 갈게요!", timestamp: new Date(Date.now() - 3600000).toISOString() }
];

// Ensure the directory exists
if (!fs.existsSync(LOCAL_IMAGES_DIR)) {
    fs.mkdirSync(LOCAL_IMAGES_DIR, { recursive: true });
}

const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS, POST, DELETE');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    // --- Serve Local Images ---
    if (req.url.startsWith('/images/')) {
        const fileName = decodeURIComponent(req.url.replace('/images/', ''));
        const filePath = path.join(LOCAL_IMAGES_DIR, fileName);

        if (fs.existsSync(filePath)) {
            const ext = path.extname(filePath).toLowerCase();
            const contentType = ext === '.png' ? 'image/png' : 'image/jpeg';
            res.writeHead(200, { 'Content-Type': contentType });
            fs.createReadStream(filePath).pipe(res);
            return;
        }
    }

    const setupSSE = (res) => {
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
        });
    };

    // --- 1. Alert Stream ---
    if (req.url === '/api/alerts/stream') {
        console.log('[Mock] Client connected to Alert stream');
        setupSSE(res);
        res.write(`event: ping\ndata: connected\n\n`);

        const sendAlert = () => {
            const rand = Math.random();
            let data = {};

            if (rand < 0.4) {
                // 1. Standard Alert (Medication)
                data = {
                    "msg_id": randomUUID(),
                    "kind": "medication",
                    "title": "복약 알림",
                    "content": "복약 시간입니다. 고혈압약을 드세요.",
                    "type": "medication"
                };
            } else if (rand < 0.8) {
                // 2. Voice Message Alert
                const messages = [
                    { sender: "딸 영희", content: "아빠, 오늘 저녁에 맛있는 거 사갈게요~" },
                    { sender: "아들 철수", content: "이번 주말에 손주들이랑 놀러 갈게요!" },
                    { sender: "며느리 수진", content: "날씨가 추운데 감기 조심하세요!" }
                ];
                const msg = messages[Math.floor(Math.random() * messages.length)];
                data = {
                    "msg_id": randomUUID(),
                    "kind": "voice",
                    "title": msg.sender,
                    "content": msg.content,
                    "type": "VOICE"
                };

                // Sync with chatHistory so it appears in the message tab
                chatHistory.unshift({
                    id: Date.now(),
                    sender: msg.sender,
                    content: msg.content,
                    timestamp: new Date().toISOString()
                });
                if (chatHistory.length > 10) chatHistory.pop(); // Keep only latest 10
            } else {
                // 3. Schedule Alert
                data = {
                    "msg_id": randomUUID(),
                    "kind": "schedule",
                    "title": "일정 알림",
                    "content": "30분 뒤에 병원 예약이 있습니다.",
                    "type": "schedule"
                };
            }

            console.log(`[Mock] Sending alert type: ${data.kind}`);
            res.write(`event: alert\ndata: ${JSON.stringify(data)}\n\n`);
        };

        const interval = setInterval(sendAlert, 5000); // Send every 5 seconds for rapid testing
        req.on('close', () => { clearInterval(interval); console.log('[Mock] Alert client disconnected'); });
    }

    // --- 2. Slideshow Stream ---
    else if (req.url === '/api/slideshow/stream') {
        console.log('[Mock] Client connected to Slideshow stream');
        setupSSE(res);

        let seq = 1;
        const sendSlide = (reason = 'timer') => {
            // Scan for local images
            const files = fs.readdirSync(LOCAL_IMAGES_DIR).filter(f => /\.(jpg|jpeg|png|webp)$/i.test(f));

            let imageUrl = '';
            let description = '가족의 소중한 추억';

            if (files.length > 0) {
                // Use local image
                const randomFile = files[Math.floor(Math.random() * files.length)];
                imageUrl = `http://localhost:${PORT}/images/${encodeURIComponent(randomFile)}`;
                description = `내 컴퓨터의 사진: ${randomFile}`;
            } else {
                // Fallback to Picsum
                imageUrl = `https://picsum.photos/1920/1080?random=${seq}`;
                description = '기본 제공 배경 사진';
            }

            const data = {
                ts: Date.now() / 1000,
                seq: seq++,
                item: {
                    id: seq,
                    url: imageUrl,
                    description: description,
                    message: "오늘도 건강하고 행복한 하루 보내세요. 항상 사랑합니다!",
                    takenAt: "2026-02-03",
                    uploader: "테스트 도우미"
                },
                reason: reason
            };
            res.write(`event: slide\ndata: ${JSON.stringify(data)}\n\n`);
        };

        sendSlide('boot');
        const interval = setInterval(() => sendSlide('timer'), 10000);
        req.on('close', () => { clearInterval(interval); console.log('[Mock] Slideshow client disconnected'); });
    }

    // --- 3. Chat (Voice) Messages API ---
    else if (req.url.startsWith('/api/iot/device/sync/voice')) {
        // DELETE Handler
        if (req.method === 'DELETE') {
            const idToDelete = parseInt(req.url.split('/').pop());
            console.log(`[Mock] Deleting message ID: ${idToDelete}`);
            chatHistory = chatHistory.filter(m => m.id !== idToDelete);
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ status: 200, message: "Deleted" }));
            return;
        }

        // GET Handler
        console.log('[Mock] Fetching chat messages');
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 200,
            data: {
                added: chatHistory,
                deleted: []
            }
        }));
    }

    // --- 4. Schedules API ---
    else if (req.url === '/api/iot/device/schedules') {
        console.log('[Mock] Fetching schedules');
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 200,
            data: [
                { id: 201, title: "치과 정기 검진", description: "오후 2시, 행복치과 예약", startAt: new Date().toISOString() },
                { id: 202, title: "마을 회관 모임", description: "오후 5시, 경로당 어르신들 모임", startAt: new Date(Date.now() + 18000000).toISOString() }
            ]
        }));
    }

    else if (req.url.startsWith('/api/slideshow/')) {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: true }));
    }

    else {
        res.writeHead(404);
        res.end('Not Found');
    }
});

server.listen(PORT, () => {
    console.log(`\n🚀 SSE Mock Server (Local Image Support) running at http://localhost:${PORT}`);
    console.log(`\n📸 Local Images Folder: ${LOCAL_IMAGES_DIR}`);
    console.log(`   (이 폴더에 사진을 넣으면 슬라이드쇼에 바로 나타납니다!)`);
    console.log(`\nWaiting for connections...\n`);
});
