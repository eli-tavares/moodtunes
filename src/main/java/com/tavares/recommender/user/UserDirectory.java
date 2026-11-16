package com.tavares.recommender.user;

import com.tavares.recommender.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Who may sign in, and with which code. Built only from configuration (never stored in the database), so a code is
 * revoked by removing it from the variable and redeploying.
 *
 * <ul>
 *   <li>{@code APP_USERS=elit:CODE1,ainek:CODE2} - one personal code each (min. 12 characters, no commas);</li>
 *   <li>else the legacy {@code APP_ACCESS_TOKEN} - a single user named {@value #LEGACY_USER};</li>
 *   <li>else no login at all (local use): everything belongs to the user {@value #LOCAL_USER}.</li>
 * </ul>
 */
public class UserDirectory {

    public static final String LOCAL_USER = "local";
    public static final String LEGACY_USER = "me";
    static final int MIN_CODE_LENGTH = 12;
    private static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,39}$");

    private final Map<String, byte[]> codes = new LinkedHashMap<>();
    private final boolean authRequired;

    public UserDirectory(AppProperties properties) {
        String spec = properties.users() == null ? "" : properties.users().trim();
        if (!spec.isEmpty()) {
            parse(spec);
            authRequired = true;
        } else if (properties.accessTokenEnabled()) {
            codes.put(LEGACY_USER, properties.accessToken().getBytes(StandardCharsets.UTF_8));
            authRequired = true;
        } else {
            authRequired = false;
        }
    }

    /** Messages name the user but never print a code. */
    private void parse(String spec) {
        List<byte[]> seen = new ArrayList<>();
        for (String entry : spec.split(",")) {
            if (entry.isBlank()) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException(
                    "APP_USERS must look like name:code,name:code (an entry is missing its ':').");
            }
            String name = entry.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String code = entry.substring(colon + 1).trim();
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("APP_USERS: '" + name
                    + "' is not a valid name (use 1-40 lowercase letters, digits, '-' or '_').");
            }
            if (code.length() < MIN_CODE_LENGTH) {
                throw new IllegalArgumentException("APP_USERS: the code for '" + name + "' is too short (at least "
                    + MIN_CODE_LENGTH + " characters).");
            }
            byte[] bytes = code.getBytes(StandardCharsets.UTF_8);
            if (codes.containsKey(name)) {
                throw new IllegalArgumentException("APP_USERS: '" + name + "' is listed twice.");
            }
            if (seen.stream().anyMatch(other -> MessageDigest.isEqual(other, bytes))) {
                throw new IllegalArgumentException("APP_USERS: two users have the same code; every code must be unique.");
            }
            seen.add(bytes);
            codes.put(name, bytes);
        }
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("APP_USERS is set but lists nobody.");
        }
    }

    /** {@code false} only for local use with no code configured. */
    public boolean authRequired() {
        return authRequired;
    }

    /** The configured names in order; the first one adopts check-ins saved before logins existed. */
    public List<String> names() {
        return authRequired ? List.copyOf(codes.keySet()) : List.of(LOCAL_USER);
    }

    public String firstName() {
        return names().getFirst();
    }

    /** The user for a presented code, if any. Compares every code, so timing does not reveal which one was close. */
    public Optional<String> authenticate(String presented) {
        if (!authRequired) {
            return Optional.of(LOCAL_USER);
        }
        if (presented == null) {
            return Optional.empty();
        }
        byte[] given = presented.getBytes(StandardCharsets.UTF_8);
        String match = null;
        for (Map.Entry<String, byte[]> entry : codes.entrySet()) {
            if (MessageDigest.isEqual(given, entry.getValue()) && match == null) {
                match = entry.getKey();
            }
        }
        return Optional.ofNullable(match);
    }
}
