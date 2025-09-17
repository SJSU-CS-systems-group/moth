package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.SearchResult;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.ActorService;
import edu.sjsu.moth.server.service.StatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;

@RestController
public class SearchController {

    @Autowired
    StatusService statusService;

    @Autowired
    AccountService accountService;

    @Autowired
    ActorService actorService;

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
        if (limit == null || limit == 0 || limit >= 41) {
            limit = 20;
        }

        if (query.contains("@")) {
            String userHandle = query.startsWith("@") ? query.substring(1) : query;
            return actorService.resolveRemoteAccount(userHandle).map(account -> {
                result.accounts.add(account);
                Status statuses = Mono.just(new Status()).block();
                result.statuses.add(statuses);
                return result;
            }).switchIfEmpty(Mono.just(new SearchResult())).onErrorResume(e -> Mono.just(new SearchResult()));
        } else {
            // normal search (of local instance)
            String searchType = type == null ? "" : type;
            switch (searchType) {
                case "accounts":
                    return accountService.filterAccountSearch(query, user, following, max_id, min_id, limit, offset,
                                                              result);
                case "statuses":
                    return statusService.filterStatusSearch(query, user, account_id, max_id, min_id, limit, offset,
                                                            result);
                case "hashtags":
                    return Mono.just(new SearchResult()); // Not implemented
                default:
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
        }
    }
}