package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("password")
public class UserPassword {
    @Id
    public String user;
    public String saltedPassword;
    public UserPassword(String user, String saltedPassword) {
        this.user = user;
        this.saltedPassword = saltedPassword;
    }
}
