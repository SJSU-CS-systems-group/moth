package edu.sjsu.moth.server.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.annotations.QueryEntity;
import edu.sjsu.moth.generated.CustomEmoji;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static edu.sjsu.moth.server.util.Util.generateUniqueId;

@QueryEntity
@Document("Actor")
public class Actor {
    //https://www.w3.org/TR/activitypub/#actor-objects, also from curling a user on mastodon
    //curl -H "Accept: application/activity+json" https://mastodon.social/users/breed
    public String type;
    @Id
    public String id;
    public String following;
    public String followers;
    public String inbox;
    public String outbox;
    public String featured;
    public String featuredTags;
    public String preferredUsername;
    public String name;
    public String summary;
    public String url;
    public JsonNode icon;
    public JsonNode image;
    public boolean manuallyApprovesFollowers;
    public boolean discoverable;
    public String published;
    public String devices;
    public JsonNode publicKey;
    public List<JsonNode> tag;
    public List<JsonNode> attachment;
    public JsonNode endpoints;

    public Actor(String type, String id, String following, String followers, String inbox, String outbox,
                 String featured, String featuredTags, String preferredUsername, String name, String summary,
                 String url, JsonNode icon, JsonNode image, boolean manuallyApprovesFollowers, boolean discoverable,
                 String published, String devices, JsonNode publicKey, ArrayList<JsonNode> tag,
                 ArrayList<JsonNode> attachment, JsonNode endpoints) {
        this.type = type;
        this.id = id;
        this.following = following;
        this.followers = followers;
        this.inbox = inbox;
        this.outbox = outbox;
        this.featured = featured;
        this.featuredTags = featuredTags;
        this.preferredUsername = preferredUsername;
        this.name = name;
        this.summary = summary;
        this.url = url;
        this.icon = icon;
        this.image = image;
        this.manuallyApprovesFollowers = manuallyApprovesFollowers;
        this.discoverable = discoverable;
        this.published = published;
        this.devices = devices;
        this.publicKey = publicKey;
        this.tag = tag;
        this.attachment = attachment;
        this.endpoints = endpoints;
    }

    public Actor() {}

    public Mono<Account> convertToAccount() {
        String serverName = "";
        try {
            serverName = new URL(id).getHost();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayList<AccountField> accountFields = new ArrayList<>();
        for (JsonNode jsonNode : attachment) {
            AccountField accountField = objectMapper.convertValue(jsonNode, AccountField.class);
            accountFields.add(accountField);
        }

        WebClient webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, "application/activity+json")
                .build();
        Mono<JsonNode> outboxResponse = webClient.get().uri(outbox).retrieve().bodyToMono(JsonNode.class);
        Mono<JsonNode> followersResponse = webClient.get().uri(outbox).retrieve().bodyToMono(JsonNode.class);
        Mono<JsonNode> followingResponse = webClient.get().uri(outbox).retrieve().bodyToMono(JsonNode.class);
        String finalServerName = serverName;
        return outboxResponse.flatMap(jsonNodeOutbox -> {
            int totalItems = jsonNodeOutbox.get("totalItems").asInt();
            return followersResponse.flatMap(jsonNodeFollowers -> {
                int totalItemFollowers = jsonNodeFollowers.get("totalItems").asInt();
                return followingResponse.map(jsonNodeFollowing -> {
                    int totalItemFollowing = jsonNodeFollowing.get("totalItems").asInt();
                    return new Account(String.valueOf(generateUniqueId()), preferredUsername,
                                       preferredUsername + "@" + finalServerName, url, name, summary,
                                       icon.get("url").asText(), icon.get("url").asText(), image.get("url").asText(),
                                       image.get("url").asText(), manuallyApprovesFollowers, accountFields,
                                       new CustomEmoji[0], false, false, discoverable, false, false, false, false,
                                       published, null, totalItems, totalItemFollowers, totalItemFollowing);
                });
            });
        });
        //don't know about custom emojis, bot, and group
        //noindex, moved, suspended, and limited are optional?
        //icon, image, tag, attachment might be null
        //not sure how to get last_status_at. outbox doesn't give a time, only the last status
    }

    //testing purposes
    public void printFields() {
        System.out.println("type: " + type);
        System.out.println("id: " + id);
        System.out.println("following: " + following);
        System.out.println("followers: " + followers);
        System.out.println("inbox: " + inbox);
        System.out.println("outbox: " + outbox);
        System.out.println("featured: " + featured);
        System.out.println("featuredTags: " + featuredTags);
        System.out.println("preferredUsername: " + preferredUsername);
        System.out.println("name: " + name);
        System.out.println("summary: " + summary);
        System.out.println("url: " + url);
        System.out.println("icon: " + icon);
        System.out.println("image: " + image);
        System.out.println("manuallyApprovesFollowers: " + manuallyApprovesFollowers);
        System.out.println("discoverable: " + discoverable);
        System.out.println("published: " + published);
        System.out.println("devices: " + devices);
        System.out.println("publicKey: " + publicKey);
        System.out.println("tag: " + tag);
        System.out.println("attachment: " + attachment);
        System.out.println("endpoints: " + endpoints);
    }
}