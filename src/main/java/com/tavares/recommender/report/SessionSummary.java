package com.tavares.recommender.report;

import com.tavares.recommender.checkin.CheckInResponse;
import java.util.List;

/** The last 7 days for a session with a therapist: the aggregated report and the individual check-ins behind it. */
public record SessionSummary(ReportResponse report, List<CheckInResponse> checkIns) {
}
