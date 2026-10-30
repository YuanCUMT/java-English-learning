/* 今日单词：粘贴导入 / 截图识别 / 清空 */
(function () {
  'use strict';

  const $ = (sel) => document.querySelector(sel);
  const wordList = $('#wordList');
  const wordCount = $('#wordCount');
  const ocrHint = $('#ocrHint');
  const toastEl = $('#toast');

  let toastTimer = null;
  function toast(msg, isError) {
    toastEl.textContent = msg;
    toastEl.className = 'toast' + (isError ? ' error' : '');
    toastEl.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toastEl.hidden = true; }, 2600);
  }

  async function api(url, options) {
    const res = await fetch(url, Object.assign({ credentials: 'same-origin' }, options));
    if (res.status === 401) {
      window.location.href = '/login';
      throw new Error('未登录');
    }
    let body = null;
    try { body = await res.json(); } catch (e) { /* 非 JSON */ }
    if (!res.ok) {
      throw new Error((body && body.message) || ('请求失败 ' + res.status));
    }
    return body;
  }

  function renderWords(words) {
    wordCount.textContent = words.length;
    const stat = $('#statWords');
    if (stat) stat.textContent = words.length;

    if (!words.length) {
      wordList.innerHTML = '<span class="chip empty">还没有词，先在下面导入</span>';
    } else {
      wordList.innerHTML = words
        .map((w) => '<span class="chip" data-word="' + w + '">' + w + '</span>')
        .join('');
    }
    const btn = $('#btnGenerate');
    if (btn) btn.disabled = words.length === 0;
  }

  async function load() {
    try {
      const body = await api('/api/words');
      renderWords(body.data.words || []);
      ocrHint.textContent = '识别引擎：' + body.data.engine;
    } catch (e) {
      toast(e.message, true);
    }
  }

  async function importText() {
    const text = $('#pasteText').value.trim();
    if (!text) { toast('先粘一段文字', true); return; }
    try {
      const body = await api('/api/words', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text })
      });
      const d = body.data;
      toast('新增 ' + d.added + ' 个，跳过重复 ' + d.skipped + ' 个');
      $('#pasteText').value = '';
      await load();
    } catch (e) {
      toast(e.message, true);
    }
  }

  async function importImage(file) {
    const form = new FormData();
    form.append('file', file);
    try {
      toast('识别中…');
      const body = await api('/api/words/ocr', { method: 'POST', body: form });
      const d = body.data;
      toast('识别到 ' + d.total + ' 个词，新增 ' + d.added + ' 个');
      await load();
    } catch (e) {
      toast(e.message, true);
    }
  }

  async function clearWords() {
    if (!confirm('清空今天的单词？')) return;
    try {
      const body = await api('/api/words', { method: 'DELETE' });
      toast('已清空 ' + body.data.deleted + ' 个');
      await load();
    } catch (e) {
      toast(e.message, true);
    }
  }

  // —— 事件 ——
  $('#btnPaste').addEventListener('click', importText);
  $('#btnClearWords').addEventListener('click', clearWords);
  $('#fileInput').addEventListener('change', (ev) => {
    const file = ev.target.files && ev.target.files[0];
    if (file) importImage(file);
    ev.target.value = '';
  });
  $('#btnGenerate').addEventListener('click', () => {
    toast('文章生成是 M3 的内容，下一步就做');
  });

  load();
})();
