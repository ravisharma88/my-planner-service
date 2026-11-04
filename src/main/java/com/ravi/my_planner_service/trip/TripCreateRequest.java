package com.ravi.my_planner_service.trip;

import java.time.LocalDate;
import java.util.List;

public record TripCreateRequest(
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        List<String> people,
        List<String> preferences,
        String notes) {
}
