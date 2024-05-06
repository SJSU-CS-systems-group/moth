package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class Follow {

    public static class FollowKey{
        public String follower_id;
        public String followed_id;

        public FollowKey(String follower_id, String followed_id){
            this.follower_id = follower_id;
            this.followed_id = followed_id;
        }
    }

    @Id
    public FollowKey id;
    public boolean notify;
    public boolean showReblog;

    public Follow(String follower, String id) {
        this.id = new FollowKey(follower, id);
    }
}
