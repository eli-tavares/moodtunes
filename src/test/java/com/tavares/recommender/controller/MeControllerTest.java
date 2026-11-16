package com.tavares.recommender.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tavares.recommender.user.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService users;

    @Test
    void meNamesTheUserAndTheirSettings() throws Exception {
        Mockito.when(users.idOf("local")).thenReturn(3L);
        Mockito.when(users.musicEnabled(3L)).thenReturn(false);

        mockMvc.perform(get("/api/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("local"))
            .andExpect(jsonPath("$.musicEnabled").value(false));
    }

    @Test
    void setMusicStoresThePreference() throws Exception {
        Mockito.when(users.idOf("local")).thenReturn(3L);
        Mockito.when(users.musicEnabled(3L)).thenReturn(true);

        mockMvc.perform(put("/api/me/music").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
            .andExpect(status().isOk());
        Mockito.verify(users).setMusicEnabled(3L, true);
    }
}
