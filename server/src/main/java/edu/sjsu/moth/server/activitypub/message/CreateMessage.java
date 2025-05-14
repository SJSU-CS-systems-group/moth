package edu.sjsu.moth.server.activitypub.message;

import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document("outbox")
public class CreateMessage extends ActivityPubMessage {

    public NoteMessage object;
    public List<String> to;
    public List<String> cc;
    public String published;

    /**
     * Construct a Create activity wrapping a Note.
     *
     * @param actorUrl the actor performing the create (e.g. "https://…/users/alice")
     * @param object   the Note payload
     */
    public CreateMessage(String actorUrl, NoteMessage object) {
        super("Create", actorUrl);
        this.setId(object.getId() + "/activity");
        this.object = object;
        this.to = object.getTo();
        this.cc = object.getCc();
        this.published = object.getPublished();
    }
}