package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;

@Document("Following")
public class Following {
    @Id
    public String id;
    public ArrayList<String> following;

    public Following(String id, ArrayList<String> following) {
        this.id = id;
        this.following = following;
    }

    // id to query for, to locate a users' followers document
    // afterwards, have a String[] array containing the users' followers and append to it, or delete
    // 'Following' can follow a similar format, but in a different collection

    public ArrayList<String> getFollowing() {
        return following;
    }
}

