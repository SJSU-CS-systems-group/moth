package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

//https://docs.joinmastodon.org/entities/CustomEmoji/
@Document(collection = "custom_emojis")
public record CustomEmoji(
        @Id String id,
        String shortcode,
        String url,
        String staticUrl,
        boolean visibleInPicker,
        String category
) {
}
