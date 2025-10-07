package edu.sjsu.moth.server.db;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpHeaders;

import java.time.Instant;

@Document("federated_activity")
public class FederatedActivity {
    @Id
    public String id;
    public String content; // the message content
    public String inboxUrl;   // the inbox URL where we sent it
    public String error;      // if failed, the error message
    public Instant created_at;  // when the message was created
    public int attempts;      // number of send attempts
    public String senderActorId; // the actor ID of the sender;

    public FederatedActivity(String inboxUrl, String senderActorId, String content, Instant created_at) {
        this.inboxUrl = inboxUrl;
        this.error = null;
        this.attempts = 0;
        this.content = content;
        this.created_at = created_at;
        this.senderActorId = senderActorId;
    }
}
