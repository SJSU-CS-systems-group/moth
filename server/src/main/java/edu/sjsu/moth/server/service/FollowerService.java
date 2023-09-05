package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.db.Followers;
import edu.sjsu.moth.server.db.FollowersRepository;
import edu.sjsu.moth.server.db.Following;
import edu.sjsu.moth.server.db.FollowingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class FollowerService {

    @Autowired
    FollowersRepository followersRepository;

    @Autowired
    FollowingRepository followingRepository;

    public Mono<Following> getFollowingById(String id) {
        return followingRepository.findById(id);
    }

    public Mono<Followers> getFollowersById(String id) {
        return followersRepository.findById(id);
    }

    public Mono<Followers> saveFollowers(Followers followers) {
        return followersRepository.save(followers);
    }

}