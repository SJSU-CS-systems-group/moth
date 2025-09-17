package edu.sjsu.moth.server.activitypub.service;

import edu.sjsu.moth.util.WebFingerUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class WebfingerService {

    private final WebClient webClient;

    public WebfingerService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<String> discoverProfileUrl(String userHandle) {
        String[] parts = userHandle.split("@");
        if (parts.length != 2) {
            return Mono.error(new IllegalArgumentException("Invalid user handle: " + userHandle));
        }
        String domain = parts[1];
        String url = "https://" + domain + "/.well-known/webfinger?resource=acct:" + userHandle;

        return webClient.get().uri(url).retrieve().bodyToMono(WebFingerUtils.WebFinger.class).flatMap(webFinger -> {
            for (WebFingerUtils.FingerLink link : webFinger.links()) {
                if (WebFingerUtils.RelType.SELF.equals(link.rel()) && "application/activity+json".equals(link.type())) {
                    return Mono.just(link.href());
                }
            }
            return Mono.empty();
        }).onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }
}