package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.StatusMention;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Optional;

@Service
@CommonsLog
public class VisibilityService {
    @Autowired
    AccountService accountService;

    @Autowired
    FollowRepository followRepository;

    final String PUBLIC_VISIBILITY = "public";
    final String QUITE_PUBLIC = "unlisted";
    final String DIRECT_VISIBILITY = "direct";
    final String PRIVATE_VISIBILITY = "private";

    public enum VISIBILITY {
        PUBLIC, UNLISTED, PRIVATE, DIRECT, UNDEFINED
    }

    public static VISIBILITY visibilityFromString(Optional<String> visibility) {
        return visibility.map(s -> switch (s) {
            case "public" -> VISIBILITY.PUBLIC;
            case "unlisted" -> VISIBILITY.UNLISTED;
            case "private" -> VISIBILITY.PRIVATE;
            case "direct" -> VISIBILITY.DIRECT;
            default -> VISIBILITY.UNDEFINED;
        }).orElse(VISIBILITY.UNDEFINED);
    }

    public Flux<Status> publicTimelinesViewable(Status status) {
        if (PUBLIC_VISIBILITY.equals(status.visibility)) return Flux.just(status);
        return Flux.empty();
    }

    public Flux<Status> homefeedViewable(Principal user, Status status) {
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("User not found")))
                .flatMapMany(userAccount -> followRepository.findAllByFollowerId(userAccount.id).flatMap(follow -> {
                    if ((follow.id.followed_id.equals(status.account.id) &&
                            validHomeFeedVisibility(status.visibility)) ||
                            (user.getName().equals(status.account.username))) {
                        return Flux.just(status);
                    }
                    return Flux.empty();
                }));
    }

    private boolean validHomeFeedVisibility(String visibility) {
        if (visibility == null) return true; // treat null as public
        return switch (visibility) {
            case PUBLIC_VISIBILITY, PRIVATE_VISIBILITY, QUITE_PUBLIC -> true;
            default -> false;
        };
    }

    public Flux<Status> profileViewable(Principal user, Status status) {
        // Handle null visibility - treat as public for backwards compatibility
        if (status.visibility == null || PUBLIC_VISIBILITY.equals(status.visibility) || QUITE_PUBLIC.equals(status.visibility)) {
            return Flux.just(status);
        }

        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("User not found"))).flatMapMany(account -> {
                    if (status.account.id.equals(account.id)) {
                        return Flux.just(status);
                    }

                    if (DIRECT_VISIBILITY.equals(status.visibility)) {
                        for (StatusMention mention : status.mentions) {
                            if (mention.id.equals(account.id)) {
                                return Flux.just(status);
                            }
                        }
                        return Flux.empty();
                    }

                    if (PRIVATE_VISIBILITY.equals(status.visibility)) {
                        return followRepository.findAllByFollowerId(account.id).flatMap(follow -> {
                            if (follow.id.followed_id.equals(status.account.id)) {
                                return Flux.just(status);
                            }
                            return Flux.empty();
                        });
                    }
                    return Flux.empty();
                });
    }
}
