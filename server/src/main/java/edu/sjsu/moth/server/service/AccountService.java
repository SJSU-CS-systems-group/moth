package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.generated.Attachment;
import edu.sjsu.moth.generated.CustomEmoji;
import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.generated.SearchResult;
import edu.sjsu.moth.server.activitypub.ActivityPubUtil;
import edu.sjsu.moth.server.activitypub.message.AcceptMessage;
import edu.sjsu.moth.server.controller.InboxController;
import edu.sjsu.moth.server.controller.MothController;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.AccountRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.security.auth.login.AccountNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    FollowService followService;

    @Autowired
    ActorService actorService;

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

    public Mono<Account> getAccount(String acct) {
        return accountRepository.findItemByAcct(acct);
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

    public Mono<String> followerHandler(String targetAcct, JsonNode inboxNode, boolean isUndo) {
        String actorUrl = inboxNode.path("actor").asText();
        String followerAcct = ActivityPubUtil.inboxUrlToAcct(actorUrl);
        boolean isRemote = ActivityPubUtil.isRemoteUser(actorUrl);

        // 1) look up the target account
        return accountRepository.findItemByAcct(targetAcct)
                .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + targetAcct)))
                .flatMap(targetAccount -> {
                    if (isRemote) {
                        // For a remote Actor we only update counts (and accept on real Follow)
                        Mono<String> followOp =
                                isUndo ? followService.removeIncomingRemoteFollow(followerAcct, targetAccount) :
                                        followService.saveIncomingRemoteFollow(followerAcct, targetAccount);
                        return isUndo ? followOp :
                                followOp.flatMap(__ -> sendAcceptMessage(inboxNode, targetAccount.acct));
                    } else {
                        return accountRepository.findItemByAcct(followerAcct).switchIfEmpty(
                                        Mono.error(new AccountNotFoundException("Follower not found: " + followerAcct)))
                                .flatMap(followerAccount -> isUndo ?
                                        followService.removeIncomingRemoteFollow(followerAcct, targetAccount) :
                                        followService.saveFollow(followerAccount, targetAccount));
                    }
                }).doOnError(e -> log.error(
                        "Failed to %s follow for %s: %s".formatted(isUndo ? "undo" : "process", targetAcct,
                                                                   e.getMessage()))).thenReturn("done");
    }

    public Mono<String> acceptHandler(String id, JsonNode inboxNode) {
        //The Undo code has to be completed, when we receive an unfollow request
        String actorUrl = inboxNode.path("actor").asText();
        String followerAcct = ActivityPubUtil.inboxUrlToAcct(actorUrl);
        return Mono.zip(accountRepository.findById(id), accountRepository.findItemByAcct(followerAcct))
                .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + id))).flatMap(tuple -> {
                    Account followerAccount = tuple.getT1();
                    Account followedAccount = tuple.getT2();
                    Mono<String> saveAndRecount =
                            followService.saveOutgoingRemoteFollow(followerAccount, followedAccount.id);
                    return saveAndRecount.thenReturn("Accept received");
                });
    }

    public Mono<Void> sendAcceptMessage(JsonNode body, String id) {
        //My profile URL
        String actorUrl = ActivityPubUtil.getActorUrl(id);
        AcceptMessage acceptMessage = new AcceptMessage(actorUrl, body);
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

    public Mono<Account> convertToAccount(Actor actor) {
        String serverName = "";
        try {
            serverName = new URL(actor.url).getHost();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        if (serverName.equalsIgnoreCase(MothConfiguration.mothConfiguration.getServerName())) {
            return getAccount(actor.preferredUsername);
        }

        ArrayList<AccountField> accountFields = new ArrayList<>();
        for (Attachment attachment : actor.attachment) {
            AccountField accountField = new AccountField(attachment.name, attachment.value, null);
            accountFields.add(accountField);
        }

        String iconLink = actor.icon != null ? actor.icon.url : "";
        String imageLink = actor.image != null ? actor.image.url : "";

        WebClient webClient =
                WebClient.builder().defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
        Mono<JsonNode> outboxResponse = webClient.get().uri(actor.outbox).retrieve().bodyToMono(JsonNode.class);
        Mono<JsonNode> followersResponse = webClient.get().uri(actor.followers).retrieve().bodyToMono(JsonNode.class);
        Mono<JsonNode> followingResponse = webClient.get().uri(actor.following).retrieve().bodyToMono(JsonNode.class);
        String finalServerName = serverName;
        return outboxResponse.flatMap(jsonNodeOutbox -> {
            int totalItems = jsonNodeOutbox.get("totalItems").asInt();
            return followersResponse.flatMap(jsonNodeFollowers -> {
                int totalItemFollowers = jsonNodeFollowers.get("totalItems").asInt();
                return followingResponse.map(jsonNodeFollowing -> {
                    int totalItemFollowing = jsonNodeFollowing.get("totalItems").asInt();
                    //change avatar, avatar static, header, header static, last status to "" from iconLink and imageLink
                    //change from String.valueOf(generateUniqueId()) to just their name
                    //changed last status from null to actor.published
                    return new Account(actor.preferredUsername, actor.preferredUsername,
                                       actor.preferredUsername + "@" + finalServerName, actor.url, actor.name,
                                       actor.summary, iconLink, iconLink, imageLink, imageLink,
                                       actor.manuallyApprovesFollowers, accountFields, new CustomEmoji[0], false, false,
                                       actor.discoverable, false, false, false, false, actor.published, actor.published,
                                       totalItems, totalItemFollowers, totalItemFollowing);
                });
            });
        });
        //don't know about custom emojis, bot, and group
        //noindex, moved, suspended, and limited are optional?
        //icon, image, tag, attachment might be null
        //not sure how to get last_status_at. outbox doesn't give a time, only the last status
    }

    public Mono<Account> fetchAccount(String actorUri) {
        return actorService.fetchActor(actorUri).flatMap(this::convertToAccount)
                .flatMap(account -> accountRepository.findItemByAcct(account.acct).flatMap(existing -> {
                    existing.username = account.username;
                    existing.acct = account.acct;
                    existing.url = account.url;
                    existing.display_name = account.display_name;
                    existing.note = account.note;
                    existing.avatar = account.avatar;
                    existing.avatar_static = account.avatar_static;
                    existing.header = account.header;
                    existing.header_static = account.header_static;
                    existing.locked = account.locked;
                    existing.fields = account.fields;
                    existing.emojis = account.emojis;
                    existing.bot = account.bot;
                    existing.group = account.group;
                    existing.discoverable = account.discoverable;
                    existing.noindex = account.noindex;
                    existing.suspended = account.suspended;
                    existing.limited = account.limited;
                    existing.created_at = account.created_at;
                    existing.last_status_at = account.last_status_at;
                    existing.statuses_count = account.statuses_count;
                    existing.followers_count = account.followers_count;
                    existing.following_count = account.following_count;
                    return accountRepository.save(existing);
                }).switchIfEmpty(Mono.defer(() -> accountRepository.save(account))));
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
