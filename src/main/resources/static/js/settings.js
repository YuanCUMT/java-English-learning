'use strict';
(() => {
  const $ = (s) => document.querySelector(s);
  const keyInput = $('#dkInput');
  const stateEl = $('#dkState');

  const api = async (url, opts = {}) => {
    const res = await fetch(url, {
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      ...opts,
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok || (body.code !== undefined && body.code !== 0)) {
      throw new Error(body.message || `请求失败（${res.status}）`);
    }
    return body.data !== undefined ? body.data : body;
  };

  const paint = (d) => {
    if (d.deepseekConfigured) {
      stateEl.textContent = d.usingOwnKey
        ? `已保存你自己的 Key：${d.deepseekKeyMasked}`
        : `正在用服务器配置文件里的 Key：${d.deepseekKeyMasked}`;
      stateEl.className = 'field-hint ok';
      keyInput.placeholder = d.usingOwnKey ? '已保存，留空则不改动' : '留空则继续用服务器上那份';
    } else {
      stateEl.textContent = '还没配置，填一个才能生成文章';
      stateEl.className = 'field-hint warn';
    }
  };

  async function load() {
    try {
      paint(await api('/api/settings'));
    } catch (e) {
      stateEl.textContent = e.message;
      stateEl.className = 'field-hint warn';
    }
  }

  $('#btnSaveKey').addEventListener('click', async () => {
    const key = keyInput.value.trim();
    if (!key) return;
    try {
      paint(await api('/api/settings', { method: 'POST', body: JSON.stringify({ deepseekKey: key }) }));
      keyInput.value = '';
      alert('已保存');
    } catch (e) {
      alert(e.message);
    }
  });

  $('#btnTestKey').addEventListener('click', async (ev) => {
    const btn = ev.currentTarget;
    const old = btn.textContent;
    btn.disabled = true;
    btn.textContent = '测试中…';
    try {
      const d = await api('/api/settings/test', { method: 'POST' });
      alert(`能用，往返 ${d.ms} ms`);
    } catch (e) {
      alert(e.message);
    } finally {
      btn.disabled = false;
      btn.textContent = old;
    }
  });

  $('#btnClearKey').addEventListener('click', async () => {
    if (!confirm('清掉自己填的 Key？清掉后会退回服务器配置文件里的那份。')) return;
    try {
      await api('/api/settings', { method: 'DELETE' });
      await load();
    } catch (e) {
      alert(e.message);
    }
  });

  load();
})();
