package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.Status;

import java.util.List;

public class Conversation {
    //https://docs.joinmastodon.org/entities/Conversation/
    public String id;
    public boolean unread;
    public List<Account> accounts;
    public Status lastStatus;

    public Conversation(String id, boolean unread, List<Account> accounts, Status lastStatus) {
        this.id = id;
        this.unread = unread;
        this.accounts = accounts;
        this.lastStatus = lastStatus;
    }

    public Conversation() {}
}