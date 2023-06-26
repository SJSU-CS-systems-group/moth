package edu.sjsu.moth.server;

public class CustomEmoji {

    private final String shortcode;
    private final String url;
    private final String static_url;
    private final boolean visible_in_picker;

    public CustomEmoji(String shortcode, String url, String static_url, boolean visible_in_picker) {
        this.shortcode = shortcode;
        this.url = url;
        this.static_url = static_url;
        this.visible_in_picker = visible_in_picker;
    }
}
