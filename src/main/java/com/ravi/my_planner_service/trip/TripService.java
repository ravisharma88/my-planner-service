package com.ravi.my_planner_service.trip;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TripService {
    private static final Set<String> CATEGORIES = Set.of(
            "Flights", "Stay", "Food", "Transport", "Activities", "Shopping", "Other");

    private final JdbcTemplate jdbc;

    public TripService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getTrips() {
        List<TripBase> trips = jdbc.query(
                "SELECT id, destination, start_date, end_date, notes FROM trips ORDER BY created_at DESC",
                (rs, row) -> new TripBase(
                        rs.getString("id"), rs.getString("destination"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class), rs.getString("notes")));
        return trips.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(String tripId) {
        return toResponse(findTripBase(tripId));
    }

    @Transactional
    public TripResponse createTrip(TripCreateRequest request) {
        if (request == null) throw badRequest("request body is required");
        String destination = requiredText(request.destination(), "destination", 255);
        if (request.startDate() == null || request.endDate() == null
                || request.endDate().isBefore(request.startDate())) {
            throw badRequest("endDate must be on or after startDate");
        }
        List<String> people = normalizePeople(request.people());
        List<String> preferences = normalizePreferences(request.preferences());
        String tripId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO trips (id, destination, start_date, end_date, notes) VALUES (?, ?, ?, ?, ?)",
                tripId, destination, request.startDate(), request.endDate(), optionalText(request.notes()));

        for (int i = 0; i < people.size(); i++) {
            jdbc.update("INSERT INTO trip_travelers (id, trip_id, display_name, sort_order) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID().toString(), tripId, people.get(i), i);
        }
        for (int i = 0; i < preferences.size(); i++) {
            jdbc.update("INSERT INTO trip_preferences (trip_id, preference, sort_order) VALUES (?, ?, ?)",
                    tripId, preferences.get(i), i);
        }
        return getTrip(tripId);
    }

    @Transactional
    public TripResponse addExpense(String tripId, ExpenseCreateRequest request) {
        if (request == null) throw badRequest("request body is required");
        TripBase trip = findTripBase(tripId);
        String title = requiredText(request.title(), "title", 255);
        String category = requiredText(request.category(), "category", 20);
        if (!CATEGORIES.contains(category)) throw badRequest("category is not supported");
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() > 12) {
            throw badRequest("amount must be greater than zero and have at most two decimal places");
        }
        if (request.date() == null) throw badRequest("date is required");

        Map<String, String> travelers = getTravelerIds(tripId);
        String payer = requiredText(request.paidBy(), "paidBy", 120);
        String payerId = travelers.get(payer);
        if (payerId == null) throw badRequest("paidBy must be a traveler on this trip");
        List<String> splitWith = normalizeNames(request.splitWith(), "splitWith", true);
        String expenseId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO trip_expenses (id, trip_id, title, category, amount, paid_by_traveler_id, expense_date) VALUES (?, ?, ?, ?, ?, ?, ?)",
                expenseId, tripId, title, category, amount, payerId, request.date());
        for (String person : splitWith) {
            String travelerId = travelers.get(person);
            if (travelerId == null) throw badRequest("splitWith must contain only travelers on this trip");
            jdbc.update("INSERT INTO trip_expense_splits (trip_id, expense_id, traveler_id) VALUES (?, ?, ?)",
                    tripId, expenseId, travelerId);
        }
        touchTrip(tripId);
        return toResponse(trip);
    }

    @Transactional
    public TripResponse addJournalEntry(String tripId, JournalEntryCreateRequest request) {
        if (request == null) throw badRequest("request body is required");
        TripBase trip = findTripBase(tripId);
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) throw badRequest("text is required");
        if (request.date() == null) throw badRequest("date is required");
        jdbc.update("INSERT INTO trip_journal_entries (id, trip_id, body, entry_date) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), tripId, text, request.date());
        touchTrip(tripId);
        return toResponse(trip);
    }

    private TripBase findTripBase(String tripId) {
        List<TripBase> trips = jdbc.query(
                "SELECT id, destination, start_date, end_date, notes FROM trips WHERE id = ?",
                (rs, row) -> new TripBase(
                        rs.getString("id"), rs.getString("destination"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class), rs.getString("notes")), tripId);
        if (trips.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        return trips.getFirst();
    }

    private TripResponse toResponse(TripBase trip) {
        List<String> people = jdbc.query(
                "SELECT display_name FROM trip_travelers WHERE trip_id = ? ORDER BY sort_order",
                (rs, row) -> rs.getString("display_name"), trip.id());
        List<String> preferences = jdbc.query(
                "SELECT preference FROM trip_preferences WHERE trip_id = ? ORDER BY sort_order",
                (rs, row) -> rs.getString("preference"), trip.id());
        Map<String, String> travelerNames = new HashMap<>();
        jdbc.query("SELECT id, display_name FROM trip_travelers WHERE trip_id = ?", (RowCallbackHandler) rs ->
                travelerNames.put(rs.getString("id"), rs.getString("display_name")), trip.id());

        List<ExpenseBase> expenseRows = jdbc.query(
                "SELECT id, title, category, amount, paid_by_traveler_id, expense_date FROM trip_expenses WHERE trip_id = ? ORDER BY expense_date DESC, created_at DESC",
                (rs, row) -> new ExpenseBase(
                        rs.getString("id"), rs.getString("title"), rs.getString("category"),
                        rs.getBigDecimal("amount"), rs.getString("paid_by_traveler_id"),
                        rs.getObject("expense_date", LocalDate.class)), trip.id());
        List<ExpenseResponse> expenses = new ArrayList<>();
        for (ExpenseBase expense : expenseRows) {
            List<String> splitWith = jdbc.query(
                    "SELECT splits.traveler_id FROM trip_expense_splits splits JOIN trip_travelers travelers ON travelers.trip_id = splits.trip_id AND travelers.id = splits.traveler_id WHERE splits.trip_id = ? AND splits.expense_id = ? ORDER BY travelers.sort_order",
                    (rs, row) -> travelerNames.get(rs.getString("traveler_id")), trip.id(), expense.id());
            expenses.add(new ExpenseResponse(expense.id(), expense.title(), expense.category(), expense.amount(),
                    travelerNames.get(expense.paidByTravelerId()), splitWith, expense.date()));
        }
        List<JournalEntryResponse> entries = jdbc.query(
                "SELECT id, body, entry_date FROM trip_journal_entries WHERE trip_id = ? ORDER BY entry_date DESC, created_at DESC",
                (rs, row) -> new JournalEntryResponse(
                        rs.getString("id"), rs.getString("body"), rs.getObject("entry_date", LocalDate.class)), trip.id());
        return new TripResponse(trip.id(), trip.destination(), trip.startDate(), trip.endDate(),
                people, preferences, trip.notes() == null ? "" : trip.notes(), expenses, entries);
    }

    private Map<String, String> getTravelerIds(String tripId) {
        Map<String, String> travelers = new HashMap<>();
        jdbc.query("SELECT id, display_name FROM trip_travelers WHERE trip_id = ?", (RowCallbackHandler) rs ->
                travelers.put(rs.getString("display_name"), rs.getString("id")), tripId);
        return travelers;
    }

    private void touchTrip(String tripId) {
        jdbc.update("UPDATE trips SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", tripId);
    }

    private static List<String> normalizePeople(List<String> values) {
        return normalizeNames(values, "people", true);
    }

    private static List<String> normalizePreferences(List<String> values) {
        if (values == null) return List.of();
        List<String> preferences = new ArrayList<>();
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String preference = requiredText(value, "preferences", 80);
            if (!normalized.add(preference.toLowerCase())) {
                throw badRequest("preferences must not contain duplicates");
            }
            preferences.add(preference);
        }
        return preferences;
    }

    private static List<String> normalizeNames(List<String> values, String field, boolean required) {
        if (values == null || (required && values.isEmpty())) {
            throw badRequest(field + (required ? " must contain at least one entry" : " is invalid"));
        }
        List<String> names = new ArrayList<>();
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String name = requiredText(value, field, 120);
            if (!normalized.add(name.toLowerCase())) throw badRequest(field + " must not contain duplicates");
            names.add(name);
        }
        return names;
    }

    private static String requiredText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw badRequest(field + " is required and must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record TripBase(String id, String destination, LocalDate startDate, LocalDate endDate, String notes) {}
    private record ExpenseBase(String id, String title, String category, BigDecimal amount, String paidByTravelerId, LocalDate date) {}
}
