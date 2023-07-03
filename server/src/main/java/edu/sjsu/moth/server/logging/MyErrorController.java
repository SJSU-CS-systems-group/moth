package edu.sjsu.moth.server.logging;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;

import java.util.logging.Logger;

@Controller
public class MyErrorController implements ErrorController {
    Logger LOG = Logger.getLogger(MyErrorController.class.getName());

    @RequestMapping(value = "/error", method = { RequestMethod.GET, RequestMethod.POST })
    @ResponseBody
    public String handleError(ServerWebExchange exchange) {
        LOG.warning("Problem processing %s : %s".formatted(exchange.getRequest().getURI(),
                                                           exchange.getResponse().getStatusCode()));
        return "{\"error\": \"%s\"}".formatted(exchange.getResponse().getStatusCode());
    }

}
