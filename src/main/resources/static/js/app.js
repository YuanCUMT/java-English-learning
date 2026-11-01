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
    // 业务码统一在 data.code 里，0 才是成功
    if (body && typeof body.code === 'number' && body.code !== 0) {
      throw new Error(body.message || ('请求失败（业务码 ' + body.code + '）'));
    }
    return body;
  }

  function renderWords(words) {
    wordCount.textContent = words.length;
    const stat = $('#statWords');
    if (stat) stat.textContent = words.length;

    const q = (($('#wordFilter') && $('#wordFilter').value) || '').trim().toLowerCase();
    const shown = q ? words.filter((w) => w.toLowerCase().includes(q)) : words;
    if (!words.length) {
      wordList.innerHTML = '<span class="chip empty">还没有词，先在下面导入</span>';
    } else if (!shown.length) {
      wordList.innerHTML = '<span class="chip empty">没有匹配「' + q + '」的词</span>';
    } else {
      wordList.innerHTML = shown
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
    // 没有可用 Key 时提前提示，别等点了「生成文章」才报错
    try {
      const s = await api('/api/settings');
      const notice = $('#keyNotice');
      // api() 返回的是整包 {code,message,data}
      if (notice) notice.hidden = !!(s && s.data && s.data.deepseekConfigured);
    } catch {}
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

  // 识别出来的词先进「候选」区：可以改拼写、删掉认错的，点保存才入库
  let pending = [];

  function renderPending() {
    const box = $('#pendingBox');
    const list = $('#pendingList');
    box.hidden = pending.length === 0;
    $('#pendingCount').textContent = pending.length;
    list.innerHTML = pending
      .map(
        (w, i) => `<span class="pending-chip">
          <input class="pending-word" data-i="${i}" value="${w}" spellcheck="false" autocomplete="off" />
          <button class="pending-del" type="button" data-del="${i}" title="删掉这个">✕</button>
        </span>`
      )
      .join('');
  }

  async function importImage(file) {
    const form = new FormData();
    form.append('file', file);
    try {
      toast('识别中…');
      const body = await api('/api/words/ocr?save=false', { method: 'POST', body: form });
      const d = body.data;
      const words = (d.words || []).filter(Boolean);
      if (!words.length) {
        toast('没识别出单词，换张清楚的图试试', true);
        return;
      }
      // 同一张图重复识别时不要重复堆，按顺序去重
      const seen = new Set(pending.map((w) => w.toLowerCase()));
      for (const w of words) {
        const k = String(w).toLowerCase();
        if (!seen.has(k)) {
          seen.add(k);
          pending.push(w);
        }
      }
      renderPending();
      toast('识别到 ' + d.total + ' 个词，确认后点「保存这些词」');
    } catch (e) {
      toast(e.message, true);
    }
  }

  async function savePending() {
    const words = pending.map((w) => w.trim()).filter(Boolean);
    if (!words.length) {
      toast('还没有可保存的词', true);
      return;
    }
    try {
      const body = await api('/api/words', { method: 'POST', body: JSON.stringify({ text: words.join(',') }) });
      const d = body.data;
      toast('新增 ' + d.added + ' 个，跳过重复 ' + d.skipped + ' 个');
      pending = [];
      renderPending();
      await load();
    } catch (e) {
      toast(e.message, true);
    }
  }

  function bindPending() {
    const list = $('#pendingList');
    if (!list) return;
    list.addEventListener('input', (e) => {
      const input = e.target.closest('.pending-word');
      if (!input) return;
      const i = Number(input.dataset.i);
      if (pending[i] !== undefined) pending[i] = input.value;
    });
    list.addEventListener('click', (e) => {
      const del = e.target.closest('[data-del]');
      if (!del) return;
      pending.splice(Number(del.dataset.del), 1);
      renderPending();
    });
    list.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && e.target.closest('.pending-word')) {
        e.preventDefault();
        savePending();
      }
    });
    $('#btnSavePending').addEventListener('click', savePending);
    $('#btnPendingClear').addEventListener('click', () => {
      pending = [];
      renderPending();
    });
  }

  // 拖拽 / 粘贴进来的可能是一批图，按顺序一张张识别
  async function importImages(files) {
    const list = Array.from(files).filter((f) => f && f.type && f.type.startsWith('image/'));
    if (!list.length) return false;
    if (list.length === 1) {
      await importImage(list[0]);
      return true;
    }
    let total = 0;
    let added = 0;
    for (let i = 0; i < list.length; i++) {
      toast('识别第 ' + (i + 1) + '/' + list.length + ' 张…');
      try {
        const form = new FormData();
        form.append('file', list[i]);
        const body = await api('/api/words/ocr', { method: 'POST', body: form });
        total += body.data.total;
        added += body.data.added;
      } catch (e) {
        toast('第 ' + (i + 1) + ' 张失败：' + e.message, true);
        return true;
      }
    }
    toast('共识别 ' + total + ' 个词，新增 ' + added + ' 个');
    await load();
    return true;
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
  const wordFilter = document.getElementById('wordFilter');
  if (wordFilter) wordFilter.addEventListener('input', () => load());
  $('#btnPaste').addEventListener('click', importText);
  $('#btnClearWords').addEventListener('click', clearWords);
  $('#fileInput').addEventListener('change', (ev) => {
    const files = ev.target.files;
    if (files && files.length) importImages(files);
    ev.target.value = '';
  });

  // —— 导入弹窗 ——
  const modalImport = $('#modalImport');
  const openImport = () => {
    modalImport.hidden = false;
    document.body.classList.add('modal-open');
    setTimeout(() => $('#pasteText').focus(), 30);
  };
  const closeImport = () => {
    modalImport.hidden = true;
    document.body.classList.remove('modal-open');
  };
  $('#btnHeroImport').addEventListener('click', openImport);
  $('#btnImportClose').addEventListener('click', closeImport);
  modalImport.addEventListener('click', (ev) => { if (ev.target === modalImport) closeImport(); });
  document.addEventListener('keydown', (ev) => {
    if (ev.key === 'Escape' && !modalImport.hidden) closeImport();
  });

  // —— 拖拽图片进导入弹窗 ——
  const dropZone = $('#importDrop');
  let dragDepth = 0;
  const hasFiles = (ev) => ev.dataTransfer && Array.from(ev.dataTransfer.types || []).includes('Files');

  dropZone.addEventListener('dragenter', (ev) => {
    if (!hasFiles(ev)) return;
    ev.preventDefault();
    dragDepth++;
    dropZone.classList.add('dragging');
  });
  dropZone.addEventListener('dragover', (ev) => {
    if (!hasFiles(ev)) return;
    ev.preventDefault();
    ev.dataTransfer.dropEffect = 'copy';
  });
  dropZone.addEventListener('dragleave', () => {
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0) dropZone.classList.remove('dragging');
  });
  dropZone.addEventListener('drop', (ev) => {
    if (!hasFiles(ev)) return;
    ev.preventDefault();
    dragDepth = 0;
    dropZone.classList.remove('dragging');
    importImages(ev.dataTransfer.files);
  });

  // —— 直接 Ctrl+V 粘贴截图（文本粘贴不拦，照常进输入框）——
  document.addEventListener('paste', (ev) => {
    const items = ev.clipboardData && ev.clipboardData.items;
    if (!items) return;
    const files = [];
    for (const it of items) {
      if (it.kind === 'file' && it.type.startsWith('image/')) {
        const f = it.getAsFile();
        if (f) files.push(f);
      }
    }
    if (!files.length) return;
    ev.preventDefault();
    openImport();
    importImages(files);
  });


  load();
})();
