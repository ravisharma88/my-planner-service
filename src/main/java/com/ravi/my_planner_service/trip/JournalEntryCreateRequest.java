package com.ravi.my_planner_service.trip;

import java.time.LocalDate;

public record JournalEntryCreateRequest(String text, LocalDate date) {
}
