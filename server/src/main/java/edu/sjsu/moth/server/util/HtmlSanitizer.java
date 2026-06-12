package edu.sjsu.moth.server.util;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * sanitizes HTML before it is stored, both from federated servers (status content, actor
 * profiles) and from local clients. the policy mirrors what mastodon allows in status
 * content: basic formatting and links, nothing executable.
 */
public class HtmlSanitizer {
    // mastodon-style allowlist: p br span a del pre code em strong b i u ul ol li blockquote h1-h5.
    // class is allowed on a/span because mastodon uses it for mention/hashtag/ellipsis/invisible.
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "span", "a", "del", "pre", "code", "em", "strong", "b", "i", "u",
                           "ul", "ol", "li", "blockquote", "h1", "h2", "h3", "h4", "h5")
            .allowAttributes("href").onElements("a")
            .allowAttributes("class").onElements("a", "span")
            .allowStandardUrlProtocols()
            .requireRelsOnLinks("nofollow", "noopener", "noreferrer")
            .toFactory();

    /**
     * sanitize HTML meant to be rendered by clients (status content, account note).
     * null stays null so callers keep their null semantics.
     */
    public static String sanitize(String html) {
        if (html == null) return null;
        // the sanitizer entity-encodes @ = ` + out of caution (e.g. mail harvesting); they are
        // inert in HTML and mention parsing depends on a literal @, so decode them back.
        return POLICY.sanitize(html)
                .replace("&#64;", "@")
                .replace("&#61;", "=")
                .replace("&#96;", "`")
                .replace("&#43;", "+");
    }

    /**
     * strip all tags from a value that clients treat as plain text (display names,
     * profile field names). does not entity-encode, since the field is not HTML.
     */
    public static String stripHtml(String text) {
        return text == null ? null : text.replaceAll("<[^>]*>", "");
    }
}
