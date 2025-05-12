package edu.sjsu.moth.server.activitypub.message;

import com.fasterxml.jackson.databind.JsonNode;

public class UndoMessage extends ActivityPubMessage {
    //Follow message accepts a String for the object parameter
    public JsonNode object;

    public UndoMessage(JsonNode object) {
        super("Undo", object.get("actor").asText());
        this.object = object;
    }
}
