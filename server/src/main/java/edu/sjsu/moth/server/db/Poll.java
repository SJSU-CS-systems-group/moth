package edu.sjsu.moth.server.db;

public class Poll {

    public String id;
    public String expires_at;
    public boolean expired;
    public boolean multiple;
    public int votes_count;
    public int voters_count;
    public PollOption[] options;
    public CustomEmoji[] emojis;
    public boolean voted;
    public int[] own_votes;

    public Poll(String id, String expires_at, boolean expired, boolean multiple, int votes_count, int voters_count,
                PollOption[] options, CustomEmoji[] emojis, boolean voted, int[] own_votes) {
        this.id = id;
        this.expires_at = expires_at;
        this.expired = expired;
        this.multiple = multiple;
        this.votes_count = votes_count;
        this.voters_count = voters_count;
        this.options = options;
        this.emojis = emojis;
        this.voted = voted;
        this.own_votes = own_votes;
    }

}
