(function() {
    const params = new URLSearchParams(window.location.search);
    const room = (params.get('room') || prompt('أدخل رمز الغرفة (Room Code):', 'WP-1615') || 'WP-1001').trim();

    const video = document.querySelector('video');
    if (!video) {
        alert('لم يتم العثور على مشغل فيديو في هذه الصفحة!');
        return;
    }

    // Create Floating HUD Overlay
    let overlay = document.getElementById('myplyr-sync-overlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'myplyr-sync-overlay';
        overlay.style.cssText = 'position:fixed;top:12px;left:12px;right:12px;z-index:999999;display:flex;align-items:center;justify-content:space-between;pointer-events:none;font-family:-apple-system,sans-serif;direction:rtl;';
        
        const badge = document.createElement('div');
        badge.id = 'myplyr-sync-badge';
        badge.style.cssText = 'background:rgba(20,27,45,0.92);backdrop-filter:blur(10px);-webkit-backdrop-filter:blur(10px);color:#3FB950;border:1px solid #30363D;padding:6px 14px;border-radius:20px;font-size:13px;font-weight:bold;display:flex;align-items:center;gap:6px;pointer-events:auto;';
        badge.innerHTML = '🟢 <span>متصل بغرفة: ' + room + '</span>';
        overlay.appendChild(badge);

        const reactions = document.createElement('div');
        reactions.style.cssText = 'display:flex;gap:8px;pointer-events:auto;';
        ['🔥', '❤️', '😂', '🍿'].forEach(emoji => {
            const btn = document.createElement('button');
            btn.innerText = emoji;
            btn.style.cssText = 'background:rgba(20,27,45,0.9);border:1px solid #30363D;border-radius:50%;width:38px;height:38px;font-size:18px;cursor:pointer;outline:none;';
            btn.onclick = () => sendEmoji(emoji);
            reactions.appendChild(btn);
        });
        overlay.appendChild(reactions);

        document.body.appendChild(overlay);
    }

    const topic = 'myplyr-party-' + room.toLowerCase();
    const wsUrl = 'wss://ntfy.sh/' + topic + '/ws';
    let ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        const badge = document.getElementById('myplyr-sync-badge');
        if (badge) badge.innerHTML = '🟢 <span>متزامن مع المضيف ✓ (' + room + ')</span>';
        publish({ action: 'join', sender: 'Safari Native' });
    };

    ws.onmessage = (event) => {
        try {
            const envelope = JSON.parse(event.data);
            if (envelope.event === 'message' && envelope.message) {
                const data = JSON.parse(envelope.message);
                handleMessage(data);
            }
        } catch (e) {}
    };

    function publish(data) {
        fetch('https://ntfy.sh/' + topic, {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain; charset=utf-8' },
            body: JSON.stringify(data)
        }).catch(() => {});
    }

    function handleMessage(data) {
        if (data.action === 'sync') {
            const targetPosSec = (data.pos || 0) / 1000.0;
            const isPlaying = (data.state === 'PLAYING');

            if (Math.abs(video.currentTime - targetPosSec) > 1.5) {
                video.currentTime = targetPosSec;
            }

            if (isPlaying && video.paused) {
                video.play().catch(() => {});
            } else if (!isPlaying && !video.paused) {
                video.pause();
            }
        } else if (data.action === 'emoji') {
            spawnEmoji(data.emoji || '🔥');
        }
    }

    function sendEmoji(emoji) {
        spawnEmoji(emoji);
        publish({ action: 'emoji', emoji: emoji, sender: 'Safari' });
    }

    function spawnEmoji(emoji) {
        const el = document.createElement('div');
        el.innerText = emoji;
        el.style.cssText = 'position:fixed;bottom:60px;left:' + (20 + Math.random() * 60) + '%;font-size:42px;z-index:9999999;pointer-events:none;animation:myplyrFly 2.2s forwards;';
        document.body.appendChild(el);
        setTimeout(() => el.remove(), 2200);
    }

    // Add CSS Animation if not present
    if (!document.getElementById('myplyr-anim-style')) {
        const style = document.createElement('style');
        style.id = 'myplyr-anim-style';
        style.innerHTML = '@keyframes myplyrFly { 0% { opacity:0; transform:translateY(20px) scale(0.6); } 15% { opacity:1; transform:translateY(0) scale(1.2); } 100% { opacity:0; transform:translateY(-280px) scale(1.5); } }';
        document.head.appendChild(style);
    }
})();
