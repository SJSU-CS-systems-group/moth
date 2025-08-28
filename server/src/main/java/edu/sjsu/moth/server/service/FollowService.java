package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.server.activitypub.ActivityPubUtil;
import edu.sjsu.moth.server.activitypub.message.FollowMessage;
import edu.sjsu.moth.server.activitypub.message.UndoMessage;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Follow;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.util.WebFingerUtils;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@CommonsLog
@Configuration
public class FollowService {

    @Autowired
    PubKeyPairRepository pubKeyPairRepository;
    @Autowired
    private FollowRepository followRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ActivityPubService activityPubService;

    public Mono<Relationship> followUser(String followerId, String followedId) {
        return Mono.zip(accountRepository.findById(followerId), accountRepository.findById(followedId)).switchIfEmpty(
                        Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                               "Follower or Followed account " + "not found")))
                .flatMap(tuple -> {
                    Account followerAccount = tuple.getT1();
                    Account followedAccount = tuple.getT2();

                    boolean isRemote = ActivityPubUtil.isRemoteUser(followedAccount.url);
                    Mono<Follow> checkFollows = followRepository.findIfFollows(followedAccount.id, followerAccount.id);

                    if (!isRemote) {
                        Mono<String> saveAndRecount = saveFollow(followerAccount, followedAccount);
                        return saveAndRecount.then(checkFollows.map(
                                follow -> new Relationship(followerAccount.id, true, false, false, true, false, false,
                                                           false, false, false, false, false, false, "")).switchIfEmpty(
                                Mono.just(new Relationship(followerAccount.id, true, false, false, false, false, false,
                                                           false, false, false, false, false, false, ""))));
                    } else {
                        String actorUrl = ActivityPubUtil.getActorUrl(followerAccount.id);
                        FollowMessage followMessage =
                                new FollowMessage(actorUrl, ActivityPubUtil.toActivityPubUserUrl(followedAccount.url));
                        JsonNode message = objectMapper.valueToTree(followMessage);

                        Mono<Void> sendFollow = activityPubService.sendSignedActivity(message, followerAccount.id,
                                                                                      message.get("object").asText() +
                                                                                              "/inbox");

                        return sendFollow.then(checkFollows.map(
                                follow -> new Relationship(followerAccount.id, false, false, false, true, false, false,
                                                           false, false, true, false, false, false, "")).switchIfEmpty(
                                Mono.just(new Relationship(followerAccount.id, false, false, false, false, false, false,
                                                           false, false, true, false, false, false, ""))));
                    }
                }).onErrorMap(e -> {
                    if (e instanceof ResponseStatusException) {
                        return e;
                    } else {
                        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                                           "An unexpected error occurred", e);
                    }
                });

    }

    public Mono<Relationship> unfollowUser(String followerId, String followedId) {
        return Mono.zip(accountRepository.findById(followerId), accountRepository.findById(followedId)).switchIfEmpty(
                        Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                               "Follower or Followed account " + "not found")))
                .flatMap(tuple -> {
                    Account followerAccount = tuple.getT1();
                    Account followedAccount = tuple.getT2();

                    String actorUrl = ActivityPubUtil.getActorUrl(followerAccount.id);
                    boolean isRemote = ActivityPubUtil.isRemoteUser(followedAccount.url);
                    Mono<Relationship> postUnfollow =
                            followRepository.findIfFollows(followedAccount.id, followerAccount.id)
                                    .map(follow -> new Relationship(followerAccount.id, false, false, false, true,
                                                                    false, false, false, false, false, false, false,
                                                                    false, "")).switchIfEmpty(Mono.just(
                                            new Relationship(followerAccount.id, false, false, false, false, false,
                                                             false,
                                                             false, false, false, false, false, false, "")));
                    if (isRemote) {
                        Mono<String> saveAndRecount = removeOutgoingRemoteFollow(followerAccount, followedAccount.id);
                        FollowMessage followMessage =
                                new FollowMessage(actorUrl, ActivityPubUtil.toActivityPubUserUrl(followedAccount.url));
                        JsonNode fMsg = objectMapper.valueToTree(followMessage);
                        UndoMessage undoMessage = new UndoMessage(fMsg);
                        JsonNode message = objectMapper.valueToTree(undoMessage);
                        Mono<Void> sendUnFollow = activityPubService.sendSignedActivity(message, followerAccount.id,
                                                                                        fMsg.get("object").asText() +
                                                                                                "/inbox");
                        return sendUnFollow.then(saveAndRecount).flatMap(follow -> postUnfollow);
                    } else {
                        Mono<String> saveAndRecount = removeFollow(followerAccount, followedAccount);
                        return saveAndRecount.flatMap(followStatus -> postUnfollow);
                    }
                }).onErrorMap(e -> {
                    if (e instanceof ResponseStatusException) {
                        return e;
                    } else {
                        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                                           "An unexpected error occurred", e);
                    }
                });
    }

    //followerAccount -> following-- || followedAccount -> followers--
    public Mono<String> removeFollow(Account followerAccount, Account followedAccount) {
        return followRepository.findIfFollows(followerAccount.id, followedAccount.id).switchIfEmpty(
                        Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No follow relation exists")))
                .flatMap(follow -> followRepository.delete(follow))
                .then(updateFollowerCounts(followedAccount, followerAccount));
    }

    //followerAccount -> following++ || followedAccount -> followers++
    public Mono<String> removeOutgoingRemoteFollow(Account followerAccount, String remoteFollowedId) {
        return followRepository.findIfFollows(followerAccount.id, remoteFollowedId).switchIfEmpty(
                        Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No follow relation exists")))
                .flatMap(follow -> followRepository.delete(follow)).then(updateFollowingCount(followerAccount));
    }

    //followerAccount -> following++ || followedAccount -> followers++
    public Mono<String> removeIncomingRemoteFollow(String remoteFollowerId, Account followedAccount) {
        return followRepository.findIfFollows(remoteFollowerId, followedAccount.id).switchIfEmpty(
                        Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No follow relation exists")))
                .flatMap(follow -> followRepository.delete(follow)).then(updateFollowersCount(followedAccount));
    }

    //followerAccount -> following++ || followedAccount -> followers++
    public Mono<String> saveFollow(Account followerAccount, Account followedAccount) {
        return followRepository.findIfFollows(followerAccount.id, followedAccount.id).switchIfEmpty(Mono.defer(() -> {
            Follow follow = new Follow(followerAccount.id, followedAccount.id);
            return followRepository.save(follow);
        })).then(updateFollowerCounts(followedAccount, followerAccount));
    }

    //followerAccount -> following++ || followedAccount -> followers++
    public Mono<String> saveOutgoingRemoteFollow(Account followerAccount, String remoteFollowedId) {
        return followRepository.findIfFollows(followerAccount.id, remoteFollowedId).switchIfEmpty(Mono.defer(() -> {
            Follow follow = new Follow(followerAccount.id, remoteFollowedId);
            return followRepository.save(follow);
        })).then(updateFollowingCount(followerAccount));
    }

    //followerAccount -> following++ || followedAccount -> followers++
    public Mono<String> saveIncomingRemoteFollow(String remoteFollowerId, Account followedAccount) {
        return followRepository.findIfFollows(remoteFollowerId, followedAccount.id).switchIfEmpty(Mono.defer(() -> {
            Follow follow = new Follow(remoteFollowerId, followedAccount.id);
            return followRepository.save(follow);
        })).then(updateFollowersCount(followedAccount));
    }

    public Mono<String> updateFollowerCounts(Account followedAccount, Account followerAccount) {
        return updateFollowersCount(followedAccount).then(updateFollowingCount(followerAccount)).thenReturn("done");
    }

    public Mono<String> updateFollowersCount(Account account) {
        return followRepository.countAllByFollowedId(account.id).flatMap(count -> {
            account.followers_count = count.intValue();
            return accountRepository.save(account);
        }).thenReturn("done");
    }

    public Mono<String> updateFollowingCount(Account account) {
        return followRepository.countAllByFollowerId(account.id).flatMap(count -> {
            account.following_count = count.intValue();
            return accountRepository.save(account);
        }).thenReturn("done");
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
}


