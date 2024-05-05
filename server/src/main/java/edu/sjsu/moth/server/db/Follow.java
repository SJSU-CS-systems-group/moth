package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class Follow {

    public static class FollowKey{
        public String followerId;
        public String followedId;

        public FollowKey(String follower_id, String followed_id){
            this.followerId = follower_id;
            this.followedId = followed_id;
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
