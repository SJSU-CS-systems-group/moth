package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.server.activityPub.ActivityPubUtil;
import edu.sjsu.moth.server.activityPub.message.FollowMessage;
import edu.sjsu.moth.server.activityPub.message.UndoMessage;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Follow;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.WebFingerUtils;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import static edu.sjsu.moth.server.util.Util.signAndSend;

@CommonsLog
@Configuration
public class FollowService {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    PubKeyPairRepository pubKeyPairRepository;

    public Mono<Relationship> followUser(String followerId, String followedId) {
        return Mono.zip(accountRepository.findById(followerId), accountRepository.findById(followedId))
                .flatMap(tuple -> {
                    Account followerAccount = tuple.getT1();
                    Account followedAccount = tuple.getT2();
                    String actorUrl =
                            String.format("https://%s/users/%s", MothConfiguration.mothConfiguration.getServerName(),
                                          followerAccount.id);
                    boolean isRemote = ActivityPubUtil.isRemoteUser(followedAccount.url);
                    FollowMessage followMessage =
                            new FollowMessage(actorUrl, ActivityPubUtil.toActivityPubUserUrl(followedAccount.url));
                    JsonNode message = objectMapper.valueToTree(followMessage);
                    Mono<Void> sendFollow = getPrivateKey(followerAccount.id, true).flatMap(
                            privKey -> signAndSend(message, actorUrl, message.get("object").asText() + "/inbox",
                                                   privKey)).then();

                    Mono<Follow> checkFollows = followRepository.findIfFollows(followedAccount.id, followerAccount.id);

                    if (!isRemote) {
                        Mono<String> saveAndRecount = saveFollow(followerAccount, followedAccount);
                        return sendFollow.then(saveAndRecount).then(checkFollows.map(
                                follow -> new Relationship(followerAccount.id, true, false, false, true, false, false,
                                                           false, false, false, false, false, false, "")).switchIfEmpty(
                                Mono.just(new Relationship(followerAccount.id, true, false, false, false, false, false,
                                                           false, false, false, false, false, false, ""))));
                    } else {
                        Mono<String> saveAndRecount = saveFollow(followerAccount, followedAccount.id);
                        return sendFollow.then(saveAndRecount).then(checkFollows.map(
                                follow -> new Relationship(followerAccount.id, false, false, false, true, false, false,
                                                           false, false, true, false, false, false, "")).switchIfEmpty(
                                Mono.just(new Relationship(followerAccount.id, false, false, false, false, false, false,
                                                           false, false, true, false, false, false, ""))));
                    }
                });
    }

    public Mono<Relationship> unfollowUser(String followerId, String followedId) {
        return Mono.zip(accountRepository.findById(followerId), accountRepository.findById(followedId))
                .flatMap(tuple -> {
                    Account followerAccount = tuple.getT1();
                    Account followedAccount = tuple.getT2();

                    String actorUrl =
                            String.format("https://%s/users/%s", MothConfiguration.mothConfiguration.getServerName(),
                                          followerAccount.id);
                    boolean isRemote = ActivityPubUtil.isRemoteUser(followedAccount.url);
                    FollowMessage followMessage =
                            new FollowMessage(actorUrl, ActivityPubUtil.toActivityPubUserUrl(followedAccount.url));
                    JsonNode fMsg = objectMapper.valueToTree(followMessage);
                    UndoMessage undoMessage = new UndoMessage(fMsg);
                    JsonNode message = objectMapper.valueToTree(undoMessage);
                    Mono<Void> sendUnFollow = getPrivateKey(followerAccount.id, true).flatMap(
                                    privKey -> signAndSend(message, actorUrl,
                                                           message.get("object").get("object").asText() + "/inbox",
                                                           privKey))
                            .then();
                    Mono<String> saveAndRecount = isRemote ? removeFollow(followerAccount, followedAccount.id) :
                            removeFollow(followerAccount, followedAccount);
                    return sendUnFollow.then(saveAndRecount).flatMap(
                            followStatus -> followRepository.findIfFollows(followedAccount.id, followerAccount.id)
                                    .map(follow -> new Relationship(followerAccount.id, false, false, false, true,
                                                                    false, false, false, false, false, false, false,
                                                                    false, "")).switchIfEmpty(Mono.just(
                                            new Relationship(followerAccount.id, false, false, false, false, false,
                                                             false, false, false, false, false, false, false, ""))));
                });
    }

    //followerAccount -> following-- || followedAccount -> followers--
    public Mono<String> removeFollow(Account followerAccount, Account followedAccount) {
        return followRepository.findIfFollows(followerAccount.id, followedAccount.id)
                .switchIfEmpty(Mono.error(new Exception("No follow relation exists")))
                .flatMap(follow -> followRepository.delete(follow))
                .then(updateFollowerCounts(followedAccount, followerAccount));
    }

    //followerAccount -> following++ || followedAccount -> followers++
    public Mono<String> removeFollow(Account followerAccount, String followedId) {
        return followRepository.findIfFollows(followerAccount.id, followedId)
                .switchIfEmpty(Mono.error(new Exception("No follow relation exists")))
                .flatMap(follow -> followRepository.delete(follow)).then(updateFollowingCount(followerAccount));
    }

    //followerAccount -> following++ || followedAccount -> followers++
    public Mono<String> removeFollow(String followerId, Account followedAccount) {
        return followRepository.findIfFollows(followerId, followedAccount.id)
                .switchIfEmpty(Mono.error(new Exception("No follow relation exists")))
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
    public Mono<String> saveFollow(Account followerAccount, String followedId) {
        return followRepository.findIfFollows(followerAccount.id, followedId).switchIfEmpty(Mono.defer(() -> {
            Follow follow = new Follow(followerAccount.id, followedId);
            return followRepository.save(follow);
        })).then(updateFollowingCount(followerAccount));
    }

    //followerAccount -> following++ || followedAccount -> followers++
    public Mono<String> saveFollow(String followerId, Account followedAccount) {
        return followRepository.findIfFollows(followerId, followedAccount.id).switchIfEmpty(Mono.defer(() -> {
            Follow follow = new Follow(followerId, followedAccount.id);
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


