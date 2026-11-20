package com.dailyreading.service;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Only demo profile: public dictionary, no personal accounts or keys. */
@Component
@ConditionalOnProperty(name="app.demo",havingValue="true")
@RequiredArgsConstructor
public class DemoBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PlatformTransactionManager transactions;
    public void run(ApplicationArguments args) throws Exception {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM dict_forms",Integer.class)>0) return;
        JsonNode root;
        try(var input=new GZIPInputStream(new ClassPathResource("demo/ecdict.json.gz").getInputStream())) {
            root=mapper.readTree(input);
        }
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            List<Object[]> words=new ArrayList<>();
            root.path("words").fields().forEachRemaining(e -> {
                JsonNode v=e.getValue();
                String cn=v.path("t").asText("").replace("\\r\\n","\n").replace("\\n","\n");
                if(cn.isBlank()) return;
                var match=java.util.regex.Pattern.compile("^([a-z]{1,5})\\.\\s*").matcher(cn);
                String pos=match.find()?match.group(1)+".":"";
                words.add(new Object[]{e.getKey().toLowerCase(Locale.ROOT),v.path("p").asText(""),pos,cn.substring(0,Math.min(1000,cn.length())),v.path("g").asText(""),v.path("c").asInt(0),v.path("o").asBoolean(false)?1:0});
            });
            jdbc.batchUpdate("INSERT IGNORE INTO dict_entries(word,phonetic,pos,translation,tag,collins,oxford) VALUES(?,?,?,?,?,?,?)",words);
            List<Object[]> forms=new ArrayList<>();
            root.path("forms").fields().forEachRemaining(e -> forms.add(new Object[]{e.getKey().toLowerCase(Locale.ROOT),e.getValue().asText().toLowerCase(Locale.ROOT)}));
            jdbc.batchUpdate("INSERT IGNORE INTO dict_forms(form,lemma) VALUES(?,?)",forms);
        });
    }
}
