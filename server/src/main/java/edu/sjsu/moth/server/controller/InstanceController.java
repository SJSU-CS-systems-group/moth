package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.CustomEmoji;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class InstanceController {
    private static final String CONTACT_ACCOUNT = MothConfiguration.mothConfiguration.getAccountName();
    private static final URLs URLS = new URLs("wss://" + MothConfiguration.mothConfiguration.getServerName());
    private static final StatsV1 STATSV1 = new StatsV1(0, 0, 0);
    private static final Statuses STATUSES = new Statuses(500, 4, 23);
    private static final MediaAttachments MEDIA_ATTACHMENTSV1 = new MediaAttachments(
            new String[] { "image/jpeg", "image/png", "image/gif", "image/webp", "video/webm", "video/mp4",
                    "video" + "/quicktime", "video/ogg", "audio/wave", "audio/wav", "audio/x-wav", "audio/x-pn-wave",
                    "audio" + "/vnd.wave", "audio/ogg", "audio/vorbis", "audio/mpeg", "audio/mp3", "audio/webm",
                    "audio/flac", "audio/aac", "audio/m4a", "audio/x-m4a", "audio/mp4", "audio/3gpp", "video/x-ms-asf"
            },
            10485760, 16777216, 41943040, 60, 2304000);
    private static final Polls POLLS = new Polls(4, 50, 5 * 60, 7 * 24 * 60 * 60);
    private static final ConfigurationV1 CONFIGURATIONV1 = new ConfigurationV1(STATUSES, MEDIA_ATTACHMENTSV1, POLLS);
    private static final Accounts ACCOUNTS = new Accounts(10);
    private static final Translation TRANSLATION = new Translation(true);
    private static final ConfigurationV2 CONFIGURATIONV2 = new ConfigurationV2(URLS, ACCOUNTS, STATUSES,
                                                                               MEDIA_ATTACHMENTSV1, POLLS, TRANSLATION);
    private static final Rule[] RULES = MothConfiguration.mothConfiguration.getRules();

    @Autowired
    private AccountRepository accountRepo;

    @GetMapping("/rules")
    public Rule[] getRules() {
        return RULES;
    }

    @GetMapping("/api/v1/instance")
    public Mono<ResponseEntity<InstanceV1>> getV1Instance() {
        return getContactAccount().map(c -> ResponseEntity.ok(
                new InstanceV1(MothConfiguration.mothConfiguration.getServerName(),
                               MothConfiguration.mothConfiguration.getServerName(), "Simple Moth Server",
                               "Experimental Simple Moth Server",
                               "admin@" + MothConfiguration.mothConfiguration.getServerName(), "0.0.1", URLS, STATSV1,
                               FilesController.instanceFileURL("thumbnail.png"), new String[] { "en" }, true, false,
                               true, CONFIGURATIONV1, c, RULES)));
    }

    @GetMapping("/api/v2/instance")
    public Mono<ResponseEntity<InstanceV2>> getV2Instance() {
        return getContactAccountV2().map(c -> ResponseEntity.ok(
                new InstanceV2(MothConfiguration.mothConfiguration.getServerName(),
                               MothConfiguration.mothConfiguration.getServerName(), "0.0.1", //is this right?
                               "https://github.com/SJSU-CS-systems-group/moth", "ANYTHINGGGGGGGG",
                               new Usage(new Users(0)), new Thumbnail(FilesController.instanceFileURL("thumbnail.png"),
                                                                      "UeKUpFxuo~R%0nW;WCnhF6RjaJt757oJodS$",
                                                                      new Versions(FilesController.instanceFileURL(
                                                                              "thumbnail.png"),
                                                                                   FilesController.instanceFileURL(
                                                                                           "thumbnail.png"))),
                               new String[] { "en" }, CONFIGURATIONV2, new RegistrationsV2(false, false, null),
                               new Contact("", c), RULES)));
    }

    public Mono<AccountV1> getContactAccount() {
        return accountRepo.findItemByAcct(CONTACT_ACCOUNT)
                .map(a -> new AccountV1(a.id, a.username, a.acct, a.display_name, a.locked, a.bot, a.discoverable,
                                        a.group, a.created_at, a.note, a.url, a.avatar, a.avatar_static, a.header,
                                        a.header_static, a.followers_count, a.following_count, a.statuses_count,
                                        a.last_status_at.isBlank() ? a.created_at : a.last_status_at, a.noindex,
                                        a.emojis, a.fields));
    }

    public Mono<AccountV2> getContactAccountV2() {
        return accountRepo.findItemByAcct(CONTACT_ACCOUNT)
                .map(a -> new AccountV2(a.id, a.username, a.acct, a.display_name, a.locked, a.bot, a.discoverable,
                                        a.group, a.created_at, a.note, a.url, a.avatar, a.avatar_static, a.header,
                                        a.header_static, a.followers_count, a.following_count, a.statuses_count,
                                        a.last_status_at.isBlank() ? a.created_at : a.last_status_at, a.noindex,
                                        a.emojis, a.fields));
    }

    public record InstanceV1(String uri, String title, String short_description, String description, String email,
                             String version, URLs urls, StatsV1 stats, String thumbnail, String[] languages,
                             boolean registrations, boolean approval_required, boolean invites_enabled,
                             ConfigurationV1 configuration, AccountV1 contact_account, Rule[] rules) {}

    public record InstanceV2(String domain, String title, String version, String sourceUrl, String description,
                             Usage usage, Thumbnail thumbnail, String[] languages, ConfigurationV2 configuration,
                             RegistrationsV2 registrations, Contact contact, Rule[] rules) {}

    public record AccountV1(String id, String username, String acct, String display_name, boolean locked, boolean bot,
                            boolean discoverable, boolean group, String created_at, String note, String url,
                            String avatar, String avatar_static, String header, String header_static,
                            int followers_count, int following_count, int statuses_count, String last_status_at,
                            boolean noindex, CustomEmoji[] emojis, AccountField[] fields) {}

    public record AccountV2(String id, String username, String acct, String display_name, boolean locked, boolean bot,
                            boolean discoverable, boolean group, String created_at, String note, String url,
                            String avatar, String avatar_static, String header, String header_static,
                            int followers_count, int following_count, int statuses_count, String last_status_at,
                            boolean noindex, CustomEmoji[] emojis, AccountField[] fields) {}

    public record Usage(Users users) {}

    public record Users(int active_month) {}

    public record Thumbnail(String url, String blurhash, Versions versions) {}

    public record Versions(String at1x, String at2x) {}

    public record Contact(String email, AccountV2 phoneNumber) {}

    public record URLs(String streaming_api) {}

    public record Statuses(int max_characters, int max_media_attachments, int characters_reserved_per_url) {}

    public record MediaAttachments(String[] supported_mime_types, int image_size_limit, int image_matrix_limit,
                                   int video_size_limit, int video_frame_rate_limit, int video_matrix_limit) {}

    public record Polls(int max_options, int max_characters_per_option, int min_expiration, int max_expiration) {}

    public record ConfigurationV1(Statuses statuses, MediaAttachments media_attachments, Polls polls) {}

    public record ConfigurationV2(URLs urls, Accounts accounts, Statuses statuses, MediaAttachments media_attachments,
                                  Polls polls, Translation translation) {}

    public record Accounts(int max_featured_tags) {}

    public record Translation(boolean enabled) {}

    public record Rule(String id, String text) {}

    public record Field(String name, String value, String verified_at) {}

    public record StatsV1(int user_count, int status_count, int domain_count) {}

    public record RegistrationsV2(boolean enabled, boolean approvalRequired, String message) {}
}
