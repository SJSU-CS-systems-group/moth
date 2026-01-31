package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.BlockRepository;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.Mute;
import edu.sjsu.moth.server.db.MuteRepository;
import edu.sjsu.moth.util.EmailCodeUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
public class MuteService {

    private final MuteRepository muteRepository;
    private final AccountRepository accountRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;

    public MuteService(MuteRepository muteRepository, AccountRepository accountRepository,
                       FollowRepository followRepository, BlockRepository blockRepository) {
        this.muteRepository = muteRepository;
        this.accountRepository = accountRepository;
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
    }

    public Mono<Relationship> mute(String muterId, String mutedId, boolean muteNotifications, Long durationSeconds) {
        return muteRepository.findByMuterIdAndMutedId(muterId, mutedId)
                .flatMap(existing -> {
                    existing.mute_notifications = muteNotifications;
                    existing.duration = durationSeconds;
                    if (durationSeconds != null && durationSeconds > 0) {
                        existing.expires_at = Instant.now().plus(Duration.ofSeconds(durationSeconds)).toString();
                    } else {
                        existing.expires_at = null;
                    }
                    return muteRepository.save(existing).then(buildRelationship(muterId, mutedId));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    String expiresAt = null;
                    if (durationSeconds != null && durationSeconds > 0) {
                        expiresAt = Instant.now().plus(Duration.ofSeconds(durationSeconds)).toString();
                    }
                    var mute = new Mute(muterId, mutedId, muteNotifications, durationSeconds, EmailCodeUtils.now(), expiresAt);
                    return muteRepository.save(mute).then(buildRelationship(muterId, mutedId));
                }));
    }

    public Mono<Relationship> unmute(String muterId, String mutedId) {
        return muteRepository.findByMuterIdAndMutedId(muterId, mutedId)
                .flatMap(mute -> muteRepository.delete(mute).then(buildRelationship(muterId, mutedId)))
                .switchIfEmpty(buildRelationship(muterId, mutedId));
    }

    public Mono<Boolean> isMuting(String muterId, String mutedId) {
        return muteRepository.findByMuterIdAndMutedId(muterId, mutedId).hasElement();
    }

    public Flux<Account> getMutedAccounts(String muterId) {
        return muteRepository.findAllByMuterId(muterId)
                .flatMap(mute -> accountRepository.findById(mute.id.muted_id));
    }

    private Mono<Relationship> buildRelationship(String sourceId, String targetId) {
        var following = followRepository.findIfFollows(sourceId, targetId).hasElement();
        var followedBy = followRepository.findIfFollows(targetId, sourceId).hasElement();
        var blocking = blockRepository.findByBlockerIdAndBlockedId(sourceId, targetId).hasElement();
        var blockedBy = blockRepository.findByBlockerIdAndBlockedId(targetId, sourceId).hasElement();
        var muting = muteRepository.findByMuterIdAndMutedId(sourceId, targetId).hasElement();
        var mutingNotifications = muteRepository.findByMuterIdAndMutedId(sourceId, targetId)
                .map(mute -> mute.mute_notifications)
                .defaultIfEmpty(false);

        return Mono.zip(following, followedBy, blocking, blockedBy, muting, mutingNotifications)
                .map(tuple -> new Relationship(
                        targetId,
                        tuple.getT1(),
                        true,
                        false,
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        tuple.getT5(),
                        tuple.getT6(),
                        false,
                        false,
                        false,
                        false,
                        ""
                ));
    }
}
