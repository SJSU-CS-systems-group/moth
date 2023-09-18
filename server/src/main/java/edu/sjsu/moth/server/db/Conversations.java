package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;

@Document("Conversations")
public class Conversations {
    //https://docs.joinmastodon.org/methods/conversations/#get
    @Id
    public String id;
    public ArrayList<Conversation> conversations;

    public Conversations(String id, ArrayList<Conversation> conversations) {
        this.id = id;
        this.conversations = conversations;
    }

    public ArrayList<Conversation> getConversations() {
        return conversations;
    }
}