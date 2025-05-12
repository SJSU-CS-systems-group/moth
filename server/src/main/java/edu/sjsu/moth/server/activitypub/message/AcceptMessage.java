package edu.sjsu.moth.server.activitypub.message;

import com.fasterxml.jackson.databind.JsonNode;

public class AcceptMessage extends ActivityPubMessage {
    //Accept message accepts an Object for the object parameter
    public JsonNode object;

    public AcceptMessage(String actor, JsonNode object) {
        super("Accept", actor);
        this.object = object;
    }
}
