export default async function handler(req, res) {
    // Enable full CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');
    res.setHeader('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Accept-Ranges, Content-Type');

    if (req.method === 'OPTIONS') {
        return res.status(200).end();
    }

    let { url, referer } = req.query;
    if (!url) {
        return res.status(400).send('Missing url parameter');
    }

    const isPlaylist = url.includes('.m3u8') || url.includes('.m3u');
    const origin = (() => { try { return new URL(url).origin; } catch (e) { return ''; } })();

    try {
        const fetchHeaders = {
            'User-Agent': 'VLC/3.0.0 LibVLC/3.0.0 (AppleTV; iOS)'
        };
        if (referer) fetchHeaders['Referer'] = referer;

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
        if (isPlaylist) {
            contentType = 'application/vnd.apple.mpegurl';
        }

        res.setHeader('Content-Type', contentType);
        res.setHeader('Accept-Ranges', 'bytes');

        if (response.headers.get('content-range')) {
            res.setHeader('Content-Range', response.headers.get('content-range'));
        }

        res.status(response.status);

        if (isHead || !response.body) {
            return res.end();
        }

        // ===== HLS playlists: rewrite segment/child-playlist URLs to stay inside the proxy =====
        if (isPlaylist && response.status === 200) {
            const text = await response.text();
            const proxied = rewritePlaylist(text, url, referer);
            res.setHeader('Cache-Control', 'no-store');
            return res.status(200).send(proxied);
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

function rewritePlaylist(text, playlistUrl, referer) {
    const base = (() => { try { return new URL(playlistUrl); } catch (e) { return null; } })();
    const lines = text.split('\n');
    const out = [];
    for (let line of lines) {
        let raw = line.replace(/\r$/, '');
        const trimmed = raw.trim();
        if (trimmed === '' || trimmed.startsWith('#')) {
            out.push(raw);
            continue;
        }
        // A URI line (segment or child playlist)
        out.push(proxifyUri(trimmed, base, referer));
    }
    // Also handle URIs embedded in tags: #EXT-X-KEY:...URI="..." and #EXT-X-MAP:URI="..."
    return out.join('\n')
        .replace(/URI="([^"]+)"/g, (m, u) => `URI="${proxifyUri(u, base, referer)}"`);
}

function proxifyUri(u, base, referer) {
    try {
        const abs = base ? new URL(u, base).toString() : u;
        let p = '/api/proxy?url=' + encodeURIComponent(abs);
        if (referer) p += '&referer=' + encodeURIComponent(referer);
        return p;
    } catch (e) {
        return u;
    }
}
