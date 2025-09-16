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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    public Mono<SearchResult> doSearch(
            @RequestParam("q") String query, Principal user,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean resolve,
            @RequestParam(required = false) Boolean following,
            @RequestParam(required = false) String account_id,
            @RequestParam(required = false) Boolean exclude_unreviewed,
            @RequestParam(required = false) String max_id,
            @RequestParam(required = false) String min_id,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {

        SearchResult result = new SearchResult();

        if (query == null || query.length() < 3) {
            return Mono.just(result);
        }

        int finalLimit = (limit == null || limit <= 0 || limit >= 41) ? 20 : limit;

        if (query.contains("@")) {
            return handleRemoteSearch(query, type, finalLimit).flatMap(this::processRemoteAccounts);
        }

        // Local instance search
        return handleLocalSearch(query, type, user, following, account_id, exclude_unreviewed, max_id, min_id,
                                 finalLimit, offset, result);
    }

    private Mono<SearchResult> handleRemoteSearch(String query, String type, int limit) {
        if (query.startsWith("@")) {
            query = query.substring(1);
        }

        String[] splitQuery = query.split("@", 2);
        if (splitQuery.length != 2 || splitQuery[0].isEmpty() || splitQuery[1].isEmpty()) {
            return Mono.empty();
        }

        String domain = splitQuery[1].trim();
        if (!domain.matches("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return Mono.empty();
        }

        String baseUrl = "https://" + domain + "/api/v2/search";
        return WebClient.builder().baseUrl(baseUrl).build().get()
                .uri(uriBuilder -> uriBuilder.queryParam("q", splitQuery[0]).queryParam("limit", limit)
                        .queryParamIfPresent("type", Optional.ofNullable(type)).build())
                .accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(SearchResult.class);
    }

    private Mono<SearchResult> processRemoteAccounts(SearchResult searchResult) {
        if (searchResult.accounts == null || searchResult.accounts.isEmpty()) {
            return Mono.just(searchResult);
        }

        // Return all accounts (normalized), save only new ones
        List<Account> allNormalizedAccounts = new ArrayList<>();

        return Flux.fromIterable(searchResult.accounts).flatMap(account -> {
            // Normalize acct if missing "@"
            String acct = account.acct;
            if (acct != null && !acct.contains("@") && account.url != null) {
                String remoteDomain = account.url.split("/")[2];
                acct = acct + "@" + remoteDomain;
            }

            String finalAcct = acct;
            boolean isRemote = account.url != null && !account.url.contains(MothController.HOSTNAME);
            if (!isRemote || finalAcct == null) {
                allNormalizedAccounts.add(account);
                return Mono.empty();
            }

            account.acct = finalAcct;
            allNormalizedAccounts.add(account); // Include in response regardless

            return accountRepository.existsByAcct(finalAcct).filter(exists -> !exists)
                    .map(ignore -> new Account(account.username, finalAcct, account.url, account.display_name,
                                               account.note, account.avatar, account.avatar_static, account.header,
                                               account.header_static, account.locked, account.fields, account.emojis,
                                               account.bot, account.group, account.discoverable, account.noindex, false,
                                               account.suspended, account.limited, account.created_at,
                                               account.last_status_at, account.statuses_count, account.followers_count,
                                               account.following_count));
        }).collectList().flatMap(newAccountsToSave -> {
            if (newAccountsToSave.isEmpty()) {
                searchResult.accounts = new ArrayList<>(allNormalizedAccounts);
                return Mono.just(searchResult);
            }

            return accountRepository.saveAll(newAccountsToSave).then(Mono.fromCallable(() -> {
                searchResult.accounts = new ArrayList<>(allNormalizedAccounts);
                return searchResult;
            }));
        });
    }

    private Mono<SearchResult> handleLocalSearch(String query, String type, Principal user, Boolean following,
                                                 String account_id, Boolean exclude_unreviewed, String max_id,
                                                 String min_id, int limit, Integer offset, SearchResult result) {
        if (type == null || type.isEmpty()) {
            return Mono.zip(
                            accountService.filterAccountSearch(query, user, following, max_id, min_id, limit, offset,
                                                               result),
                            statusService.filterStatusSearch(query, user, account_id, max_id, min_id, limit, offset,
                                                             result))
                    .map(tuple -> {
                        result.accounts = tuple.getT1().accounts;
                        result.statuses = tuple.getT2().statuses;
                        return result;
                    });
        }

        return switch (type) {
            case "accounts" ->
                    accountService.filterAccountSearch(query, user, following, max_id, min_id, limit, offset, result);
            case "statuses" ->
                    statusService.filterStatusSearch(query, user, account_id, max_id, min_id, limit, offset, result);
            case "hashtags" -> Mono.empty(); // Not implemented
            default -> Mono.just(result);
        };
    }
}
