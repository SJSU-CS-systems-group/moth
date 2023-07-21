package edu.sjsu.moth.server.db;

import com.querydsl.core.annotations.QueryEntity;

@QueryEntity
public class StatusTag {

    public String name;
    public String url;

    public StatusTag(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public StatusTag() {}
}