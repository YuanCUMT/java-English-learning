package com.dailyreading.service;

import com.dailyreading.mapper.DictMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

/** Exact word boundaries, with batched dictionary inflection lookup. */
@Service
@RequiredArgsConstructor
public class TargetWordMatcher {
    private final DictMapper dict;
    private static final Pattern TOKEN = Pattern.compile("[a-z]+(?:['’-][a-z]+)*");
    public record Coverage(List<String> targets, List<String> matched, List<String> missing,
                           int required, double ratio) {}

    public Coverage measure(JsonNode article, List<String> rawTargets, String mode, int examLimit) {
        List<String> targets = rawTargets.stream().map(DictService::normalize)
                .filter(s -> !s.isBlank()).distinct().toList();
        StringBuilder body = new StringBuilder();
        article.path("paragraphs").forEach(p -> body.append(' ').append(p.path("en").asText("")));
        String normalized = DictService.normalize(body.toString());
        Set<String> tokens = new LinkedHashSet<>();
        TOKEN.matcher(normalized).results().forEach(m -> tokens.add(m.group()));
        Set<String> all = new LinkedHashSet<>(tokens);
        all.addAll(targets.stream().filter(t -> !t.contains(" ")).toList());
        List<String> keys = new ArrayList<>(all);
        Map<String,String> lemmas = new HashMap<>();
        for (int i=0; i<keys.size(); i+=500) {
            for (var row : dict.findLemmas(keys.subList(i, Math.min(i+500, keys.size())))) {
                lemmas.put(String.valueOf(row.get("form")), String.valueOf(row.get("lemma")));
            }
        }
        Set<String> canonical = new HashSet<>(tokens);
        tokens.forEach(t -> canonical.add(lemmas.getOrDefault(t,t)));
        String flat = " " + normalized.replaceAll("[^a-z0-9'\\-]+", " ").trim() + " ";
        List<String> matched = new ArrayList<>(), missing = new ArrayList<>();
        for (String target : targets) {
            boolean hit = target.contains(" ") ? flat.contains(" " + target + " ")
                    : canonical.contains(target) || canonical.contains(lemmas.getOrDefault(target,target));
            (hit ? matched : missing).add(target);
        }
        int required = "exam".equals(mode) ? Math.min(examLimit,targets.size()) : (targets.size()*4+4)/5;
        return new Coverage(targets,matched,missing,required,targets.isEmpty()?0:matched.size()/(double)targets.size());
    }
}
