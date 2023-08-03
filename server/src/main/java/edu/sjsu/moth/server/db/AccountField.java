package edu.sjsu.moth.server.db;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountField {
    public String name;
    public String value;
    public String verified_at;

    @JsonCreator
    public AccountField(@JsonProperty("name") String name,
                        @JsonProperty("value") String value,
                        @JsonProperty("verified_at") String verified_at) {
        this.name = name;
        this.value = value;
        this.verified_at = verified_at;
    }
}
