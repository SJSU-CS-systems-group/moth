package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.StatusRepository;
import edu.sjsu.moth.server.db.UserList;
import edu.sjsu.moth.server.db.UserListRepository;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.util.EmailCodeUtils;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class ListsController {

    private final UserListRepository userListRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final StatusRepository statusRepository;

    public ListsController(UserListRepository userListRepository, AccountRepository accountRepository,
                           AccountService accountService, StatusRepository statusRepository) {
        this.userListRepository = userListRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.statusRepository = statusRepository;
    }

    public record ListResponse(String id, String title, String replies_policy, boolean exclusive) {
        public static ListResponse from(UserList list) {
            return new ListResponse(list.id, list.title, list.replies_policy, list.exclusive);
        }
    }

    @GetMapping("/api/v1/lists")
    public Mono<ResponseEntity<List<ListResponse>>> getLists(Principal user) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .flatMap(acct -> userListRepository.findAllByOwnerId(acct.id)
                        .map(ListResponse::from)
                        .collectList())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(List.of()));
    }

    @PostMapping("/api/v1/lists")
    public Mono<ResponseEntity<ListResponse>> createList(Principal user, @RequestBody CreateListRequest request) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .flatMap(acct -> {
                    var list = new UserList(
                            new ObjectId().toHexString(),
                            acct.id,
                            request.title(),
                            request.replies_policy(),
                            request.exclusive() != null && request.exclusive(),
                            EmailCodeUtils.now()
                    );
                    return userListRepository.save(list);
                })
                .map(list -> ResponseEntity.ok(ListResponse.from(list)));
    }

    @GetMapping("/api/v1/lists/{id}")
    public Mono<ResponseEntity<ListResponse>> getList(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return userListRepository.findById(id)
                .map(list -> ResponseEntity.ok(ListResponse.from(list)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/v1/lists/{id}")
    public Mono<ResponseEntity<ListResponse>> updateList(Principal user, @PathVariable String id,
                                                          @RequestBody UpdateListRequest request) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .flatMap(acct -> userListRepository.findById(id)
                        .filter(list -> list.owner_id.equals(acct.id))
                        .flatMap(list -> {
                            if (request.title() != null) {
                                list.title = request.title();
                            }
                            if (request.replies_policy() != null) {
                                list.replies_policy = request.replies_policy();
                            }
                            if (request.exclusive() != null) {
                                list.exclusive = request.exclusive();
                            }
                            return userListRepository.save(list);
                        }))
                .map(list -> ResponseEntity.ok(ListResponse.from(list)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/v1/lists/{id}")
    public Mono<ResponseEntity<Object>> deleteList(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .flatMap(acct -> userListRepository.findById(id)
                        .filter(list -> list.owner_id.equals(acct.id))
                        .flatMap(list -> userListRepository.delete(list).thenReturn(list)))
                .map(list -> ResponseEntity.ok().build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/lists/{id}/accounts")
    public Mono<ResponseEntity<List<Account>>> getListAccounts(Principal user, @PathVariable String id,
                                                                @RequestParam(required = false, defaultValue = "40") int limit) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return userListRepository.findById(id)
                .flatMap(list -> Flux.fromIterable(list.account_ids)
                        .flatMap(accountRepository::findById)
                        .take(limit)
                        .collectList())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(List.of()));
    }

    @PostMapping("/api/v1/lists/{id}/accounts")
    public Mono<ResponseEntity<Object>> addAccountsToList(Principal user, @PathVariable String id,
                                                           @RequestBody AddAccountsRequest request) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .flatMap(acct -> userListRepository.findById(id)
                        .filter(list -> list.owner_id.equals(acct.id))
                        .flatMap(list -> {
                            for (String accountId : request.account_ids()) {
                                if (!list.account_ids.contains(accountId)) {
                                    list.account_ids.add(accountId);
                                }
                            }
                            return userListRepository.save(list);
                        }))
                .map(list -> ResponseEntity.ok().build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/v1/lists/{id}/accounts")
    public Mono<ResponseEntity<Object>> removeAccountsFromList(Principal user, @PathVariable String id,
                                                                @RequestParam(name = "account_ids[]") List<String> accountIds) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .flatMap(acct -> userListRepository.findById(id)
                        .filter(list -> list.owner_id.equals(acct.id))
                        .flatMap(list -> {
                            list.account_ids.removeAll(accountIds);
                            return userListRepository.save(list);
                        }))
                .map(list -> ResponseEntity.ok().build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/timelines/list/{id}")
    public Mono<ResponseEntity<List<Status>>> getListTimeline(Principal user, @PathVariable String id,
                                                               @RequestParam(required = false) String max_id,
                                                               @RequestParam(required = false) String since_id,
                                                               @RequestParam(required = false) String min_id,
                                                               @RequestParam(required = false, defaultValue = "20") int limit) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return userListRepository.findById(id)
                .flatMap(list -> {
                    if (list.account_ids.isEmpty()) {
                        return Mono.just(List.<Status>of());
                    }
                    return Flux.fromIterable(list.account_ids)
                            .flatMap(accountId -> statusRepository.findAll()
                                    .filter(status -> status.account != null &&
                                            accountId.equals(status.account.id)))
                            .take(limit)
                            .collectList();
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(List.of()));
    }

    public record CreateListRequest(String title, String replies_policy, Boolean exclusive) {}
    public record UpdateListRequest(String title, String replies_policy, Boolean exclusive) {}
    public record AddAccountsRequest(List<String> account_ids) {}
}
