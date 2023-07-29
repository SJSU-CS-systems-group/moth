package edu.sjsu.moth.server.db;

import java.util.ArrayList;

public class Hashtags {
    private String name;
    private String url;

    public Hashtags() {
        ArrayList<History> history = new ArrayList<>(0);
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public static class History {
        private int day;
        private int accounts;
        private int uses;

        public int getDay() {
            return day;
        }

        public int accounts() {
            return accounts;
        }

        public int uses() {
            return uses;
        }
    }
}

