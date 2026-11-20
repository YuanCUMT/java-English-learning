package com.dailyreading.service;
import com.dailyreading.mapper.ArticleMapper;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleGenerationRegressionTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final ArticleStore store=mock(ArticleStore.class);
    private final DeepSeekClient client=mock(DeepSeekClient.class);
    private final TargetWordMatcher matcher=mock(TargetWordMatcher.class);
    private final WordFilter filter=mock(WordFilter.class);
    private final SettingService settings=mock(SettingService.class);
    private ArticleService service;
    private JsonNode sample;
    private final List<String> targets=List.of("modest","procedure","reluctant","cautious","abandon","witness","vivid");
    @BeforeEach void setup() throws Exception {
        service=new ArticleService(new PromptBuilder(),client,settings,store,mock(ArticleMapper.class),filter,matcher,new RequestGuard(),mapper);
        sample=mapper.readTree(getClass().getResourceAsStream("/demo/article.json"));
        when(filter.filter(7L,targets)).thenReturn(new WordFilter.Result(targets,List.of()));
        when(client.completeJson(anyString(),anyString(),anyDouble(),any())).thenReturn(sample);
    }
    @Test void insufficientCoverageNeverTouchesStore() {
        when(matcher.measure(any(),anyList(),anyString(),anyInt())).thenReturn(new TargetWordMatcher.Coverage(targets,List.of("modest"),targets.subList(1,7),6,1.0/7));
        assertThrows(IllegalStateException.class,()->service.generate(7L,targets,new PromptBuilder.Options("review","B2",5,5,15,null)));
        verifyNoInteractions(store);
    }
    @Test void invalidStructureNeverTouchesStore() {
        ((com.fasterxml.jackson.databind.node.ObjectNode)sample).remove("sentences");
        assertThrows(IllegalStateException.class,()->service.generate(7L,targets,new PromptBuilder.Options("review","B2",5,5,15,null)));
        verifyNoInteractions(store);
    }
    @Test void optionNeedsSeparator() {
        ((com.fasterxml.jackson.databind.node.ArrayNode)sample.path("questions").get(0).path("options")).set(0,mapper.valueToTree("Another option"));
        assertFalse(ArticleValidator.check(sample,5,5).isEmpty());
    }
    @Test void missingTranslationRejected() {
        ((com.fasterxml.jackson.databind.node.ObjectNode)sample.path("paragraphs").get(0)).put("cn","");
        assertFalse(ArticleValidator.check(sample,5,5).isEmpty());
    }
    @Test void inventedQuoteRejected() {
        ((com.fasterxml.jackson.databind.node.ObjectNode)sample.path("questions").get(0)).put("locate","An invented sentence nowhere in the text.");
        assertFalse(ArticleValidator.check(sample,5,5).isEmpty());
    }
    @Test void missingExplanationRejected() {
        ((com.fasterxml.jackson.databind.node.ObjectNode)sample.path("questions").get(0)).remove("explain");
        assertFalse(ArticleValidator.check(sample,5,5).isEmpty());
    }
    @Test void bundledSamplePasses() { assertTrue(ArticleValidator.check(sample,5,5).isEmpty()); }
}
