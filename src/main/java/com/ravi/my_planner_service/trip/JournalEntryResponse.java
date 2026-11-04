package com.ravi.my_planner_service.trip;

import java.time.LocalDate;

public record JournalEntryResponse(String id, String text, LocalDate date) {
}
