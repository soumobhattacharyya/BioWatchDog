(() => {
  const $ = (id) => document.getElementById(id);
  const state = { active: false, ws: null, sessionId: null, sent: 0, windowSize: 120, lastKeyUp: null, lastPointer: null, lastPointerSent: 0 };
  function setStatus(text) { $('status').textContent = text; }
  function setPolicy(title, message, action, tone = 'neutral') {
    $('policyTitle').textContent = title;
    $('policyMessage').textContent = message;
    $('actionPill').textContent = action;
    $('actionPill').className = `pill ${tone}`;
  }
  function updateCounts() { $('eventCount').textContent = String(state.sent); }
  function send(payload) {
    if (!state.active || !state.ws || state.ws.readyState !== WebSocket.OPEN) return;
    state.ws.send(JSON.stringify(payload));
    state.sent += 1;
    updateCounts();
  }
  function stop(reason = 'Collection stopped. The server discards this in-memory session.') {
    state.active = false;
    if (state.ws) { try { state.ws.close(1000, 'user stopped collection'); } catch (_) {} }
    state.ws = null;
    state.sessionId = null;
    state.lastKeyUp = null;
    window.removeEventListener('keydown', onKeyDown, true);
    window.removeEventListener('keyup', onKeyUp, true);
    window.removeEventListener('pointermove', onPointerMove, true);
    $('startBtn').disabled = false;
    $('stopBtn').disabled = true;
    $('collectionState').textContent = 'Stopped';
    setStatus(reason);
    setPolicy('Collection stopped', 'No new events are being sent. Refresh the page to reset the demo counters.', 'COLLECTION STOPPED');
  }
  function onKeyDown(event) {
    if (event.repeat) return;
    // Never inspect event.key, event.code, target value, or typed content.
    const now = performance.now();
    event.target && event.target.closest && event.target.closest('button');
    const token = { downAt: now, dwellMs: null };
    keyStarts.push(token);
  }
  const keyStarts = [];
  function onKeyUp() {
    const now = performance.now();
    const started = keyStarts.pop();
    if (!started) return;
    const dwellMs = Math.max(0, Math.min(2000, now - started.downAt));
    const flightMs = state.lastKeyUp === null ? 0 : Math.max(0, Math.min(5000, now - state.lastKeyUp));
    state.lastKeyUp = now;
    send({ type: 'KEY', eventTime: Date.now(), dwellMs: Number(dwellMs.toFixed(2)), flightMs: Number(flightMs.toFixed(2)) });
  }
  function onPointerMove(event) {
    const now = performance.now();
    if (now - state.lastPointerSent < 100) return;
    const x = Math.max(0, Math.min(1, event.clientX / Math.max(1, window.innerWidth)));
    const y = Math.max(0, Math.min(1, event.clientY / Math.max(1, window.innerHeight)));
    if (state.lastPointer && Math.abs(x - state.lastPointer.x) + Math.abs(y - state.lastPointer.y) < 0.006) return;
    state.lastPointer = { x, y };
    state.lastPointerSent = now;
    send({ type: 'POINTER', eventTime: Date.now(), x: Number(x.toFixed(4)), y: Number(y.toFixed(4)) });
  }
  async function start() {
    if (state.active) return;
    try {
      const health = await fetch('/api/health', { cache: 'no-store' }).then(r => { if (!r.ok) throw new Error('Health endpoint unavailable'); return r.json(); });
      state.windowSize = health.windowSize || 120;
      $('windowCount').textContent = `0 / ${state.windowSize}`;
      $('modelStatus').textContent = health.modelAvailable ? 'Loaded' : 'Not configured';
      $('modelStatus').style.color = health.modelAvailable ? 'var(--accent)' : 'var(--warn)';
    } catch (error) { setStatus('Cannot contact local backend. Start Spring Boot and try again.'); return; }
    state.sessionId = crypto.randomUUID();
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${location.host}/ws/events?sessionId=${encodeURIComponent(state.sessionId)}`;
    const socket = new WebSocket(url);
    state.ws = socket;
    socket.onopen = () => {
      state.active = true;
      state.sent = 0;
      state.lastKeyUp = null;
      keyStarts.length = 0;
      state.lastPointer = null;
      state.lastPointerSent = 0;
      $('eventCount').textContent = '0';
      $('collectionState').textContent = 'Active — opt-in';
      $('startBtn').disabled = true;
      $('stopBtn').disabled = false;
      setStatus('Collection active. Keep this tab focused and interact naturally; stop at any time.');
      setPolicy('Collecting a window', 'A trained encoder alone cannot verify a user. This MVP does not store or compare enrollment profiles.', 'DEMO ACTIVE');
      window.addEventListener('keydown', onKeyDown, true);
      window.addEventListener('keyup', onKeyUp, true);
      window.addEventListener('pointermove', onPointerMove, true);
    };
    socket.onmessage = (message) => {
      let data;
      try { data = JSON.parse(message.data); } catch (_) { return; }
      if (data.windowSize) { state.windowSize = data.windowSize; $('windowCount').textContent = `${data.eventCount || 0} / ${state.windowSize}`; }
      if (data.status) {
        const tone = data.status === 'LOW_RISK' ? 'good' : data.status === 'STEP_UP_RECOMMENDED' ? 'warning' : 'neutral';
        setPolicy(data.status.replaceAll('_', ' '), data.message || 'No additional detail.', data.action || 'NO ACTION', tone);
      } else if (data.type === 'ERROR') setStatus(`An event was rejected: ${data.message}`);
    };
    socket.onerror = () => setStatus('WebSocket error. Check that the backend is running and then stop the demo.');
    socket.onclose = () => { if (state.active) stop('Connection closed. Collection has stopped.'); };
  }
  $('startBtn').addEventListener('click', start);
  $('stopBtn').addEventListener('click', () => stop());
  fetch('/api/health', { cache: 'no-store' }).then(r => r.json()).then(data => {
    $('modelStatus').textContent = data.modelAvailable ? 'Loaded' : 'Not configured';
    $('modelStatus').style.color = data.modelAvailable ? 'var(--accent)' : 'var(--warn)';
    $('windowCount').textContent = `0 / ${data.windowSize || 120}`;
  }).catch(() => { $('modelStatus').textContent = 'Offline'; });
})();
