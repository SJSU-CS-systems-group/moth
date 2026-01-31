package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class Bookmark {

    public static class BookmarkKey {
        public String account_id;
        public String status_id;

        public BookmarkKey(String account_id, String status_id) {
            this.account_id = account_id;
            this.status_id = status_id;
        }

        public BookmarkKey() {
        }
    }

    @Id
    public BookmarkKey id;

    public String created_at;

    public Bookmark(String account_id, String status_id, String created_at) {
        this.id = new BookmarkKey(account_id, status_id);
        this.created_at = created_at;
    }

    public Bookmark() {
    }
}
