package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document("tokens")
public class Token {
    @Id
    public String token;
    public String user;
    // https://www.baeldung.com/mongodb-java-date-operations recommends LocatDateTime
    public LocalDateTime created_at;

    public Token() {}

    public Token(String token, String user, LocalDateTime created_at) {
        this.token = token;
        this.user = user;
        this.created_at = created_at;
    }

    public Token(String token, String user) {
        this(token, user, LocalDateTime.now());
    }
}
