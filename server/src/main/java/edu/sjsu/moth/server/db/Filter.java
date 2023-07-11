package edu.sjsu.moth.server.db;

//Definition: https://docs.joinmastodon.org/entities/Filter/
public class Filter {

    public String id;
    public String title;
    public String[] context;
    public String expires_at;
    public String filter_action;
    public FilterKeyword[] keywords;
    public FilterStatus[] statuses;

    public Filter(String id, String title, String[] context, String expires_at, String filter_action,
                  FilterKeyword[] keywords, FilterStatus[] statuses) {
        this.id = id;
        this.title = title;
        this.context = context;
        this.expires_at = expires_at;
        this.filter_action = filter_action;
        this.keywords = keywords;
        this.statuses = statuses;
    }

}
