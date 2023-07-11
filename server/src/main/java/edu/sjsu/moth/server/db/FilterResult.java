package edu.sjsu.moth.server.db;

public class FilterResult {
    public FilterStatus filter;
    public  String[] keyword_matches;
    public String status_matches;

    public FilterResult(FilterStatus filter, String[] keyword_matches, String status_matches) {
        this.filter = filter;
        this.keyword_matches = keyword_matches;
        this.status_matches = status_matches;
    }


}
