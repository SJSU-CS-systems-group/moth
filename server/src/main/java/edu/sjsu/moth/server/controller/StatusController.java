package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.MediaAttachment;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.generated.StatusEdit;
import edu.sjsu.moth.generated.StatusSource;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.MediaService;
import edu.sjsu.moth.server.service.StatusService;
import edu.sjsu.moth.server.util.Util;
import edu.sjsu.moth.util.EmailCodeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RestController
public class StatusController {
    private final StatusService statusService;
    private final AccountService accountService;
    private final MediaService mediaService;
    private final Logger LOG = Logger.getLogger(StatusController.class.getName());

    public StatusController(StatusService statusService, AccountService accountService, MediaService mediaService) {
        this.statusService = statusService;
        this.accountService = accountService;
        this.mediaService = mediaService;
    }

    // Status Editing:
    // Edit a status
    @PutMapping(value = "/api/v1/statuses/{id}")
    Mono<Status> editStatus(@RequestBody V1PostStatus newStatus, @PathVariable String id) {
        return statusService.edit(id, newStatus.status);
    }

    // Get status source (plaintext version of status)
    @GetMapping("api/v1/statuses/{id}/source")
    Mono<StatusSource> getStatusSource(@PathVariable String id) {
        return statusService.findStatusSource(id);
    }

    // Get status edit history
    @GetMapping("api/v1/statuses/{id}/history")
    Mono<ArrayList<StatusEdit>> getStatusHistory(@PathVariable String id) {
        return statusService.findHistory(id);
    }

    // Get a single status by ID
    @GetMapping("/api/v1/statuses/{id}")
    Mono<ResponseEntity<Status>> getApiV1StatusById(Principal user, @PathVariable String id) {
        return statusService.findStatusById(id)
                .flatMap(status -> {
                    if (user != null) {
                        return accountService.getAccount(user.getName())
                                .flatMap(acct -> statusService.hasUserFavourited(acct.id, id)
                                        .flatMap(favourited -> statusService.hasUserReblogged(acct.id, id)
                                                .map(reblogged -> {
                                                    status.favourited = favourited;
                                                    status.reblogged = reblogged;
                                                    return status;
                                                })))
                                .switchIfEmpty(Mono.just(status));
                    }
                    return Mono.just(status);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // Get previous and after statuses in thread
    @GetMapping("/api/v1/statuses/{id}/context")
    Mono<ResponseEntity<Object>> getApiV1StatusContext(@PathVariable String id) {
        return statusService.getStatusContext(id)
                .map(context -> ResponseEntity.ok((Object) Map.of(
                        "ancestors", context.ancestors(),
                        "descendants", context.descendants())))
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // Favourite a status
    @PostMapping("/api/v1/statuses/{id}/favourite")
    Mono<ResponseEntity<Status>> favouriteStatus(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.favourite(acct.id, id))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // Unfavourite a status
    @PostMapping("/api/v1/statuses/{id}/unfavourite")
    Mono<ResponseEntity<Status>> unfavouriteStatus(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.unfavourite(acct.id, id))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // Reblog (boost) a status
    @PostMapping("/api/v1/statuses/{id}/reblog")
    Mono<ResponseEntity<Status>> reblogStatus(Principal user, @PathVariable String id,
                                               @RequestParam(required = false) String visibility) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.reblog(acct.id, id, visibility))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // Unreblog (unboost) a status
    @PostMapping("/api/v1/statuses/{id}/unreblog")
    Mono<ResponseEntity<Status>> unreblogStatus(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.unreblog(acct.id, id))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // Get accounts who favourited a status
    @GetMapping("/api/v1/statuses/{id}/favourited_by")
    Mono<ResponseEntity<List<Account>>> getFavouritedBy(Principal user, @PathVariable String id,
                                                         @RequestParam(required = false, defaultValue = "40") int limit) {
        return statusService.getAccountsWhoFavourited(id)
                .flatMap(accountId -> accountService.getAccountById(accountId))
                .take(limit)
                .collectList()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(List.of()));
    }

    // Get accounts who reblogged a status
    @GetMapping("/api/v1/statuses/{id}/reblogged_by")
    Mono<ResponseEntity<List<Account>>> getRebloggedBy(Principal user, @PathVariable String id,
                                                        @RequestParam(required = false, defaultValue = "40") int limit) {
        return statusService.getAccountsWhoReblogged(id)
                .flatMap(accountId -> accountService.getAccountById(accountId))
                .take(limit)
                .collectList()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(List.of()));
    }

    // Bookmark a status
    @PostMapping("/api/v1/statuses/{id}/bookmark")
    Mono<ResponseEntity<Status>> bookmarkStatus(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.bookmark(acct.id, id))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // Unbookmark a status
    @PostMapping("/api/v1/statuses/{id}/unbookmark")
    Mono<ResponseEntity<Status>> unbookmarkStatus(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.unbookmark(acct.id, id))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // Pin a status
    @PostMapping("/api/v1/statuses/{id}/pin")
    Mono<ResponseEntity<Status>> pinStatus(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.pin(acct.id, id))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.unprocessableEntity().build()));
    }

    // Unpin a status
    @PostMapping("/api/v1/statuses/{id}/unpin")
    Mono<ResponseEntity<Status>> unpinStatus(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.unpin(acct.id, id))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // Mute a conversation
    @PostMapping("/api/v1/statuses/{id}/mute")
    Mono<ResponseEntity<Status>> muteConversation(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.muteConversation(acct.id, id))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // Unmute a conversation
    @PostMapping("/api/v1/statuses/{id}/unmute")
    Mono<ResponseEntity<Status>> unmuteConversation(Principal user, @PathVariable String id) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName())))
                .flatMap(acct -> statusService.unmuteConversation(acct.id, id))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

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
    @PostMapping(value = "/api/v1/statuses", consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<Object>> postApiV1Statuses(Principal user, @RequestBody V1PostStatus body) { //
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
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName()))).flatMap(acct -> {
                    var mediaAttachments = new ArrayList<MediaAttachment>();
                    if (body.media_ids != null) for (var id : body.media_ids) {
                        var attachment = mediaService.lookupCachedAttachment(id);
                        if (attachment != null) mediaAttachments.add(attachment);
                    }
                    // generate a numeric ID for the status
                    var status = new Status(Long.toString(Util.generateUniqueId()), EmailCodeUtils.now(), body.in_reply_to_id, null, body.sensitive,
                                            body.spoiler_text == null ? "" : body.spoiler_text, body.visibility,
                                            body.language, null, null, 0, 0, 0, false, false, false, false, body.status,
                                            null, null, acct, mediaAttachments, new ArrayList<>(), List.of(), List.of(),
                                            null, null, body.status, EmailCodeUtils.now());
                    return statusService.save(status).map(ResponseEntity::ok);
                });
    }

    @PostMapping(value = "/api/v1/statuses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<ResponseEntity<Object>> postApiV1Statuses(Principal user,
                                                   @RequestPart(name = "status") String status,
                                                   @RequestPart(name = "media_ids", required = false)
                                                   String[] media_ids, @RequestPart(name = "poll", required = false)
                                                   V1PostStatus.V1PostPoll poll,
                                                   @RequestPart(name = "in_reply_to_id", required = false)
                                                   String in_reply_to_id,
                                                   @RequestPart(name = "sensitive", required = false) String sensitive,
                                                   @RequestPart(name = "spoiler_text", required = false)
                                                   String spoiler_text,
                                                   @RequestPart(name = "visibility") String visibility,
                                                   @RequestPart(name = "language") String language,
                                                   @RequestPart(name = "scheduled_at", required = false)
                                                   String scheduled_at) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                     .body(new AppController.ErrorResponse("The access token is invalid")));
        }

        if (status == null || status.length() == 0) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                     .body(new AppController.ErrorResponse("Validation failed: Text can't be blank")));
        }
        if (scheduled_at != null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                     .body(new AppController.ErrorResponse("scheduled posts are not supported")));
        }

        return accountService.getAccount(user.getName())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName()))).flatMap(acct -> {
                    var mediaAttachments = new ArrayList<MediaAttachment>();
                    if (media_ids != null) for (var id : media_ids) {
                        var attachment = mediaService.lookupCachedAttachment(id);
                        if (attachment != null) mediaAttachments.add(attachment);
                    }
                    // generate a numeric ID for the status
                    var s = new Status(Long.toString(Util.generateUniqueId()), EmailCodeUtils.now(), in_reply_to_id, null,
                                       sensitive != null && sensitive.equals("true"),
                                       spoiler_text == null ? "" : spoiler_text, visibility, language, null, null, 0, 0,
                                       0, false, false, false, false, status, null, null, acct, mediaAttachments,
                                       new ArrayList<>(), List.of(), List.of(), null, null, status,
                                       EmailCodeUtils.now());
                    return statusService.save(s).map(ResponseEntity::ok);
                });
    }

    @PostMapping(value = "/api/v1/statuses", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Mono<ResponseEntity<Object>> postApiV1StatusesFormUrlEncoded(Principal user, ServerWebExchange exchange) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AppController.ErrorResponse("The access token is invalid")));
        }

        return exchange.getFormData().flatMap(form -> {
            String status = form.getFirst("status");
            String in_reply_to_id = form.getFirst("in_reply_to_id");
            String sensitive = form.getFirst("sensitive");
            String spoiler_text = form.getFirst("spoiler_text");
            String visibility = form.getFirst("visibility");
            String language = form.getFirst("language");
            String scheduled_at = form.getFirst("scheduled_at");
            List<String> media_ids = form.get("media_ids[]");
            if (media_ids == null) {
                media_ids = form.get("media_ids");
            }

            if (status == null || status.isEmpty()) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(new AppController.ErrorResponse("Validation failed: Text can't be blank")));
            }
            if (scheduled_at != null) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(new AppController.ErrorResponse("scheduled posts are not supported")));
            }

            List<String> finalMediaIds = media_ids;
            return accountService.getAccount(user.getName())
                    .switchIfEmpty(Mono.error(new UsernameNotFoundException(user.getName()))).flatMap(acct -> {
                        var mediaAttachments = new ArrayList<MediaAttachment>();
                        if (finalMediaIds != null) for (var id : finalMediaIds) {
                            var attachment = mediaService.lookupCachedAttachment(id);
                            if (attachment != null) mediaAttachments.add(attachment);
                        }
                        // generate a numeric ID for the status
                        var s = new Status(Long.toString(Util.generateUniqueId()), EmailCodeUtils.now(), in_reply_to_id, null,
                                sensitive != null && sensitive.equals("true"),
                                spoiler_text == null ? "" : spoiler_text,
                                visibility != null ? visibility : "public",
                                language, null, null, 0, 0, 0, false, false, false, false, status, null, null, acct,
                                mediaAttachments, new ArrayList<>(), List.of(), List.of(), null, null, status,
                                EmailCodeUtils.now());
                        return statusService.save(s).map(s2 -> ResponseEntity.ok((Object) s2));
                    });
        });
    }

    @DeleteMapping("/api/v1/statuses/{id}")
    Mono<ResponseEntity<Status>> postApiV1Statuses(Principal user, @PathVariable String id) {
        return statusService.findStatusById(id).flatMap(s -> statusService.delete(s).thenReturn(ResponseEntity.ok(s)));
    }

    // spec: https://docs.joinmastodon.org/methods/timelines/#home
    // notes: spec don't indicate that min/max/since_id are optional, but clients don't always pass them
    @GetMapping("/api/v1/timelines/home")
    Mono<ResponseEntity<List<Status>>> getApiV1TimelinesHome(Principal user,
                                                             @RequestParam(required = false) String max_id,
                                                             @RequestParam(required = false, defaultValue = "0")
                                                             String since_id,
                                                             @RequestParam(required = false) String min_id,
                                                             @RequestParam(required = false, defaultValue = "20")
                                                             int limit) {
        return statusService.getHomeTimeline(user, max_id, since_id, min_id, limit, true).map(ResponseEntity::ok);
    }

    // spec: https://docs.joinmastodon.org/methods/accounts/#statuses
    @GetMapping("/api/v1/accounts/{id}/statuses")
    Mono<ResponseEntity<List<Status>>> getApiV1AccountsStatuses(Principal user, @PathVariable String id,
            @RequestParam(required = false) String max_id,
            @RequestParam(required = false) String since_id,
            @RequestParam(required = false) String min_id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean only_media,
            @RequestParam(required = false) Boolean exclude_replies,
            @RequestParam(required = false) Boolean exclude_reblogs,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) String tagged) {
        // If the id already looks like a remote handle, go through the remote path
        if (id.contains("@")) {
            return statusService.getStatusesForId(user, id, max_id, since_id, min_id, only_media, exclude_replies,
                                                  exclude_reblogs, pinned, tagged, limit).map(ResponseEntity::ok);
        }

        return accountService.getAccountByIdOrAcct(id)
                .flatMap(acct -> statusService.getStatusesForId(user, acct.username, max_id, since_id, min_id,
                                                                only_media, exclude_replies, exclude_reblogs, pinned,
                                                                tagged, limit))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/trends/statuses")
    Mono<ResponseEntity<List<Status>>> getApiV1TrendingStatuses(
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        return statusService.getAllStatuses(offset, limit).collectList().map(ResponseEntity::ok);
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
