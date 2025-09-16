package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("federated_message")
public class FederatedMessage {
    @Id
    public String id;
    public String messageId;  // the "id" field from the actual message
    public String content;  // the message content
    public String inboxUrl;   // the inbox URL where we sent it
    public String status;     // "pending", "sent", "failed"
    public String error;      // if failed, the error message
    public Instant created_at;  // when the message was created
    public int attempts;      // number of send attempts

    public FederatedMessage() {}

    public FederatedMessage(String id, String messageId, String inboxUrl, String status, String error, int attempts) {
        this.id = id;
        this.messageId = messageId;
        this.inboxUrl = inboxUrl;
        this.status = status;
        this.error = error;
        this.attempts = attempts;
    }
}
