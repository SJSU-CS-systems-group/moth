package edu.sjsu.moth.server.db;

public class PollOption {

    public String title;
    public int votes_count;

    public PollOption(String title, int votes_count) {
        this.title = title;
        this.votes_count = votes_count;
    }


}
