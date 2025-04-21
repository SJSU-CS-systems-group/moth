package edu.sjsu.moth.server.service;

import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.controller.MothController;
import edu.sjsu.moth.server.db.FollowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.logging.Logger;

@Service
public class VisibilityService {
    Logger LOG = Logger.getLogger(VisibilityService.class.getName());

    @Autowired
    AccountService accountService;

    @Autowired
    FollowRepository followRepository;

    final String PUBLIC_VISIBILITY = "public";
    final String QUITE_PUBLIC = "unlisted";
    final String DIRECT_VISIBILITY = "direct";


    public Flux<Status> publicTimelinesViewable(Status status) {
        if (status.visibility.equals(PUBLIC_VISIBILITY)) return Flux.just(status);
        return Flux.empty();
    }

    public boolean homefeedViewable(Principal user, Status status) { return false; }
    public boolean profileViewable(Principal user, Status status) { return false; };
    public boolean permalinkViewable(Principal user, Status status) { return false; };
}
