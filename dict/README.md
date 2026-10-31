# 本地词典（ECDICT 子集）

`ecdict.json.gz`（0.8 MB）是我从 [ECDICT](https://github.com/skywind3000/ECDICT) 抽出来的子集，
用来支撑「阅读时点词即查」和「单词本补释义」：**不需要 API Key、不联网、毫秒级出结果**。

## 来源与许可证

- 上游：`skywind3000/ECDICT`（77 万词条）
- 许可证：**MIT**，Copyright (c) 2025 Linwei
- 本目录下的文件是它的**子集再分发**，同样遵循 MIT

## 抽了哪些

从 77 万条里保留 **21,699 条**，规则如下：

| 条件 | 说明 |
| --- | --- |
| `tag` 含 `zk/gk/ky/cet4/cet6/toefl/ielts/gre` | 各类考试大纲词 |
| 或 `oxford = 1` | 牛津 3000 核心词 |
| 或 `collins ≥ 3` | 柯林斯三星以上常用词 |
| 或 `frq ≤ 20000` | 词频前两万 |
| 排除带空格的多词条目 | 短语交给模型，词典里本来就没有 |

另外我从 `exchange` 字段反推出 **27,278 条变形索引**（`takes → take`、`running → run`），
所以在文章里点到变形也能查到期。

导进 MySQL 后实际是 **21,339 行 `dict_entries` + 27,278 行 `dict_forms`** ——
比 JSON 里少的那 360 条是原形重复、被主键去重了。

## 覆盖情况

拿我自己攒的那 330 个真实背过的词测：**318 个命中（96.4%）**。
漏掉的 12 个都是新造词或现代复合词（`multitask`、`mindset`、`brainstorm` 一类），
这些仍然会落到 DeepSeek。

## 条目形状

```json
{ "words": { "ledger": { "t": "分类帐, 分户总帐…", "p": "ˈledʒə", "s": "n:100", "g": "gre", "c": 1, "o": 1 } },
  "forms": { "takes": "take" } }
```

- `t` 释义，`p` 音标（已从老式 ASCII 记法转成 IPA 字符），`s` 词性比例，`g` 考试标签，`c` 柯林斯星级，`o` 牛津标记

## 怎么进数据库

`ecdict.json.gz` 在这个仓库里只是**源数据**，真正查词走的是 MySQL 里的两张表：

```bash
node tools/export-dict-sql.js          # 读 dict/ecdict.json.gz，生成 sql/dict-data.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/dict-data.sql
```

生成的 `sql/dict-data.sql` 约 2.6 MB，是构建产物，没有进仓库。

## 关于重新抽取

原始 77 万条的 `ecdict.csv`（66 MB）太大，没有放进仓库；上面那个 gz 是我一次性抽好的结果。
抽取脚本留在我上一版 Node 项目里（`tools/build-dict.js`），这个 Java 仓库只做「gz → SQL」这一步。
真要重抽，把同一份 `ecdict.csv` 按上面的过滤规则跑一遍、产出同样的 `{ words, forms }` 结构即可，
`DictService` 不用改。