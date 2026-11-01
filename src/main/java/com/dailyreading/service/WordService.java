package com.dailyreading.service;

import com.dailyreading.entity.Word;
import com.dailyreading.mapper.LookupCacheMapper;
import com.dailyreading.mapper.WordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final DeepSeekClient deepSeek;
    private final SettingService settingService;
    private final LookupCacheMapper lookupCacheMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    @Transactional
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
        // 先把所有天的词一次性拿到，再整体批量查词典，避免逐词查（N+1）
        // 一次取回全部词（1 条 SQL），再在内存里按天分组——避免「1 + 天数」次查询
        Map<LocalDate, List<Word>> byDate = new LinkedHashMap<>();
        Set<String> allWords = new LinkedHashSet<>();
        for (Word w : wordMapper.listAllByUser(userId)) {
            byDate.computeIfAbsent(w.getBatchDate(), (k) -> new ArrayList<>()).add(w);
            allWords.add(w.getWord());
        }
        if (byDate.isEmpty()) return out;
        Map<String, Map<String, Object>> dict = dictService.lookupBatch(new ArrayList<>(allWords));
        // 词典查不到的词，再看看 lookup_cache 里有没有补过释义（「补全释义」按钮写进去的）
        List<String> misses = new ArrayList<>();
        for (String w : allWords) {
            if (dict.get(DictService.normalize(w)) == null) misses.add(w);
        }
        for (String w : misses) {
            String payload = lookupCacheMapper.findPayload(userId, DictService.normalize(w));
            if (payload == null) continue;
            try {
                JsonNode node = objectMapper.readTree(payload);
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("phonetic", node.path("phonetic").asText(""));
                e.put("pos", node.path("pos").asText(""));
                e.put("cn", node.path("cn").asText(""));
                dict.put(DictService.normalize(w), e);
            } catch (Exception ignored) {
                // 缓存坏了就当没有
            }
        }

        for (Map.Entry<LocalDate, List<Word>> byDay : byDate.entrySet()) {
            LocalDate d = byDay.getKey();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Word w : byDay.getValue()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("word", w.getWord());
                Map<String, Object> e = dict.get(DictService.normalize(w.getWord()));
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

    /**
     * 给「内置词典查不到」的词补释义：一次请求交一批（默认最多 40 个），
     * 模型返回的释义写进 lookup_cache，之后同一个词不再花钱。
     */
    public Map<String, Object> fillMeanings(Long userId, List<String> rawWords) throws Exception {
        LinkedHashSet<String> want = new LinkedHashSet<>();
        if (rawWords != null) {
            for (String raw : rawWords) {
                String w = raw == null ? "" : raw.trim().toLowerCase();
                if (w.isEmpty() || w.length() > 40) continue;
                if (dictService.lookup(w) != null) continue; // 词典里已有，不用花这个钱
                want.add(w);
                if (want.size() >= 40) break;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (want.isEmpty()) {
            out.put("filled", 0);
            out.put("words", List.of());
            return out;
        }

        String system = """
                你是考研英语词汇老师。我只给你一批英文单词，请给每个词最常见的中文释义。
                只输出 JSON 数组，不要任何解释，格式：
                [{"word":"原词","phonetic":"音标，没有就空字符串","pos":"词性，如 n./v./adj.","cn":"中文释义；多个义项用分号隔开，尽量简短"}]
                必须把给定的每一个词都返回；不要编造音标。
                """;
        String user = "单词列表：" + String.join(", ", want);

        JsonNode node = deepSeek.completeJson(system, user, 0.2, settingService.effectiveDeepSeekKey(userId));
        List<String> filled = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String word = item.path("word").asText("").trim().toLowerCase();
                String cn = item.path("cn").asText("").trim();
                if (word.isEmpty() || cn.isEmpty() || !want.contains(word)) continue;
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("word", word);
                data.put("phonetic", item.path("phonetic").asText(""));
                data.put("pos", item.path("pos").asText(""));
                data.put("cn", cn);
                data.put("collocation", "");
                data.put("example", "");
                data.put("tags", List.of("AI"));
                data.put("source", "model");
                lookupCacheMapper.save(userId, word, "model", objectMapper.writeValueAsString(data));
                filled.add(word);
            }
        }
        out.put("filled", filled.size());
        out.put("words", filled);
        out.put("requested", want.size());
        return out;
    }
}
