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

  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));

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

  function render(marks) {
    const learning = marks.filter((m) => m.status !== 'mastered').length;
    const mastered = marks.length - learning;
    statLearning.textContent = learning;
    statMastered.textContent = mastered;

    if (!marks.length) {
      list.innerHTML = '<p class="empty-hint">还没有生词。读文章时点任意单词，点「☆ 收藏」就会出现在这里。</p>';
      return;
    }
    const pick = (st) => marks.filter((m) => (m.status === 'mastered') === st);
    const learningList = pick(false);
    const masteredList = pick(true);

    let html = '';
    if (learningList.length) {
      html += `<h3 class="mk-sec">学习中 <span class="count">${learningList.length}</span></h3>`;
      html += learningList.map(card).join('');
    }
    if (masteredList.length) {
      html += `<h3 class="mk-sec">已掌握 <span class="count">${masteredList.length}</span></h3>`;
      html += masteredList.map(card).join('');
    }
    list.innerHTML = html;
  }

  /** 每日单词：一天一段，卡片只放词、音标、词性、释义 */
  function renderDays(days) {
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
        return `<section class="day">
          <h3 class="day-head">
            <span class="d">${esc(d.date)}</span>
            <span class="n">${d.count} 个</span>
          </h3>
          <div class="vcards">${cards}</div>
        </section>`;
      })
      .join('');
  }

  async function loadDays() {
    try {
      const data = await api('/api/words/history');
      renderDays((data && data.days) || []);
    } catch (err) {
      toast(err.message, true);
    }
  }

  function switchTab(name) {
    document.querySelectorAll('.vtab').forEach((b) => {
      b.classList.toggle('active', b.dataset.tab === name);
    });
    const daily = name !== 'mine';
    dailyPanel.hidden = !daily;
    markPanel.hidden = daily;
  }

  document.querySelectorAll('.vtab').forEach((b) => {
    b.addEventListener('click', () => switchTab(b.dataset.tab));
  });

  async function load() {
    try {
      const data = await api('/api/marks');
      render((data && data.marks) || []);
    } catch (err) {
      toast(err.message, true);
    }
  }

  list.addEventListener('click', async (e) => {
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
  });

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
})();
