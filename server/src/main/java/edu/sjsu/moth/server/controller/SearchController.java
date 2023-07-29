package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

@RestController
public class SearchController {

    @Autowired
    AccountRepository accountRepository; // our own repository

    @Autowired
    public SearchController(WebClient.Builder webBuilder) {
    }

    @GetMapping("api/v2/search")
    public Mono<SearchResult> doSearch(@RequestParam("q") String query,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(required = false) Boolean resolve,
                                       @RequestParam(required = false) Boolean following,
                                       @RequestParam(required = false) String account_id,
                                       @RequestParam(required = false) Boolean exclude_unreviewed,
                                       @RequestParam(required = false) String max_id,
                                       @RequestParam(required = false) String min_id,
                                       @RequestParam(required = false) Integer limit,
                                       @RequestParam(required = false) Integer offset) {
        SearchResult result = new SearchResult();
        // return empty SearchResult obj. , until query.length() == 3
        if (query.length() < 3) {
            return Mono.just(result);
        }
        // search results limiter defaulter
        if (limit == null || limit == 0) {
            limit = 10;
        }
        //// this is meant for if someone does @user@someInstance.com, splitQuery[2] will be someInstance
        if (query.contains("@")) {
            String[] splitQuery = query.split("@", 3); // split into 3 parts, split at every @ char
            String domain = "https://" + splitQuery[2] + "/api/v2/search"; // use as domain below
            if (splitQuery.length == 3) {
                Integer finalLimit = limit; // necessary as local var ref from lambda must be final
                return WebClient.builder()
                        .baseUrl(domain)
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder.queryParam("q", splitQuery[1])
                                .queryParam("limit", finalLimit)
                                .build())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(SearchResult.class)
                        .onErrorResume(WebClientException.class, e -> {
                            System.err.println("Error: " + e.getMessage());
                            e.printStackTrace();
                            return Mono.empty();
                        });
            }
        } else {
            /// normal search (of local instance)
            return accountRepository.findByAcctLike(query).take(limit).collectList().map(accounts -> {
                result.getAccounts().addAll(accounts);
                if (type != null && type.equals("accounts"))
                // set other arrays empty if only searching for accounts
                {
                    result.setStatuses(new ArrayList<>(0));
                    result.setHashtags(new ArrayList<>(0));
                }
                return result;
            });
        }
        return Mono.empty();
    }
}
