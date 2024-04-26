package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;


public class Group {

    @JsonProperty("id")
    public String id;
    @JsonProperty("type")
    public String type;
    @JsonProperty("preferredUsername")
    public String preferredUsername;
    @JsonProperty("url")
    public String url;
    @JsonProperty("attachment")
    public List<Attachment> attachment = new ArrayList<>();
}
