package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class Pin {

    public static class PinKey {
        public String account_id;
        public String status_id;

        public PinKey(String account_id, String status_id) {
            this.account_id = account_id;
            this.status_id = status_id;
        }

        public PinKey() {
        }
    }

    @Id
    public PinKey id;

    public String created_at;

    public Pin(String account_id, String status_id, String created_at) {
        this.id = new PinKey(account_id, status_id);
        this.created_at = created_at;
    }

    public Pin() {
    }
}
