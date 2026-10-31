package com.dailyreading.service;

import com.dailyreading.entity.Word;
import com.dailyreading.mapper.WordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WordService {

    private final WordMapper wordMapper;
    private final DictService dictService;

    /**
     * 把一段文本里出现的英文单词抽出来：按非字母切分，去重，只留 2-40 位的。
     * 粘贴导入和 OCR 识别结果走同一个方法。
     */
    public static List<String> extract(String text) {
        if (text == null || text.isBlank()) return List.of();
        Set<String> out = new LinkedHashSet<>();
        for (String raw : text.split("[^A-Za-z'\\-]+")) {
            String w = raw.replaceAll("^[-']+|[-']+$", "").toLowerCase();
            if (w.length() >= 2 && w.length() <= 40 && w.matches("[a-z][a-z'\\-]+")) {
                out.add(w);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * 导入一天的单词。
     *
     * @return {added, skipped, total, words}
     */
    public Map<String, Object> importWords(Long userId, LocalDate date, List<String> words) {
        int added = 0;
        int skipped = 0;
        for (String w : words) {
            String norm = w.trim().toLowerCase();
            if (norm.isEmpty()) continue;
            int n = wordMapper.insertIgnore(userId, norm, norm, date);
            if (n > 0) added++;
            else skipped++;
        }
        List<Word> list = wordMapper.listByDate(userId, date);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", added);
        out.put("skipped", skipped);
        out.put("total", list.size());
        out.put("words", list.stream().map(Word::getWord).toList());
        return out;
    }

    /**
     * 单词本「每日单词」用：按日期倒序，每天带回该天的词与词典释义。
     * 释义查本地词典（21,339 条），查不到就留空，前端只显示词本身。
     */
    public List<Map<String, Object>> history(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d : wordMapper.listDates(userId)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Word w : wordMapper.listByDate(userId, d)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("word", w.getWord());
                Map<String, Object> e = dictService.lookup(w.getWord());
                if (e != null) {
                    item.put("phonetic", e.get("phonetic"));
                    item.put("pos", e.get("pos"));
                    item.put("cn", e.get("cn"));
                }
                items.add(item);
            }
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.toString());
            day.put("count", items.size());
            day.put("words", items);
            out.add(day);
        }
        return out;
    }

    public int totalWords(Long userId) {
        return wordMapper.countAll(userId);
    }

    public List<Word> listByDate(Long userId, LocalDate date) {
        return wordMapper.listByDate(userId, date);
    }

    public List<LocalDate> dates(Long userId) {
        return wordMapper.listDates(userId);
    }

    public int clear(Long userId, LocalDate date) {
        return wordMapper.deleteByDate(userId, date);
    }

    public int countByDate(Long userId, LocalDate date) {
        return wordMapper.countByDate(userId, date);
    }
}
