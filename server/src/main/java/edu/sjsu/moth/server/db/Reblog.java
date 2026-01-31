package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class Reblog {

    public static class ReblogKey {
        public String account_id;
        public String status_id;

        public ReblogKey(String account_id, String status_id) {
            this.account_id = account_id;
            this.status_id = status_id;
        }

        public ReblogKey() {
        }
    }

    @Id
    public ReblogKey id;

    public String reblog_status_id;

    public String created_at;

    public Reblog(String account_id, String status_id, String reblog_status_id, String created_at) {
        this.id = new ReblogKey(account_id, status_id);
        this.reblog_status_id = reblog_status_id;
        this.created_at = created_at;
    }

    public Reblog() {
    }
}
