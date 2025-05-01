package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.generated.SearchResult;
import edu.sjsu.moth.server.controller.InboxController;
import edu.sjsu.moth.server.controller.MothController;
import edu.sjsu.moth.server.db.AcceptMessage;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Follow;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.db.WebfingerAlias;
import edu.sjsu.moth.server.db.WebfingerRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.WebFingerUtils;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    FollowRepository followRepository;

    @Autowired
    PubKeyPairRepository pubKeyPairRepository;

    @Autowired
    EmailService emailService;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActivityPubService activityPubService;

    static PubKeyPair genPubKeyPair(String acct) {
        var pair = WebFingerUtils.genPubPrivKeyPem();
        return new PubKeyPair(acct, pair.pubKey(), pair.privKey());
    }

    public Mono<Void> createAccount(String username, String email, String password) {
        // there are some ugly race conditions and failure cases here!!!
        log.info("creating %s for %s\n".formatted(username, email));
        return accountRepository.findItemByAcct(username).flatMap(a -> Mono.error(EmailService.AlreadyRegistered::new))
                .then(emailService.assignAccountToEmail(email, username, password))
                .then(accountRepository.save(new Account(username)))
                .then(webfingerRepository.save(new WebfingerAlias(username, username, MothController.HOSTNAME)))
                .then(pubKeyPairRepository.save(genPubKeyPair(username))).then();
    }

    public Mono<Account> getAccount(String username) {
        return accountRepository.findItemByAcct(username);
    }

    public Mono<Account> getAccountById(String id) {
        return accountRepository.findById(id);
    }

    public Mono<String> getPublicKey(String id, boolean addIfMissing) {
        var mono = pubKeyPairRepository.findItemByAcct(id).map(pair -> pair.publicKeyPEM);
        if (addIfMissing) {
            mono = mono.switchIfEmpty(Mono.just(WebFingerUtils.genPubPrivKeyPem()).flatMap(
                            p -> pubKeyPairRepository.save(new PubKeyPair(id, p.pubKey(), p.privKey())))
                                              .map(p -> p.publicKeyPEM));
        }
        return mono;
    }

    public Mono<String> getPrivateKey(String id, boolean addIfMissing) {
        var mono = pubKeyPairRepository.findItemByAcct(id).map(pair -> pair.privateKeyPEM);
        if (addIfMissing) {
            mono = mono.switchIfEmpty(Mono.just(WebFingerUtils.genPubPrivKeyPem()).flatMap(
                            p -> pubKeyPairRepository.save(new PubKeyPair(id, p.pubKey(), p.privKey())))
                                              .map(p -> p.privateKeyPEM));
        }
        return mono;
    }

    public Mono<String> followerHandler(String id, JsonNode inboxNode, String requestType) {
        switch (requestType) {
            case "Follow" -> {
                String follower = inboxNode.get("actor").asText();
                //To test this send a follow request from another mastodon app
                //follower is the person who sent the follow request
                var follow = new Follow(follower, id);
                var myDomain = MothConfiguration.mothConfiguration.getServerName();
                String followerDomain;
                try {
                    //The domain of the person who sent the follow request
                    followerDomain = new URL(follower).getHost();
                } catch (MalformedURLException e) {
                    return Mono.error(new RuntimeException("Error: Malformed actor URL"));
                }
                if (!followerDomain.equals(myDomain)) {
                    // Compose this Mono into the flow so it actually runs
                    return sendAcceptMessage(inboxNode, id, myDomain, followerDomain).then(
                            accountRepository.findItemByAcct(id).switchIfEmpty(
                                            Mono.error(new RuntimeException("Error: User account does not exist")))
                                    .flatMap(account -> {
                                        return followRepository.save(follow)
                                                .then(followRepository.countAllByFollowedId(account.id)
                                                              .flatMap(followersCount -> {
                                                                  account.followers_count = followersCount.intValue();
                                                                  return accountRepository.save(account);
                                                              })).thenReturn("done");
                                    }));

                } else {
                    return accountRepository.findItemByAcct(id)
                            .switchIfEmpty(Mono.error(new RuntimeException("Error: User account does not exist")))
                            .flatMap(account -> accountRepository.findItemByAcct(follower).switchIfEmpty(
                                    Mono.error(new RuntimeException("Error: Follower account does not exist"))).flatMap(
                                    followerAccount -> followRepository.save(follow)
                                            .then(followRepository.countAllByFollowedId(account.id)
                                                          .flatMap(followersCount -> {
                                                              account.followers_count = followersCount.intValue();
                                                              return accountRepository.save(account);
                                                          }))
                                            .then(followRepository.countAllByFollowerId(followerAccount.id)
                                                          .flatMap(followingCount -> {
                                                              followerAccount.following_count =
                                                                      followingCount.intValue();
                                                              return accountRepository.save(followerAccount);
                                                          })).thenReturn("done")));
                }
            }
            case "Accept" -> {
                //The accept code has to be completed, when we receive an Accept message for a Follow request
                System.out.println("Received Accept activity: " + inboxNode.toPrettyString());
                return Mono.just("Accept received");
            }
            case "Undo" -> {
                //The Undo code has to be completed, when we receive an unfollow request
                System.out.println("Received Undo activity: " + inboxNode.toPrettyString());
                return Mono.just("Undo received");
            }
        }

        return Mono.error(new RuntimeException("Error: Unsupported request type"));
    }

    //This is to send an accept message to the follower server, so that they know the request has been accepted
    // otherwise it will be in a pending state
    public Mono<Void> sendAcceptMessage(JsonNode body, String id, String mydomain, String followerDomain) {
        String uuid = UUID.randomUUID().toString();
        //Message UUID to differentiate one message from other
        String messageId = String.format("https://%s/%s", mydomain, uuid);
        //My profile URL
        String actorUrl = String.format("https://%s/users/%s", mydomain, id);
        AcceptMessage acceptMessage = new AcceptMessage(messageId, actorUrl, body);
        JsonNode message = objectMapper.valueToTree(acceptMessage);
        return activityPubService.sendSignedActivity(message, id, body.get("actor").asText() + "/inbox");
    }

    public Mono<ArrayList<Account>> userFollowInfo(String id, String max_id, String since_id, String min_id,
                                                   Integer limit) {
        return followRepository.findAllByFollowerId(id)
                .flatMap(follow -> accountRepository.findById(follow.id.followed_id))
                .collect(ArrayList::new, ArrayList::add);
    }

    public Mono<ArrayList<Account>> userFollowingInfo(String id, String max_id, String since_id, String min_id,
                                                      Integer limit) {
        return followRepository.findAllByFollowedId(id)
                .flatMap(follow -> accountRepository.findById(follow.id.follower_id))
                .collect(ArrayList::new, ArrayList::add);
    }

    public Mono<InboxController.UsersFollowResponse> usersFollow(String id, Integer page, Integer limit,
                                                                 String followType) {
        var items = followType.equals("following") ?
                followRepository.findAllByFollowerId(id).map(followedUser -> followedUser.id.followed_id).take(limit)
                        .collectList() :
                followRepository.findAllByFollowedId(id).map(followerUser -> followerUser.id.follower_id).take(limit)
                        .collectList();
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
                String newReturnID =
                        limit != null ? returnID + "?page=" + page + "&limit=" + limit : returnID + "?page=" + page;
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



    public List<String> paginateFollowers(List<String> followers, int pageNo, int pageSize) {
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
        return getAccount(user.getName()).switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> followRepository.findAllByFollowerId(acct.id).collect(Collectors.toSet()).flatMap(
                        followers -> accountRepository.findByAcctLike(query)
                                .filter(account -> following == null || !following || followers.contains(account.id))
                                .take(limit).collectList().map(accounts -> {
                                    result.accounts.addAll(accounts);
                                    if (max_id != null) result.accounts.stream()
                                            .filter(c -> Integer.parseInt(c.id) < Integer.parseInt(max_id));
                                    if (min_id != null) result.accounts.stream()
                                            .filter(c -> Integer.parseInt(c.id) > Integer.parseInt(min_id));
                                    if (offset != null) result.accounts.subList(offset, result.accounts.size());
                                    return result;
                                })));
    }


    public Mono<Relationship> checkRelationship(String followerId, String followedId) {
        var followed = followRepository.findIfFollows(followerId, followedId).hasElement();
        return followed.flatMap(isFollow -> {
            if (isFollow) {
                return followRepository.findIfFollows(followedId, followerId)
                        .map(follow -> new Relationship(followerId, true, false, false, true, false, false, false,
                                                        false, false, false, false, false, "")).switchIfEmpty(Mono.just(
                                new Relationship(followerId, true, false, false, false, false, false, false, false,
                                                 false, false, false, false, "")));
            } else {
                return followRepository.findIfFollows(followedId, followerId)
                        .map(follow -> new Relationship(followerId, false, false, false, true, false, false, false,
                                                        false, false, false, false, false, "")).switchIfEmpty(Mono.just(
                                new Relationship(followerId, false, false, false, false, false, false, false, false,
                                                 false, false, false, false, "")));
            }
        });
    }
}
