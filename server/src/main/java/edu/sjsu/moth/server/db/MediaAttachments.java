package edu.sjsu.moth.server.db;

import java.util.HashMap;

public class MediaAttachments {

    public String id;
    public String type;

    public String url;
    public String preview_url;
    public String remote_url;
    public HashMap<String, Object> meta;
    public String description;
    public String blurhash;

    public MediaAttachments(String id, String type, String url, String preview_url, String remote_url, HashMap<String
            , Object> meta, String description, String blurhash) {
        this.id = id;
        this.type = type;
        this.url = url;
        this.preview_url = preview_url;
        this.remote_url = remote_url;
        this.meta = meta;
        this.description = description;
        this.blurhash = blurhash;
    }

}
