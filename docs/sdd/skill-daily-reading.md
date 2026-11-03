# Skill：每日阅读 · 考研英语（Java 版）开发能力包

> 这是一份给大模型看的「上岗说明书」。把本文档和仓库一起交给模型，它就知道在这个项目里该怎么改代码、怎么验证、哪些地方不许动。

---

## 1. 何时使用这个 skill

满足任一条件就加载：

- 要在 `java-reader` 仓库里新增/修改功能；
- 要排查「识别不出词 / 文章生成失败 / 查词没反应」这类线上问题；
- 要打包、部署、写交作业材料。

不适用：与本项目无关的通用 Java 问题。

---

## 2. 项目坐标（先看这些，别猜）

| 事项 | 值 |
|---|---|
| 技术栈 | Spring Boot 3.3.5 + Thymeleaf + MyBatis 3.0.3 + MySQL 8 |
| JDK | 21（`JAVA_HOME` 指向 JDK 21，PATH 上的 java 可能是 25） |
| 构建 | `.\tools\apache-maven-3.9.9\bin\mvn.cmd -B -q package -DskipTests` |
| 测试 | `mvn -B test`（当前 54 个用例） |
| 启动 | `启动.cmd`（自动拉起 MySQL + jar + 浏览器） |
| 端口 | 8080（应用）、3306（MySQL） |
| 数据目录 | `D:\MySQLServer`（免安装版，含 `mysql-start.ps1` / `stop-mysql.cmd`） |
| 本地配置 | `src/main/resources/application-local.yml`（**不提交**，含真实 Key） |

**打包前必须先停掉正在运行的 java 进程**，否则 `mvn package` 会报
`Unable to rename ... to ...jar.original`。

---

## 3. 目录结构与职责

```
src/main/java/com/dailyreading/
  common/        ApiResponse、ApiExceptionHandler（统一 {code,message,data}）
  config/        DeepSeekProperties、OcrProperties、WebConfig、LoginInterceptor
  controller/    AuthController、WordController、ArticleController、DictController、
                 MarkController、SettingsController、FeedbackController、PageController
  entity/        User、Word、Article、Mark、DictEntry、Feedback
  mapper/        MyBatis 接口（XML 在 resources/mapper 或注解）
  service/       ArticleService、WordService、DictService、MarkService、SettingService、
                 PhraseLookupService、SecretBox、LoginAttemptService、OcrCacheService
  service/ocr/   OcrService 接口 + BaiduOcrService / TesseractOcrService / MockOcrService
                 + OcrResolver（链式降级）
src/main/resources/
  templates/     Thymeleaf：index / article / vocab / archive / settings / feedback / error
  static/js/     app.js（工作区）、article.js（阅读页）、vocab.js（单词本）、settings.js
  static/css/    app.css（基础）+ bauhaus.css（视觉）
  application.yml        主配置（provider: auto）
  application-local.yml  真实 Key 与本机参数（.gitignore）
sql/init.sql    10 张表
dict/           ecdict.json.gz（21339 词 + 27278 变形，0.8 MB）
tools/          start.ps1（一键启动）、export-dict-sql.js、install-shortcut.ps1
```

---

## 4. 改代码前的固定动作

1. `git status` 确认工作区干净；
2. 读 `docs/api.md` 相关接口、`docs/er.md` 相关表；
3. 若涉及表结构：先写 `ALTER`，人工执行后再改代码；
4. 若涉及付费接口：确认触发点是用户点击，不是自动跑。

---

## 5. 三条护栏（违反即打回）

| 护栏 | 具体要求 |
|---|---|
| 不碰密钥 | 不在代码里硬编码 Key；不在日志里打印 Key；不把 `application-local.yml` 提交 |
| 不破契约 | 接口返回固定 `{code,message,data}`；`code=0` 成功；改动同步 `docs/api.md` |
| 不越权 | 所有用户数据操作带 `user_id`；未登录由 `LoginInterceptor` 拦 401 |

---

## 6. OCR 链路（最容易踩坑的地方）

```
OcrResolver.chain() 按 provider 排序：
  auto      → baidu（有 Key 时）→ tesseract（有 tessdata 时）→ mock
  baidu     → baidu → tesseract → mock
  tesseract → tesseract → mock
  mock      → mock（仅 mock）
recognize() 依次尝试，失败降级，返回 Result(words, engine, degraded)
```

规则：

- **候选词必须人工确认后才入库**（`save=false` 时只返回预览）；
- 同一张图走 `ocr_cache`（sha1 键），但 **mock 结果永不写缓存**；
- `engine` 以 `mock` 开头时，前端必须提示「这是模拟数据」。

改这里的任何一条，都要同步 `docs/sdd/eval-set.md` 的对应检查项。

---

## 7. 查词链路

```
/api/lookup?word=X          → 内置词典（ECDICT 子集）命中则返回，未命中 404 + canAskModel
/api/lookup?word=X&ai=1     → 走 DeepSeek，结果写 lookup_cache，source="model"
```

- 词形的正查反查都在 `DictService`：先查变形表拿原形，再查词条；
- 短语（含空格）不进词典，直接走 `ai=1`；
- 前端浮层未命中时先显示「问模型查一下」按钮，用户点了才花钱。

---

## 8. 文章生成链路

```
POST /api/articles/generate {mode, difficulty}
  → 取当天单词（exam 模式最多 15 个）
  → PromptBuilder 组装提示词（复习/真题两套规则）
  → DeepSeek 返回 JSON
  → ArticleValidator 校验（标题、段落数、题数、选项前缀、答案范围）
  → ArticleStore.replace() 事务写 articles + article_words
  → 返回文章 JSON
```

- 事务边界：AI 调用在事务外，落库在事务内；
- `matchWords()` 校验目标词是否真的出现在正文里，一个都没用到直接报错。

---

## 9. 验证清单（提交前逐条过）

- [ ] `mvn -B test` 全绿（54 个）
- [ ] 启动后三条主链路手工跑通：截图识别 → 保存 → 生成文章 → 点词查词
- [ ] 未登录访问 `/` 跳登录页；登录后导航四项都在
- [ ] 改过表结构 → `sql/init.sql` 与 `docs/er.md` 同步
- [ ] 改过接口 → `docs/api.md` 同步
- [ ] `git status` 里没有 `application-local.yml`、没有 `data/`

---

## 10. 已知坑位（都是踩过的）

| 现象 | 根因 | 处理 |
|---|---|---|
| 改了配置不生效 | `application-local.yml` 被打进 jar | 改完必须重新 `mvn package` |
| 打包报 `Unable to rename` | jar 被运行中的 java 占用 | 先 `Stop-Process` 再打包 |
| Lombok 不生效，满屏「找不到符号 getXxx」 | JDK 21 默认不跑 classpath 上的注解处理器 | pom 里配 `<proc>full</proc>` + `annotationProcessorPaths` |
| 启动即崩 `duplicate key: app` | `application.yml` 里有两个 `app:` | 合并成一个 |
| 百度 OCR 报「错误 18」 | 账号未实名，QPS 配额为 0 | 先实名认证 |
| 侧栏单词显示成一条横带 | flex 列容器里子项被压缩 | 列表项加 `flex: 0 0 auto` |
| `存储引擎` 页面空白 | 模板里 `#articleBody` 被外层 `hidden` 包住 | 只隐藏内容容器 |

---

## 11. 交付物清单

| 文件 | 说明 |
|---|---|
| `docs/report/08242051_裴思远_Java课程报告.docx` | 课程报告正文（17 页，15 张表） |
| `docs/sdd/sdd.md` | 规约驱动开发说明书 |
| `docs/sdd/eval-set.md` | 评测集（规约条目 ↔ 检查项） |
| `docs/sdd/acceptance.md` | 里程碑验收记录 |
| `docs/使用说明.md` | 面向使用者的操作手册（含截图） |
| `docs/api.md` / `docs/er.md` | 接口契约 / 数据库设计 |
| `application-local.yml.example` | 本地配置模板 |
