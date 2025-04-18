package edu.sjsu.moth.server.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.util.EmailCodeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * simple utility methods
 */
public class Util {
    public static ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    /**
     * super gross code to convert and enumeration to a stream. (should be built into java!)
     *
     * @param en  the enumeration to stream
     * @param <T> the type of objects being streamed
     * @return a stream of the enumeration
     */
    public static <T> Stream<T> enumerationToStream(Enumeration<T> en) {
        return StreamSupport.stream(
                Spliterators.spliterator(en.asIterator(), Long.MAX_VALUE, Spliterator.IMMUTABLE | Spliterator.NONNULL),
                false);
    }

    /*
     * simple helper method to encapsulate the annoying exception handling with Mono and URI
     */
    public static Mono<URI> getMonoURI(String uri) {
        try {
            return Mono.just(new URI(uri));
        } catch (URISyntaxException e) {
            return Mono.error(e);
        }
    }

    /*
     * URL encode a parameter
     */
    public static String URLencode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    public static long generateUniqueId() {
        return EmailCodeUtils.Uniquifier.generateId();
    }

    public static String generatePassword() {
        var bytes = new byte[6];
        new Random().nextBytes(bytes);
        // we need to exclude O, I, and l they are too similar to other letters and numbers
        return Base64.getUrlEncoder().encodeToString(bytes).replace('l', '@').replace('O', '^').replace('I', '$');
    }

    //Print method, testing purposes
    public static void printJsonNode(JsonNode node, String indent) {
        if (node.isObject()) {
            // Print keys and their values for object nodes
            node.fields().forEachRemaining(entry -> {
                System.out.println(indent + entry.getKey() + ": ");
                printJsonNode(entry.getValue(), indent + "    "); // Increase indentation for nested objects
            });
        } else if (node.isArray()) {
            // Print each element in the array
            for (JsonNode element : node) {
                printJsonNode(element, indent);
            }
        } else if (node.isValueNode()) {
            // Print the value of scalar nodes
            String value = node.asText();
            if (value.isEmpty()) {
                System.out.println();
            } else {
                System.out.println(indent + value);
            }
        }
    }

    public static Mono<Void> signAndSend(JsonNode message, String name, String domain, String targetDomain,
                                         String inbox, String privateKey) {
        String actorUrl = "https://" + domain + "/users/" + name;
        /*String inbox;
        if(type.equals("ACCEPT_MESSAGE")){
            inbox = message.get("object").get("actor").asText() + "/inbox";
        }else{
            inbox = message.get("actor").asText() + "/inbox";
        }*/
        String inboxPath = inbox.replace("https://" + targetDomain, "");
        try {
            PrivateKey pvtKey = getPrivateKeyFromPEM(privateKey);

            String json = new ObjectMapper().writeValueAsString(message);
            String digest = Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));

            DateTimeFormatter httpDateFormat =
                    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
            String date = ZonedDateTime.now(ZoneId.of("GMT")).format(httpDateFormat);

            String stringToSign =
                    "(request-target): post " + inboxPath + "\n" + "host: " + targetDomain + "\n" + "date: " + date +
                            "\n" + "digest: SHA-256=" + digest;
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(pvtKey);
            signer.update(stringToSign.getBytes(StandardCharsets.UTF_8));
            String signatureB64 = Base64.getEncoder().encodeToString(signer.sign());

            String signatureHeader = "keyId=\"" + actorUrl + "#main-key" +
                    "\",headers=\"(request-target) host date digest\",signature=\"" + signatureB64 + "\"";

            WebClient webClient =
                    WebClient.builder().defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();

            return webClient.post().uri(inbox).header("Host", targetDomain).header("Date", date)
                    .header("Digest", "SHA-256=" + digest).header("Signature", signatureHeader)
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(message).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                              clientResponse -> clientResponse.bodyToMono(String.class).flatMap(body -> {
                                  System.err.println("4xx error body: " + body);
                                  return Mono.error(new RuntimeException("Client error: " + body));
                              })).onStatus(HttpStatusCode::is5xxServerError,
                                           clientResponse -> clientResponse.bodyToMono(String.class).flatMap(body -> {
                                               System.err.println("5xx error body: " + body);
                                               return Mono.error(new RuntimeException("Server error: " + body));
                                           }))

                    .bodyToMono(String.class).doOnNext(response -> System.out.println("Response: " + response)).then();

        } catch (Exception e) {
            System.err.println("Error while sending ACCEPT : " + e.getMessage());
            return Mono.error(new RuntimeException("Signing failed", e));
        }
    }

    public static PrivateKey getPrivateKeyFromPEM(String pem) throws Exception {
        String cleanedPem = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", ""); // Remove any newlines or extra spaces

        byte[] encoded = Base64.getDecoder().decode(cleanedPem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); // or "EC" for EC keys
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * a lazy hashmap with approximate TTL. keys added to the hashmap will "expire" (get removed) after a specific
     * amount of time has passed (the TTL). TTL processing is done periodically based on the TTL. the expiration
     * task keeps track of the keys (lastKey) from the previous runs and removes them. the logic is as fallows:
     * <p>
     * 1. remove all the keys in lastKeys from the current hashmap
     * 2. set lastKeys to the current lists of keys in the current hashmap
     * <p>
     * there are two anomalies that arise from this implementation: 1) a key that is added right after the expiration
     * runs, will stay in the hashmap for 2 * TTL. 2) if the same key is removed from the hashmap and then readded in
     * between expiration tasks, the next task will still remove the key even though the TTL from the readd has not
     * expired.
     */
    public static class TTLHashMap<K, V> extends ConcurrentHashMap<K, V> {
        public final ScheduledFuture<?> scheduled;
        private List<K> lastKeys = List.of();

        public TTLHashMap(long ttl, TimeUnit ttlTimeUnit) {
            this.scheduled = scheduledExecutorService.scheduleWithFixedDelay(scheduleRunnable(), ttl, ttl, ttlTimeUnit);
        }

        /*****
         ***** BEWARE: if you change this method make sure to never use this! only work with the weak reference or
         ***** it may never get cleaned up!
         *****/
        private Runnable scheduleRunnable() {
            final WeakReference<TTLHashMap<K, V>> weakThis = new WeakReference<>(this);
            return () -> {
                // use a weak reference so that this lambda doesn't create a strong reference and
                // make the hashmap live forever
                var strongThis = weakThis.get();
                // if the hashmap goes away cancel the scheduling by throwing an exception
                if (strongThis == null) throw new RuntimeException("hashmap gone");
                strongThis.lastKeys.forEach(strongThis::remove);
                strongThis.lastKeys = strongThis.keySet().stream().toList();
            };
        }
    }

}