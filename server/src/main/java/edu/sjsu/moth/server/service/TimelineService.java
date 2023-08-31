package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Marker;
import edu.sjsu.moth.server.db.TimelineRecord;
import edu.sjsu.moth.server.db.TimelineRepository;
import edu.sjsu.moth.util.EmailCodeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class TimelineService {
    private static final Marker EMPTY_MARKER = new Marker("0", 0, EmailCodeUtils.epoch());
    @Autowired
    TimelineRepository timelineRepository;

    private TimelineRecord getEmptyTimelineRecord(String acct) {
        var r = new TimelineRecord();
        r.acct = acct;
        r.markers = Map.of();
        return r;
    }

    public Mono<Map<String, Marker>> getMarkersForUser(String name, List<String> timeline) {
        return timelineRepository.findById(name)
                .defaultIfEmpty(getEmptyTimelineRecord(name))
                .map(r -> timeline.stream()
                        .map(k -> Map.entry(k, r.markers.getOrDefault(k, EMPTY_MARKER)))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public Mono<Map<String, Marker>> setMarkerForUser(String name, String homeLast, String notificationsLast) {
        return timelineRepository.findById(name).map(r -> {
            HashMap<String, Marker> updated = new HashMap<>();
            if (homeLast != null) {
                var oldR = r.markers.get("home");
                var newR = new Marker(homeLast, oldR == null ? 1 : oldR.version + 1, EmailCodeUtils.now());
                r.markers.put("home", newR);
                updated.put("home", newR);
            }
            if (notificationsLast != null) {
                var oldR = r.markers.get("notifications");
                var newR = new Marker(notificationsLast, oldR == null ? 1 : oldR.version + 1, EmailCodeUtils.now());
                r.markers.put("notifications", newR);
                updated.put("notifications", newR);
            }
            return updated;
        });
    }
}
