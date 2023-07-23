package edu.sjsu.moth.server.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import edu.sjsu.moth.generated.QStatus;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.StatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;

@Configuration
public class StatusService {
    @Autowired
    StatusRepository statusRepository;

    public Mono<Status> save(Status status) {
        return statusRepository.save(status);
    }

    public Mono<List<Status>> getTimeline(Principal user, String max_id, String since_id, String min_id, int limit) {
        // TODO: this is an intial hacked implementation. dumps all the statuses
        var qStatus = new QStatus("start");
        var predicate = qStatus.content.isNotNull();
        predicate = addRangeQueries(predicate, max_id, since_id, max_id);
        return statusRepository.findAll(predicate, Sort.by(Sort.Direction.DESC, "id")).take(limit).collectList();
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
}
