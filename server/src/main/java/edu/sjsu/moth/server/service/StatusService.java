package edu.sjsu.moth.server.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import edu.sjsu.moth.generated.Application;
import edu.sjsu.moth.generated.Card;
import edu.sjsu.moth.generated.CustomEmoji;
import edu.sjsu.moth.generated.MediaAttachment;
import edu.sjsu.moth.generated.Poll;
import edu.sjsu.moth.generated.QStatus;
import edu.sjsu.moth.generated.SearchResult;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.ExternalStatus;
import edu.sjsu.moth.server.db.ExternalStatusRepository;
import edu.sjsu.moth.server.db.Following;
import edu.sjsu.moth.server.db.FollowingRepository;
import edu.sjsu.moth.server.db.Followers;
import edu.sjsu.moth.server.db.FollowersRepository;
import edu.sjsu.moth.server.db.GroupRepository;
import edu.sjsu.moth.server.db.StatusMention;
import edu.sjsu.moth.server.db.StatusRepository;
import edu.sjsu.moth.server.db.StatusTag;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class StatusService {

    @Autowired
    StatusRepository statusRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    ExternalStatusRepository externalStatusRepository;

    @Autowired
    FollowingRepository followingRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    FollowersRepository followersRepository;

    @Autowired
    GroupRepository groupRepository;


//    public Mono<Status> save(Status status) {
//        return statusRepository.save(status);
//    }

    public Mono<Status> save(Status status) {

        var mono = Mono.empty();

        //mono = followersRepository.save(new Followers("umesh", new ArrayList<>())).then(Mono.empty());
        ArrayList<String> accountsmentioned = new ArrayList<>();
        String[] words = status.content.split(" ");
        for(String s: words){
            if(s.charAt(0) == '@')
                accountsmentioned.add(s);
        }

        for(String s : accountsmentioned){
            String groupName = s.substring(1);
            Mono<Object> finalMono = mono;
            mono = mono.then(accountRepository.findItemByAcct(groupName).
                    flatMap(a ->{
                        for(AccountField af : a.fields){
                            if(af.name.equalsIgnoreCase("Group") && af.value.equalsIgnoreCase("True")){

                                Status groupStatus = new Status(null, status.createdAt, status.inReplyToId, status.inReplyToAccountId, status.sensitive,
                                                                status.spoilerText, status.visibility, status.language, status.repliesCount, status.reblogsCount,
                                                                status.favouritesCount, status.favourited, status.reblogged, status.muted, status.bookmarked,
                                                                status.content, status.reblog,  status.application, a,
                                                                status.mediaAttachments,  status.mentions, status.tags,
                                                                status.emojis, status.card, status.poll, status.text, status.edited_at);

                                return finalMono.then(followersRepository.findItemById(groupName)
                                                         .flatMap(f->{
                                                             if(f.followers.contains(status.account.id)){
                                                                 return statusRepository.save(groupStatus);
                                                             }
                                                             return Mono.empty();
                                                         })
                                );

                            }
                        }
                        return Mono.empty();
                    }


            ));
        }

        return mono.then(statusRepository.save(status));

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

    public Mono<List<Status>> getTimeline(Principal user, String max_id, String since_id, String min_id, int limit) {
        var qStatus = new QStatus("start");
        var predicate = qStatus.content.isNotNull();
        predicate = addRangeQueries(predicate, max_id, since_id, max_id);
        var external = externalStatusRepository.findAll(predicate, Sort.by(Sort.Direction.DESC, "id"))
                .flatMap(statuses -> filterStatusByViewable(user, statuses))
                .take(limit);
        var internal = statusRepository.findAll(predicate, Sort.by(Sort.Direction.DESC, "id"))
                .flatMap(statuses -> filterStatusByViewable(user, statuses))
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
                .flatMap(statuses -> filterStatusByViewable(user, statuses))
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

    private Flux<Status> filterStatusByViewable(Principal user, Status status) {
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMapMany(acct -> followingRepository.findItemById(acct.id)
                        .switchIfEmpty(Mono.just(new Following(acct.id, new ArrayList<>())))
                        .map(Following::getFollowing)
                        .flatMapMany(followings -> (status.visibility.equals("public") || followings.contains(
                                status.id)) ? Flux.just(status) : Flux.empty()));
    }

}