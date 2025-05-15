package edu.sjsu.moth.server.activitypub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.activitypub.ActivityPubUtil;
import edu.sjsu.moth.server.activitypub.message.CreateMessage;
import edu.sjsu.moth.server.activitypub.message.NoteMessage;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@CommonsLog
@Configuration
public class OutboxService {

    public CreateMessage buildCreateActivity(Status status) {
        String actorUrl = ActivityPubUtil.toActivityPubUserUrl(status.account.url);
        NoteMessage note = buildNoteMessage(status, actorUrl);

        return new CreateMessage(actorUrl, note);
    }

    private NoteMessage buildNoteMessage(Status status, String actorUrl) {
        NoteMessage.Replies.First first = new NoteMessage.Replies.First();
        first.setNext(status.getUri() + "/replies?only_other_accounts=true&page=true");
        first.setPartOf(status.getUri() + "/replies");
        first.setItems(Collections.emptyList());
        String cc = "";
        String bcc = "";

        if (status.visibility.equals("private")) {
            cc = actorUrl + "/followers";
            bcc = "";
        } else {
            cc = "https://www.w3.org/ns/activitystreams#Public";
            bcc = actorUrl + "/followers";
        }

        NoteMessage.Replies replies = new NoteMessage.Replies();
        replies.setId(status.getUri() + "/replies");
        replies.setFirst(first);

        return new NoteMessage(status.getUri(), null, null, status.createdAt, status.getUrl(), actorUrl, List.of(cc),
                               List.of(bcc), status.sensitive, status.getUri(), null, status.text, status.content,
                               Map.of("en", status.content), Collections.emptyList(), Collections.emptyList(), replies);
    }
}
