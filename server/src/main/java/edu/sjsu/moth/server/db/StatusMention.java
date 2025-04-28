package edu.sjsu.moth.server.db;

import lombok.ToString;

@ToString
public class StatusMention {

    public String id;
    public String username;

    public String url;

    public String acct;

    public StatusMention(String id, String username, String url, String acct) {
        this.id = id;
        this.username = username;
        this.url = url;
        this.acct = acct;
    }

    public StatusMention() {}

}