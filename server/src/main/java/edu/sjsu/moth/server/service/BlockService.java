package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Block;
import edu.sjsu.moth.server.db.BlockRepository;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.MuteRepository;
import edu.sjsu.moth.util.EmailCodeUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class BlockService {

    private final BlockRepository blockRepository;
    private final AccountRepository accountRepository;
    private final FollowRepository followRepository;
    private final MuteRepository muteRepository;

    public BlockService(BlockRepository blockRepository, AccountRepository accountRepository,
                        FollowRepository followRepository, MuteRepository muteRepository) {
        this.blockRepository = blockRepository;
        this.accountRepository = accountRepository;
        this.followRepository = followRepository;
        this.muteRepository = muteRepository;
    }

    public Mono<Relationship> block(String blockerId, String blockedId) {
        return blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .flatMap(existing -> buildRelationship(blockerId, blockedId))
                .switchIfEmpty(Mono.defer(() -> {
                    var block = new Block(blockerId, blockedId, EmailCodeUtils.now());
                    return blockRepository.save(block)
                            .then(followRepository.findIfFollows(blockerId, blockedId)
                                    .flatMap(follow -> followRepository.delete(follow))
                                    .then())
                            .then(followRepository.findIfFollows(blockedId, blockerId)
                                    .flatMap(follow -> followRepository.delete(follow))
                                    .then())
                            .then(buildRelationship(blockerId, blockedId));
                }));
    }

    public Mono<Relationship> unblock(String blockerId, String blockedId) {
        return blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .flatMap(block -> blockRepository.delete(block)
                        .then(buildRelationship(blockerId, blockedId)))
                .switchIfEmpty(buildRelationship(blockerId, blockedId));
    }

    public Mono<Boolean> isBlocking(String blockerId, String blockedId) {
        return blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId).hasElement();
    }

    public Mono<Boolean> isBlockedBy(String userId, String potentialBlockerId) {
        return blockRepository.findByBlockerIdAndBlockedId(potentialBlockerId, userId).hasElement();
    }

    public Flux<Account> getBlockedAccounts(String blockerId) {
        return blockRepository.findAllByBlockerId(blockerId)
                .flatMap(block -> accountRepository.findById(block.id.blocked_id));
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
