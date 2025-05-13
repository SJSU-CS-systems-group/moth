package edu.sjsu.moth.server.activitypub;

import edu.sjsu.moth.server.util.MothConfiguration;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class ActivityPubUtil {

    public static String inboxUrlToAcct(String inboxUrl) {
        URI uri = URI.create(inboxUrl);
        String host = uri.getHost();
        String path = uri.getPath();
        String[] segments = path.split("/");

        if (segments.length < 2) {
            throw new IllegalArgumentException("Invalid inbox URL format: " + inboxUrl);
        }

        String username = segments[segments.length - 1];

        if (!isRemoteUser(inboxUrl)) {
            return username;
        } else {
            return username + "@" + host;
        }
    }

    public static String getActorUrl(String id) {
        return String.format("https://%s/users/%s", MothConfiguration.mothConfiguration.getServerName(), id);
    }

    public static String getRemoteDomain(String uri) {
        try {
            return new URL(uri).getHost();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public static boolean isRemoteUser(String actor) {
        String domain = getRemoteDomain(actor);
        String localDomain = MothConfiguration.mothConfiguration.getServerName();

        if (domain == null) {
            return false;
        }

        return !domain.equals(localDomain);
    }

    //Just some local implementations until I figure out the actual requirement and middleware
    public static String toActivityPubUserUrl(String url) {
        if (url == null || !url.contains("/@")) {
            throw new IllegalArgumentException("Invalid Mastodon-style URL");
        }

        String baseUrl = url.substring(0, url.indexOf("/@"));
        String username = url.substring(url.indexOf("/@") + 2); // Skip "/@"
        return baseUrl + "/users/" + username;
    }

}
