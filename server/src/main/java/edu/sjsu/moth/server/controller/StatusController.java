package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.QStatus;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.StatusRepository;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;

@RestController
public class StatusController {
    @Autowired
    StatusRepository statusRepository;

    @Autowired
    AccountService accountService;

    // defined in https://docs.joinmastodon.org/methods/statuses/
    // in that reference it looks like parameters come as form parameters, but in practice they
    // come in as JSON
    //
    // TODO:
    //   support schedule at
    //   add support for reblog
    //   text vs content processing
    //   media attachment processing
    //   poll processing
    @PostMapping("/api/v1/statuses")
    Mono<ResponseEntity<Object>> postApiV1Statuses(Principal user, @RequestBody V1PostStatus body) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                     .body(new AppController.ErrorResponse("The access token is invalid")));
        }
        if (body.status == null || body.status.length() == 0) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                     .body(new AppController.ErrorResponse("Validation failed: Text can't be blank")));
        }
        if (body.scheduled_at != null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                     .body(new AppController.ErrorResponse("scheduled posts are not supported")));
        }

        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> {
                    // if i pass a null id to status it gets filled in by the repo with the objectid
                    var status = new Status(null, Util.now(), body.in_reply_to_id, null, body.sensitive,
                                            body.spoiler_text == null ? "" : body.spoiler_text, body.visibility,
                                            body.language, null, null, 0, 0, 0, false, false, false, false, body.status,
                                            null, null, acct, List.of(), List.of(), List.of(), List.of(), null, null,
                                            body.status, Util.now());
                    return statusRepository.save(status).map(ResponseEntity::ok);
                });
    }

    // spec: https://docs.joinmastodon.org/methods/accounts/#statuses
    @GetMapping("/api/v1/accounts/{id}/statuses")
    Mono<ResponseEntity<List<Status>>> getApiV1AccountsStatuses(@PathVariable String id,
            /* String. Return results older than this ID */ String max_id,
            /* String. Return results newer than this ID */ String since_id,
            /* String. Return results immediately newer than this ID */ String min_id,
            /* Integer. Maximum number of results to return. Defaults to 20 statuses. Max 40 statuses. */ Integer limit,
            /* Boolean. Filter out statuses without attachments. */ Boolean only_media,
            /* Boolean. Filter out statuses in reply to a different account. */ Boolean exclude_replies,
            /* Boolean. Filter out boosts from the response. */ Boolean exclude_reblogs,
            /* Boolean. Filter for pinned statuses only. Defaults to false, which includes all statuses. Pinned
            statuses do not receive special priority in the order of the returned results. */ Boolean pinned,
            /* String. Filter for statuses using a specific hashtag */ String tagged) {
        return accountService.getAccountById(id).map(acct -> acct.username).flatMapMany(u -> {
            var acct = new QStatus("account.acct");
            var predicate = acct.account.acct.eq(u);
            if (max_id != null) predicate = predicate.and(new QStatus("max").id.lt(max_id));
            if (since_id != null) predicate = predicate.and(new QStatus("since").id.gt(since_id));
            // this isn't right. i'm not sure how to express close
            if (min_id != null) predicate = predicate.and(new QStatus("min").id.gt(min_id));
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
            return statusRepository.findAll(predicate, Sort.by(Sort.Direction.DESC, "id")).take(count);
        }).collectList().map(ResponseEntity::ok);
    }

    public static class V1PostStatus {
        /**
         * REQUIRED String. The text content of the status. If media_ids is provided, this becomes optional.
         * Attaching a poll is optional while status is provided media_ids[]
         */
        public String status;
        /**
         * REQUIRED Array of String. Include Attachment IDs to be attached as media. If provided, status becomes
         * optional, and poll cannot be used.
         */
        public String[] media_ids;
        public V1PostPoll poll;
        /**
         * String. ID of the status being replied to, if status is a reply.
         */
        public String in_reply_to_id;
        /**
         * Boolean. Mark status and attached media as sensitive? Defaults to false.
         */
        public boolean sensitive;
        /**
         * String. Text to be shown as a warning or subject before the actual content. Statuses are generally
         * collapsed behind this field.
         */
        public String spoiler_text;
        /**
         * String. Sets the visibility of the posted status to public, unlisted, private, direct.
         */
        public String visibility;
        /**
         * String. ISO 639 language code for this status.
         */
        public String language;
        /**
         * String. ISO 8601 Datetime at which to schedule a status. Providing this parameter will cause
         * ScheduledStatus to be returned instead of Status. Must be at least 5 minutes in the future.
         */
        public String scheduled_at;

        public static class V1PostPoll {
            /**
             * REQUIRED Array of String. Possible answers to the poll. If provided, media_ids cannot be used, and
             * poll[expires_in] must be provided.
             */
            public String[] options;
            /**
             * REQUIRED Integer. Duration that the poll should be open, in seconds. If provided, media_ids cannot be
             * used, and poll[options] must be provided.
             */
            public int expires_in;
            /**
             * Boolean. Allow multiple choices? Defaults to false.
             */
            public boolean multiple;
            /**
             * Boolean. Hide vote counts until the poll ends? Defaults to false.
             */
            public boolean hide_totals;
        }
    }
}
