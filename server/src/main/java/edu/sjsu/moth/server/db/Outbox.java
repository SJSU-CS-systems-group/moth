package edu.sjsu.moth.server.db;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.server.activitypub.message.NoteMessage;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("outbox")
public class Outbox {
    @Id
    @Getter
    @Setter
    private String id;               // the CreateMessage.id
    @Getter
    @Setter
    private String actor;
    @Getter
    @Setter
    private String published;
    @Getter
    @Setter
    private List<String> to;
    @Getter
    @Setter
    private List<String> cc;
    @Getter
    @Setter
    private String type = "Create";

    /**
     * The full CreateMessage JSON, stored as a tree.
     */
    @Getter
    @Setter
    private NoteMessage object;

    // <-- default ctor, getters & setters -->

    public Outbox() {}

    public Outbox(String id, String actor, String published, List<String> to, List<String> cc, NoteMessage object) {
        this.id = id;
        this.actor = actor;
        this.published = published;
        this.to = to;
        this.cc = cc;
        this.object = object;
    }
}
