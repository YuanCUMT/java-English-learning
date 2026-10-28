# 本地词典（ECDICT 子集）

`ecdict.json.gz`（0.8 MB）是从 [ECDICT](https://github.com/skywind3000/ECDICT) 抽出来的子集，
用于「阅读时点词即查」和「单词本补释义」，**不需要 API Key、不联网、毫秒级**。

## 来源与许可证

- 上游：`skywind3000/ECDICT`（77 万词条）
- 许可证：**MIT**，Copyright (c) 2025 Linwei
- 本目录下的文件是其**子集的再分发**，同样遵循 MIT

## 抽了哪些

从 77 万条里保留 **21,699 条**，规则（`tools/build-dict.js`）：

| 条件 | 说明 |
| --- | --- |
| `tag` 含 `zk/gk/ky/cet4/cet6/toefl/ielts/gre` | 各类考试大纲词 |
| 或 `oxford = 1` | 牛津 3000 核心词 |
| 或 `collins ≥ 3` | 柯林斯三星以上常用词 |
| 或 `frq ≤ 20000` | 词频前两万 |
| 排除带空格的多词条目 | 短语交给模型，词典查不到 |

另外从 `exchange` 字段反推出 **27,278 条变形索引**（`takes → take`、`running → run`），
所以在文章里点到变形也能查到期。

## 覆盖情况

拿仓库里那 330 个真实背过的词测：**318 个命中（96.4%）**。
漏掉的 12 个都是新造词或现代复合词（`multitask`、`mindset`、`brainstorm`、`unreadable` 一类），
这些仍然会落到模型。

## 条目形状

```json
{ "words": { "ledger": { "t": "分类帐, 分户总帐…", "p": "ˈledʒə", "s": "n:100", "g": "gre", "c": 1, "o": 1 } },
  "forms": { "takes": "take" } }
```

- `t` 释义，`p` 音标（已从老式 ASCII 记法转成 IPA 字符），`s` 词性比例，`g` 考试标签，`c` 柯林斯星级，`o` 牛津标记

## 重新生成

```bash
git clone --depth 1 --filter=blob:none --no-checkout https://github.com/skywind3000/ECDICT.git _dict
cd _dict && git checkout HEAD -- ecdict.csv
node tools/build-dict.js _dict/ecdict.csv     # 输出到 dict/ecdict.json.gz
```

想换别的词典，只要产出同样的 `{ words, forms }` 结构即可，`lib/localdict.js` 不用改。
