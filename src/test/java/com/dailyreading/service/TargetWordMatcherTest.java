package com.dailyreading.service;
import com.dailyreading.mapper.DictMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetWordMatcherTest {
    private final DictMapper dict=mock(DictMapper.class);
    private final TargetWordMatcher matcher=new TargetWordMatcher(dict);
    private final ObjectMapper om=new ObjectMapper();
    private TargetWordMatcher.Coverage measure(String body,List<String> targets,String mode) {
        return matcher.measure(om.valueToTree(Map.of("paragraphs",List.of(Map.of("en",body)))),targets,mode,15);
    }
    @Test void baseForm() { assertEquals(List.of("take"),measure("We take notes.",List.of("take"),"review").matched()); }
    @Test void takesAlone() { inflection("takes","take"); }
    @Test void takingAlone() { inflection("taking","take"); }
    @Test void takenAlone() { inflection("taken","take"); }
    @Test void irregularWent() { inflection("went","go"); }
    private void inflection(String form,String lemma) {
        when(dict.findLemmas(anyList())).thenReturn(List.of(Map.of("form",form,"lemma",lemma)));
        assertEquals(List.of(lemma),measure(form,List.of(lemma),"review").matched());
    }
    @Test void prefixIsNotAnInflection() { assertTrue(measure("A takeover.",List.of("take"),"review").matched().isEmpty()); }
    @Test void punctuationAndCase() { assertEquals(List.of("take"),measure("TAKE!",List.of(" Take ","take"),"review").matched()); }
    @Test void phraseBoundaries() { assertTrue(measure("In terms of cost",List.of("in term of"),"review").matched().isEmpty()); }
    @Test void phraseWhitespace() { assertEquals(1,measure("In   terms of cost",List.of("in terms of"),"review").matched().size()); }
    @Test void eightyPercentRounding() {
        var c=measure("one two three four",List.of("one","two","three","four","five","six"),"review");
        assertEquals(5,c.required()); assertEquals(4,c.matched().size()); assertEquals(2,c.missing().size());
    }
    @Test void eightyPercentExact() { assertEquals(4,measure("a b c d",List.of("a","b","c","d","e"),"review").required()); }
    @Test void examShortList() { assertEquals(2,measure("one two",List.of("one","two"),"exam").required()); }
    @Test void batchQueries() { measure("take walk",List.of("take","walk"),"review"); verify(dict,times(1)).findLemmas(anyList()); }
}
