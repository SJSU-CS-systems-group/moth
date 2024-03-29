package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("webfinger")
public class WebfingerAlias {
    @Id
    public final String alias;
    public String user;
    public String host;

    public WebfingerAlias(String alias, String user, String host) {
        this.alias = alias;
        this.user = user;
        this.host = host;
    }
}
