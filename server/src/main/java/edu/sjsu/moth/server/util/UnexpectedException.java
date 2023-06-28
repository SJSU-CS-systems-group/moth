package edu.sjsu.moth.server.util;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.text.MessageFormat;
import java.util.logging.Logger;

/**
 * this class will catch all unhandled exceptions, log them, and return an error to the client.
 */
@ControllerAdvice
public class UnexpectedException {
    final static private Logger LOG = Logger.getLogger(UnexpectedException.class.getName());

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        var stack = exception.getStackTrace();
        var sb = new StringBuilder();
        for (var i = 0; i < 5 && i < stack.length; i++) {
            sb.append(stack[i]).append("\n");
        }
        LOG.severe(MessageFormat.format("Unhandled exception: {0} {1}\n{2}", request.getDescription(true),
                                        exception.getMessage(), sb.toString()));
        var message = exception.getMessage();
        if (message == null) message = exception.toString();
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
                .body(MessageFormat.format("'{'\"error\":\"{0}\"'}'", message));
    }
}