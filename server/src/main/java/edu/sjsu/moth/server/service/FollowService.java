package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Follow;
import edu.sjsu.moth.server.db.FollowRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@CommonsLog
@Configuration
public class FollowService {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private AccountRepository accountRepository;


    public Mono<Relationship> followUser(String followerId, String followedId) {
        var followResult = saveFollow(followerId, followedId);
        return followResult.flatMap(followStatus -> followRepository.findIfFollows(followedId, followerId)
                .map(follow -> new Relationship(followerId, true, false, false, true, false, false, false, false, false,
                                                false, false, false, "")).switchIfEmpty(Mono.just(
                        new Relationship(followerId, true, false, false, false, false, false, false, false, false,
                                         false, false, false, ""))));
    }

    public Mono<Relationship> unfollowUser(String followerId, String followedId) {
        var followResult = saveFollow(followerId, followedId);
        return followResult.flatMap(followStatus -> followRepository.findIfFollows(followedId, followerId)
                .map(follow -> new Relationship(followerId, false, false, false, false, false, false, false, false, false,
                                                false, false, false, "")).switchIfEmpty(Mono.just(
                        new Relationship(followerId, false, false, false, false, false, false, false, false, false,
                                         false, false, false, ""))));
    }

    public Mono<Follow> removeFollow(String followerId, String followedId) {
        return followRepository
                .findIfFollows(followerId, followedId)
                .flatMap(follow ->
                                 followRepository
                                         .delete(follow)
                                         .thenReturn(follow)
                );
    }

    public Mono<Follow> saveFollow(String followerId, String followedId) {
        return followRepository.findIfFollows(followerId, followedId).switchIfEmpty(Mono.defer(() -> {
            Follow follow = new Follow(followerId, followedId);
            return followRepository.save(follow);
        }));
    }

    public Mono<String> updateFollowerCounts(Account account, Account followerAccount) {
        return updateFollowersCount(account)
                .then(updateFollowingCount(followerAccount))
                .thenReturn("done");
    }

    public Mono<Void> updateFollowersCount(Account account) {
        return followRepository.countAllByFollowedId(account.id)
                .flatMap(count -> {
                    account.followers_count = count.intValue();
                    return accountRepository.save(account);
                }).then();
    }

    public Mono<Void> updateFollowingCount(Account followerAccount) {
        return followRepository.countAllByFollowerId(followerAccount.id)
                .flatMap(count -> {
                    followerAccount.following_count = count.intValue();
                    return accountRepository.save(followerAccount);
                }).then();
    }
}
