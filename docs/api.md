# 接口文档

基础地址：`http://127.0.0.1:8080`

> 安全相关的做法：密码用 BCrypt 存；API Key 在库里用 AES-GCM 加密（`enc:v1:` 前缀，密钥在 `data/secret.key`，读到老明文会自动加密写回）；
> 登录失败 5 次锁 5 分钟（按「用户名 + IP」计数）；退出只接受 POST，避免被链接预览、浏览器预取之类的东西「顺手登出」。

## 通用约定

**统一响应结构**

```json
{ "code": 0, "message": "ok", "data": { } }
```

`code = 0` 表示成功，其余为失败；失败时 `data` 为 `null`，`message` 是可以直接给用户看的中文。

| code | 含义 |
|---|---|
| 0 | 成功 |
| 400 | 参数不合法（缺字段、日期格式错、单词为空……） |
| 401 | 未登录 |
| 404 | 资源不存在 |
| 500 | 服务端错误，或外部服务（DeepSeek / OCR）出错 |

**登录态**

登录成功后服务端下发 `DR_SESSION` Cookie（`HttpOnly` + `SameSite=Lax`，只走 Cookie，不往 URL 上拼 jsessionid）。

- 页面路由未登录 → `302` 跳 `/login`
- `/api/**` 未登录 → `{"code":401,"message":"请先登录"}`

**免登录接口**：`/login`、`/register`、`/api/health`。

**curl 带登录态**

```bash
curl -s -c jar.txt -o /dev/null -d "username=tester&password=test1234" http://127.0.0.1:8080/login
curl -s -b jar.txt http://127.0.0.1:8080/api/marks
```

## 页面路由（服务端渲染，Thymeleaf）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 首页：今日单词 + 今日文章 |
| GET | `/login` | 登录页 |
| POST | `/login` | 提交登录（表单 `username` / `password`），成功跳 `/` |
| GET | `/register` | 注册页 |
| POST | `/register` | 提交注册（`username` / `password` / `confirm`） |
| GET | `/logout` | 退出，清 session |
| GET | `/vocab` | 单词本：学习中 / 已掌握两摞 |
| GET | `/archive` | 历史文章列表 |
| GET | `/article/{id}` | 读某一篇；不存在或不属于当前用户 → 302 回 `/archive` |

## API 一览

| 分组 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 系统 | GET | `/api/health` | 健康检查 |
| 词典 | GET | `/api/lookup?word=` | 查词（含词形还原） |
| 词典 | GET | `/api/dict/size` | 词条总数 |
| 单词 | GET | `/api/words?date=` | 某天的作业词（默认今天） |
| 单词 | POST | `/api/words` | 粘贴文本导入 |
| 单词 | POST | `/api/words/ocr` | 上传图片识别导入（multipart） |
| 单词 | POST | `/api/words/fill-meanings` | 给内置词典查不到的词补释义（一次最多 40 个）；模型判定不是词的碎片会被顺手删掉；`{"all":true}` 会扫该用户全部天数，返回 `scanned / filled / removed / remaining` |
| 单词 | DELETE | `/api/words?date=` | 清空某天（默认今天） |
| 生词本 | GET | `/api/marks` | 全部生词 + 两摞计数 |
| 生词本 | POST | `/api/marks` | 收藏（已存在则更新） |
| 生词本 | PATCH | `/api/marks/{id}/status` | 标为已掌握 / 退回学习 |
| 生词本 | DELETE | `/api/marks/{id}` | 删除一条 |
| 生词本 | DELETE | `/api/marks?status=` | 清空一摞，`status=all` 全清 |
| 文章 | GET | `/api/articles/status` | 模型是否已配置 |
| 文章 | GET | `/api/articles/today` | 今天这篇文章（没有则 `data:null`） |
| 文章 | GET | `/api/articles/{id}` | 按 id 取文章 |
| 文章 | POST | `/api/articles/generate` | 生成文章（调 DeepSeek，较慢） |

---

## 系统

### GET /api/health

```json
{ "code": 0, "message": "ok", "data": { "status": "up", "app": "daily-reading" } }
```

## 词典

### GET /api/lookup?word=takes

先查 `lookup_cache` → 再查本地词典（`dict_forms` 还原词形后查 `dict_entries`）→ 都没有才问模型。

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "word": "take",
    "surface": "takes",
    "phonetic": "/teik/",
    "pos": "vt.",
    "cn": "拿, 取, 抓, 带领, 获得, 就座, 接受, 吃, 吸引, 采取, 乘, 需要, 花费",
    "tags": ["zk", "gk", "ielts"],
    "collins": 1,
    "oxford": 1,
    "source": "ecdict"
  }
}
```

- `word` 是词典原形，`surface` 是用户传进来的形态（`takes` → `take`）
- `source`：`ecdict` 本地词典 / `model` 模型查的
- `tags`：`zk` 中考、`gk` 高考、`ky` 考研、`cet4/6` 四六级、`toefl/ielts/gre`
- 查不到 → `{"code":404,"message":"词典里没有这个词"}`

### GET /api/dict/size

```json
{ "code": 0, "message": "ok", "data": { "entries": 21339 } }
```

## 单词

### GET /api/words?date=2026-10-31

`date` 可省略（默认今天）。

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "date": "2026-10-31",
    "count": 0,
    "words": []
  }
}
```

`engine` 是当前生效的识别引擎（`mock` / `baidu 高精度版` / `tesseract（离线，不用账号）`），前端显示在「选图识别」按钮下面。

### POST /api/words

```json
{ "text": "abandon 放弃\nharvest 收成\nreluctant adj. 不情愿的", "date": "2026-10-31" }
```

`text` 里只抽英文词，中文和标点忽略；`date` 可省略。

```json
{ "code": 0, "message": "ok", "data": { "added": 3, "skipped": 0, "total": 3, "words": ["abandon", "harvest", "reluctant"] } }
```

`skipped` 是这一天已经有的词（`uk_words_user_date_word` 去重，不会覆盖）。

### POST /api/words/ocr

`multipart/form-data`，字段名 `file`。可选 `date`，可选 `save`（默认 `true`）。

- `save=true`：识别完直接入库，返回 `added / skipped / total / words / engine`
- `save=false`：**只识别不入库**，返回 `{words, total, engine, degraded, cached, dropped}` 给前端做候选词确认；
  `dropped` 是被判为「截图认错的碎片」而自动去掉的词（如 `bl`、`kb`）

```bash
# 直接入库
curl -b jar.txt -F "file=@word-card.png" http://127.0.0.1:8080/api/words/ocr

# 只拿候选词，确认后用户自己点保存
curl -b jar.txt -F "file=@word-card.png" "http://127.0.0.1:8080/api/words/ocr?save=false"
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "added": 7,
    "skipped": 0,
    "total": 7,
    "words": ["abandon", "harvest", "modest", "procedure", "reluctant", "vivid"],
    "engine": "baidu 高精度版",
    "degraded": false,
    "cached": false
  }
}
```

图片为空 / 不是图片 → `400`；识别引擎报错（比如百度配额用完）→ `500`，`message` 里带外部服务的原始错误。

### DELETE /api/words?date=2026-10-31

```json
{ "code": 0, "message": "ok", "data": { "deleted": 19, "date": "2026-10-31" } }
```

## 生词本

### GET /api/marks

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "marks": [
      {
        "id": 3,
        "userId": 3,
        "word": "in terms of",
        "lemma": null,
        "phonetic": null,
        "pos": "phr.",
        "cn": "就……而言；在……方面",
        "examSense": null,
        "collocation": "in terms of sth",
        "example": null,
        "note": "文中表示评判维度",
        "status": "learning",
        "source": "model",
        "addedAt": "2026-10-30T21:14:18"
      }
    ],
    "learning": 2,
    "mastered": 1
  }
}
```

### POST /api/marks

只 `word` 必填，其余可选。

```json
{
  "word": "ledger",
  "phonetic": "/ˈledʒə/",
  "pos": "n.",
  "cn": "账本；分布式账本",
  "examSense": "常与 blockchain/distributed 搭配",
  "collocation": "distributed ledger",
  "note": "文中指区块链上的记账本"
}
```

```json
{ "code": 0, "message": "ok", "data": { "mark": { "id": 2, "word": "ledger", "status": "learning", "...": "..." } } }
```

同一个词再收藏是**更新**（`uk_marks_user_word`），不是新增一条。`word` 为空 → `400`。

### PATCH /api/marks/{id}/status

```json
{ "status": "mastered" }
```

`status` 只接受 `learning` / `mastered`，其他值 → `400`。

### DELETE /api/marks/{id}

```json
{ "code": 0, "message": "ok", "data": null }
```

### DELETE /api/marks?status=learning

`status` 默认 `learning`；传 `all` 清空整本。

```json
{ "code": 0, "message": "ok", "data": { "deleted": 2 } }
```

## 文章

### GET /api/articles/status

```json
{ "code": 0, "message": "ok", "data": { "aiConfigured": true } }
```

`aiConfigured` 决定「生成文章」按钮是否可用：`application-local.yml` 里的 `app.deepseek.api-key` 是 `sk-` 开头且长度合理才算配好。

### GET /api/articles/today

今天已经生成过就返回文章对象，没有则 `data:null`。

### GET /api/articles/{id}

不存在或不属于当前用户 → `404`。

### POST /api/articles/generate

**会真的调 DeepSeek**，一篇约 15–20 秒。

```json
{ "mode": "review", "difficulty": "C1", "paragraphs": 5, "questions": 5, "topic": "" }
```

| 字段 | 说明 | 默认 |
|---|---|---|
| `mode` | `review` 复习模式 / `exam` 真题模式 | `review` |
| `difficulty` | `B2` / `C1` / `C2` | `C1` |
| `paragraphs` | 段数 | `5` |
| `questions` | 题量 | `5` |
| `topic` | 指定题材，空串表示让模型自选 | 空 |

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "title": "The Quiet Harvest: How Digital Ledgers Are Changing Charity",
    "subtitle": "静默的收获：数字账本如何改变慈善",
    "wordCount": 347,
    "mode": "exam",
    "articleDate": "2026-10-30",
    "content": { "paragraphs": [], "words": [], "sentences": [], "questions": [] }
  }
}
```

`content` 里四段结构：

| 字段 | 内容 |
|---|---|
| `paragraphs` | `[{ "en": "...", "cn": "..." }]` 逐段英文 + 中文对照 |
| `words` | `[{ "word", "phonetic", "pos", "cn", "examSense", "collocation", "example" }]` 词汇卡 |
| `sentences` | `[{ "sent", "structure", "meaning" }]` 长难句拆解 |
| `questions` | `[{ "type", "q", "options", "answer", "locate", "explain" }]` 理解题，`type` 是五类题型之一 |

今天的作业词为空 → `400`「今天还没有单词」；模型返回的 JSON 缺字段或不是合法 JSON → `500`。

## 设置（每个用户自己的 Key）

### POST /api/settings/theme

改外观（浅色 / 深色 / 跟随系统）。只动外观，不影响 Key。

```bash
curl -b jar.txt -H 'Content-Type: application/json' \
  -d '{"theme":"dark"}' http://127.0.0.1:8080/api/settings/theme
```

```json
{ "code": 0, "message": "ok", "data": { "theme": "dark" } }
```

`theme` 只接受 `light` / `dark` / `auto`，其它值返回 `code=400`。
`GET /api/settings` 的返回里也多了 `theme` 字段。

### GET /api/settings

返回当前用户实际生效的 Key 状态，**只回显掩码**，不返回明文。

```bash
curl -b jar.txt http://127.0.0.1:8080/api/settings
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "deepseekKeyMasked": "sk-4d53****1fe4",
    "deepseekConfigured": true,
    "usingOwnKey": false
  }
}
```

`usingOwnKey=false` 表示现在用的是 `application-local.yml` 里那份。

### POST /api/settings

```json
{ "deepseekKey": "sk-..." }
```

非 `sk-` 开头 → `400`。保存到这个用户的 `user_settings`，立刻生效，不用重启。

### POST /api/settings/test

真发一次最小请求试 Key，返回 `{ "ok": true, "ms": 812 }`。Key 不可用时返回 `500`，`message` 带 DeepSeek 的原始报错。

### DELETE /api/settings

清掉用户自己填的 Key，退回配置文件那份。

### POST /api/words/fill-meanings

内置词典（21,339 条）查不到的词——OCR 认出来的生僻词、拼错的词、专有名词——单独调一次模型补释义，
结果写进 `lookup_cache`，同一个词以后不再花钱。

```json
{ "words": ["au", "hardline", "kil"] }
```

```json
{ "code": 0, "message": "ok", "data": { "filled": 3, "requested": 3, "words": ["au", "hardline", "kil"] } }
```

- 一次最多处理 40 个（前端按钮会把整本缺释义的词分批送上）
- 词典里已经有的词会被跳过，不产生调用
- 需要用户自己配好 DeepSeek Key；没配会返回 `code: 500` 并带提示
