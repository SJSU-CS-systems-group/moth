package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.SearchResult;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.ExternalActorRepository;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.StatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;
import java.time.Duration;
import lombok.extern.apachecommons.CommonsLog;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@CommonsLog
@RestController
public class SearchController {

    @Autowired
    StatusService statusService;

    @Autowired
    AccountService accountService;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    public SearchController(WebClient.Builder webBuilder) {
    }

    @GetMapping("api/v2/search")
    // DOCS FOR SPECS --> https://docs.joinmastodon.org/methods/search/
    public Mono<SearchResult> doSearch(@RequestParam("q") String query, Principal user, // user sending request, must
                                       // be authenticated user
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
        // return empty SearchResult obj. , until query.length() >= 3
        if (query.length() < 3) {
            return Mono.just(result);
        }
        // search results limiter defaulter
        if (limit == null || limit == 0 || limit >= 41) {
            limit = 20;
        }
        //// this is meant for if someone does @user@someInstance.com, splitQuery[1] will be someInstance
        if (query.contains("@")) {
            if (query.startsWith("@")) // truncate leading @ if its above, transform into user@someInstance.com
                query = query.substring(1);
            String[] splitQuery = query.split("@", 2); // split into 2 parts, split at @ char
            
            // If query doesn't have both username and domain, treat as local search
            if (splitQuery.length != 2 || splitQuery[0].isEmpty() || splitQuery[1].isEmpty()) {
                // Fall through to local search with the query (without @)
                query = query.replace("@", "");
            } else {
                String domain = splitQuery[1].trim();

                // Validate domain (basic check)
                if (!domain.matches("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
                    // Invalid domain, return empty Mono to indicate no response body
                    return Mono.empty();
                }

                String baseUrl = "https://" + domain + "/api/v2/search";
                Integer finalLimit = limit; // necessary as local var ref from lambda must be final
                return WebClient.builder()
                        .baseUrl(baseUrl)
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder.queryParam("q", splitQuery[0]).queryParam("limit", finalLimit)
                                .queryParamIfPresent("type", Optional.ofNullable(type)).build())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(SearchResult.class)
                        .timeout(Duration.ofSeconds(10)) // Add timeout to prevent hanging
                        .flatMap(searchResult -> {
                            if (searchResult == null || searchResult.accounts == null || searchResult.accounts.isEmpty()) {
                                return Mono.just(result);
                            }
                            // Filter out local users and normalize remote accts
                            List<Account> remoteAccounts = searchResult.accounts.stream().filter(account -> {
                                // If the URL does not point to your own domain, it's remote
                                return account != null && account.url != null && !account.url.contains(MothController.HOSTNAME);
                            }).peek(account -> {
                                // If acct does not contain "@", normalize it with the remote domain
                                if (account.acct != null && !account.acct.contains("@")) {
                                    try {
                                        String remoteDomain = account.url.split("/")[2]; // e.g., mastodon.social
                                        account.acct = account.acct + "@" + remoteDomain;
                                        account.id = account.acct;
                                    } catch (Exception e) {
                                        // If URL parsing fails, skip normalization
                                        log.warn("Failed to parse remote account URL: " + account.url);
                                    }
                                }
                            }).toList();

                            if (remoteAccounts.isEmpty()) {
                                return Mono.just(result);
                            }

                            return accountRepository.saveAll(remoteAccounts).then(Mono.just(searchResult));
                        }).onErrorResume(Exception.class, e -> {
                            // Log error and return empty result instead of crashing
                            log.warn("Error searching remote instance " + domain + ": " + e.getMessage());
                            return Mono.just(result);
                        });
            }
        }
        
        // Local search (either no @ or partial @ query)
        {
            // normal search (of local instance)
            switch (type) {
                case "": {
                    return Mono.zip(
                            accountService.filterAccountSearch(query, user, following, max_id, min_id, limit, offset,
                                                               result),
                            statusService.filterStatusSearch(query, user, account_id, max_id, min_id, limit, offset,
                                                             result)).map(t -> {
                        result.accounts = t.getT1().accounts;
                        result.statuses = t.getT2().statuses;
                        return result;
                    });
                }
                case "accounts": {
                    return accountService.filterAccountSearch(query, user, following, max_id, min_id, limit, offset,
                                                              result);
                }
                case "statuses": {
                    return statusService.filterStatusSearch(query, user, account_id, max_id, min_id, limit, offset,
                                                            result);
                }
                case "hashtags": {
                    // incomplete; complete when hashtags are implemented
                    // check RequestParams: exclude_unreviewed, max_id, min_id, offset
                    return Mono.just(result);
                }
                default: {
                    return Mono.just(result);
                }
            }
        }
    }
}
