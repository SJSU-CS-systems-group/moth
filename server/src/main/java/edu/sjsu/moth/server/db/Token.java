package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document("tokens")
public class Token {
    @Id
    public String token;
    public String user;
    public String appName;
    public String appWebsite;
    // https://www.baeldung.com/mongodb-java-date-operations recommends LocatDateTime
    public LocalDateTime created_at;

    public Token() {}

    public Token(String token, String user, String appName, String appWebsite, LocalDateTime created_at) {
        this.token = token;
        this.user = user;
        this.appName = appName;
        this.appWebsite = appWebsite;
        this.created_at = created_at;
    }
}
