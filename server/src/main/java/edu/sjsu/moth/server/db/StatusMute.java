package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class StatusMute {

    public static class StatusMuteKey {
        public String account_id;
        public String status_id;

        public StatusMuteKey(String account_id, String status_id) {
            this.account_id = account_id;
            this.status_id = status_id;
        }

        public StatusMuteKey() {
        }
    }

    @Id
    public StatusMuteKey id;

    public String created_at;

    public StatusMute(String account_id, String status_id, String created_at) {
        this.id = new StatusMuteKey(account_id, status_id);
        this.created_at = created_at;
    }

    public StatusMute() {
    }
}
