package com.dailyreading.entity;

import lombok.Data;

/**
 * 本地词典词条（ECDICT 子集）。
 */
@Data
public class DictEntry {
    private String word;
    private String phonetic;
    /** 从译文前缀抽出来的词性，如 n. / vt. */
    private String pos;
    private String translation;
    /** zk gk ky cet4 cet6 toefl ielts gre */
    private String tag;
    private Integer collins;
    private Integer oxford;
}
