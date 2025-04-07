package edu.sjsu.moth.server.service;

import com.querydsl.core.types.Ops;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import edu.sjsu.moth.generated.QStatus;
import edu.sjsu.moth.generated.SearchResult;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.generated.StatusEdit;
import edu.sjsu.moth.generated.StatusSource;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.ExternalStatus;
import edu.sjsu.moth.server.db.ExternalStatusRepository;
import edu.sjsu.moth.server.db.Follow;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.StatusEditCollection;
import edu.sjsu.moth.server.db.StatusHistoryRepository;
import edu.sjsu.moth.server.db.StatusMention;
import edu.sjsu.moth.server.db.StatusRepository;
import org.bson.types.ObjectId;
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
import java.util.logging.Logger;

@Configuration
public class StatusService {

    @Autowired
    StatusRepository statusRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    ExternalStatusRepository externalStatusRepository;

    @Autowired
    FollowRepository followRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    StatusHistoryRepository statusHistoryRepository;

    Logger LOG = Logger.getLogger(StatusService.class.getName());

    public Mono<ArrayList<StatusEdit>> findHistory(String id) {
        return statusHistoryRepository.findById(id).map(edits -> edits.collection);
    }

    public Mono<StatusSource> findStatusSource(String id) {
        return statusRepository.findById(id).map(status -> {
            StatusSource statusSource = new StatusSource();
            statusSource.setId(status.id);
            statusSource.setText(status.content);
            statusSource.setSpoilerText(status.spoilerText);
            return statusSource;
        });
    }

    public Mono<Status> edit(String id, String newStatus) {
        return statusRepository.findById(id).flatMap(status -> {
            status.content = newStatus;
            return save(status);
        });
    }

    public Mono<Status> save(Status status) {
        // create a Mono that we can tack onto
        var mono = Mono.empty();
        ArrayList<String> accountsmentioned = new ArrayList<>();
        String[] words = status.content.split(" ");
        for (String s : words) {
            if (s.charAt(0) == '@') accountsmentioned.add(s);
        }

        /*
            Collect local mentions. This is not an efficient regex.
            A more efficient regex can be found here
            https://github.com/mastodon/mastodon/blob/0479efdbb65a87ea80f0409d0131b1dbf20b1d32/app/models/account.rb#L74
         */
        for (String s : accountsmentioned) {
            String username = s.split("@")[1];
            mono = mono.then(
                    accountService
                            .getAccountById(username)
                            .switchIfEmpty(
                                    Mono.error(new UsernameNotFoundException("Mentioned account not found: " + username))
                            )
                            .map(acc -> {
                                    LOG.finest("Adding mention: " + acc.username);
                                    status.mentions.add(new StatusMention(acc.id, acc.username, acc.url, acc.acct));
                                    return Mono.empty();
                            })
            );
        }

        // check to see if the post mentions a group account. if it does create a mono for a status post by that group
        for (String s : accountsmentioned) {
            String groupName = s.substring(1);
            Mono<Object> finalMono = mono;
            // tack the new group post Mono onto mono
            mono = mono.then(accountRepository.findItemByAcct(groupName).flatMap(a -> {
                for (AccountField af : a.fields) {
                    if (af.name.equalsIgnoreCase("Group") && af.value.equalsIgnoreCase("True")) {
                        Status groupStatus =
                                new Status(null, status.createdAt, status.inReplyToId, status.inReplyToAccountId,
                                           status.sensitive, status.spoilerText, status.visibility, status.language,
                                           status.getUri(), status.getUrl(), status.repliesCount, status.reblogsCount,
                                           status.favouritesCount, status.favourited, status.reblogged, status.muted,
                                           status.bookmarked, status.content, status.reblog, status.application, a,
                                           status.mediaAttachments, status.mentions, status.tags, status.emojis,
                                           status.card, status.poll, status.text, status.edited_at);

                        return finalMono.then(
                                followRepository.findAllByFollowedId(groupName).collectList().flatMap(list -> {

                                    for (Follow f : list) {
                                        if (f.id.follower_id.equals(status.account.id)) {
                                            return statusRepository.save(groupStatus).then(Mono.fromRunnable(
                                                    () -> System.out.println("Gets Executed.....1")));
                                        }
                                    }
                                    return Mono.empty();
                                }).switchIfEmpty(Mono.fromRunnable(() -> System.out.println("Gets Executed.....2"))));
                    }
                }
                return Mono.empty();
            }));
        }

        return mono.then(statusRepository.save(status)).flatMap(
                s -> statusHistoryRepository.findById(s.id).defaultIfEmpty(new StatusEditCollection(s.id))
                        .flatMap(sh -> statusHistoryRepository.save(sh.addEdit(s))).thenReturn(s));
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

    public Mono<List<Status>> getTimeline(Principal user, String max_id, String since_id, String min_id, int limit,
                                          boolean isFollowingTimeline) {
        var qStatus = QStatus.status;
        var predicate = qStatus.content.isNotNull();
        predicate = addRangeQueries(predicate, max_id, since_id, max_id);
        var external = externalStatusRepository.findAll(predicate, Sort.by(Sort.Direction.DESC, "id"))
                .flatMap(statuses -> filterStatusByViewable(user, statuses, isFollowingTimeline)).take(limit);
        var internal = statusRepository.findAll(predicate, Sort.by(Sort.Direction.DESC, "id"))
                //.switchIfEmpty(Mono.fromRunnable(() -> System.out.println("No status found")))
                //.doOnNext(x -> System.out.println("before filter: " + x))
                .flatMap(statuses -> filterStatusByViewable(user, statuses, isFollowingTimeline));
        //.doOnNext(x -> System.out.println("after filter: " + x)).take(limit);

        //TODO: we may want to merge sort them, unsure if merge does that
        return Flux.merge(external, internal).collectList();
    }

    private BooleanExpression addRangeQueries(BooleanExpression predicate, String max_id, String since_id,
                                              String min_id) {
        Path<ObjectId> statusIdPath = Expressions.path(ObjectId.class, QStatus.status.id.getMetadata());
        if (max_id != null) predicate = predicate.and(
                Expressions.predicate(Ops.LT, statusIdPath, Expressions.constant(new ObjectId(convertToHex(max_id)))));
        if (since_id != null) predicate = predicate.and(Expressions.predicate(Ops.GT, statusIdPath,
                                                                              Expressions.constant(new ObjectId(
                                                                                      convertToHex(since_id)))));
        // this isn't right. i'm not sure how to express close
        if (min_id != null) predicate = predicate.and(
                Expressions.predicate(Ops.GT, statusIdPath, Expressions.constant(new ObjectId(convertToHex(min_id)))));
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
                .flatMap(statuses -> filterStatusByViewable(user, statuses, false)).take(limit).collectList()
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
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName()))).flatMapMany(acct -> {
                    if (status.account.id.equals(acct.id)) {
                        return Flux.just(status);
                    }
                    if (!isFollowingTimeline && status.visibility.equals("public")) return Flux.just(status);
                    return followRepository.findAllByFollowerId(acct.id).flatMap(
                            following -> following.id.followed_id.equals(status.account.id) ? Flux.just(status) :
                                    Flux.empty());
                });
    }

    private String convertToHex(String payload) {
        return String.format("%1$24s", payload).replace(' ', '0');
    }

}
