package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.Marker;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.StatusRepository;
import edu.sjsu.moth.server.service.StatusService;
import edu.sjsu.moth.server.service.TimelineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class TimelineController {

    private final StatusService statusService;
    private final TimelineService timelineService;
    private final StatusRepository statusRepository;

    public TimelineController(StatusService statusService, TimelineService timelineService,
                              StatusRepository statusRepository) {
        this.statusService = statusService;
        this.timelineService = timelineService;
        this.statusRepository = statusRepository;
    }

    @GetMapping("/api/v1/markers")
    public Mono<ResponseEntity<Map<String, Marker>>> getApiV1Markers(Principal user, @RequestParam(name = "timeline[]")
    List<String> timeline) {
        if (user == null) return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        return timelineService.getMarkersForUser(user.getName(), timeline).map(ResponseEntity::ok);
    }

    @PostMapping("/api/v1/markers")
    public Mono<ResponseEntity<Map<String, Marker>>> postApiV1Markers(Principal user, @RequestParam(name = "home" +
            "[last_read_id]", required = false) String homeLast, @RequestParam(name = "notifications" +
            "[last_read_id]", required = false) String notificationsLast) {
        if (user == null) return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        return timelineService.setMarkerForUser(user.getName(), homeLast, notificationsLast).map(ResponseEntity::ok);
    }

    @GetMapping("/api/v1/timelines/public")
    Mono<ResponseEntity<List<Status>>> getApiV1TimelinesPublic(Principal user,
                                                             @RequestParam(required = false) String max_id,
                                                             @RequestParam(required = false) String since_id,
                                                             @RequestParam(required = false) String min_id,
                                                             @RequestParam(required = false) boolean local,
                                                             @RequestParam(required = false, defaultValue = "20")
                                                             int limit) {
        return statusService.getPublicTimeline(user, max_id, since_id, min_id, limit, local).map(ResponseEntity::ok);
    }

    @GetMapping("/api/v1/timelines/tag/{hashtag}")
    Mono<ResponseEntity<List<Status>>> getApiV1TimelinesTag(Principal user,
                                                             @PathVariable String hashtag,
                                                             @RequestParam(required = false) String max_id,
                                                             @RequestParam(required = false) String since_id,
                                                             @RequestParam(required = false) String min_id,
                                                             @RequestParam(required = false) boolean local,
                                                             @RequestParam(required = false) boolean only_media,
                                                             @RequestParam(required = false, defaultValue = "20")
                                                             int limit) {
        return statusRepository.findByTagName(hashtag.toLowerCase())
                .filter(status -> "public".equals(status.visibility) || "unlisted".equals(status.visibility))
                .take(limit)
                .collectList()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(List.of()));
    }
}
