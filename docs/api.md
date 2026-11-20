# 接口契约

所有 API 返回 code、message、data，code=0 成功；错误 HTTP 状态与业务码同步。少数业务码 4001/4002 映射 HTTP 400。查词未命中额外返回 canAskModel。页面路由和认证表单不计为 REST API。

所有用户 API 要求登录；除 GET/HEAD/OPTIONS 外，需 X-CSRF-TOKEN 请求头。表单使用 _csrf 隐藏字段。令牌从当前页面 csrf-token meta 获取；重新登录后刷新页面。

| 方法与路径 | 说明 |
|---|---|
| GET /api/health | 应用标识、demo/mysql 模式、ready；不返回私有数据 |
| GET/POST/DELETE /api/words | 查、导入、按日期清空词表；日期 YYYY-MM-DD |
| GET /api/words/history | 用户全部每日词及释义 |
| POST /api/words/ocr | multipart file；默认 save=false 返回候选，支持 save=true 明确保存；仅 PNG/JPEG |
| POST /api/words/fill-meanings | 显式 AI 批量补释义，最多 40 个；不自动删除疑似无效词 |
| GET /api/articles/status | 是否已配置模型 Key |
| GET /api/articles/today | 当天最新文章，缺失时 data=null |
| POST /api/articles/generate | mode review/exam；difficulty B2/C1/C2；paragraphs/questions 3–8；topic 最长 200 字 |
| GET /api/articles/{id} | 自己的文章，否则 404 |
| POST /api/examples/load | 明确载入固定示例；已有当天 review 文章则返回其 ID，不覆盖 |
| GET /api/lookup | word 最长 80 字符；ai=1 才主动调用模型 |
| GET /api/dict/size | 本地词典条数 |
| GET/POST /api/marks | 查或收藏当前用户生词 |
| PATCH /api/marks/{id}/status | learning/mastered，越权 404 |
| DELETE /api/marks/{id} | 删除自己的生词，越权 404 |
| DELETE /api/marks | 按 status 清理自己的生词；属于用户主动操作 |
| GET/POST/DELETE /api/settings | Key 状态、保存与清除；不回显完整 Key |
| POST /api/settings/theme | 外观 light/dark/auto |
| POST /api/settings/test | 使用自己的 Key 发送最小真实模型请求，可能产生费用 |

认证：GET/POST /login、GET/POST /register、POST /logout；GET /logout 不退出。仅体验配置开放 POST /demo/start，需 CSRF，生成隔离临时账号并载入固定示例。

文章 body.coverage：targets、matched、missing、required、ratio。复习 required=ceil(4N/5)；真题 required=min(15,N)，额外命中允许。示例 body.example=true、source=bundled-example。

错误：400 输入格式/不合规生成结果；401 未登录；403 CSRF；404 无资源/越权；405 方法不支持；409 重复模型请求；413 上传过大；415 请求媒体类型不支持；500 服务失败。对外不返回异常类名和完整上游响应。控制器原有通用包装可能将部分上游失败记为 4002/HTTP 400，以页面提示为准。

### 2026-11-15 页面验证失败的展示

API 路径的无效 CSRF 请求仍返回 HTTP 403 与 `{code,message,data}` JSON，即使请求接受 HTML。浏览器非 API 表单请求接受 `text/html` 时返回 HTTP 403 恢复页面，不执行原操作，不回显用户名、密码或表单正文。恢复链接仅为固定站内路径，用户重新打开页面后自行提交；不自动重试生成请求。
