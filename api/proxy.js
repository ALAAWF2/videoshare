export default async function handler(req, res) {
    // Enable full CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');
    res.setHeader('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Accept-Ranges, Content-Type');

    if (req.method === 'OPTIONS') {
        return res.status(200).end();
    }

    let { url } = req.query;
    if (!url) {
        return res.status(400).send('Missing url parameter');
    }

    try {
        const fetchHeaders = {
            'User-Agent': 'VLC/3.0.0 LibVLC/3.0.0 (AppleTV; iOS)'
        };

        if (req.headers.range) {
            fetchHeaders['Range'] = req.headers.range;
        }

        const isHead = req.method === 'HEAD';
        const response = await fetch(url, {
            method: isHead ? 'HEAD' : 'GET',
            headers: fetchHeaders,
            redirect: 'follow'
        });

        // Detect Content-Type, defaulting to video/mp4 or application/vnd.apple.mpegurl
        let contentType = response.headers.get('content-type') || 'video/mp4';
        if (contentType.includes('octet-stream') || contentType.includes('matroska') || contentType.includes('mkv')) {
            contentType = 'video/mp4';
        }
        if (url.includes('.m3u8')) {
            contentType = 'application/vnd.apple.mpegurl';
        }

        res.setHeader('Content-Type', contentType);
        res.setHeader('Accept-Ranges', 'bytes');

        if (response.headers.get('content-range')) {
            res.setHeader('Content-Range', response.headers.get('content-range'));
        }
        if (response.headers.get('content-length')) {
            res.setHeader('Content-Length', response.headers.get('content-length'));
        }

        res.status(response.status);

        if (isHead || !response.body) {
            return res.end();
        }

        const reader = response.body.getReader();
        
        req.on('close', () => {
            try {
                reader.cancel();
            } catch (e) {}
        });

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            res.write(value);
        }
        res.end();
    } catch (err) {
        if (!res.headersSent) {
            res.status(500).send('Proxy Error: ' + err.message);
        }
    }
}
