package com.tavares.recommender.controller;

import com.tavares.recommender.checkin.CheckInRequest;
import com.tavares.recommender.checkin.CheckInResponse;
import com.tavares.recommender.checkin.CheckInService;
import com.tavares.recommender.report.ReportRange;
import com.tavares.recommender.report.ReportResponse;
import com.tavares.recommender.report.ReportService;
import com.tavares.recommender.report.SessionSummary;
import com.tavares.recommender.config.ApiGuardFilter;
import com.tavares.recommender.service.BadRequestException;
import com.tavares.recommender.user.UserService;
import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Saving, listing and deleting check-ins, plus the week/month/3-month reports and the data export. */
@RestController
@RequestMapping("/api")
public class CheckInController {

    private final CheckInService checkIns;
    private final ReportService reports;
    private final UserService users;

    /**
     * Creates the controller.
     *
     * @param checkIns the check-in service
     * @param reports  builds the reports and the session summary
     * @param users    maps the signed-in name to a database id
     */
    public CheckInController(CheckInService checkIns, ReportService reports, UserService users) {
        this.checkIns = checkIns;
        this.reports = reports;
        this.users = users;
    }

    /**
     * Saves a check-in for the signed-in person.
     *
     * @param user    the signed-in person (set by the access filter)
     * @param request the emotions, and optionally the person's words, the flag and a strength
     * @return {@code 201 Created} with the saved check-in
     */
    @PostMapping(value = "/checkins", consumes = "application/json", produces = "application/json")
    public ResponseEntity<CheckInResponse> save(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                                @Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CheckInResponse.from(checkIns.save(users.idOf(user), request.emotions(), request.note(), request.isFlagged(), request.intensity())));
    }

    /**
     * Lists the signed-in person's latest check-ins.
     *
     * @param user  the signed-in person
     * @param limit how many to return (default 20, at most 100)
     * @return the check-ins, newest first
     */
    @GetMapping(value = "/checkins", produces = "application/json")
    public List<CheckInResponse> recent(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                        @RequestParam(defaultValue = "20") int limit) {
        return checkIns.recent(users.idOf(user), limit).stream().map(CheckInResponse::from).toList();
    }

    /**
     * Deletes one of the signed-in person's check-ins.
     *
     * @param user the signed-in person
     * @param id   the check-in to delete
     * @return {@code 204 No Content}, or {@code 404} if it does not exist or belongs to someone else
     */
    @DeleteMapping("/checkins/{id}")
    public ResponseEntity<Void> delete(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                       @PathVariable long id) {
        return checkIns.delete(users.idOf(user), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * Builds a week, month or 3-month report.
     *
     * @param user  the signed-in person
     * @param range {@code week}, {@code month} or {@code 3months}
     * @param tz    the viewer's IANA time zone (for example {@code Europe/Dublin}), so "today" and day buckets match their day
     * @return the report
     * @throws BadRequestException for an unknown range or time zone
     */
    @GetMapping(value = "/reports", produces = "application/json")
    public ReportResponse report(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                 @RequestParam(defaultValue = "week") String range,
                                 @RequestParam(defaultValue = "UTC") String tz) {
        return reports.report(users.idOf(user), ReportRange.parse(range), zone(tz));
    }

    /**
     * Builds the last 7 days with every check-in in them, for the "Summary" tab (to show or send to a therapist).
     *
     * @param user the signed-in person
     * @param tz   the viewer's IANA time zone
     * @return the report and the check-ins behind it
     * @throws BadRequestException for an unknown time zone
     */
    @GetMapping(value = "/summary", produces = "application/json")
    public SessionSummary summary(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                  @RequestParam(defaultValue = "UTC") String tz) {
        return reports.sessionSummary(users.idOf(user), zone(tz));
    }

    /**
     * Everything the signed-in person has saved, as a JSON download: their backup.
     *
     * @param user the signed-in person
     * @return the export, sent as an attachment
     */
    @GetMapping(value = "/export", produces = "application/json")
    public ResponseEntity<Map<String, Object>> export(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user) {
        Map<String, Object> body = new LinkedHashMap<>();
        Instant now = Instant.now();
        body.put("app", "MoodTunes");
        body.put("user", user);
        body.put("exportedAt", now);
        body.put("checkIns", checkIns.all(users.idOf(user)).stream().map(CheckInResponse::from).toList());
        String filename = "moodtunes-export-" + now.atOffset(ZoneOffset.UTC).toLocalDate() + ".json";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString())
            .body(body);
    }

    /**
     * Parses a time zone from the query string.
     *
     * @param tz an IANA zone id
     * @return the zone
     * @throws BadRequestException if it is not a valid zone
     */
    private static ZoneId zone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw new BadRequestException("tz must be a valid time zone such as Europe/Dublin.");
        }
    }
}
