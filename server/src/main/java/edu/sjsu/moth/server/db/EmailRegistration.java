package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("emailregistration")
public class EmailRegistration {
    // id differs from email by trying to normalize things like lowercasing and removing any dots from user part
    @Id
    public String id;
    public String email;
    public String username;
    public String saltedPassword;
    public String lastEmail;
    public String firstEmail;
}
