package edu.sjsu.moth.server.db;

//Definition: https://docs.joinmastodon.org/entities/PreviewCard/
public class PreviewCard {

    public String url;
    public String title;
    public String description;
    public String type;
    public String author_name;
    public String author_url;
    public String provider_name;
    public String provider_url;
    public String html;
    public int width;
    public int height;
    public String image;
    public String blurhash;

    public PreviewCard(String url, String title, String description, String type, String author_name,
                       String author_url, String provider_name, String provider_url, String html, int width,
                       int height, String image, String blurhash) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.type = type;
        this.author_name = author_name;
        this.author_url = author_url;
        this.provider_name = provider_name;
        this.provider_url = provider_url;
        this.html = html;
        this.width = width;
        this.height = height;
        this.image = image;
        this.blurhash = blurhash;
    }
}
