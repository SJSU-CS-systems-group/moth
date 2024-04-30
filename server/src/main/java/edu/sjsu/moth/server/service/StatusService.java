package edu.sjsu.moth.server.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import edu.sjsu.moth.generated.QStatus;
import edu.sjsu.moth.generated.SearchResult;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.ExternalStatus;
import edu.sjsu.moth.server.db.ExternalStatusRepository;
import edu.sjsu.moth.server.db.StatusRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class StatusService {

    @Autowired
    StatusRepository statusRepository;

    @Autowired
    ExternalStatusRepository externalStatusRepository;

    @Autowired
    AccountService accountService;

    public Mono<Status> save(Status status) {
        return statusRepository.save(status);
    }

    public Mono<ExternalStatus> saveExternal(ExternalStatus status) {
        return externalStatusRepository.save(status);
    }

    public Mono<Void> delete(Status status) {
        return statusRepository.delete(status);
    }

    public Mono<Status> findStatusById(String id) {
        System.out.println(statusRepository.findById(id));
        return statusRepository.findById(id);
    }

    public Mono<List<Status>> getTimeline(Principal user, String max_id, String since_id, String min_id, int limit, boolean isFollowingTimeline) {
        var qStatus = new QStatus("start");
        var predicate = qStatus.content.isNotNull();
        predicate = addRangeQueries(predicate, max_id, since_id, max_id);
        var external = externalStatusRepository.findAll(predicate, Sort.by(Sort.Direction.DESC, "id"))
                .flatMap(statuses -> filterStatusByViewable(user, statuses, isFollowingTimeline))
                .take(limit);
        var internal = statusRepository.findAll(predicate, Sort.by(Sort.Direction.DESC, "id"))
                .flatMap(statuses -> filterStatusByViewable(user, statuses, isFollowingTimeline))
                .take(limit);

        //TODO: we may want to merge sort them, unsure if merge does that
        return Flux.merge(external, internal).collectList();
    }

    private BooleanExpression addRangeQueries(BooleanExpression predicate, String max_id, String since_id,
                                              String min_id) {
        if (max_id != null) predicate = predicate.and(new QStatus("max").id.lt(max_id));
        if (since_id != null) predicate = predicate.and(new QStatus("since").id.gt(since_id));
        // this isn't right. i'm not sure how to express close
        if (min_id != null) predicate = predicate.and(new QStatus("min").id.gt(min_id));
        return predicate;
    }

    public Mono<List<Status>> getStatusesForId(String username, String max_id, String since_id, String min_id,
                                               Boolean only_media, Boolean exclude_replies, Boolean exclude_reblogs,
                                               Boolean pinned, String tagged, Integer limit) {
        var acct = new QStatus("account.acct");
        var predicate = acct.account.acct.eq(username);
        predicate = addRangeQueries(predicate, max_id, since_id, min_id);
        if (only_media != null && only_media)
            predicate = predicate.and(new QStatus("media").mediaAttachments.isNotEmpty());
        if (exclude_replies != null && exclude_replies)
            predicate = predicate.and(new QStatus("reply").inReplyToId.isNull());
        if (exclude_reblogs != null && exclude_reblogs)
            predicate = predicate.and(new QStatus("reblog").reblog.isNull());
        // TODO: how to tell if it is pinned?
        // if (pinned) predicate = predicate.and(new QStatus("pinned")..isNull());
        if (tagged != null) predicate = predicate.and(new QStatus("tagged").tags.any().name.eq(tagged));

        // now apply the limit
        int count = limit == null || limit > 40 || limit < 1 ? 40 : limit;
        return statusRepository.findAll(predicate, Sort.by(Sort.Direction.DESC, "id")).take(count).collectList();
    }

    public Flux<Status> getAllStatuses(int offset, int limit) {
        return statusRepository.findAll().skip(offset).take(limit);
    }

    @NotNull
    public Mono<SearchResult> filterStatusSearch(String query, Principal user, String account_id, String max_id,
                                                 String min_id, Integer limit, Integer offset, SearchResult result) {
        return statusRepository.findByStatusLike(query)
                .flatMap(statuses -> filterStatusByViewable(user, statuses, false))
                .take(limit)
                .collectList()
                .map(statuses -> {
                    // check RequestParams: account_id, max_id, min_id, offset
                    result.statuses.addAll(statuses);
                    if (account_id != null)
                        result.statuses.stream().filter(c -> Integer.parseInt(c.id) == Integer.parseInt(account_id));
                    if (max_id != null)
                        result.statuses.stream().filter(c -> Integer.parseInt(c.id) < Integer.parseInt(max_id));
                    if (min_id != null)
                        result.statuses.stream().filter(c -> Integer.parseInt(c.id) > Integer.parseInt(min_id));
                    if (offset != null) result.statuses.subList(offset, result.statuses.size());
                    return result;
                });
    }

    private Flux<Status> filterStatusByViewable(Principal user, Status status, boolean isFollowingTimeline) {
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMapMany(acct -> followingRepository.findItemById(acct.id)
                        .switchIfEmpty(Mono.just(new Following(acct.id, new ArrayList<>())))
                        .map(Following::getFollowing)
                        .flatMapMany(followings -> ((!isFollowingTimeline && status.visibility.equals("public")) || followings.contains(
                                status.id)) ? Flux.just(status) : Flux.empty()));
    }

}