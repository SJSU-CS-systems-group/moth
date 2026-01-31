package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class Favourite {

    public static class FavouriteKey {
        public String account_id;
        public String status_id;

        public FavouriteKey(String account_id, String status_id) {
            this.account_id = account_id;
            this.status_id = status_id;
        }

        public FavouriteKey() {
        }
    }

    @Id
    public FavouriteKey id;

    public String created_at;

    public Favourite(String account_id, String status_id, String created_at) {
        this.id = new FavouriteKey(account_id, status_id);
        this.created_at = created_at;
    }

    public Favourite() {
    }
}
