package edu.sjsu.moth.server.util;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.util.WebFingerUtils;
import lombok.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

/**
 * this class provides a nice way to manage TTL caches.
 */
@Configuration
public class FIFOCacher implements ApplicationContextAware {
    private static ApplicationContext appCtx;
    public final long TTLSeconds = 500;
    // always synchronize when using!
    private final LinkedHashMap<String, KeyCacheRecord> cachedKeys = new LinkedHashMap<>();

    public static FIFOCacher getFIFOCacher() {
        return appCtx.getBean(FIFOCacher.class);
    }

    @Scheduled(fixedRate = TTLSeconds / 2, timeUnit = TimeUnit.SECONDS)
    private void cacheCleaner() {
        synchronized (cachedKeys) {
            var expiredTime = LocalDateTime.now().minus(TTLSeconds, ChronoUnit.SECONDS);
            var it = cachedKeys.entrySet().iterator();
            while (it.hasNext()) {
                var next = it.next();
                if (next.getValue().createTime.isBefore(expiredTime)) it.remove();
                else break;
            }
        }
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        appCtx = applicationContext;
    }

    // fetch the public key for the selected account (we really need to cache this)
    public Mono<PublicKey> fetchPEM(String url) {
        synchronized (cachedKeys) {
            if (cachedKeys.containsKey(url)) return cachedKeys.get(url).mono();

            int indexOfHash = url.indexOf('#');
            var getURL = indexOfHash > 0 ? url.substring(0, indexOfHash) : url;
            var mono = WebClient.create()
                    .get()
                    .uri(getURL)
                    .header(HttpHeaders.ACCEPT, WebFingerUtils.MothMimeType.APPLICATION_ACTIVITY_VALUE)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .flatMap(n -> {
                        try {
                            var pem = Util.stripQuotes(n.get("publicKey").get("publicKeyPem").toString());
                            var s = pem.replaceAll("---+[^-]+---+", "").replace("\\n", "");
                            var spec = new X509EncodedKeySpec(Base64.getDecoder().decode(s));
                            return Mono.just(KeyFactory.getInstance("RSA").generatePublic(spec));
                        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                            return Mono.error(e);
                        }
                    })
                    .cache();
            cachedKeys.put(url, new KeyCacheRecord(url, mono, LocalDateTime.now()));
            return mono;
        }
    }

    record KeyCacheRecord(String url, Mono<PublicKey> mono, LocalDateTime createTime) {}
}
