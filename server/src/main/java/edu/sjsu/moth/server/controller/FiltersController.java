package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.Filter;
import edu.sjsu.moth.server.db.FiltersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class FiltersController {

    private final FiltersRepository filtersRepository;

    @Autowired
    public FiltersController(FiltersRepository filtersRepository) {
        this.filtersRepository = filtersRepository;
    }

    @GetMapping("/api/v1/filters")
    public Flux<Filter> getAllFilters(@RequestHeader("Authorization") String authorizationHeader) {
        return filtersRepository.findAll();
    }
}
