package com.tavares.recommender.controller;

import com.tavares.recommender.checkin.CheckInRequest;
import com.tavares.recommender.checkin.CheckInResponse;
import com.tavares.recommender.checkin.CheckInService;
import com.tavares.recommender.report.ReportRange;
import com.tavares.recommender.report.ReportResponse;
import com.tavares.recommender.report.ReportService;
import com.tavares.recommender.service.BadRequestException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Saving, listing and deleting check-ins, plus the week/month/3-month reports and the data export. */
@RestController
@RequestMapping("/api")
public class CheckInController {

    private final CheckInService checkIns;
    private final ReportService reports;

    public CheckInController(CheckInService checkIns, ReportService reports) {
        this.checkIns = checkIns;
        this.reports = reports;
    }

    @PostMapping(value = "/checkins", consumes = "application/json", produces = "application/json")
    public ResponseEntity<CheckInResponse> save(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CheckInResponse.from(checkIns.save(request.emotions())));
    }

    @GetMapping(value = "/checkins", produces = "application/json")
    public List<CheckInResponse> recent(@RequestParam(defaultValue = "20") int limit) {
        return checkIns.recent(limit).stream().map(CheckInResponse::from).toList();
    }

    @DeleteMapping("/checkins/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        return checkIns.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** {@code tz} is the viewer's IANA time zone (e.g. Europe/Dublin) so "today" and day buckets match their day. */
    @GetMapping(value = "/reports", produces = "application/json")
    public ReportResponse report(@RequestParam(defaultValue = "week") String range,
                                 @RequestParam(defaultValue = "UTC") String tz) {
        return reports.report(ReportRange.parse(range), zone(tz));
    }

    /** Everything you have saved, as a JSON download: your backup. */
    @GetMapping(value = "/export", produces = "application/json")
    public ResponseEntity<Map<String, Object>> export() {
        Map<String, Object> body = new LinkedHashMap<>();
        Instant now = Instant.now();
        body.put("app", "MoodTunes");
        body.put("exportedAt", now);
        body.put("checkIns", checkIns.all().stream().map(CheckInResponse::from).toList());
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
