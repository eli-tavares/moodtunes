package com.tavares.recommender.controller;

import com.tavares.recommender.config.ApiGuardFilter;
import com.tavares.recommender.user.MeResponse;
import com.tavares.recommender.user.MusicSettingRequest;
import com.tavares.recommender.user.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who is signed in and their personal settings. The user always comes from the verified code, never from the client. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService users;

    /**
     * Creates the controller.
     *
     * @param users the user service
     */
    public MeController(UserService users) {
        this.users = users;
    }

    /**
     * Says who is signed in and what their settings are.
     *
     * @param user the signed-in person (set by the access filter)
     * @return their name and whether music suggestions are on
     */
    @GetMapping(produces = "application/json")
    public MeResponse me(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user) {
        return new MeResponse(user, users.musicEnabled(users.idOf(user)));
    }

    /**
     * Turns music suggestions on or off for the signed-in person only. The choice is saved on the server.
     *
     * @param user    the signed-in person
     * @param request the new setting
     * @return the person's name and the setting as now saved
     */
    @PutMapping(value = "/music", consumes = "application/json", produces = "application/json")
    public MeResponse setMusic(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                               @Valid @RequestBody MusicSettingRequest request) {
        long id = users.idOf(user);
        users.setMusicEnabled(id, request.enabled());
        return new MeResponse(user, users.musicEnabled(id));
    }
}
