package edu.sjsu.moth.server.logging;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.logging.Logger;

@Controller
public class MyErrorController implements ErrorController {
    Logger LOG = Logger.getLogger(MyErrorController.class.getName());

    @RequestMapping(value = "/error", method = { RequestMethod.GET, RequestMethod.POST })
    @ResponseBody
    public String handleError(HttpServletRequest request) {
        LOG.warning("Problem processing " +
                            request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) + ": " +
                            request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        return "{\"error\": \"%s\"}".formatted(request.getAttribute(RequestDispatcher.ERROR_MESSAGE));
    }

}
