package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class Follow {
    @Id
    public String follower_id;
    @Id
    public String followed_id;
    public boolean notify;
    public boolean showReblog;

    public Follow(String follower, String id) {
        this.follower_id = follower;
        this.followed_id = id;
    }
}
