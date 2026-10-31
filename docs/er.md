# 数据库设计（ER 图）

9 张表，全部 `utf8mb4` / InnoDB。建表语句见 [`../sql/init.sql`](../sql/init.sql)。

## 关系图

> GitHub 会直接渲染下面的 Mermaid；本地用 VS Code（装 Markdown Preview Mermaid 插件）或 Typora 也能看。

```mermaid
erDiagram
    users ||--o{ words          : "每天的作业词"
    users ||--o{ marks          : "生词本"
    users ||--o{ articles       : "生成的文章"
    users ||--o{ lookup_cache   : "查词缓存"
    articles ||--o{ article_words : "本篇用了哪些词"
    dict_forms }o--|| dict_entries : "form → lemma"

    users {
        bigint   id            PK "自增主键"
        varchar  username      UK "登录名 3–20 位"
        varchar  password_hash    "BCrypt 哈希"
        datetime created_at       "注册时间"
    }

    words {
        bigint   id         PK
        bigint   user_id    FK "→ users.id"
        varchar  word          "规范形（小写）"
        varchar  surface       "原文形态，如 Ledger / takes"
        date     batch_date    "属于哪一天的作业"
        datetime created_at
    }

    marks {
        bigint   id          PK
        bigint   user_id     FK "→ users.id"
        varchar  word           "单词或短语（小写）"
        varchar  lemma          "词典原形，可空"
        varchar  phonetic       "音标"
        varchar  pos            "词性，短语为 phr."
        varchar  cn             "中文释义"
        varchar  exam_sense     "考研常考义项"
        varchar  collocation    "常用搭配"
        varchar  example        "例句"
        varchar  note           "这句里的具体含义"
        enum     status         "learning / mastered"
        varchar  source         "ecdict / model"
        datetime added_at
    }

    articles {
        bigint   id           PK
        bigint   user_id      FK "→ users.id"
        date     article_date    "属于哪一天"
        varchar  mode            "review / exam"
        varchar  title
        varchar  subtitle        "中文副标题"
        varchar  gist            "一句话主旨"
        varchar  difficulty      "C1 等"
        int      word_count      "正文词数"
        longtext content         "完整 JSON：段落/译文/词汇卡/长难句/题目"
        datetime created_at
    }

    article_words {
        bigint  id         PK
        bigint  article_id FK "→ articles.id"
        varchar word
        tinyint is_target     "1=目标词"
    }

    dict_entries {
        varchar  word        PK "词条原形"
        varchar  phonetic
        varchar  pos            "从译文前缀抽出的词性"
        varchar  translation    "中文释义"
        varchar  tag            "zk gk ky cet4 cet6 toefl ielts gre"
        int      collins        "柯林斯星级 1–5"
        tinyint  oxford         "是否牛津 3000"
        int      frq            "词频，越小越常用"
    }

    dict_forms {
        varchar form  PK "变形，如 takes"
        varchar lemma    "→ dict_entries.word"
    }

    lookup_cache {
        bigint   id         PK
        bigint   user_id    FK "→ users.id"
        varchar  word
        varchar  source        "ecdict / model"
        longtext payload       "JSON"
        datetime created_at
    }

    ocr_cache {
        char    image_sha1 PK "图片内容的 sha1"
        longtext payload      "JSON：识别到的词 + 坐标"
        datetime created_at
    }
```

## 表清单

| # | 表 | 作用 | 关键约束 |
|---|---|---|---|
| 1 | `users` | 用户 | `uk_users_username` 唯一 |
| 2 | `words` | 每天导入的作业词 | `uk_words_user_date_word (user_id, batch_date, word)` 同一天同一个词只留一条 |
| 3 | `marks` | 生词本（阅读时收藏） | `uk_marks_user_word (user_id, word)` 收藏过的词再收藏是更新，不是新增 |
| 4 | `articles` | 生成的文章 | `uk_articles_user_date_mode (user_id, article_date, mode)` 一天一个模式只能有一篇 |
| 5 | `article_words` | 文章用了哪些词 | 外键级联删除 |
| 6 | `dict_entries` | 本地词典词条（21,339 条） | 主键就是 `word`；`translation` 建了全文索引 |
| 7 | `dict_forms` | 词形 → 原形（27,278 条） | 主键 `form`，查 `takes` 时先查这张表 |
| 8 | `lookup_cache` | 模型查过的词 | `uk_lookup_user_word`，避免同一个词重复花钱 |
| 9 | `ocr_cache` | 同一张图不重复识别 | 主键是图片内容的 sha1 |

## 几个设计取舍

**为什么 `articles.content` 存一整份 JSON，不拆成段落表、题目表？**

因为它的读写方式是「一次生成、整体展示」——没有任何场景需要单独查「第 3 段」。拆开要多 4 张表、多一堆 join，却换不来查询能力。真要统计题目正确率，再加一张独立的答题记录表更合适。

**为什么 `marks` 有 `word` 和 `lemma` 两列？**

`word` 是用户实际收藏的形态（可能是短语 `in terms of`），`lemma` 是词典原形。查词时用 `lemma` 命中词典，展示时用 `word` 保留原样。

**为什么 `dict_forms` 单独一张表？**

一个原形有多个变形（`take / takes / took / taken / taking`），塞进 `dict_entries` 的某一列会变成逗号分隔字符串，没法建索引。单独一张表，主键就是 `form`，查一个词最多两次点查：先 `dict_forms` 拿 `lemma`，再 `dict_entries` 拿释义。

**为什么 `ocr_cache` 的主键是 sha1？**

同一张截图经常被重复上传（手滑点两次、换浏览器再传一次）。用图片内容的 sha1 做键，命中就直接返回上次的识别结果 —— 离线引擎省几秒，将来换回百度云则是省钱。

**为什么 `words` 要存 `surface`？**

识别出来是 `Ledger`、`takes`，规范形要转成 `ledger`、`take` 才好查词典和查重。但界面上还是应该显示用户原本看到的形态，所以两个都留着。
