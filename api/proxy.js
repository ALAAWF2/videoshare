export default async function handler(req, res) {
    const { url } = req.query;
    if (!url) {
        return res.status(400).send('Missing url parameter');
    }

    try {
        const fetchHeaders = {
            'User-Agent': 'VLC/3.0.0'
        };

        if (req.headers.range) {
            fetchHeaders['Range'] = req.headers.range;
        }

        const response = await fetch(url, { headers: fetchHeaders });

        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', '*');

        const contentType = response.headers.get('content-type') || 'video/mp4';
        res.setHeader('Content-Type', contentType);

        if (response.headers.get('content-range')) {
            res.setHeader('Content-Range', response.headers.get('content-range'));
        }
        if (response.headers.get('accept-ranges')) {
            res.setHeader('Accept-Ranges', response.headers.get('accept-ranges'));
        }
        if (response.headers.get('content-length')) {
            res.setHeader('Content-Length', response.headers.get('content-length'));
        }

        res.status(response.status);

        if (!response.body) {
            return res.end();
        }

        const reader = response.body.getReader();
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            res.write(value);
        }
        res.end();
    } catch (err) {
        res.status(500).send('Proxy Error: ' + err.message);
    }
}
