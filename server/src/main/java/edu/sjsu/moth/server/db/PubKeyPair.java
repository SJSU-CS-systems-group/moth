package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("pubkeypair")
public class PubKeyPair {
    @Id
    public String acct;
    public String publicKeyPEM;
    public String privateKeyPEM;

    public PubKeyPair() {}

    public PubKeyPair(String acct, String publicKeyPEM, String privateKeyPEM) {
        this.acct = acct;
        this.publicKeyPEM = publicKeyPEM;
        this.privateKeyPEM = privateKeyPEM;
    }
}
