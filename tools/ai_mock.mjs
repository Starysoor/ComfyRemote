// Mock OpenAI-compatible server for local E2E testing of the AI prompt-expansion
// feature (no real API key needed). Run: node tools/ai_mock.mjs  [port=8189]
import http from 'node:http';

const port = Number(process.argv[2]) || 8189;
http.createServer((req, res) => {
    if (req.method === 'POST' && req.url.endsWith('/chat/completions')) {
        let body = '';
        req.on('data', c => { body += c; });
        req.on('end', () => {
            console.log('--- request ---');
            console.log('url:', req.url, '| auth:', req.headers.authorization);
            const j = JSON.parse(body);
            console.log('model:', j.model, '| system prompt head:', j.messages[0].content.slice(0, 40));
            const user = j.messages[j.messages.length - 1].content;
            const isTest = j.messages[0].content.includes('连通性测试');
            const out = isTest
                ? '连接成功'
                : `【mock扩写】一张复古咖啡馆海报，写着“初雪咖啡”，暖黄灯光映在雪夜里，窗边落雪，电影感光效，构图完整，高细节（原文：${user}）`;
            res.setHeader('content-type', 'application/json');
            res.end(JSON.stringify({ choices: [{ message: { role: 'assistant', content: out } }] }));
        });
    } else {
        res.statusCode = 404;
        res.end('{}');
    }
}).listen(port, () => console.log(`mock OpenAI server on :${port}`));
