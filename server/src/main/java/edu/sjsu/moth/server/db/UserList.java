package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.List;

public class UserList {

    @Id
    public String id;

    public String owner_id;

    public String title;

    public String replies_policy;

    public boolean exclusive;

    public List<String> account_ids;

    public String created_at;

    public UserList(String id, String owner_id, String title, String replies_policy, boolean exclusive, String created_at) {
        this.id = id;
        this.owner_id = owner_id;
        this.title = title;
        this.replies_policy = replies_policy != null ? replies_policy : "list";
        this.exclusive = exclusive;
        this.account_ids = new ArrayList<>();
        this.created_at = created_at;
    }

    public UserList() {
        this.account_ids = new ArrayList<>();
    }
}
