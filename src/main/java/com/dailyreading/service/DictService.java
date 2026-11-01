package com.dailyreading.service;

import com.dailyreading.entity.DictEntry;
import com.dailyreading.mapper.DictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * 本地词典查询：先查词形索引（takes -> take），再查词条。
 * 这一层将来会被「今日词表 -> 缓存 -> 本地词典 -> 模型」的四级链路复用。
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private final DictMapper dictMapper;

    /** 规范化：小写、去首尾空白、弯引号拉直、空白折叠 */
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase()
                .replace('\u2019', '\'')
                .replaceAll("\\s+", " ");
    }

    /**
     * @return 命中返回 {word, surface, ...}；未命中返回 null
     */
    public Map<String, Object> lookup(String raw) {
        String key = normalize(raw);
        if (key.isEmpty()) return null;

        String surface = key;
        String head = key;

        String lemma = dictMapper.findLemma(key);
        if (lemma != null && !lemma.equals(key)) {
            head = lemma;
        }

        DictEntry entry = dictMapper.findByWord(head);
        if (entry == null && !head.equals(key)) {
            entry = dictMapper.findByWord(key);
            head = entry == null ? key : key;
        }
        if (entry == null) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("word", entry.getWord());
        out.put("surface", surface);
        out.put("phonetic", entry.getPhonetic() == null || entry.getPhonetic().isEmpty()
                ? "" : "/" + entry.getPhonetic() + "/");
        out.put("pos", entry.getPos());
        out.put("cn", entry.getTranslation());
        out.put("tags", entry.getTag() == null ? new String[0] : entry.getTag().split(" "));
        out.put("collins", entry.getCollins());
        out.put("oxford", entry.getOxford());
        out.put("source", "ecdict");
        return out;
    }

    public int size() {
        return dictMapper.countEntries();
    }    /**
     * 批量版 lookup：单词本「每日单词」一次拉几百个词时，逐词查是 N+1。
     * 这里按 500 一批查词形表和词条表，返回 key 是 normalize 之后的原词。
     */
    public Map<String, Map<String, Object>> lookupBatch(List<String> raws) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (raws == null || raws.isEmpty()) return out;

        List<String> keys = new ArrayList<>(new LinkedHashSet<>(raws.stream()
                .map(DictService::normalize).filter(s -> !s.isEmpty()).toList()));
        if (keys.isEmpty()) return out;

        Map<String, String> lemma = new HashMap<>();
        for (List<String> chunk : chunks(keys)) {
            for (Map<String, Object> row : dictMapper.findLemmas(chunk)) {
                Object f = row.get("form");
                Object l = row.get("lemma");
                if (f != null && l != null) lemma.put(String.valueOf(f), String.valueOf(l));
            }
        }

        Set<String> heads = new LinkedHashSet<>();
        for (String k : keys) heads.add(lemma.getOrDefault(k, k));
        heads.addAll(keys);

        Map<String, DictEntry> entries = new HashMap<>();
        for (List<String> chunk : chunks(new ArrayList<>(heads))) {
            for (DictEntry e : dictMapper.findByWords(chunk)) entries.put(e.getWord(), e);
        }

        for (String k : keys) {
            DictEntry entry = entries.get(lemma.getOrDefault(k, k));
            if (entry == null) entry = entries.get(k);
            if (entry == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("word", entry.getWord());
            item.put("surface", k);
            item.put("phonetic", entry.getPhonetic() == null || entry.getPhonetic().isEmpty()
                    ? "" : "/" + entry.getPhonetic() + "/");
            item.put("pos", entry.getPos());
            item.put("cn", entry.getTranslation());
            out.put(k, item);
        }
        return out;
    }

    private static List<List<String>> chunks(List<String> all) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += 500) {
            out.add(all.subList(i, Math.min(i + 500, all.size())));
        }
        return out;
    }
}