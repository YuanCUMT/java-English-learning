package com.dailyreading.service;

import com.dailyreading.entity.Mark;
import com.dailyreading.mapper.MarkMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarkService {

    private final MarkMapper markMapper;

    public List<Mark> list(Long userId) {
        return markMapper.listByUser(userId);
    }

    public int count(Long userId, String status) {
        return markMapper.countByStatus(userId, status);
    }

    /** 收进生词本。同一个词重复收藏只会更新，不会产生第二条。 */
    public Mark add(Long userId, Map<String, Object> body) {
        String word = normalize(str(body.get("word")));
        if (word.isEmpty()) throw new IllegalArgumentException("单词不能为空");
        if (word.length() > 80) throw new IllegalArgumentException("单词太长了");

        Mark m = new Mark();
        m.setUserId(userId);
        m.setWord(word);
        m.setLemma(cut(str(body.get("lemma")), 80));
        m.setPhonetic(cut(str(body.get("phonetic")), 64));
        m.setPos(cut(str(body.get("pos")), 16));
        m.setCn(cut(str(body.get("cn")), 1000));
        m.setExamSense(cut(str(body.get("examSense")), 500));
        m.setCollocation(cut(str(body.get("collocation")), 300));
        m.setExample(cut(str(body.get("example")), 500));
        m.setNote(cut(str(body.get("note")), 500));
        String source = str(body.get("source"));
        m.setSource("model".equals(source) ? "model" : "ecdict");
        markMapper.upsert(m);
        return markMapper.findByWord(userId, word);
    }

    public Mark setStatus(Long userId, Long id, String status) {
        if (!"learning".equals(status) && !"mastered".equals(status)) {
            throw new IllegalArgumentException("status 只能是 learning 或 mastered");
        }
        if (markMapper.updateStatus(id, userId, status) == 0) {
            throw new IllegalArgumentException("生词不存在");
        }
        return markMapper.findById(id, userId);
    }

    public void remove(Long userId, Long id) {
        markMapper.delete(id, userId);
    }

    public int clear(Long userId, String status) {
        if ("all".equals(status)) {
            return markMapper.deleteByStatus(userId, "learning") + markMapper.deleteByStatus(userId, "mastered");
        }
        return markMapper.deleteByStatus(userId, "mastered".equals(status) ? "mastered" : "learning");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** 小写、去掉首尾空白、连续空格压成一个，短语才能稳定命中唯一键。 */
    private static String normalize(String s) {
        return str(s).trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
