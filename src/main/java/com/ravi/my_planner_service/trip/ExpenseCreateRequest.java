package com.ravi.my_planner_service.trip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseCreateRequest(
        String title,
        String category,
        BigDecimal amount,
        String paidBy,
        List<String> splitWith,
        LocalDate date) {
}
