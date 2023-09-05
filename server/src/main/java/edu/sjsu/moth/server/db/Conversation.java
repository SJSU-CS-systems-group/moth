package edu.sjsu.moth.server.db;

import java.util.HashMap;
import java.util.List;

public class Conversation {
    //https://docs.joinmastodon.org/entities/Conversation/
    public String id;
    public HashMap<String, Boolean> accountsRead;
    public List<String> accountIds;
    public String lastStatusId;

    public Conversation(String id, HashMap<String, Boolean> accountsRead, List<String> accountIds,
                        String lastStatusId) {
        this.id = id;
        this.accountsRead = accountsRead;
        this.accountIds = accountIds;
        this.lastStatusId = lastStatusId;
    }

    public Conversation() {}
}