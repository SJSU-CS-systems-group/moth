package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class Mute {

    public static class MuteKey {
        public String muter_id;
        public String muted_id;

        public MuteKey(String muter_id, String muted_id) {
            this.muter_id = muter_id;
            this.muted_id = muted_id;
        }

        public MuteKey() {
        }
    }

    @Id
    public MuteKey id;

    public boolean mute_notifications;

    public Long duration;

    public String created_at;

    public String expires_at;

    public Mute(String muter_id, String muted_id, boolean mute_notifications, Long duration, String created_at, String expires_at) {
        this.id = new MuteKey(muter_id, muted_id);
        this.mute_notifications = mute_notifications;
        this.duration = duration;
        this.created_at = created_at;
        this.expires_at = expires_at;
    }

    public Mute() {
    }
}
