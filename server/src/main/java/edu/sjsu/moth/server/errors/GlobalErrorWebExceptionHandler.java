package edu.sjsu.moth.server.errors;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

@CommonsLog
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {
    public GlobalErrorWebExceptionHandler(ErrorAttributes errorAttributes, WebProperties.Resources resources,
                                          ApplicationContext applicationContext, ServerCodecConfigurer configurer) {
        super(errorAttributes, resources, applicationContext);
        this.setMessageReaders(configurer.getReaders());
        this.setMessageWriters(configurer.getWriters());
    }

    /*
     * when logging the stack trace, only log stack frames from our code.
     */
    private static void filterStack(StringBuilder logBuilder, Throwable cause) {
        var stacks = cause.getStackTrace();
        for (int i = 0; i < stacks.length; i++) {
            var stack = stacks[i];
            if (stack.getClassName().startsWith("edu.sjsu") || i == 0 || i == stacks.length - 1) {
                logBuilder.append("\n        ").append(stack);
            }
        }
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {

        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    /*
     * try to concisely all errors. this is a global logger. for brevity, we only print elements of the
     * stack that are in our code. (starts with edu.sjsu)
     */
    @Override
    protected void logError(ServerRequest request, ServerResponse response, Throwable throwable) {
        var errorAttributes = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        errorAttributes.remove("timestamp"); // the log already has one
        var logBuilder = new StringBuilder("Request error: ").append(errorAttributes);
        if (throwable != null) {
            logBuilder.append(" exception: ").append(throwable.getMessage());
            var cause = throwable.getCause();
            if (cause != null) {
                logBuilder.append(" caused by ").append(cause.getMessage());
                filterStack(logBuilder, cause);
            } else {
                filterStack(logBuilder, throwable);
            }
        }
        log.warn(logBuilder.toString());
    }

    /*
     * we are going to render the error response as Json
     */
    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        var errorAttributes = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        return ServerResponse.status((Integer) errorAttributes.get("status"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(
                        Map.of("path", errorAttributes.getOrDefault("path", "missing path"), "detail",
                               errorAttributes.getOrDefault("error", "missing error"))));
    }
}
