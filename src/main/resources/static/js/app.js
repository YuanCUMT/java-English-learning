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
    const opts = Object.assign({ credentials: 'same-origin' }, options);
    // 有 body 但没写 Content-Type 时默认按 JSON 发，否则浏览器会用 text/plain，
    // 后端 @RequestBody 收不了（HttpMediaTypeNotSupportedException / 415）。
    if (opts.body && typeof opts.body === 'string' && !opts.headers) {
      opts.headers = { 'Content-Type': 'application/json' };
    }
    const res = await fetch(url, opts);
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
      wordList.innerHTML = '<p class="row-empty">还没有词，先在下面导入</p>';
    } else if (!shown.length) {
      wordList.innerHTML = '<p class="row-empty">没有匹配「' + q + '」的词</p>';
    } else {
      wordList.innerHTML = shown
        .map((w) => '<button type="button" class="word-row" data-word="' + w + '">' + w + '</button>')
        .join('');
      wordList.querySelectorAll('.word-row').forEach((el) => {
        el.onclick = (ev) => {
          // 别让同一个 click 冒泡到 article.js 的「点别处关浮层」逻辑，否则刚开的浮层立刻被关掉
          ev.stopPropagation();
          if (window.__drOpenWord) window.__drOpenWord(ev.clientX, ev.clientY, el.dataset.word);
        };
      });
    }
    const btn = $('#btnGenerate');
    if (btn) btn.disabled = words.length === 0;
  }

  const markList = $('#markList');

  function renderMarks(marks) {
    const n = marks.length;
    const badge = $('#markCount');
    if (badge) badge.textContent = n;
    if (!markList) return;
    if (!n) {
      markList.innerHTML = '<p class="row-empty">阅读时点词，点「☆ 收藏」就收进来</p>';
      return;
    }
    markList.innerHTML = marks
      .map((m) => '<button type="button" class="mark-row" data-mark-word="' + m.word + '">' +
        '<span class="w">' + m.word + '</span>' +
        '<span class="m">' + (m.cn || m.note || '') + '</span>' +
        '</button>')
      .join('');
    markList.querySelectorAll('.mark-row').forEach((el) => {
      el.onclick = (ev) => {
        ev.stopPropagation();
        if (window.__drOpenWord) window.__drOpenWord(ev.clientX, ev.clientY, el.dataset.markWord);
      };
    });
  }

  async function loadMarks() {
    if (!markList) return;
    try {
      const body = await api('/api/marks');
      renderMarks(body.data.marks || []);
    } catch (e) { /* 生词面板挂了不影响记单词 */ }
  }

  /** 保存完自动补释义：词典里没有的词交给模型，顺便把模型判定「不是词」的碎片删掉 */
  async function autoFillMeanings() {
// 扫「我名下所有天数」，不只是今天：每次导入完都把缺口补上，最多 3 轮（每轮 40 个）
    let filledTotal = 0;
    const removedAll = [];
    try {
      toast('正在补全没有释义的词…');
      for (let round = 0; round < 3; round++) {
        const body = await api('/api/words/fill-meanings', {
          method: 'POST',
          body: JSON.stringify({ all: true }),
        });
        const d = body.data || {};
        filledTotal += d.filled || 0;
        if (Array.isArray(d.removed)) removedAll.push(...d.removed);
        const remaining = d.remaining || 0;
        if (!remaining || ((d.filled || 0) === 0 && removedAll.length === 0)) break;
      }
      const parts = [];
      if (filledTotal) parts.push('补了 ' + filledTotal + ' 个释义');
      if (removedAll.length) {
        parts.push('删掉 ' + removedAll.length + ' 个认错的词（'
          + removedAll.slice(0, 4).join('、') + (removedAll.length > 4 ? '…' : '') + '）');
      }
      if (parts.length) {
        toast(parts.join('，'));
        await load();
      }
    } catch { /* 补释义失败不影响已经存好的词 */ }
  }

  async function load() {
    try {
      const body = await api('/api/words');
      renderWords(body.data.words || []);
      ocrHint.textContent = '识别引擎：' + body.data.engine;
      loadMarks();
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
      await autoFillMeanings();
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
      const engine = String(d.engine || '');
      const dropped = Array.isArray(d.dropped) ? d.dropped : [];
      const tail = dropped.length
        ? '；已自动去掉 ' + dropped.length + ' 个像认错的（' + dropped.slice(0, 4).join('、') + (dropped.length > 4 ? '…' : '') + '）'
        : '';
      if (d.degraded) {
        toast('识别到 ' + d.total + ' 个词' + tail + '。真实 OCR 这次失败了，用的是备用引擎（' + engine + '），结果可能不准', true);
      } else if (engine.toLowerCase().startsWith('mock')) {
        toast('识别到 ' + d.total + ' 个词' + tail + '。当前是 mock 模拟数据，不是从图片里认出来的，请手动核对', true);
      } else {
        toast('识别到 ' + d.total + ' 个词' + tail);
      }
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
      await autoFillMeanings();
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

  // 拖拽 / 粘贴进来的可能是一批图：逐张走同一套「先候选、再确认」流程，不绕过确认直接入库
  async function importImages(files) {
    const list = Array.from(files).filter((f) => f && f.type && f.type.startsWith('image/'));
    if (!list.length) return false;
    for (let i = 0; i < list.length; i++) {
      if (list.length > 1) toast('识别第 ' + (i + 1) + '/' + list.length + ' 张…');
      await importImage(list[i]);
    }
    if (list.length > 1) toast('共 ' + list.length + ' 张图，结果都进候选区了，确认后点「保存这些词」');
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

  // 侧栏的词条 / 生词：点一下就查词（用 document 代理，免得被模块内的 if 块挡住）
  document.addEventListener('click', (e) => {
    const row = e.target.closest('.word-row, .mark-row');
    if (!row || !window.__drOpenWord) return;
    const word = row.dataset.word || row.dataset.markWord;
    if (word) window.__drOpenWord(e.clientX, e.clientY, word);
  });

  bindPending();
  load();
})();
