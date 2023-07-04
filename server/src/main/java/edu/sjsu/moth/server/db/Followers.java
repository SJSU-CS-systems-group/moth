package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;

@Document("Followers")
public class Followers {
    @Id
    public String id;
    public ArrayList<String> followers;

    public Followers(String id, ArrayList<String> followers) {
        this.id = id;
        this.followers = followers;
    }

    // id to query for, to locate a users' followers document
    // afterwards, have a String[] array containing the users' followers and append to it, or delete
    // 'Following' can follow a similar format, but in a different collection

    public ArrayList<String> getFollowers() {
        return followers;
    }
}

