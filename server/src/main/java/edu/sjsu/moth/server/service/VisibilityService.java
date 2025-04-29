package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.controller.MothController;
import edu.sjsu.moth.server.db.FollowRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.logging.Logger;

@Service
@CommonsLog
public class VisibilityService {
    final String PUBLIC_VISIBILITY = "public";
    final String QUITE_PUBLIC = "unlisted";
    final String DIRECT_VISIBILITY = "direct";


    public Flux<Status> publicTimelinesViewable(Status status) {
        if (status.visibility.equals(PUBLIC_VISIBILITY)) return Flux.just(status);
        return Flux.empty();
    }

}
