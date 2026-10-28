package com.dailyreading.service;

import com.dailyreading.entity.DictEntry;
import com.dailyreading.mapper.DictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
    }
}
