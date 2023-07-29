package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.Status;

import java.util.ArrayList;

public class SearchResult {

    // later, requires statuses as well as hashtags (? maybe deprecated)
    // https://docs.joinmastodon.org/entities/Search/
    private ArrayList<Account> accounts;
    private ArrayList<Status> statuses;
    private ArrayList<Hashtags> hashtags; // change data type once we have it

    // avoids null pointer exception caused by simply directly grabbing with getAccounts() below.
    public SearchResult() {
        this.accounts = new ArrayList<>(0);
        this.statuses = new ArrayList<>(0);
        this.hashtags = new ArrayList<>(0);
    }

    public ArrayList<Account> getAccounts() {
        return accounts;
    }

    public ArrayList<Status> getStatuses() {return statuses;}

    public ArrayList<Hashtags> getHashtags() {return hashtags;}

    public ArrayList<Account> setAccounts(ArrayList<Account> accountsModify) {
        return this.accounts = accountsModify;
    }

    public ArrayList<Status> setStatuses(ArrayList<Status> statusesModify) {
        return this.statuses = statusesModify;
    }

    public ArrayList<Hashtags> setHashtags(ArrayList<Hashtags> hashtagsModify) { return this.hashtags = hashtagsModify; }
}


