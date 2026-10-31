/* 文章渲染 + 生成 + 点词查释义。原生 JS，没有构建步骤。 */
(() => {
  'use strict';

  const $ = (s, r = document) => r.querySelector(s);
  const box = $('#articleBox');
  const body = $('#articleBody');
  const loading = $('#articleLoading');
  const btnGen = $('#btnGenerate');
  const toastEl = $('#toast');

  let state = { article: null, showCn: false };
  let popover = null;
  let lastSel = '';
  let openedAt = 0;

  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));

  function toast(msg, isError) {
    if (!toastEl) return;
    toastEl.textContent = msg;
    toastEl.className = 'toast' + (isError ? ' error' : '');
    toastEl.hidden = false;
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { toastEl.hidden = true; }, 2600);
  }

  async function api(url, opts) {
    const res = await fetch(url, Object.assign({ headers: { 'Content-Type': 'application/json' } }, opts));
    const data = await res.json().catch(() => null);
    if (!res.ok || !data || data.code !== 0) {
      throw new Error((data && data.message) || `请求失败（HTTP ${res.status}）`);
    }
    return data.data;
  }

  // ---------- 高亮 ----------
  function targetPattern(words) {
    const list = (words || [])
      .map((w) => String(w || '').trim().toLowerCase())
      .filter((w) => w && /^[a-z][a-z'\- ]*$/.test(w));
    if (!list.length) return null;
    // 长的先匹配（短语优先），再加常见的屈折后缀
    const alts = [...new Set(list)]
      .sort((a, b) => b.length - a.length)
      .map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/ /g, '\\s+'));
    return new RegExp(`\\b(${alts.join('|')})(s|es|ed|d|ing)?\\b`, 'gi');
  }

  function highlight(text, re) {
    if (!re) return esc(text);
    let out = '';
    let last = 0;
    let m;
    re.lastIndex = 0;
    while ((m = re.exec(text))) {
      out += esc(text.slice(last, m.index));
      out += `<mark class="w" data-w="${esc(m[1].toLowerCase())}">${esc(m[0])}</mark>`;
      last = m.index + m[0].length;
    }
    out += esc(text.slice(last));
    return out;
  }

  // ---------- 渲染 ----------
  function render(article) {
    state.article = article;
    if (!article) {
      box.hidden = false;
      body.hidden = true;
      return;
    }
    const b = article.body || {};
    const paras = b.paragraphs || [];
    const words = b.words || [];
    const sents = b.sentences || [];
    const qs = b.questions || [];
    const re = targetPattern(words.map((w) => w.word));

    const ymd = article.date || '';
    const html = [];
    html.push(`<header class="art-head">
      <h2>${esc(b.title)}</h2>
      ${b.subtitle ? `<p class="art-sub">${esc(b.subtitle)}</p>` : ''}
      <div class="art-meta">
        <span class="pill">${article.mode === 'exam' ? '真题模式' : '复习模式'}</span>
        <span class="pill">${esc(ymd)}</span>
        <span class="pill">${esc(article.difficulty || '')}</span>
        <span class="pill">${paras.length} 段</span>
        <span class="pill">约 ${article.wordCount || 0} 词</span>
        <span class="pill">目标词 ${words.length} 个</span>
      </div>
      ${b.gist ? `<p class="art-gist">${esc(b.gist)}</p>` : ''}
      <div class="art-tools">
        <button class="tool-link" id="btnRead" type="button">▶ 朗读全文</button>
        <button class="tool-link" id="btnCn" type="button">显示中文</button>
      </div>
    </header>`);

    html.push('<div class="paras">');
    paras.forEach((p, i) => {
      html.push(`<div class="para">
        <span class="para-no">${String(i + 1).padStart(2, '0')}</span>
        <div class="para-main">
          <p class="en">${highlight(p.en || '', re)}</p>
          <p class="cn" hidden>${esc(p.cn || '')}</p>
          <div class="para-tools">
            <button class="tool-link sm" type="button" data-psay>▶ 朗读本段</button>
            <button class="tool-link sm" type="button" data-pcn>显示本段翻译</button>
          </div>
        </div>
      </div>`);
    });
    html.push('</div>');

    if (words.length) {
      html.push('<section class="art-sec"><h3>今日词汇</h3><div class="vgrid">');
      words.forEach((w) => {
        html.push(`<div class="vcard">
          <div class="vtop"><b>${esc(w.word)}</b>
            ${w.phonetic ? `<span class="ph">${esc(w.phonetic)}</span>` : ''}
            ${w.pos ? `<span class="pos">${esc(w.pos)}</span>` : ''}</div>
          <div class="vcn">${esc(w.cn || '')}</div>
          ${w.examSense ? `<div class="vsense"><i>常考</i>${esc(w.examSense)}</div>` : ''}
          ${w.collocation ? `<div class="vcol"><i>搭配</i>${esc(w.collocation)}</div>` : ''}
          ${w.example ? `<div class="vex">例：${esc(w.example)}</div>` : ''}
        </div>`);
      });
      html.push('</div></section>');
    }

    if (sents.length) {
      html.push('<section class="art-sec"><h3>长难句</h3>');
      sents.forEach((s, i) => {
        html.push(`<div class="lsent">
          <p class="ls-en">${esc(s.sent || '')}</p>
          <p class="ls-st"><i>结构</i>${esc(s.structure || '')}</p>
          <p class="ls-cn">${esc(s.meaning || '')}</p>
        </div>`);
      });
      html.push('</section>');
    }

    if (qs.length) {
      html.push('<section class="art-sec"><h3>理解题</h3><div class="qs">');
      qs.forEach((q, qi) => {
        html.push(`<div class="q" data-q="${qi}" data-answer="${esc(q.answer || '')}">
          <div class="q-head">${q.type ? `<span class="qtype">${esc(q.type)}</span>` : ''}
            <span class="qtext">${esc(q.q || '')}</span></div>
          <div class="q-opts">`);
        (q.options || []).forEach((o) => {
          const letter = String(o).trim().charAt(0).toUpperCase();
          html.push(`<button class="q-opt" type="button" data-k="${esc(letter)}">${esc(o)}</button>`);
        });
        html.push(`</div>
          <div class="q-exp" hidden>
            ${q.locate ? `<p class="q-locate"><i>原文定位</i>${esc(q.locate)}</p>` : ''}
            <p>${esc(q.explain || '')}</p>
          </div>
        </div>`);
      });
      html.push('</div></section>');
    }

    body.innerHTML = html.join('');
    box.hidden = true;
    body.hidden = false;
    bindArticle();
  }

  function bindArticle() {
    const btnCn = $('#btnCn');
    if (btnCn) {
      btnCn.onclick = () => {
        state.showCn = !state.showCn;
        body.querySelectorAll('.para .cn').forEach((el) => { el.hidden = !state.showCn; });
        btnCn.textContent = state.showCn ? '隐藏中文' : '显示中文';
        btnCn.classList.toggle('on', state.showCn);
        syncParaTools();
      };
      const btnRead = document.getElementById('btnRead');
      if (btnRead) btnRead.onclick = readArticle;
    }

    // 每一段自己的「朗读本段 / 显示本段翻译」
    body.querySelectorAll('.para').forEach((p) => {
      const say = p.querySelector('[data-psay]');
      if (say) say.onclick = (e) => {
        e.stopPropagation();
        readParagraph(p);
      };
      const cnBtn = p.querySelector('[data-pcn]');
      if (cnBtn) cnBtn.onclick = (e) => {
        e.stopPropagation();
        const cn = p.querySelector('.cn');
        cn.hidden = !cn.hidden;
        setParaCnLabel(p);
      };
    });
    syncParaTools();

    body.querySelectorAll('.q').forEach((q) => {
      q.querySelectorAll('.q-opt').forEach((btn) => {
        btn.onclick = () => {
          if (q.classList.contains('done')) return;
          q.classList.add('done');
          const picked = btn.dataset.k;
          const right = (q.dataset.answer || '').trim().toUpperCase().charAt(0);
          q.querySelectorAll('.q-opt').forEach((b) => {
            if (b.dataset.k === right) b.classList.add('right');
            else if (b === btn) b.classList.add('wrong');
          });
          q.querySelector('.q-exp').hidden = false;
        };
      });
    });

    body.querySelectorAll('mark.w').forEach((m) => {
      m.onclick = (e) => {
        e.stopPropagation();
        openWord(e.clientX, e.clientY, m.dataset.w);
      };
    });
  }

  // ---------- 查词浮层 ----------
  function closePopover() {
    if (popover) { popover.remove(); popover = null; }
  }

  function openWord(x, y, word) {
    closePopover();
    const el = document.createElement('div');
    el.className = 'popover';
    el.innerHTML = `<div class="pop-head"><b>${esc(word)}</b><span class="pop-text">查词中…</span></div>`;
    document.body.appendChild(el);
    popover = el;
    place(el, x, y);

    const load = (askAi) => api('/api/lookup?word=' + encodeURIComponent(word) + (askAi ? '&ai=1' : ''))
      .then((d) => {
        if (askAi) el.innerHTML = '<div class="pop-head"><b>' + esc(word) + '</b><span class="pop-text">问模型…</span></div>';
        // 后端 /api/lookup 返回的 data 本身就是词条（没有再包一层 entry），这里两种都兼容
        const e = d.entry || d || {};
        if (!e.cn && d && d.canAskModel) {
          el.innerHTML = `
            <div class="pop-head"><b>${esc(word)}</b><span class="pop-src">本地词典没有</span></div>
            <p class="pop-cn">这个词不在内置词典里。</p>
            <div class="pop-acts">
              <button class="btn primary sm" type="button" data-ai>问模型查一下</button>
            </div>`;
          el.querySelector('[data-ai]').addEventListener('click', (ev) => {
            ev.stopPropagation();
            load(true);
          });
          return;
        }
        el.innerHTML = `
          <div class="pop-head">
            <b>${esc(e.word || word)}</b>
            ${e.phonetic ? `<span class="ph">${esc(e.phonetic)}</span>` : ''}
            ${e.pos ? `<span class="pos">${esc(e.pos)}</span>` : ''}
            <span class="pop-src">${d.source === 'ecdict' ? '本地词典' : 'AI'}</span>
          </div>
          <p class="pop-cn">${esc(e.cn || '没有查到释义')}</p>
          ${e.collocation ? `<p class="pop-col"><i>搭配</i>${esc(e.collocation)}</p>` : ''}
          ${e.example ? `<p class="pop-ex">例：${esc(e.example)}</p>` : ''}
          ${e.tags && e.tags.length ? `<div class="pop-tags">${e.tags.map((t) => `<span>${esc(t)}</span>`).join('')}</div>` : ''}
          <div class="pop-acts">
            <button class="btn ghost sm" type="button" data-say>朗读</button>
            <button class="btn ghost sm" type="button" data-mark>☆ 收藏</button>
          </div>
        `;
        el.querySelector('[data-say]').addEventListener('click', (ev) => {
          ev.stopPropagation();
          speak(e.word || word);
        });
        el.querySelector('[data-mark]').addEventListener('click', async (ev) => {
          ev.stopPropagation();
          const btn = ev.currentTarget;
          btn.disabled = true;
          try {
            await api('/api/marks', {
              method: 'POST',
              body: JSON.stringify({
                word: e.word || word,
                lemma: e.lemma || e.word || word,
                phonetic: e.phonetic || '',
                pos: e.pos || '',
                cn: e.cn || '',
                examSense: e.examSense || '',
                collocation: e.collocation || '',
                example: e.example || '',
                note: e.note || '',
                source: d.source === 'model' ? 'model' : 'ecdict',
              }),
            });
            btn.textContent = '已在生词本';
            toast('已收进生词本');
          } catch (err) {
            btn.disabled = false;
            toast(err.message, true);
          }
        });
      })
      .catch((err) => {
        el.innerHTML = `<div class="pop-head"><b>${esc(word)}</b></div><p class="pop-cn">${esc(err.message)}</p>`;
      });

    load(false);
  }

  // 只念这一段；再点一次就停
  function readParagraph(p) {
    if (!('speechSynthesis' in window)) { toast('这个浏览器不支持朗读', true); return; }
    if (p.classList.contains('reading')) {
      if (reading) stopReading();
      speechSynthesis.cancel();
      p.classList.remove('reading');
      syncSayLabels();
      return;
    }
    if (reading) stopReading();
    speechSynthesis.cancel();
    document.querySelectorAll('.para.reading').forEach((x) => x.classList.remove('reading'));
    p.classList.add('reading');
    const u = new SpeechSynthesisUtterance(p.querySelector('.en').textContent.trim());
    u.lang = 'en-US';
    u.rate = 0.95;
    const clear = () => { p.classList.remove('reading'); syncSayLabels(); };
    u.onend = clear;
    u.onerror = clear;
    speechSynthesis.speak(u);
    syncSayLabels();
  }

  // 段落按钮的文案跟着“正在读哪段”走，读的时候变成「■ 停止」
  function syncSayLabels() {
    body.querySelectorAll('.para').forEach((p) => {
      const btn = p.querySelector('[data-psay]');
      if (btn) btn.textContent = p.classList.contains('reading') ? '■ 停止' : '▶ 朗读本段';
    });
  }

  function setParaCnLabel(p) {
    const btn = p.querySelector('[data-pcn]');
    if (!btn) return;
    const hidden = p.querySelector('.cn').hidden;
    btn.textContent = hidden ? '显示本段翻译' : '隐藏本段翻译';
    btn.classList.toggle('on', !hidden);
  }

  function syncParaTools() {
    body.querySelectorAll('.para').forEach(setParaCnLabel);
  }

  function speak(text) {
    if (!('speechSynthesis' in window)) {
      toast('这个浏览器不支持朗读', true);
      return;
    }
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'en-US';
    speechSynthesis.cancel();
    speechSynthesis.speak(u);
  }

  function place(el, x, y) {
    document.body.appendChild(el);
    const r = el.getBoundingClientRect();
    let left = x + 12;
    let top = y + 14;
    if (left + r.width > window.innerWidth - 12) left = window.innerWidth - r.width - 12;
    if (top + r.height > window.innerHeight - 12) top = Math.max(12, y - r.height - 12);
    el.style.left = left + 'px';
    el.style.top = top + 'px';
  }

  document.addEventListener('click', (e) => {
    // 划词查词是在 mouseup 里开的，紧接着的 click 别把刚开的浮层关掉
    if (popover && !popover.contains(e.target) && !e.target.closest('mark.w') && Date.now() - openedAt > 250) closePopover();
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closePopover(); });

  // ---------- 划词查词 ----------
  // 点单词能查，但很多生词不在目标词表里、也不一定是单个词，所以这里支持拖选后再查。
  body.addEventListener('mouseup', () => {
    setTimeout(() => {
      const sel = window.getSelection();
      if (!sel || sel.isCollapsed) return;
      const raw = sel.toString().replace(/\s+/g, ' ').trim();
      if (!raw) return;
      if (!/^[A-Za-z][A-Za-z'\- ]*$/.test(raw)) return; // 含数字/中文的就不当词查
      if (raw.length > 60 || raw.split(' ').length > 8) {
        toast('选得太长了，一次最多 8 个词', true);
        return;
      }
      const range = sel.getRangeAt(0);
      const rect = range.getBoundingClientRect();
      openedAt = Date.now();
      openWord(rect.left, rect.bottom - 4, raw.toLowerCase());
    }, 0);
  });

  // 朗读整篇：一段一段念，念到哪段就高亮哪段
  let reading = false;
  function stopReading() {
    reading = false;
    if ('speechSynthesis' in window) speechSynthesis.cancel();
    body.querySelectorAll('.para.reading').forEach((p) => p.classList.remove('reading'));
    const b = $('#btnRead');
    if (b) b.textContent = '▶ 朗读全文';
  }

  function readArticle() {
    const btn = $('#btnRead');
    if (reading) { stopReading(); return; }
    if (!('speechSynthesis' in window)) { toast('这个浏览器不支持朗读', true); return; }
    const paras = [...body.querySelectorAll('.para')];
    if (!paras.length) return;
    reading = true;
    if (btn) btn.textContent = '■ 停止朗读';
    syncSayLabels();
    let i = 0;
    const next = () => {
      if (!reading) return;
      if (i >= paras.length) { stopReading(); return; }
      paras.forEach((p) => p.classList.remove('reading'));
      const p = paras[i];
      p.classList.add('reading');
      p.scrollIntoView({ block: 'center', behavior: 'smooth' });
      const u = new SpeechSynthesisUtterance(p.querySelector('.en').textContent.trim());
      u.lang = 'en-US';
      u.rate = 0.95;
      u.onend = () => { i += 1; next(); };
      u.onerror = () => { i += 1; next(); };
      speechSynthesis.speak(u);
    };
    next();
  }

  // ---------- 生成 ----------
  async function generate() {
    if (!btnGen) return;
    btnGen.disabled = true;
    box.hidden = true;
    body.hidden = true;
    loading.hidden = false;
    $('#articleLoadingHint').textContent = '正在写文章，大约 20–40 秒……';
    try {
      const modeSel = $('#genMode');
      const diffSel = $('#genDifficulty');
      const data = await api('/api/articles/generate', {
        method: 'POST',
        body: JSON.stringify({
          mode: (modeSel && modeSel.value) || 'review',
          difficulty: (diffSel && diffSel.value) || 'C1',
        }),
      });
      render(data);
      toast('文章生成好了');
    } catch (err) {
      toast(err.message, true);
      box.hidden = !!state.article;
      if (!state.article) box.hidden = false;
    } finally {
      loading.hidden = true;
      btnGen.disabled = false;
    }
  }

  if (btnGen) {
    btnGen.addEventListener('click', (e) => {
      e.preventDefault();
      generate();
    });
  }

  // 快捷键：1–4 选答案，c 切中文，Esc 关浮层（Esc 在别处已绑）
  document.addEventListener('keydown', (e) => {
    if (e.target && /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
    if (e.ctrlKey || e.altKey || e.metaKey) return;

    if (e.key >= '1' && e.key <= '4') {
      const qs = [...body.querySelectorAll('.q')];
      const cur = qs.find((q) => !q.classList.contains('done'));
      const opt = cur && cur.querySelectorAll('.q-opt')[Number(e.key) - 1];
      if (opt) {
        opt.click();
        e.preventDefault();
      }
      return;
    }

    if (e.key === 'c' || e.key === 'C') {
      const btnCn = $('#btnCn');
      if (btnCn) {
        btnCn.click();
        e.preventDefault();
      }
    }
  });

  // 首次加载：历史文章读 ?id，首页读今天
  const articleId = document.body.getAttribute('data-article-id');
  const url = articleId ? `/api/articles/${articleId}` : '/api/articles/today';
  api(url)
    .then((d) => render(d || null))
    .catch(() => render(null));
})();
