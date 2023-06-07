package edu.sjsu.moth.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.MessageFormat;
import java.util.regex.Pattern;

@EnableMongoRepositories
@RestController
public class MothController {

    @Autowired
    WebfingerRepository webfingerRepo;

    @GetMapping("/")
    public String index() {
        return "hello";
    }

    @GetMapping("/.alias")
    public String alias(@RequestParam String alias, @RequestParam String user, @RequestParam String host) {
        webfingerRepo.save(new WebfingerAlias(alias, user, host));
        return "added";
    }

    @GetMapping("/.well-known/webfinger")
    public String webfinger(@RequestParam String resource) {
        var pattern = Pattern.compile("acct:([^@]+)@(.+)");
        var match = (pattern.matcher(resource));
        if (match.find()) {
            var user = match.group(1);
            var foundUser = webfingerRepo.findItemByName(user);
            if (foundUser != null) {
                System.out.println(foundUser);
                var host = match.group(2);
                var result = MessageFormat.format("""
                        '{'
                          "subject": "acct:{0}@{1}",
                          "aliases": [
                            "https://{3}/@{2}",
                            "https://{3}/users/{2}"
                          ],
                          "links": [
                            '{'
                              "rel": "http://webfinger.net/rel/profile-page",
                              "type": "text/html",
                              "href": "https://{3}/@{2}"
                            '}',
                            '{'
                              "rel": "self",
                              "type": "application/activity+json",
                              "href": "https://{3}/users/{2}"
                            '}'
                          ]
                        '}'""", user, host, foundUser.user, foundUser.host);
                System.out.println(result);
                return result;
            }
        }
        return "";
    }

}
