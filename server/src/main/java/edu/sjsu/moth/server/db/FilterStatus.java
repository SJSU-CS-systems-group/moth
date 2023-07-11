package edu.sjsu.moth.server.db;

//Definition: https://docs.joinmastodon.org/entities/FilterStatus/
public class FilterStatus {

    public String id;
    public String status_id;

    public FilterStatus(String id, String status_id) {
        this.id = id;
        this.status_id = status_id;
    }

}
