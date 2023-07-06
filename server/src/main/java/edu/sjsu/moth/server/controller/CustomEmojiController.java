package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.server.db.CustomEmoji;
import edu.sjsu.moth.server.db.CustomEmojiDB;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class CustomEmojiController {
    //implement logic to fetch and return the custom emojis from the server
    //use a repository to handle the data retrieval!
    private CustomEmojiDB customEmojiDB;

    public CustomEmojiController(CustomEmojiDB customEmojiDB) {
        this.customEmojiDB = customEmojiDB;
    }
    //https://docs.joinmastodon.org/methods/custom_emojis/#get
    @GetMapping("/api/v1/custom_emojis")
    public Flux<CustomEmoji> getAllCustomEmojis() {
        return customEmojiDB.getAllCustomEmojis();
    }
}
