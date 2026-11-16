package com.tavares.recommender.user;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tavares.recommender.config.ChatClientAdapter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The whole app (filters, controllers, services, real SQL) with two people signed in by their own codes. */
@SpringBootTest(properties = "app.users=elit:code-for-elit-1,ainek:code-for-ainek-2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserIsolationIntegrationTest {

    private static final String ELIT = "code-for-elit-1";
    private static final String AINEK = "code-for-ainek-2";
    private static final String SONGS = "[{\"title\":\"A Song\",\"artist\":\"Band\",\"description\":\"Fits\"}]";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private UserService users;

    @Autowired
    private UserDirectory directory;

    @MockitoBean
    private ChatClientAdapter chat;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM check_in_emotion").update();
        jdbc.sql("DELETE FROM check_in").update();
        jdbc.sql("DELETE FROM app_user WHERE name = 'me'").update();
        users.idOf("elit");
        users.idOf("ainek");
        jdbc.sql("UPDATE app_user SET music_enabled = TRUE").update();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String code) {
        return request.header("X-Access-Token", code).contentType(MediaType.APPLICATION_JSON);
    }

    private long saveAs(String code, String emotionsJson) throws Exception {
        String body = mvc.perform(as(post("/api/checkins"), code).content("{\"emotions\":" + emotionsJson + "}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void everyoneNeedsTheirOwnCode() throws Exception {
        for (var request : List.of(get("/api/checkins"), get("/api/me"), get("/api/export"),
            get("/api/reports?range=week"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(as(request, "wrong-code")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void eachPersonSeesOnlyTheirOwnLog() throws Exception {
        saveAs(ELIT, "[\"sad\"]");
        saveAs(AINEK, "[\"happy\",\"calm\"]");
        saveAs(AINEK, "[\"tired\"]");

        mvc.perform(as(get("/api/checkins"), ELIT)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].emotions[0]").value("sad"));
        mvc.perform(as(get("/api/checkins"), AINEK)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(as(get("/api/reports?range=week&tz=UTC"), ELIT)).andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.checkIns").value(1))
            .andExpect(jsonPath("$.emotions[0].emotion").value("sad"));
        mvc.perform(as(get("/api/reports?range=week&tz=UTC"), AINEK)).andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.checkIns").value(2));

        // exports contain only the caller's data and say whose it is
        mvc.perform(as(get("/api/export"), ELIT)).andExpect(status().isOk())
            .andExpect(jsonPath("$.user").value("elit"))
            .andExpect(jsonPath("$.checkIns.length()").value(1))
            .andExpect(content().string(not(containsString("happy"))));
        mvc.perform(as(get("/api/export"), AINEK)).andExpect(jsonPath("$.checkIns.length()").value(2))
            .andExpect(content().string(not(containsString("sad"))));
    }

    @Test
    void youCannotDeleteSomeoneElsesCheckInAndItSurvives() throws Exception {
        long elitsId = saveAs(ELIT, "[\"sad\"]");

        mvc.perform(as(delete("/api/checkins/" + elitsId), AINEK)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/checkins"), ELIT)).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(as(delete("/api/checkins/" + elitsId), ELIT)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/checkins"), ELIT)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theUserComesFromTheCodeNeverFromAnythingElseTheClientSends() throws Exception {
        saveAs(ELIT, "[\"sad\"]");
        mvc.perform(as(get("/api/checkins?user=elit"), AINEK)
                .header("X-User", "elit").header("X-Access-User", "elit").header("moodtunes.user", "elit"))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(get("/api/me"), AINEK)).andExpect(jsonPath("$.name").value("ainek"));
    }

    @Test
    void musicCanBeSwitchedOffPerPersonAndThenCostsNothing() throws Exception {
        when(chat.call(anyString(), anyString())).thenReturn(SONGS);
        String music = "{\"emotions\":[\"happy\"],\"mediaType\":\"SONGS\"}";

        mvc.perform(as(get("/api/me"), AINEK)).andExpect(jsonPath("$.musicEnabled").value(true));
        mvc.perform(as(put("/api/me/music"), AINEK).content("{\"enabled\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.musicEnabled").value(false));

        // ainek: refused, and no AI call is made
        mvc.perform(as(post("/api/recommend"), AINEK).content(music)).andExpect(status().isForbidden());
        verify(chat, never()).call(anyString(), anyString());

        // elit is unaffected
        mvc.perform(as(get("/api/me"), ELIT)).andExpect(jsonPath("$.musicEnabled").value(true));
        mvc.perform(as(post("/api/recommend"), ELIT).content(music)).andExpect(status().isOk());
        verify(chat, times(1)).call(anyString(), anyString());

        // and it can be switched back on
        mvc.perform(as(put("/api/me/music"), AINEK).content("{\"enabled\":true}")).andExpect(status().isOk());
        mvc.perform(as(post("/api/recommend"), AINEK).content(music)).andExpect(status().isOk());
    }

    @Test
    void theMusicSwitchNeedsAProperValue() throws Exception {
        mvc.perform(as(put("/api/me/music"), ELIT).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/me/music"), ELIT).content("{\"enabled\":\"maybe\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void checkInsSavedBeforeLoginsExistGoToTheFirstUser() {
        // what an upgrade looks like: rows with no owner (and one saved by the old single-code user "me")
        jdbc.sql("INSERT INTO check_in (id, created_at) VALUES (9001, TIMESTAMP WITH TIME ZONE '2026-11-10 10:00:00+00')").update();
        jdbc.sql("INSERT INTO check_in_emotion (check_in_id, emotion) VALUES (9001, 'happy')").update();
        long me = users.idOf("me");
        jdbc.sql("INSERT INTO check_in (id, user_id, created_at) VALUES (9002, :me, TIMESTAMP WITH TIME ZONE '2026-11-11 10:00:00+00')")
            .param("me", me).update();
        jdbc.sql("INSERT INTO check_in_emotion (check_in_id, emotion) VALUES (9002, 'calm')").update();

        users.adoptLegacyData(directory);

        long elit = users.idOf("elit");
        assertEquals(2, jdbc.sql("SELECT COUNT(*) FROM check_in WHERE user_id = :id").param("id", elit)
            .query(Integer.class).single(), "both check-ins now belong to elit, the first configured user");
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM check_in WHERE user_id IS NULL").query(Integer.class).single());
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM app_user WHERE name = 'me'").query(Integer.class).single(),
            "the old single-code user is merged away");
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM check_in WHERE user_id = :id").param("id", users.idOf("ainek"))
            .query(Integer.class).single(), "nothing leaks to the second user");
        assertTrue(Instant.now().isAfter(Instant.EPOCH));
    }
}
