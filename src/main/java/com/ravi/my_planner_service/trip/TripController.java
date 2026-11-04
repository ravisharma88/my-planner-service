package com.ravi.my_planner_service.trip;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public List<TripResponse> getTrips() {
        return tripService.getTrips();
    }

    @GetMapping("/{tripId}")
    public TripResponse getTrip(@PathVariable String tripId) {
        return tripService.getTrip(tripId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse createTrip(@RequestBody TripCreateRequest request) {
        return tripService.createTrip(request);
    }

    @PostMapping("/{tripId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse addExpense(@PathVariable String tripId, @RequestBody ExpenseCreateRequest request) {
        return tripService.addExpense(tripId, request);
    }

    @PostMapping("/{tripId}/journal-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse addJournalEntry(@PathVariable String tripId, @RequestBody JournalEntryCreateRequest request) {
        return tripService.addJournalEntry(tripId, request);
    }
}
