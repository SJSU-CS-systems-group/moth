package edu.sjsu.moth.server.db;

public class AccountField {
    public String name;
    public String value;
    public String verified_at;

    public AccountField(String name, String value, String verified_at) {
        this.name = name;
        this.value = value;
        this.verified_at = verified_at;
    }
}
