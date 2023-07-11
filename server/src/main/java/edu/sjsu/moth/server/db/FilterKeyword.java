package edu.sjsu.moth.server.db;

// Definition: https://docs.joinmastodon.org/entities/FilterKeyword/
public class FilterKeyword {

    public String id;
    public String keyword;
    public boolean whole_word;

    public FilterKeyword(String id, String keyword, boolean whole_word) {
        this.id = id;
        this.keyword = keyword;
        this.whole_word = whole_word;
    }
}
