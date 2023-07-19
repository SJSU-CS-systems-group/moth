package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.StatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

public class TimelineController {
    @Autowired
    StatusRepository statusRepository;



    @GetMapping("/api/v1/timelines/home")
public Mono<ResponseEntity<Status[]>> timelinesHome(@RequestHeader("Authorization") String authorizationHeader, @RequestParam String max_id,
                                                    @RequestParam String since_id, @RequestParam String min_id, @RequestParam(defaultValue = "40") Integer limit){


    System.out.println(limit);

    return null;
}
}
