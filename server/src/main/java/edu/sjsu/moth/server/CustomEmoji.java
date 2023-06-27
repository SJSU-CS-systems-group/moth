package edu.sjsu.moth.server;

public class CustomEmoji {

    private String shortcode;
    private String url;
    private String static_url;
    private boolean visible_in_picker;

    public CustomEmoji(String shortcode, String url, String static_url, boolean visible_in_picker) {
        this.shortcode = shortcode;
        this.url = url;
        this.static_url = static_url;
        this.visible_in_picker = visible_in_picker;
    }
}
