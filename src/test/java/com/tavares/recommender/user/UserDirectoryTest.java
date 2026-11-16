package com.tavares.recommender.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tavares.recommender.config.AppProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserDirectoryTest {

    private static final AppProperties.RateLimit LIMITS = new AppProperties.RateLimit(20, 120, 10, 120, 600);

    private static UserDirectory of(String token, String users) {
        return new UserDirectory(new AppProperties(token, users, LIMITS));
    }

    @Test
    void eachPersonHasTheirOwnCode() {
        UserDirectory d = of("", "elit:code-for-elit-1, Ainek :code-for-ainek-2");

        assertTrue(d.authRequired());
        assertEquals(List.of("elit", "ainek"), d.names(), "names are trimmed and lower-cased, order kept");
        assertEquals("elit", d.firstName());
        assertEquals(Optional.of("elit"), d.authenticate("code-for-elit-1"));
        assertEquals(Optional.of("ainek"), d.authenticate("code-for-ainek-2"));
    }

    @Test
    void wrongOrMissingCodesMatchNobody() {
        UserDirectory d = of("", "elit:code-for-elit-1,ainek:code-for-ainek-2");
        assertEquals(Optional.empty(), d.authenticate("nope"));
        assertEquals(Optional.empty(), d.authenticate(""));
        assertEquals(Optional.empty(), d.authenticate(null));
        assertEquals(Optional.empty(), d.authenticate("code-for-elit-"), "a prefix of a code is not the code");
        assertEquals(Optional.empty(), d.authenticate("code-for-elit-1 "), "no sloppy matching");
    }

    @Test
    void aCodeMayContainColonsAndSymbols() {
        UserDirectory d = of("", "elit:ab:cd+ef/gh==ijkl");
        assertEquals(Optional.of("elit"), d.authenticate("ab:cd+ef/gh==ijkl"));
    }

    @Test
    void theOldSingleCodeStillWorksAsUserMe() {
        UserDirectory d = of("old-shared-code", "");
        assertTrue(d.authRequired());
        assertEquals(List.of("me"), d.names());
        assertEquals(Optional.of("me"), d.authenticate("old-shared-code"));
    }

    @Test
    void usersWinOverTheOldCode() {
        UserDirectory d = of("old-shared-code", "elit:code-for-elit-1");
        assertEquals(Optional.empty(), d.authenticate("old-shared-code"));
        assertEquals(Optional.of("elit"), d.authenticate("code-for-elit-1"));
    }

    @Test
    void noConfigurationMeansNoLoginForLocalUse() {
        UserDirectory d = of("", "");
        assertFalse(d.authRequired());
        assertEquals(List.of("local"), d.names());
        assertEquals(Optional.of("local"), d.authenticate(null));
    }

    @Test
    void badConfigurationFailsFastWithoutPrintingCodes() {
        for (String bad : List.of("elit", "elit:short", ":code-for-nobody-1", "Bad Name:code-for-bad-name-1",
            "elit:code-for-elit-1,elit:code-for-elit-2", "elit:same-code-for-both,ainek:same-code-for-both", ",", " , ")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> of("", bad), bad);
            assertFalse(e.getMessage().contains("code-for-elit-1") || e.getMessage().contains("same-code-for-both"),
                "message must not contain a code: " + e.getMessage());
        }
    }
}
