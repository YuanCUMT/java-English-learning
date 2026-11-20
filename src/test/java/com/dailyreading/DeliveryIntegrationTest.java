package com.dailyreading;
import com.dailyreading.service.*;
import com.dailyreading.mapper.*;
import com.dailyreading.entity.Article;
import com.dailyreading.controller.AuthController;
import com.dailyreading.config.CsrfProtection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.time.LocalDate;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.profiles.active=demo","spring.datasource.url=jdbc:h2:mem:delivery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.crypto.key-file=target/test-secrets/delivery.key"})
@AutoConfigureMockMvc
class DeliveryIntegrationTest {
    @Autowired MockMvc mvc; @Autowired UserService users; @Autowired ExampleService examples;
    @Autowired ArticleMapper articles; @Autowired ArticleStore store; @Autowired JdbcTemplate jdbc;
    @Autowired MarkService marks; @Autowired ObjectMapper mapper;
    @Autowired TargetWordMatcher targetMatcher;
    @Autowired DictService dictionary;
    MockHttpSession session; Long user;
    @BeforeEach void account() {
        var u=users.register("u"+UUID.randomUUID().toString().replace("-","").substring(0,12),"password123","password123");
        user=u.getId(); session=new MockHttpSession(); session.setAttribute(AuthController.SESSION_USER_ID,user);
        session.setAttribute(AuthController.SESSION_USERNAME,u.getUsername()); session.setAttribute(CsrfProtection.ATTRIBUTE,"test-token");
    }
    @Test void csrfRejectsUnsafeRequest() throws Exception { mvc.perform(post("/api/words").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"take\"}")).andExpect(status().isForbidden()); }
    @Test void csrfRejectsWrongToken() throws Exception { mvc.perform(post("/api/words").session(session).header("X-CSRF-TOKEN","wrong").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden()); }
    @Test void validCsrfCanImport() throws Exception { mvc.perform(post("/api/words").session(session).header("X-CSRF-TOKEN","test-token").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"take walk\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)); }
    @Test void invalidDateIs400() throws Exception { mvc.perform(get("/api/words").param("date","not-a-date").session(session)).andExpect(status().isBadRequest()); }
    @Test void badJsonIs400() throws Exception { mvc.perform(post("/api/words").session(session).header("X-CSRF-TOKEN","test-token").contentType(MediaType.APPLICATION_JSON).content("{" )).andExpect(status().isBadRequest()); }
    @Test void wrongMediaIs415() throws Exception { mvc.perform(post("/api/words").session(session).header("X-CSRF-TOKEN","test-token").contentType(MediaType.TEXT_PLAIN).content("abc")).andExpect(status().isUnsupportedMediaType()); }
    @Test void unauthenticatedApiIs401() throws Exception { mvc.perform(get("/api/words")).andExpect(status().isUnauthorized()); }
    @Test void malformedImageIs400() throws Exception {
        mvc.perform(multipart("/api/words/ocr").file("file","not an image".getBytes()).session(session).header("X-CSRF-TOKEN","test-token")).andExpect(status().isBadRequest());
    }
    @Test void invalidDifficultyIs400() throws Exception {
        mvc.perform(post("/api/articles/generate").session(session).header("X-CSRF-TOKEN","test-token").contentType(MediaType.APPLICATION_JSON).content("{\"difficulty\":\"Z9\"}")).andExpect(status().isBadRequest());
    }
    @Test void exampleIsValidAndIdempotent() {
        Long first=examples.load(user); assertEquals(first,examples.load(user));
        var saved=articles.findById(first,user); assertNotNull(saved);
        assertTrue(saved.getContent().contains("bundled-example"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM articles WHERE user_id=?",Integer.class,user));
    }
    @Test void realDictionaryHandlesIndependentInflections() {
        for(String word:List.of("take","takes","taking","taken")) {
            var body=mapper.valueToTree(Map.of("paragraphs",List.of(Map.of("en",word))));
            assertEquals(List.of("take"),targetMatcher.measure(body,List.of("take"),"review",15).matched(),word);
        }
        assertEquals("abandon",dictionary.lookup("abandon").get("word"));
        assertEquals("abandon",dictionary.lookupBatch(List.of("abandon")).get("abandon").get("word"));
    }
    @Test void anotherUserCannotReadArticle() throws Exception {
        Long first=examples.load(user); session.setAttribute(AuthController.SESSION_USER_ID,otherUser());
        mvc.perform(get("/api/articles/"+first).session(session)).andExpect(status().isNotFound());
    }
    @Test void anotherUserCannotChangeMark() throws Exception {
        var mark=marks.add(user,Map.of("word","take","cn","拿"));
        session.setAttribute(AuthController.SESSION_USER_ID,otherUser());
        mvc.perform(patch("/api/marks/"+mark.getId()+"/status").session(session).header("X-CSRF-TOKEN","test-token").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"mastered\"}")).andExpect(status().isNotFound());
        assertEquals("learning",jdbc.queryForObject("SELECT status FROM marks WHERE id=?",String.class,mark.getId()));
    }
    private Long otherUser() { return users.register("u"+UUID.randomUUID().toString().replace("-","").substring(0,12),"password123","password123").getId(); }
    @Test void schemaCanRepeatWithoutLosingData() {
        Long id=examples.load(user);
        new ResourceDatabasePopulator(new ClassPathResource("schema-demo.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
        assertNotNull(articles.findById(id,user));
    }
    @Test void realDatabaseRollsBackFailedReplacement() {
        Long old=examples.load(user); Article replacement=articles.findById(old,user); replacement.setId(null);
        assertThrows(Exception.class,()->store.replace(replacement,Collections.singletonList(null)));
        assertNotNull(articles.findById(old,user));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM articles WHERE user_id=?",Integer.class,user));
    }
    @Test void registerRotatesSessionAndRendersCsrf() throws Exception {
        MockHttpSession anonymous=new MockHttpSession(); anonymous.setAttribute(CsrfProtection.ATTRIBUTE,"old-token"); String id=anonymous.getId();
        var result=mvc.perform(post("/register").session(anonymous).param("_csrf","old-token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username","r"+UUID.randomUUID().toString().substring(0,10).replace("-","x")).param("password","password123").param("confirm","password123"))
            .andExpect(status().is3xxRedirection()).andReturn();
        var fresh=(MockHttpSession)result.getRequest().getSession(false); assertNotEquals(id,fresh.getId());
        mvc.perform(get("/").session(fresh)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("csrf-token")));
    }
    @Test void offlineEntryWorks() throws Exception {
        MockHttpSession anonymous=new MockHttpSession(); anonymous.setAttribute(CsrfProtection.ATTRIBUTE,"entry");
        mvc.perform(post("/demo/start").session(anonymous).param("_csrf","entry").contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().is3xxRedirection());
    }
    private String renderedToken(String html) {
        var matcher=java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "Rendered form must have a nonempty CSRF token");
        return matcher.group(1);
    }
    @Test void loginCanRetryAfterWrongPassword() throws Exception {
        MockHttpSession anonymous=new MockHttpSession();
        var page=mvc.perform(get("/login").session(anonymous)).andExpect(status().isOk()).andReturn();
        String token=renderedToken(page.getResponse().getContentAsString());
        String username=(String)session.getAttribute(AuthController.SESSION_USERNAME);
        var failed=mvc.perform(post("/login").session(anonymous).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("_csrf",token).param("username",username).param("password","incorrect"))
            .andExpect(status().isOk()).andExpect(view().name("login")).andReturn();
        String retry=renderedToken(failed.getResponse().getContentAsString());
        assertEquals(token,retry);
        mvc.perform(post("/login").session(anonymous).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("_csrf",retry).param("username",username).param("password","password123"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }
    @Test void registerCanRetryAfterPasswordMismatch() throws Exception {
        MockHttpSession anonymous=new MockHttpSession();
        var page=mvc.perform(get("/register").session(anonymous)).andExpect(status().isOk()).andReturn();
        String token=renderedToken(page.getResponse().getContentAsString());
        String username="r"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
        var failed=mvc.perform(post("/register").session(anonymous).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("_csrf",token).param("username",username).param("password","password123").param("confirm","different"))
            .andExpect(status().isOk()).andExpect(view().name("register")).andReturn();
        String retry=renderedToken(failed.getResponse().getContentAsString());
        assertEquals(token,retry);
        mvc.perform(post("/register").session(anonymous).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("_csrf",retry).param("username",username).param("password","password123").param("confirm","password123"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }

}
