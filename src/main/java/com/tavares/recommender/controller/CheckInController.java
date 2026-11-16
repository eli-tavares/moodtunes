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

    public CheckInController(CheckInService checkIns, ReportService reports, UserService users) {
        this.checkIns = checkIns;
        this.reports = reports;
        this.users = users;
    }

    @PostMapping(value = "/checkins", consumes = "application/json", produces = "application/json")
    public ResponseEntity<CheckInResponse> save(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                                @Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CheckInResponse.from(checkIns.save(users.idOf(user), request.emotions(), request.note(), request.isFlagged(), request.intensity())));
    }

    @GetMapping(value = "/checkins", produces = "application/json")
    public List<CheckInResponse> recent(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                        @RequestParam(defaultValue = "20") int limit) {
        return checkIns.recent(users.idOf(user), limit).stream().map(CheckInResponse::from).toList();
    }

    @DeleteMapping("/checkins/{id}")
    public ResponseEntity<Void> delete(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                       @PathVariable long id) {
        return checkIns.delete(users.idOf(user), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** {@code tz} is the viewer's IANA time zone (e.g. Europe/Dublin) so "today" and day buckets match their day. */
    @GetMapping(value = "/reports", produces = "application/json")
    public ReportResponse report(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                 @RequestParam(defaultValue = "week") String range,
                                 @RequestParam(defaultValue = "UTC") String tz) {
        return reports.report(users.idOf(user), ReportRange.parse(range), zone(tz));
    }

    /** The last 7 days with every check-in in them, for the "Summary" tab (to show or send to a therapist). */
    @GetMapping(value = "/summary", produces = "application/json")
    public SessionSummary summary(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                  @RequestParam(defaultValue = "UTC") String tz) {
        return reports.sessionSummary(users.idOf(user), zone(tz));
    }

    /** Everything the signed-in user has saved, as a JSON download: their backup. */
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

    private static ZoneId zone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw new BadRequestException("tz must be a valid time zone such as Europe/Dublin.");
        }
    }
}
