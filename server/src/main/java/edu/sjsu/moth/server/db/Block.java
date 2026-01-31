package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;

public class Block {

    public static class BlockKey {
        public String blocker_id;
        public String blocked_id;

        public BlockKey(String blocker_id, String blocked_id) {
            this.blocker_id = blocker_id;
            this.blocked_id = blocked_id;
        }

        public BlockKey() {
        }
    }

    @Id
    public BlockKey id;

    public String created_at;

    public Block(String blocker_id, String blocked_id, String created_at) {
        this.id = new BlockKey(blocker_id, blocked_id);
        this.created_at = created_at;
    }

    public Block() {
    }
}
