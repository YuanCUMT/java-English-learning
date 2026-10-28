/**
 * 把 bcz-reader 里已经抽好的 ECDICT 子集（dict/ecdict.json.gz）
 * 导出成可以直接 mysql < 执行的 dict-data.sql。
 *
 * 用法： node tools/export-dict-sql.js
 * 产物： sql/dict-data.sql
 */
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

const ROOT = path.resolve(__dirname, '..');
// 优先用仓库自带的 dict/ecdict.json.gz；没有才回头找 bcz-reader 里那份
const SRC = [
  path.join(ROOT, 'dict', 'ecdict.json.gz'),
  path.resolve(ROOT, '..', 'bcz-reader', 'dict', 'ecdict.json.gz'),
].find((p) => fs.existsSync(p));
const OUT = path.join(ROOT, 'sql', 'dict-data.sql');

if (!SRC) {
  console.error('找不到 dict/ecdict.json.gz，请先把它放到项目的 dict/ 目录下');
  process.exit(1);
}

const raw = JSON.parse(zlib.gunzipSync(fs.readFileSync(SRC)));
const words = raw.words || {};
const forms = raw.forms || {};

// 与 lib/localdict.js 保持一致的词性识别
const POS_RE = /\b(n|v|vt|vi|a|adj|adv|prep|conj|pron|num|art|int|aux|abbr)\./g;
function posOf(cn) {
  const seen = new Map();
  for (const m of String(cn).matchAll(POS_RE)) seen.set(m[1], (seen.get(m[1]) || 0) + 1);
  let best = '';
  let max = 0;
  for (const [p, n] of seen) if (n > max) { best = p; max = n; }
  return best === 'a' ? 'adj.' : best ? `${best}.` : '';
}

/** 释义清洗：字面量 \r\n 还原成真换行，去掉 [网络] 这类词典域标注，压长度 */
function cleanCn(text) {
  return String(text || '')
    .replace(/\\r\\n|\\n|\\r/g, '\n')
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !/^\[(网络|计|化|医|法|经|机|数|物|生|农|军|体|心|哲|逻|语|史|地|天|电子)\]/.test(l))
    .join('\n')
    .slice(0, 1000);
}

const q = (v) => (v === null || v === undefined || v === '' ? 'NULL' : `'${String(v).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`);

const lines = [];
lines.push('-- 由 tools/export-dict-sql.js 自动生成，不要手改');
lines.push(`-- 词条 ${Object.keys(words).length}，变形 ${Object.keys(forms).length}`);
lines.push('SET NAMES utf8mb4;');
lines.push('SET FOREIGN_KEY_CHECKS = 0;');
lines.push('USE daily_reading;');
lines.push('TRUNCATE TABLE dict_entries;');
lines.push('TRUNCATE TABLE dict_forms;');
lines.push('');

function emit(table, cols, rows, batch = 500) {
  for (let i = 0; i < rows.length; i += batch) {
    const chunk = rows.slice(i, i + batch);
    lines.push(`INSERT INTO ${table} (${cols.join(',')}) VALUES`);
    lines.push(chunk.map((r) => `(${r.join(',')})`).join(',\n') + ';');
  }
}

// ---- 词条 ----
const entryRows = [];
for (const [word, w] of Object.entries(words)) {
  const cn = cleanCn(w.t);
  if (!cn) continue;
  let pos = '';
  const lead = cn.match(/^([a-z]{1,5})\.\s*/);
  if (lead) pos = `${lead[1]}.`;
  else pos = posOf(cn);
  const body = lead ? cleanCn(cn.slice(lead[0].length)) : cn;
  entryRows.push([
    q(word.toLowerCase()),
    q(w.p || ''),
    q(pos),
    q(body || cn),
    q(w.g || ''),
    w.c === undefined ? 'NULL' : Number(w.c),
    w.o ? 1 : 0,
  ]);
}
emit('dict_entries', ['word', 'phonetic', 'pos', 'translation', 'tag', 'collins', 'oxford'], entryRows);
lines.push('');

// ---- 变形 ----
const formRows = Object.entries(forms)
  .filter(([f, l]) => f && l && f !== l)
  .map(([f, l]) => [q(f.toLowerCase()), q(String(l).toLowerCase())]);
emit('dict_forms', ['form', 'lemma'], formRows);
lines.push('');

lines.push('SET FOREIGN_KEY_CHECKS = 1;');

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, lines.join('\n'), 'utf8');

const size = fs.statSync(OUT).size;
console.log(`已生成 ${OUT}`);
console.log(`  词条 ${entryRows.length} 行，变形 ${formRows.length} 行，文件 ${(size / 1024 / 1024).toFixed(1)} MB`);
