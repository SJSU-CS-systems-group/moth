package edu.sjsu.moth.server.activitypub.message;

public class FollowMessage extends ActivityPubMessage {
    //Follow message accepts a String for the object parameter
    public String object;

    public FollowMessage(String actor, String object) {
        super("Follow", actor);
        this.object = object;
    }
}
