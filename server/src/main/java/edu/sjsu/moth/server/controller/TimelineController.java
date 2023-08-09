package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.Marker;
import edu.sjsu.moth.server.service.TimelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class TimelineController {
    @Autowired
    TimelineService timelineService;

    @GetMapping("/api/v1/markers")
    public Mono<ResponseEntity<Map<String, Marker>>> getApiV1Markers(Principal user, @RequestParam(name =
            "timeline" + "[]") List<String> timeline) {
        if (user == null) return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        return timelineService.getMarkersForUser(user.getName(), timeline).map(ResponseEntity::ok);
    }

    @PostMapping("/api/v1/markers")
    public Mono<ResponseEntity<Map<String, Marker>>> postApiV1Markers(Principal user, @RequestParam(name = "home" +
            "[last_read_id]", required = false) String homeLast, @RequestParam(name = "notifications[last_read_id]",
            required = false) String notificationsLast) {
        if (user == null) return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        return timelineService.setMarkerForUser(user.getName(), homeLast, notificationsLast).map(ResponseEntity::ok);
    }
}
