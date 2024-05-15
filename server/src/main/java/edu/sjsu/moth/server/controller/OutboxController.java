package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.beans.support.PagedListHolder.DEFAULT_PAGE_SIZE;

@CommonsLog
@RestController
public class OutboxController {

    ObjectMapper mappedLoad;

    public OutboxController(ObjectMapper mappedLoad) {
        this.mappedLoad = mappedLoad;
    }

    @GetMapping("/users/{id}/outbox")
    public OutboxSenderResponse fetchOutboxLink(
            @PathVariable String id, @RequestParam(required = false) Integer limit) {

        /**
         * JSON:
         * {"@context":"https://www.w3.org/ns/activitystreams",
         * "id":"https://shirley.homeofcode.com/users/shirley/outbox",
         * "type":"OrderedCollection",
         * "totalItems":3,
         * "first":"https://shirley.homeofcode.com/users/shirley/outbox?page=true",
         * "last":"https://shirley.homeofcode.com/users/shirley/outbox?min_id=0\u0026page=true"}
         */
        String returnID = MothController.BASE_URL + "/users/" + id + "outbox";
        int pageSize = limit != null ? limit : DEFAULT_PAGE_SIZE;
        return new OutboxSenderResponse(returnID, "OrderedCollection", 3, returnID + "?page=1",
                                        returnID + "?page=" + pageSize);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "@context", "id", "type", "totalItems", "first", "last" })
    public record OutboxSenderResponse(String id, String type, int totalItems, String first, String last) {
        @JsonProperty("@context")
        public String getContext() {
            return "https://www.w3.org/ns/activitystreams";
        }
    }
}
