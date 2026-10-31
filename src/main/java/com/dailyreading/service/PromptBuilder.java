package com.dailyreading.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

/**
 * 把「今天的单词 + 用户偏好」变成一段提示词。
 * 两种模式共用一套骨架，只在「用词密度 / 句长 / 结构 / 题目」四处换规则。
 */
@Component
public class PromptBuilder {

    public record Options(String mode, String difficulty, int paragraphs, int questions,
                          int examWordLimit, String topic) {}

    public String system() {
        return """
                你是一位资深的考研英语阅读命题人，为中国的考研学生写阅读材料。
                你写的英文必须地道、克制、有信息量，像一个受过训练的专栏作者，而不是像教科书。
                严禁：空洞的排比、格言式的结尾（"X is to be human" 这类）、"首先其次最后"的模板、
                虚构的人物故事、以及把每个目标词都塞进一句话里。

                只输出一个 JSON 对象，不要任何解释、不要 markdown 代码块。字段如下：
                {
                  "title": "英文标题，不超过 12 个词，可以带冒号",
                  "subtitle": "中文标题",
                  "gist": "一句中文导语，20-40 字，点出文章要讨论的张力",
                  "paragraphs": [{"en": "英文正文段落", "cn": "该段的中文翻译"}],
                  "words": [{"word": "原文形式", "phonetic": "/音标/", "pos": "词性缩写",
                             "cn": "中文释义", "examSense": "考研常考的那个义项或用法",
                             "collocation": "常用搭配", "example": "一句英文例句"}],
                  "sentences": [{"sent": "原文里的长难句", "structure": "中文讲主干和从句怎么套的",
                                 "meaning": "整句中文意思"}],
                  "questions": [{"type": "细节题/推断题/词义句意题/态度题/主旨题",
                                 "q": "英文题干", "options": ["A. ...", "B. ...", "C. ...", "D. ..."],
                                 "answer": "A", "locate": "原文定位到的那一句（英文）", "explain": "中文解析"}]
                }
                """;
    }

    public String user(List<String> words, Options opt) {
        boolean exam = "exam".equalsIgnoreCase(opt.mode());
        StringJoiner w = new StringJoiner(", ");
        words.forEach(w::add);

        StringBuilder sb = new StringBuilder();
        sb.append("今天学生背的单词：").append(w).append("\n\n");
        sb.append("请用这些词写一篇英文文章。\n");
        sb.append("- 难度：").append(difficultyHint(opt.difficulty())).append("\n");
        sb.append("- 段落数：").append(opt.paragraphs()).append("\n");
        sb.append("- 题材：").append(opt.topic() == null || opt.topic().isBlank()
                ? "社科 / 科普类议论文，话题要具体（例如某一项具体的技术、政策、行业变化），不要泛泛谈人生"
                : opt.topic()).append("\n\n");

        if (exam) {
            sb.append("""
                    【真题模式】这段是重点，请严格遵守：
                    1. 只从上面的词表里挑 %d 个词用进文章，其余的词不要出现。用上的词原样使用（可以变形）。
                    2. 正文 400-550 词。%s，允许 2-3 个长难句（带插入语、分词结构、同位语从句）。
                    3. 结构必须有「让步 + 反驳」：先认真陈述一方观点，再用 "Yet" / "That objection has force, but" 转折。
                       不要「现象 → 科学解释 → 历史回顾 → 提出问题 → 文化升华」这种五段模板。
                    4. 结尾不要升华、不要格言、不要提「我们应该」。就以一个具体的、未解决的张力收尾。
                    5. words 数组只放你实际用到的词。
                    """.formatted(opt.examWordLimit(), sentenceRule(opt.difficulty())));
        } else {
            sb.append("""
                    【复习模式】这段是重点：
                    1. 把上面给的词尽可能都用上（至少八成），实在塞不进去的才跳过。
                    2. 句子短一些，平均 12-18 词，别为了难而难。
                    3. 重点是让读者在语境里反复见到这些词，所以表达要自然，别硬凑。
                    """);
        }

        sb.append("\n- 题目数：").append(opt.questions()).append(" 道，四选一，考法要不一样（细节、推断、词义、态度、主旨混着来）。");
        sb.append("\n- sentences 里放 2 个真正的长难句（必须是文章里出现过的原句）。");
        sb.append("\n- 全部用简体中文解释，英文正文用英式拼写也可以，但全文要统一。");
        return sb.toString();
    }
    /** 把难度档位翻成具体的句子长度 / 用词要求：光给 B2/C2 这种代号，模型也只能瞎猜 */
    private String difficultyHint(String difficulty) {
        return switch (String.valueOf(difficulty).toUpperCase()) {
            case "B2" -> "B2，句子 12-18 词，少用嵌套从句和超纲词，读起来轻松";
            case "C2" -> "C2，平均句长 26-38 词，多用抽象名词与多重修饰，可出现少量超纲词";
            default -> "C1，考研英语一真题难度，平均句长 20-28 词，含 1-2 处嵌套从句";
        };
    }

    private String sentenceRule(String difficulty) {
        return switch (String.valueOf(difficulty).toUpperCase()) {
            case "B2" -> "每句 15-25 词，尽量少用嵌套";
            case "C2" -> "每句 26-40 词，句子里套从句";
            default -> "每句 20-35 词";
        };
    }
}