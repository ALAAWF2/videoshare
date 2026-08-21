const http = require('http');
const https = require('https');
const url = require('url');

const PORT = process.env.PORT || 3000;

const server = http.createServer((req, res) => {
    // CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        return res.end();
    }

    const parsed = url.parse(req.url, true);
    const targetUrl = parsed.query.url;

    if (!targetUrl) {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        return res.end('<h1>MyPlyr Stream Proxy is Running 🚀</h1>');
    }

    const targetParsed = url.parse(targetUrl);
    const client = targetParsed.protocol === 'https:' ? https : http;

    const requestHeaders = {
        'User-Agent': 'VLC/3.0.0',
        'Accept': '*/*'
    };

    if (req.headers.range) {
        requestHeaders['Range'] = req.headers.range;
    }

    const options = {
        hostname: targetParsed.hostname,
        port: targetParsed.port || (targetParsed.protocol === 'https:' ? 443 : 80),
        path: targetParsed.path,
        method: 'GET',
        headers: requestHeaders
    };

    const proxyReq = client.request(options, (proxyRes) => {
        const responseHeaders = {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
            'Access-Control-Allow-Headers': '*',
            'Content-Type': proxyRes.headers['content-type'] || 'video/mp4'
        };

        if (proxyRes.headers['content-range']) responseHeaders['Content-Range'] = proxyRes.headers['content-range'];
        if (proxyRes.headers['accept-ranges']) responseHeaders['Accept-Ranges'] = proxyRes.headers['accept-ranges'];
        if (proxyRes.headers['content-length']) responseHeaders['Content-Length'] = proxyRes.headers['content-length'];

        res.writeHead(proxyRes.statusCode, responseHeaders);
        proxyRes.pipe(res);
    });

    proxyReq.on('error', (err) => {
        res.writeHead(500, { 'Content-Type': 'text/plain' });
        res.end('Proxy Error: ' + err.message);
    });

    proxyReq.end();
});

server.listen(PORT, () => {
    console.log(`Stream Proxy running on port ${PORT}`);
});
