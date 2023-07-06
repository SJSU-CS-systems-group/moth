package edu.sjsu.moth.server.db;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CustomEmojiDB {

    private CustomEmojiRepository emojiRepository;

    public CustomEmojiDB(CustomEmojiRepository emojiRepository) {
        this.emojiRepository = emojiRepository;
    }
    public Flux<CustomEmoji> getAllCustomEmojis() {
        return emojiRepository.findAll();
    }

    //not sure if we need these methods :(
    public Mono<CustomEmoji> saveCustomEmoji(CustomEmoji customEmoji) {
        return emojiRepository.save(customEmoji);
    }

    public Mono<CustomEmoji> getCustomEmojiById(String id) {
        return emojiRepository.findById(id);
    }

    public Mono<Void> deleteCustomEmoji(String id) {
        return emojiRepository.deleteById(id);
    }

    public Mono<CustomEmoji> getCustomEmojiByShortcode(String shortcode) {
        return emojiRepository.findByShortcode(shortcode);
    }

    public Mono<CustomEmoji> getCustomEmojiByUrl(String url) {
        return emojiRepository.findByUrl(url);
    }

    public Mono<CustomEmoji> getCustomEmojiByStaticUrl(String staticUrl) {
        return emojiRepository.findByStaticUrl(staticUrl);
    }

    public Flux<CustomEmoji> getVisibleCustomEmojis() {
        return emojiRepository.findVisibleCustomEmojis();
    }

    public Flux<CustomEmoji> getCustomEmojisByCategory(String category) {
        return emojiRepository.findByCategory(category);
    }
}

