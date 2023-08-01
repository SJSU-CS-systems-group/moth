package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Optional;

@RestController
public class SearchController {

    @Autowired
    AccountRepository accountRepository; // our own repository

    @Autowired
    public SearchController(WebClient.Builder webBuilder) {
    }

    @GetMapping("api/v2/search")
    // DOCS FOR SPECS --> https://docs.joinmastodon.org/methods/search/
    public Mono<SearchResult> doSearch(@RequestParam("q") String query,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(required = false) Boolean resolve,
                                       @RequestParam(required = false) Boolean following,
                                       @RequestParam(required = false) String account_id,
                                       @RequestParam(required = false) Boolean exclude_unreviewed,
                                       @RequestParam(required = false) String max_id,
                                       @RequestParam(required = false) String min_id,
                                       @RequestParam(required = false) Integer limit,
                                       @RequestParam(required = false) Integer offset)
    {
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
            String domain = "https://" + splitQuery[1] + "/api/v2/search"; // use as domain below
            if (splitQuery.length == 2) {
                Integer finalLimit = limit; // necessary as local var ref from lambda must be final
                return WebClient.builder()
                        .baseUrl(domain)
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder.queryParam("q", splitQuery[0])
                                .queryParam("limit", finalLimit)
                                .queryParamIfPresent("type", Optional.ofNullable(type))
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
            // TO DO: finish other search types, finish @RequestParam processing
            return accountRepository.findByAcctLike(query).take(limit).collectList().map(accounts -> {
                result.getAccounts().addAll(accounts);
                if (type != null && type.equals("accounts"))
                // return as is
                {
                    return result;
                }
                return result;
            });
        }
        return Mono.empty();
    }

    public static class SearchResult {
        // https://docs.joinmastodon.org/entities/Search/
        private ArrayList<Account> accounts;
        private ArrayList<Status> statuses;
        private ArrayList<Hashtags> hashtags;

        // constructor, avoiding null pointer exception error
        public SearchResult() {
            this.accounts = new ArrayList<>(0);
            this.statuses = new ArrayList<>(0);
            this.hashtags = new ArrayList<>(0);
        }

        // getters, setters
        public ArrayList<Account> getAccounts() {return accounts;}

        public ArrayList<Status> getStatuses() {return statuses;}

        public ArrayList<Hashtags> getHashtags() {return hashtags;}

        public ArrayList<Account> setAccounts(ArrayList<Account> accountsModify) {
            return this.accounts = accountsModify;
        }

        public ArrayList<Status> setStatuses(ArrayList<Status> statusesModify) {return this.statuses = statusesModify;}

        public ArrayList<Hashtags> setHashtags(ArrayList<Hashtags> hashtagsModify) {
            return this.hashtags = hashtagsModify;
        }
    }

    public static class Hashtags {
        private String name, url;

        public Hashtags() {
            ArrayList<History> history = new ArrayList<>(0);
        }

        // getters, setters
        public String getName() {return name;}

        public String getUrl() {return url;}

        //history class
        public static class History {
            private int day, uses, accounts;

            // getters, setters
            public int getDay() {return day;}

            public int getAccounts() {
                return accounts;
            }

            public int getUses() {return uses;}
        }
    }
}
