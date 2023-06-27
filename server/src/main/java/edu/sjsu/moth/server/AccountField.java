package edu.sjsu.moth.server;

public class AccountField {
    private String name;
    private String value;
    private String verified_at;

    public AccountField(String name, String value, String verified_at) {
        this.name = name;
        this.value = value;
        this.verified_at = verified_at;
    }
}
