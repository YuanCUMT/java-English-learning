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
    const advice = $('#dkAdvice');
    if (d.deepseekConfigured) {
      stateEl.textContent = d.usingOwnKey
        ? `已保存你自己的 Key：${d.deepseekKeyMasked}`
        : `正在用服务器配置文件里的 Key：${d.deepseekKeyMasked}`;
      stateEl.className = 'field-hint ok';
      keyInput.placeholder = d.usingOwnKey ? '已保存，留空则不改动' : '留空则继续用服务器上那份';
      advice.className = 'key-advice ok';
      advice.innerHTML = d.usingOwnKey
        ? '<b>你已经填了自己的 Key</b>，现在就走你这份，只对你这个账号生效。不想用了点「清除」，会退回配置文件那份。'
        : '<b>现在不用填。</b>程序正在用 <code>application-local.yml</code> 里那份 Key —— 你自己用可以直接关掉这页。' +
          '<br>要把项目发给别人或交作业时，记得把那份配置里的 Key 删掉（或者整个 <code>application-local.yml</code> 不提交），让对方来这页填自己的。';
    } else {
      stateEl.textContent = '还没配置，填一个才能生成文章';
      stateEl.className = 'field-hint warn';
      advice.className = 'key-advice warn';
      advice.innerHTML = '<b>必须填。</b>这台机器既没有配置文件里的 Key，也没人填过自己的 —— 不填就只能看界面，生成文章和查生词都会失败。';
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
