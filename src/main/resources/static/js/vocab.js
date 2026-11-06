'use strict';
/* 单词本页：两个列表——每日单词（按天分组，带词典释义）、我的生词（学习中 / 已掌握）。 */
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const list = $('#markList');
  const toastEl = $('#toast');
  const statLearning = $('#statLearning');
  const statMastered = $('#statMastered');
  const dayList = $('#dayList');
  const dailyPanel = $('#dailyPanel');
  const markPanel = $('#markPanel');
  const tabDailyCount = $('#tabDailyCount');
  const tabMineCount = $('#tabMineCount');
const phrasePanel = $('#phrasePanel');
const phraseList = $('#phraseList');
const tabPhraseCount = $('#tabPhraseCount');
  const sortSel = $('#vocabSort');
  const searchInput = $('#vocabSearch');
  const mkSwitch = $('#mkSwitch');
  const mkCountLearning = $('#mkCountLearning');
  const mkCountMastered = $('#mkCountMastered');

  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));

  let allDays = [];
  // 我的生词：两个快捷视图（学习中 / 已掌握），各自记一份排序
  let mkView = 'learning';
  let activeTab = 'daily';
  const sortMemory = { daily: 'time-desc', 'marks:learning': 'time-desc', 'marks:mastered': 'time-desc', phrases: 'time-desc' };
  const sortSlot = () => (activeTab === 'daily' ? 'daily' : activeTab === 'phrases' ? 'phrases' : 'marks:' + mkView);
  const currentSort = () => (sortSel && sortSel.value) || sortMemory[sortSlot()] || 'time-desc';
  function syncSortUi() {
    if (sortSel) sortSel.value = sortMemory[sortSlot()] || 'time-desc';
  }
  let allMarks = [];
  let toastTimer = null;
  function toast(msg, isError) {
    toastEl.textContent = msg;
    toastEl.classList.toggle('error', !!isError);
    toastEl.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toastEl.hidden = true; }, 2600);
  }

  async function api(url, opts) {
    const res = await fetch(url, {
      headers: { 'Content-Type': 'application/json' },
      ...opts,
    });
    let data = null;
    try { data = await res.json(); } catch { /* 忽略 */ }
    if (!res.ok) throw new Error((data && data.message) || `请求失败（${res.status}）`);
    if (data && data.code !== 0) throw new Error(data.message || '请求失败');
    return data ? data.data : null;
  }

  function card(m) {
    const date = (m.addedAt || '').slice(0, 10);
    const mastered = m.status === 'mastered';
    const subs = [
      m.collocation ? `<div class="mk-sub"><i>搭配</i>${esc(m.collocation)}</div>` : '',
      m.examSense ? `<div class="mk-sub"><i>考研常考</i>${esc(m.examSense)}</div>` : '',
      m.note ? `<div class="mk-sub mk-note">${esc(m.note)}</div>` : '',
    ].join('');
    return `<article class="mark-card${mastered ? ' is-mastered' : ''}" data-id="${m.id}">
      <header class="mk-top">
        <span class="w">${esc(m.word)}</span>
        ${m.phonetic ? `<span class="ph">${esc(m.phonetic)}</span>` : ''}
        ${m.pos ? `<span class="pos">${esc(m.pos)}</span>` : ''}
      </header>
      ${m.cn ? `<p class="m">${esc(m.cn)}</p>` : ''}
      ${subs}
      <footer class="mk-foot">
        <span class="mk-date">收于 ${esc(date)}</span>
        <span class="mk-acts">
          <button class="btn ghost sm" type="button" data-act="say">朗读</button>
          <button class="btn ghost sm" type="button" data-act="${mastered ? 'learning' : 'mastered'}">
            ${mastered ? '退回学习' : '已掌握'}
          </button>
          <button class="btn ghost sm" type="button" data-act="remove">删除</button>
        </span>
      </footer>
    </article>`;
  }

  function render(marks, noMatch) {
    // 顶部统计永远是「全部生词」的口径，搜索只筛下面的列表，不改总数
    const total = allMarks.filter((m) => !/\s/.test(m.word || ''));
    const learning = total.filter((m) => m.status !== 'mastered').length;
    const mastered = total.length - learning;
    statLearning.textContent = learning;
    statMastered.textContent = mastered;

    if (!marks.length) {
      list.innerHTML = noMatch
        ? '<p class="empty-hint">没有匹配的生词。</p>'
        : '<p class="empty-hint">还没有生词。读文章时点任意单词，点「☆ 收藏」就会出现在这里。</p>';
      return;
    }
    const pick = (st) => marks.filter((m) => (m.status === 'mastered') === st);
    const learningList = pick(false);
    const masteredList = pick(true);
    if (mkCountLearning) mkCountLearning.textContent = learningList.length;
    if (mkCountMastered) mkCountMastered.textContent = masteredList.length;

    const shown = mkView === 'mastered' ? masteredList : learningList;
    const label = mkView === 'mastered' ? '已掌握' : '学习中';
    if (!shown.length) {
      list.innerHTML = `<p class="empty-hint">${label}里还没有词。</p>`;
      renderPhrases(marks);
      return;
    }
    list.innerHTML =
      `<h3 class="mk-sec">${label} <span class="count">${shown.length}</span></h3>` +
      shown.map(card).join('');

    renderPhrases(marks);
  }

  /** 短语：收藏过的多词表达（词里带空格），单独一栏 */
  function renderPhrases(marks, noMatch) {
    const phrases = marks.filter((m) => /\s/.test(m.word || ''));
    if (tabPhraseCount) tabPhraseCount.textContent = phrases.length;
    if (!phrases.length) {
      phraseList.innerHTML = noMatch
        ? '<p class="empty-hint">没有匹配的短语。</p>'
        : '<p class="empty-hint">还没有短语。读文章时拖选几个词（比如 in terms of），点「☆ 收藏短语」就会收进来。</p>';
      return;
    }
    phraseList.innerHTML =
      `<h3 class="mk-sec">短语 <span class="count">${phrases.length}</span></h3>` +
      phrases.map(card).join('');
  }

  /** 每日单词：一天一段，卡片只放词、音标、词性、释义 */
  function renderDays(days, flatMode) {
    const total = days.reduce((n, d) => n + d.count, 0);
    if (tabDailyCount) tabDailyCount.textContent = total;
    if (!days.length) {
      dayList.innerHTML = '<p class="empty-hint">还没有导入过单词。回首页用「选图识别」或「粘贴导入」加一批。</p>';
      return;
    }
    dayList.innerHTML = days
      .map((d) => {
        const cards = d.words
          .map(
            (w) => `<article class="vcard">
              <header class="v-top">
                <span class="w">${esc(w.word)}</span>
                ${w.phonetic ? `<span class="ph">${esc(w.phonetic)}</span>` : ''}
                ${w.pos ? `<span class="pos">${esc(w.pos)}</span>` : ''}
              </header>
              ${w.cn ? `<p class="cn">${esc(w.cn)}</p>` : ''}
            </article>`
          )
          .join('');
        if (flatMode) {
          return `<section class="day">
          <h3 class="day-head">
            <span class="d">全部单词</span>
            <span class="n">${d.count} 个</span>
          </h3>
          <div class="vcards">${cards}</div>
        </section>`;
        }
        return `<section class="day">
          <h3 class="day-head">
            <span class="d">${esc(d.date)}</span>
            <span class="n">${d.count} 个</span>
          </h3>
          <div class="vcards">${cards}</div>
        </section>`;
      })
      .join('');
    paintFillButton(days);
  }

  // 内置词典查不到的词：给一个「补全释义」按钮，一次最多补 40 个（走模型，会花钱，所以要点一下才动）
  let fillBtn = null;
  function paintFillButton(days) {
    const missing = [];
    days.forEach((d) => d.words.forEach((w) => { if (!w.cn) missing.push(w.word); }));
    if (!missing.length) {
      if (fillBtn) { fillBtn.remove(); fillBtn = null; }
      return;
    }
    if (!fillBtn) {
      fillBtn = document.createElement('button');
      fillBtn.className = 'btn ghost';
      fillBtn.type = 'button';
      fillBtn.id = 'btnFillMeaning';
      const tools = document.querySelector('.vocab-tools');
      if (tools) tools.appendChild(fillBtn); else return;
      fillBtn.onclick = () => fillMeanings(fillBtn.dataset.words.split(','));
    }
    fillBtn.dataset.words = missing.slice(0, 40).join(',');
    fillBtn.textContent = '补全释义（' + missing.length + ' 个）';
    autoFillOnce(missing);
  }

  // 进了单词本就别让缺口留着：自动补，一次最多 40 个，最多跑 3 轮
  // （补完 missing 会变少甚至归零，自然就停了；坏了还有上面那个按钮兜底）
  let autoRounds = 0;
  function autoFillOnce(missing) {
    if (autoRounds >= 3) return;
    autoRounds++;
    setTimeout(() => fillMeanings(missing.slice(0, 40)), 250);
  }

  async function fillMeanings(words) {
    if (!words.length) return;
    fillBtn.disabled = true;
    const old = fillBtn.textContent;
    fillBtn.textContent = '正在补 ' + words.length + ' 个…';
    try {
      const body = await api('/api/words/fill-meanings', { method: 'POST', body: JSON.stringify({ words }) });
      const n = body.data.filled || 0;
      const removed = Array.isArray(body.data.removed) ? body.data.removed : [];
      const parts = [];
      if (n) parts.push('补好了 ' + n + ' 个词的释义');
      if (removed.length) parts.push('删掉 ' + removed.length + ' 个认错的词（' + removed.slice(0, 4).join('、') + (removed.length > 4 ? '…' : '') + '）');
      toast(parts.length ? parts.join('，') : '模型没返回新的释义，稍后再试');
      await loadDays();
    } catch (e) {
      toast(e.message, true);
    } finally {
      if (fillBtn) { fillBtn.disabled = false; fillBtn.textContent = old; }
    }
  }

  async function loadDays() {
    try {
      const data = await api('/api/words/history');
      allDays = (data && data.days) || [];
      paintDays();
    } catch (err) {
      toast(err.message, true);
    }
  }

  /// 排序 + 搜索都在前端做：一个人导入过的词，几千条以内完全够用
  function paintDays() {
    const q = (searchInput && searchInput.value ? searchInput.value : '').trim().toLowerCase();
    const mode = currentSort();

    let days = allDays.map((d) => ({
      ...d,
      words: (d.words || []).filter((w) => !q
        || String(w.word || '').toLowerCase().includes(q)
        || String(w.cn || '').toLowerCase().includes(q)),
    }));
    days = days.filter((d) => d.words.length);
    days.forEach((d) => { d.count = d.words.length; });

    if (mode === 'time-asc') {
      days = days.slice().reverse();
    } else if (mode === 'alpha') {
      const flat = [];
      days.forEach((d) => d.words.forEach((w) => flat.push(w)));
      flat.sort((a, b) => String(a.word).localeCompare(String(b.word), 'en'));
      days = flat.length ? [{ date: '', count: flat.length, words: flat }] : [];
    }
    if (!days.length && q) {
      dayList.innerHTML = '<p class="empty-hint">没有匹配的词。</p>';
      return;
    }
    renderDays(days, mode === 'alpha');
  }

  function switchTab(name) {
    document.querySelectorAll('.vtab').forEach((b) => {
      b.classList.toggle('active', b.dataset.tab === name);
    });
    dailyPanel.hidden = name !== 'daily';
    markPanel.hidden = name !== 'mine';
    phrasePanel.hidden = name !== 'phrases';
    activeTab = name;
    syncSortUi();
  }

  // 学习中 / 已掌握 快捷切换：每个视图各自记排序
  document.querySelectorAll('[data-mkview]').forEach((b) => {
    b.addEventListener('click', () => {
      mkView = b.dataset.mkview;
      document.querySelectorAll('[data-mkview]').forEach((x) => {
        x.classList.toggle('is-on', x === b);
      });
      syncSortUi();
      paintMarks();
    });
  });

  document.querySelectorAll('.vtab').forEach((b) => {
    b.addEventListener('click', () => switchTab(b.dataset.tab));
  });

  function timeOf(x) {
    return String(x.addedAt || x.updatedAt || x.createdAt || '');
  }

  function sortList(items, key) {
    const mode = currentSort();
    const arr = items.slice();
    if (mode === 'alpha') {
      arr.sort((a, b) => String(a[key]).localeCompare(String(b[key]), 'en'));
    } else if (mode === 'time-asc') {
      arr.sort((a, b) => timeOf(a).localeCompare(timeOf(b)));
    } else {
      arr.sort((a, b) => timeOf(b).localeCompare(timeOf(a)));
    }
    return arr;
  }

  function paintMarks() {
    const q = (searchInput && searchInput.value ? searchInput.value : '').trim().toLowerCase();
    const hit = allMarks.filter((m) => !q
      || String(m.word || '').toLowerCase().includes(q)
      || String(m.cn || '').toLowerCase().includes(q));
    render(sortList(hit, 'word'), Boolean(q) && !hit.length);
  }

  function paintPhrases() {
    const q = (searchInput && searchInput.value ? searchInput.value : '').trim().toLowerCase();
    const all = allMarks.filter((m) => /\s/.test(m.word || ''));
    const hit = all.filter((m) => !q
      || String(m.word || '').toLowerCase().includes(q)
      || String(m.cn || '').toLowerCase().includes(q));
    renderPhrases(sortList(hit, 'word'), Boolean(q) && !hit.length);
  }
  function paintAll() {
    paintDays();
    paintMarks();
    paintPhrases();
  }

  if (sortSel) {
    sortSel.addEventListener('change', () => {
      sortMemory[sortSlot()] = sortSel.value;
      paintAll();
    });
  }
  if (searchInput) searchInput.addEventListener('input', paintAll);

  async function load() {
    try {
      const data = await api('/api/marks');
      allMarks = (data && data.marks) || [];
      paintMarks();
      paintPhrases();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function onMarkClick(e) {
    const btn = e.target.closest('[data-act]');
    if (!btn) return;
    const cardEl = e.target.closest('.mark-card');
    const id = cardEl && cardEl.dataset.id;
    if (!id) return;
    const act = btn.dataset.act;

    try {
      if (act === 'remove') {
        if (!confirm('从生词本里删掉这个词？')) return;
        await api(`/api/marks/${id}`, { method: 'DELETE' });
        toast('已删除');
      } else if (act === 'say') {
        const word = cardEl.querySelector('.w').textContent;
        if ('speechSynthesis' in window) {
          const u = new SpeechSynthesisUtterance(word);
          u.lang = 'en-US';
          speechSynthesis.cancel();
          speechSynthesis.speak(u);
        } else {
          toast('这个浏览器不支持朗读', true);
        }
        return;
      } else {
        await api(`/api/marks/${id}/status`, {
          method: 'PATCH',
          body: JSON.stringify({ status: act }),
        });
        toast(act === 'mastered' ? '已移到「已掌握」' : '已退回「学习中」');
      }
      await load();
    } catch (err) {
      toast(err.message, true);
    }
  }

  list.addEventListener('click', onMarkClick);
  phraseList.addEventListener('click', onMarkClick);

  $('#btnClearAll').addEventListener('click', async () => {
    if (!confirm('确定清空全部生词？这会删掉学习和已掌握两摞。')) return;
    try {
      await api('/api/marks?status=all', { method: 'DELETE' });
      toast('生词本已清空');
      await load();
    } catch (err) {
      toast(err.message, true);
    }
  });

  loadDays();
  load();

  // 支持 /vocab?tab=phrases 直达某个标签（文档和 README 里会用到）
  const wanted = new URLSearchParams(location.search).get('tab');
  if (wanted && ['daily', 'mine', 'phrases'].includes(wanted) && wanted !== 'daily') {
    switchTab(wanted);
  }
})();
