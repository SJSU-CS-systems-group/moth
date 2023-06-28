package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.server.util.Util;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InstanceController {
    private static final URLsV1 URLSV1 = new URLsV1("wss://" + MothConfiguration.mothConfiguration.getServerName());
    private static final StatsV1 STATSV1 = new StatsV1(0, 0, 0);
    private static final StatusesV1 STATUSESV1 = new StatusesV1(500, 4, 23);
    private static final MediaAttachmentsV1 MEDIA_ATTACHEMENTSV1 = new MediaAttachmentsV1(
            new String[] { "image/jpeg", "image/png", "image/gif", "image/webp", "video/webm", "video/mp4",
                    "video" + "/quicktime", "video/ogg", "audio/wave", "audio/wav", "audio/x-wav", "audio/x-pn-wave",
                    "audio" + "/vnd.wave", "audio/ogg", "audio/vorbis", "audio/mpeg", "audio/mp3", "audio/webm",
                    "audio/flac", "audio/aac", "audio/m4a", "audio/x-m4a", "audio/mp4", "audio/3gpp", "video/x-ms-asf"
            },
            10485760, 16777216, 41943040, 60, 2304000);
    private static final PollsV1 POLLSV1 = new PollsV1(4, 50, 5 * 60, 7 * 24 * 60 * 60);
    private static final ConfigurationV1 CONFIGURATIONV1 = new ConfigurationV1(STATUSESV1, MEDIA_ATTACHEMENTSV1,
                                                                               POLLSV1);
    private static final AccountV1 CONTACT_ACCOUNT = new AccountV1("1", "admin", "admin", "Admin", false, false, true,
                                                                   false, Util.now(), "admin",
                                                                   MothController.BASE_URL + "/@admin",
                                                                   FilesController.userFileURL("admin", "avatar.png"),
                                                                   FilesController.userFileURL("admin", "avatar.png"),
                                                                   FilesController.userFileURL("admin", "header.png"),
                                                                   FilesController.userFileURL("admin", "header.png"),
                                                                   0, 0, 0, Util.now(), new String[0], new FieldV1[0]);
    private static final RuleV1[] RULESV1 = new RuleV1[] { new RuleV1("1", "Be excellent to each other.") };
    private static final InstanceV1 INSTANCEV1 = new InstanceV1(MothConfiguration.mothConfiguration.getServerName(),
                                                                MothConfiguration.mothConfiguration.getServerName(),
                                                                "Simple Moth Server", "Experimental Simple Moth Server",
                                                                "admin@" + MothConfiguration.mothConfiguration.getServerName(),
                                                                "0.0.1", URLSV1, STATSV1,
                                                                FilesController.instanceFileURL("thumbnail.png"),
                                                                new String[] { "en" }, true, false, true,
                                                                CONFIGURATIONV1, CONTACT_ACCOUNT, RULESV1);

    @GetMapping("/api/v2/instance")
    public ResponseEntity<Object> getV2Instance() {
        // make api calls/retrieve data aka instance info
        Instance.Usage usage = new Instance.Usage(0, 0);
        Instance.Thumbnail thumbnail = new Instance.Thumbnail("", 0, 0);
        String[] languages = {};
        Instance.Configuration configuration = new Instance.Configuration(false);
        Instance.Registrations registrations = new Instance.Registrations(true);
        Instance.Contact contact = new Instance.Contact("", "");
        Instance.Rule[] rules = {};
        //^from nested class to give initial values for the corresponding properties of the Instance class!

        Instance instance = new Instance("cheah.homeofcode.com", "Moth", "4.0.0rc1",
                                         "https://github.com/mastodon/mastodon", "ANYTHINGGGGGGGG", usage, thumbnail,
                                         languages, configuration, registrations, contact, rules);
        return ResponseEntity.ok(instance);
    }

    @GetMapping("/api/v1/instance")
    public ResponseEntity<InstanceV1> getV1Instance() {return ResponseEntity.ok(INSTANCEV1);}

    // this might be V2...
    public record Instance(String domain, String title, String version, String sourceUrl, String description,
                           Usage usage, Thumbnail thumbnail, String[] languages, Configuration configuration,
                           Registrations registrations, Contact contact, Rule[] rules) {

        public record Usage(int posts, int users) {}

        public record Thumbnail(String url, int width, int height) {}

        public record Configuration(boolean supportsStreaming) {}

        public record Registrations(boolean open) {}

        public record Contact(String email, String phoneNumber) {}

        public record Rule(String id, String text) {}
    }

    record InstanceV1(String uri, String title, String short_description, String description, String email,
                      String version, URLsV1 urls, StatsV1 stats, String thumbnail, String[] languages,
                      boolean registrations, boolean approval_required, boolean invites_enabled,
                      ConfigurationV1 configuration, AccountV1 contact_account, RuleV1[] rules) {}

    record AccountV1(String id, String username, String acct, String display_name, boolean locked, boolean bot,
                     boolean discoverable, boolean group, String created_at, String note, String url, String avatar,
                     String avatar_static, String header, String header_static, int followers_count,
                     int following_count, int statuses_count, String last_status_at, String[] emojis,
                     FieldV1[] fields) {}

    record FieldV1(String name, String value, String verified_at) {}

    record RuleV1(String id, String text) {}

    record URLsV1(String streaming_api) {}

    record StatsV1(int user_count, int status_count, int domain_count) {}

    record StatusesV1(int max_characters, int max_media_attachments, int characters_reserved_per_url) {}

    record PollsV1(int max_options, int max_characters_per_option, int min_expiration, int max_expiration) {}

    record ConfigurationV1(InstanceController.StatusesV1 statuses, MediaAttachmentsV1 media_attachements,
                           InstanceController.PollsV1 polls) {}

    record MediaAttachmentsV1(String[] supported_mime_types, int image_size_limit, int image_matrix_limit,
                              int video_size_limit, int video_frame_limit, int video_matrix_limit) {}
}
