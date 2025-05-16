package edu.sjsu.moth.server.activitypub.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.sjsu.moth.server.util.MothConfiguration;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public abstract class ActivityPubMessage {
    @JsonProperty("@context")
    private String context = "https://www.w3.org/ns/activitystreams";
    private String id;
    private String type;
    private String actor;

    protected ActivityPubMessage(String type, String actor) {
        this.type = type;
        this.actor = actor;
        this.id =
                String.format("https://%s/%s", MothConfiguration.mothConfiguration.getServerName(), UUID.randomUUID());
    }
}
