package com.ravi.my_planner_service;

import com.ravi.my_planner_service.trip.ExpenseCreateRequest;
import com.ravi.my_planner_service.trip.JournalEntryCreateRequest;
import com.ravi.my_planner_service.trip.TripCreateRequest;
import com.ravi.my_planner_service.trip.TripResponse;
import com.ravi.my_planner_service.trip.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MyPlannerServiceApplicationTests {

    @Autowired
    private TripService tripService;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void savesAndLoadsTripWithTravelersExpensesAndJournalEntries() {
        TripResponse created = tripService.createTrip(new TripCreateRequest(
                "Sample City, Country", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                List.of("You", "Sam"), List.of("Foodie"), "Test notes"));

        TripResponse withExpense = tripService.addExpense(created.id(), new ExpenseCreateRequest(
                "Shared dinner", "Food", new BigDecimal("24.50"), "You",
                List.of("You", "Sam"), LocalDate.of(2026, 12, 2)));
        TripResponse withJournal = tripService.addJournalEntry(created.id(), new JournalEntryCreateRequest(
                "A memorable dinner", LocalDate.of(2026, 12, 2)));
        TripResponse loaded = tripService.getTrip(created.id());

        assertNotNull(loaded.id());
        assertEquals(created.id(), withExpense.id());
        assertEquals(List.of("You", "Sam"), loaded.people());
        assertEquals(List.of("Foodie"), loaded.preferences());
        assertEquals("Test notes", loaded.notes());
        assertEquals(1, loaded.expenses().size());
        assertEquals(new BigDecimal("24.50"), loaded.expenses().getFirst().amount());
        assertEquals("You", loaded.expenses().getFirst().paidBy());
        assertEquals(List.of("You", "Sam"), loaded.expenses().getFirst().splitWith());
        assertEquals(1, loaded.journalEntries().size());
        assertEquals("A memorable dinner", loaded.journalEntries().getFirst().text());
        assertEquals(created.id(), withJournal.id());
        assertTrue(tripService.getTrips().stream().anyMatch(trip -> trip.id().equals(created.id())));
    }
}
