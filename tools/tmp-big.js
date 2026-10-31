const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const EDGE = process.env['ProgramFiles(x86)'] + '\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9337;
const PROF = fs.mkdtempSync(path.join(os.tmpdir(), 'dr-'));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const login = await fetch('http://127.0.0.1:8080/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'username=tester&password=test1234',
    redirect: 'manual',
  });
  const m = (login.headers.get('set-cookie') || '').match(/DR_SESSION=([^;]+)/);
  if (!m) throw new Error('no cookie');

  const child = spawn(EDGE, ['--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + PROF, '--window-size=1600,1050', 'about:blank'], { stdio: 'ignore' });
  let ws;
  for (let i = 0; i < 60 && !ws; i++) {
    await sleep(300);
    try {
      const l = await (await fetch('http://127.0.0.1:' + PORT + '/json/list')).json();
      const p = l.find((t) => t.type === 'page');
      if (p) ws = p.webSocketDebuggerUrl;
    } catch {}
  }
  const sock = new WebSocket(ws);
  let seq = 0;
  const pend = new Map();
  const send = (method, params = {}) =>
    new Promise((res, rej) => {
      const id = ++seq;
      pend.set(id, { res, rej });
      sock.send(JSON.stringify({ id, method, params }));
    });
  sock.addEventListener('message', (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pend.has(msg.id)) {
      const { res, rej } = pend.get(msg.id);
      pend.delete(msg.id);
      msg.error ? rej(new Error(msg.error.message)) : res(msg.result);
    }
  });
  await new Promise((r) => sock.addEventListener('open', r));
  await send('Network.enable');
  await send('Network.setCookie', { name: 'DR_SESSION', value: m[1], domain: '127.0.0.1', path: '/' });
  await send('Page.enable');
  await send('Page.navigate', { url: 'http://127.0.0.1:8080/' });
  await sleep(4000);
  const shot = await send('Page.captureScreenshot', { format: 'png' });
  fs.writeFileSync('tools/tmp-big.png', Buffer.from(shot.data, 'base64'));
  console.log('ok');
  sock.close();
  child.kill();
  process.exit(0);
})().catch((e) => { console.error(e.message); process.exit(1); });