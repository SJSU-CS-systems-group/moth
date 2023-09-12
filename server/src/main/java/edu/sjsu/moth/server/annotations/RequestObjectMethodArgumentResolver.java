package edu.sjsu.moth.server.annotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MimeType;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolverSupport;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * allow RequestObject to work with forms, URL arguments, and JSON blobs.
 * i super wish i could use the code under org.springframework.http.codec.json! unfortunately, the structure,
 * assumptions about being invoked with raw streams, and private fields and methods make it impossible :'(
 */
@Component
public class RequestObjectMethodArgumentResolver extends HandlerMethodArgumentResolverSupport {
    /* from Jackson2CodecSupport */
    public static final List<MimeType> defaultMimeTypes = List.of(MediaType.APPLICATION_JSON,
                                                                  new MediaType("application", "*+json"),
                                                                  MediaType.APPLICATION_NDJSON);
    public static final Charset UTF_8 = StandardCharsets.UTF_8;

    ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    RequestObjectMethodArgumentResolver(ReactiveAdapterRegistry registry) {
        super(registry);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        var hasParam = parameter.hasParameterAnnotation(RequestObject.class);
        var canCreate = mapper.canDeserialize(
                mapper.constructType(ResolvableType.forMethodParameter(parameter).getType()));
        return hasParam && canCreate;
    }

    @Override
    public @NotNull Mono<Object> resolveArgument(MethodParameter parameter, @NotNull BindingContext bindingContext,
                                                 ServerWebExchange exchange) {
        var contentType = exchange.getRequest().getHeaders().getContentType();
        var reader = mapper.readerFor(parameter.getParameterType());
        if (defaultMimeTypes.stream().anyMatch(ct -> ct.isCompatibleWith(contentType))) {
            return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(body -> {
                try {
                    return Mono.just(reader.readValue(body.asInputStream()));
                } catch (IOException e) {
                    return Mono.error(e);
                }
            });
        }
        var root = new ObjectNode(JsonNodeFactory.instance);
        parseParams(exchange.getRequest().getQueryParams(), root);
        return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(db -> {
            var formdata = db.toString(UTF_8);
            var exps = StringUtils.delimitedListToStringArray(formdata, "&");
            var vars = new LinkedMultiValueMap<String, String>();
            for (var exp : exps) {
                var parts = exp.split("=", 2);
                if (parts.length == 1) {
                    vars.add(URLDecoder.decode(parts[0], UTF_8), null);
                } else {
                    vars.add(URLDecoder.decode(parts[0], UTF_8), URLDecoder.decode(parts[1], UTF_8));
                }
            }
            parseParams(vars, root);
            return Mono.empty();
        }).then(Mono.defer(() -> {
            try {
                return Mono.just(reader.readValue(root));
            } catch (IOException e) {
                return Mono.error(e);
            }
        }));
    }

    private void parseParams(MultiValueMap<String, String> params, ObjectNode root) {
        params.forEach((key, value) -> {
            var lbrack = key.indexOf('[');
            if (lbrack == -1) {
                parseKey(root, key, null, value);
            } else {
                parseKey(root, key.substring(0, lbrack), key.substring(lbrack), value);
            }
        });
    }

    private void parseKey(ObjectNode root, String name, String rest, List<String> values) {
        if (rest == null) {
            // there should only be one, so we will go with the first :)
            root.put(name, values.get(0));
        } else if (rest.equals("[]")) {
            var array = root.putArray(name);
            values.forEach(array::add);
        } else {
            var rbrack = rest.indexOf(']');
            if (rbrack == -1) throw new RuntimeException("Malformed hashed variable missing ]: " + name + rest);
            var obj = (ObjectNode) root.get(name);
            if (obj == null) obj = root.putObject(name);

            name = rest.substring(1, rbrack);
            rest = rbrack + 1 < rest.length() ? rest.substring(rbrack + 1) : null;
            parseKey(obj, name, rest, values);
        }
    }
}
