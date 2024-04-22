package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.generated.SearchResult;
import edu.sjsu.moth.server.controller.InboxController;
import edu.sjsu.moth.server.controller.MothController;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Followers;
import edu.sjsu.moth.server.db.FollowersRepository;
import edu.sjsu.moth.server.db.Following;
import edu.sjsu.moth.server.db.FollowingRepository;
import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.db.WebfingerAlias;
import edu.sjsu.moth.server.db.WebfingerRepository;
import edu.sjsu.moth.util.WebFingerUtils;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.beans.support.PagedListHolder.DEFAULT_PAGE_SIZE;

/**
 * this class manages the logic and DB access around account management.
 * controllers shouldn't be doing account management directly and this class
 * should not be generating views for controllers.
 */
@CommonsLog
@Configuration
public class AccountService {
    @Autowired
    WebfingerRepository webfingerRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    FollowersRepository followersRepository;

    @Autowired
    FollowingRepository followingRepository;

    @Autowired
    PubKeyPairRepository pubKeyPairRepository;

    @Autowired
    EmailService emailService;

    static PubKeyPair genPubKeyPair(String acct) {
        var pair = WebFingerUtils.genPubPrivKeyPem();
        return new PubKeyPair(acct, pair.pubKey(), pair.privKey());
    }

    public Mono<Void> createAccount(String username, String email, String password) {
        // there are some ugly race conditions and failure cases here!!!
        log.info("creating %s for %s\n".formatted(username, email));
        return accountRepository.findItemByAcct(username)
                .flatMap(a -> Mono.error(EmailService.AlreadyRegistered::new))
                .then(emailService.assignAccountToEmail(email, username, password))
                .then(accountRepository.save(new Account(username)))
                .then(webfingerRepository.save(new WebfingerAlias(username, username, MothController.HOSTNAME)))
                .then(pubKeyPairRepository.save(genPubKeyPair(username)))
                .then();
    }

    public Mono<Account> getAccount(String username) {

        var test = accountRepository.findItemByAcct(username);
        System.out.println(test);
        return test;
    }

    public Mono<Account> getAccountById(String id) {
        return accountRepository.findById(id);
    }

    public Mono<String> getPublicKey(String id, boolean addIfMissing) {
        var mono = pubKeyPairRepository.findItemByAcct(id).map(pair -> pair.publicKeyPEM);
        if (addIfMissing) {
            mono = mono.switchIfEmpty(Mono.just(WebFingerUtils.genPubPrivKeyPem())
                                              .flatMap(p -> pubKeyPairRepository.save(
                                                      new PubKeyPair(id, p.pubKey(), p.privKey())))
                                              .map(p -> p.publicKeyPEM));
        }
        return mono;
    }

    public Mono<String> followerHandler(String id, JsonNode inboxNode, String requestType) {
        String follower = inboxNode.get("actor").asText();
        if (requestType.equals("Follow")) {
            // find id, grab arraylist, append
            return accountRepository.findItemByAcct(id)
                    .switchIfEmpty(Mono.error(new RuntimeException("Error: Account to follow does not exist")))
                    .then(followersRepository.findItemById(id)
                                  .switchIfEmpty(Mono.just(new Followers(id, new ArrayList<>())))
                                  .flatMap(followedUser -> {
                                      followedUser.getFollowers().add(follower);
                                      return followersRepository.save(followedUser).thenReturn("done");
                                  }));
        }
        if (requestType.equals("Undo")) {
            // find id, grab arraylist, remove
            return followersRepository.findItemById(id).flatMap(followedUser -> {
                followedUser.getFollowers().remove(follower);
                return followersRepository.save(followedUser).thenReturn("done");
            });
        }
        return Mono.empty();
    }

    public Mono<InboxController.UsersFollowResponse> usersFollow(String id,
                                                                 @RequestParam(required = false) Integer page,
                                                                 @RequestParam(required = false) Integer limit,
                                                                 String followType) {
        var items = followType.equals("following") ? followingRepository.findItemById(id)
                .map(Following::getFollowing) : followersRepository.findItemById(id).map(Followers::getFollowers);
        String returnID = MothController.BASE_URL + "/users/" + id + followType;
        int pageSize = limit != null ? limit : DEFAULT_PAGE_SIZE;
        if (page == null) {
            String first = returnID + "?page=1";
            return items.map(
                    v -> new InboxController.UsersFollowResponse(returnID, "OrderedCollection", v.size(), first, null,
                                                                 null, null));
        } else { // page number is given
            int pageNum = page < 1 ? 1 : page;
            return items.map(v -> {
                String newReturnID = limit != null ? returnID + "?page=" + page + "&limit=" + limit : returnID +
                        "?page=" + page;
                if (pageNum * pageSize >= v.size()) { // no next page
                    return new InboxController.UsersFollowResponse(newReturnID, "OrderedCollectionPage", v.size(), null,
                                                                   null, returnID,
                                                                   paginateFollowers(v, pageNum, pageSize));
                } else {
                    String next = returnID + "?page=" + (pageNum + 1);
                    if (limit != null) {
                        next += "&limit=" + limit;
                    }
                    return new InboxController.UsersFollowResponse(newReturnID, "OrderedCollectionPage", v.size(), null,
                                                                   next, returnID,
                                                                   paginateFollowers(v, pageNum, pageSize));
                }
            });
        }
    }

    public List<String> paginateFollowers(ArrayList<String> followers, int pageNo, int pageSize) {
        int startIndex = (pageNo - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, followers.size());
        if (startIndex >= followers.size()) {
            return Collections.emptyList();
        }
        return followers.subList(startIndex, endIndex);
    }

    public Mono<Account> updateAccount(Account a) {
        return accountRepository.save(a);
    }

    public Mono<SearchResult> filterAccountSearch(String query, Principal user, Boolean following, String max_id,
                                                  String min_id, Integer limit, Integer offset, SearchResult result) {
        return accountRepository.findByAcctLike(query).take(limit).collectList().map(accounts -> {
            result.accounts.addAll(accounts);
            // check RequestParams: following, max_id, min_id, limit, offset
            if (following != null && following && user != null) {
                for (int i = 0; i < result.accounts.size(); i++) {
                    int finalI = i;
                    followersRepository.findItemById(((Account) user).id).map(followers -> {
                        if (!followers.followers.contains(user)) {
                            result.accounts.remove(accounts.get(finalI));
                        }
                        return null;
                    });
                }
            }
            if (max_id != null) result.accounts.stream().filter(c -> Integer.parseInt(c.id) < Integer.parseInt(max_id));
            if (min_id != null) result.accounts.stream().filter(c -> Integer.parseInt(c.id) > Integer.parseInt(min_id));
            if (offset != null) result.accounts.subList(offset, result.accounts.size());
            return result;
        });
    }
}
