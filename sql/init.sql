-- ============================================================
-- 每日阅读 · 考研英语    数据库初始化脚本
-- MySQL 8.0+，全部 utf8mb4
-- 执行： mysql -uroot -p < init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS daily_reading
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE daily_reading;

-- ------------------------------------------------------------
-- 1. 用户
-- ------------------------------------------------------------
DROP TABLE IF EXISTS users;
CREATE TABLE users
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL COMMENT '登录名，3-20 位',
    password_hash VARCHAR(72)  NOT NULL COMMENT 'BCrypt 哈希',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB COMMENT ='用户';

-- ------------------------------------------------------------
-- 2. 每日单词（截图识别 / 粘贴导入的作业词）
--    word 存小写规范形，surface 存识别到的原始形态（Ledger / takes）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS words;
CREATE TABLE words
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    word       VARCHAR(64) NOT NULL COMMENT '规范形（小写）',
    surface    VARCHAR(64) NOT NULL COMMENT '原文形态',
    batch_date DATE        NOT NULL COMMENT '属于哪一天的作业',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_words_user_date_word (user_id, batch_date, word),
    KEY idx_words_user_date (user_id, batch_date),
    CONSTRAINT fk_words_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT ='每日单词';

-- ------------------------------------------------------------
-- 3. 生词本（阅读时收藏）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS marks;
CREATE TABLE marks
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    word        VARCHAR(80)  NOT NULL COMMENT '单词或短语（小写）',
    lemma       VARCHAR(80)  DEFAULT NULL COMMENT '词典原形',
    phonetic    VARCHAR(64)  DEFAULT NULL,
    pos         VARCHAR(16)  DEFAULT NULL COMMENT '词性，短语为 phr.',
    cn          VARCHAR(1000) DEFAULT NULL COMMENT '中文释义',
    exam_sense  VARCHAR(500) DEFAULT NULL COMMENT '考研常考义项',
    collocation VARCHAR(300) DEFAULT NULL COMMENT '常用搭配',
    example     VARCHAR(500) DEFAULT NULL,
    note        VARCHAR(500) DEFAULT NULL COMMENT '这句里的具体含义',
    status      ENUM ('learning','mastered') NOT NULL DEFAULT 'learning' COMMENT '学习中/已掌握',
    source      VARCHAR(16)  NOT NULL DEFAULT 'ecdict' COMMENT 'ecdict/model',
    added_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marks_user_word (user_id, word),
    KEY idx_marks_user_status (user_id, status),
    CONSTRAINT fk_marks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT ='生词本';

-- ------------------------------------------------------------
-- 4. 文章
--    content 存整份 JSON（段落/译文/词汇卡/长难句/题目），
--    因为它是「一次生成、整体读取」的文档，拆表反而更麻烦。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS articles;
CREATE TABLE articles
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    article_date DATE        NOT NULL,
    mode         VARCHAR(16) NOT NULL COMMENT 'review/exam',
    title        VARCHAR(255) NOT NULL,
    subtitle     VARCHAR(255) DEFAULT NULL COMMENT '中文副标题',
    gist         VARCHAR(500) DEFAULT NULL COMMENT '一句话主旨',
    difficulty   VARCHAR(16) DEFAULT NULL COMMENT 'C1 等',
    word_count   INT         DEFAULT 0,
    content      LONGTEXT    NOT NULL COMMENT '完整 JSON',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_articles_user_date_mode (user_id, article_date, mode),
    KEY idx_articles_user_date (user_id, article_date),
    CONSTRAINT fk_articles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT ='生成的文章';

-- ------------------------------------------------------------
-- 5. 文章用词（哪篇文章用了哪些词，用于「本篇用到 X/Y 个」）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS article_words;
CREATE TABLE article_words
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    article_id BIGINT      NOT NULL,
    word       VARCHAR(64) NOT NULL,
    is_target  TINYINT     NOT NULL DEFAULT 1 COMMENT '1=目标词',
    PRIMARY KEY (id),
    KEY idx_aw_article (article_id),
    CONSTRAINT fk_aw_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT ='文章用词';

-- ------------------------------------------------------------
-- 6. 本地词典词条（ECDICT 子集，21,699 条）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dict_entries;
CREATE TABLE dict_entries
(
    word        VARCHAR(64)   NOT NULL,
    phonetic    VARCHAR(64)   DEFAULT NULL,
    pos         VARCHAR(16)   DEFAULT NULL COMMENT '从译文前缀抽出的词性',
    translation VARCHAR(1000) DEFAULT NULL COMMENT '中文释义',
    tag         VARCHAR(64)   DEFAULT NULL COMMENT 'zk gk ky cet4 cet6 toefl ielts gre',
    collins     INT           DEFAULT NULL COMMENT '柯林斯星级 1-5',
    oxford      TINYINT       DEFAULT 0 COMMENT '是否牛津 3000',
    frq         INT           DEFAULT NULL COMMENT '词频，越小越常用',
    PRIMARY KEY (word),
    KEY idx_dict_tag (tag),
    FULLTEXT KEY ft_dict_translation (translation)
) ENGINE = InnoDB COMMENT ='本地词典词条';

-- ------------------------------------------------------------
-- 7. 词形索引（27,278 条：takes -> take）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dict_forms;
CREATE TABLE dict_forms
(
    form  VARCHAR(64) NOT NULL,
    lemma VARCHAR(64) NOT NULL,
    PRIMARY KEY (form),
    KEY idx_forms_lemma (lemma)
) ENGINE = InnoDB COMMENT ='词形 -> 原形';

-- ------------------------------------------------------------
-- 8. 查词缓存（模型查过的词，避免重复调用）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS lookup_cache;
CREATE TABLE lookup_cache
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    word       VARCHAR(80) NOT NULL,
    source     VARCHAR(16) NOT NULL COMMENT 'ecdict/model',
    payload    LONGTEXT    NOT NULL COMMENT 'JSON',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lookup_user_word (user_id, word),
    CONSTRAINT fk_lookup_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT ='查词缓存';

-- ------------------------------------------------------------
-- 9. OCR 结果缓存（同一张图不重复调用云 OCR，省钱）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ocr_cache;
CREATE TABLE ocr_cache
(
    image_sha1 CHAR(40) NOT NULL COMMENT '图片内容 sha1',
    payload    LONGTEXT NOT NULL COMMENT 'JSON：识别到的词 + 坐标',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (image_sha1)
) ENGINE = InnoDB COMMENT ='OCR 结果缓存';

DROP TABLE IF EXISTS user_settings;
CREATE TABLE user_settings
(
    user_id    BIGINT      NOT NULL,
    skey       VARCHAR(40) NOT NULL,
    svalue     TEXT        NOT NULL,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, skey)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户自己的设置（API Key 等）';
