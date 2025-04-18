package edu.sjsu.moth.server.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class AcceptMessage {
    @JsonProperty("@context")
    public final String context = "https://www.w3.org/ns/activitystreams";
    public String id;
    public String type = "Accept";
    public String actor;
    public JsonNode object;

    public AcceptMessage(String id, String actor, JsonNode object) {
        this.id = id;
        this.actor = actor;
        this.object = object;
    }
}

