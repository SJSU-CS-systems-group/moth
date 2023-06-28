package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.server.db.UserPassword;
import edu.sjsu.moth.server.db.UserPasswordRepository;
import edu.sjsu.moth.server.db.WebfingerAlias;
import edu.sjsu.moth.server.db.WebfingerRepository;
import edu.sjsu.moth.server.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Controller
public class TestAliasController {

    static private final String ADD_FORM = """
            </table><input type=\"submit\" value=\"Delete\"><br></form><form action=/aliases/adding>
            <label for=\"alias\">Alias:</label><br><input type=\"text\" name=\"alias\"><br><br>
            <label for=\"user\">User:</label><br><input type=\"text\" name=\"user\"><br><br>
            <label for=\"host\">Host:</label><br><input type=\"text\" name=\"host\"><br><br>
            <input type=\"submit\" value=\"Add\"><br>
            </form><br>
            """;
    static private final String ALIAS_ROW =
            "<tr><td><input type=\"checkbox\" name=\"del\" " + "value=\"%s\"/></td" + "><td>%s</td><td>%s</td><td>%s" + "</td></tr>";
    @Autowired
    WebfingerRepository webfingerRepository;
    @Autowired
    UserPasswordRepository userPasswordRepository;

    @GetMapping("/aliases")
    public Mono<ResponseEntity<String>> getAliases() {
        return webfingerRepository.findAll().collect(StringBuilder::new, (sb, a) -> sb.append(
                ALIAS_ROW.formatted(a.alias, a.alias, a.user, a.host)))
                .map(sb -> ResponseEntity.ok("<form action=/aliases/delete><table>" + sb.toString() + ADD_FORM));
    }

    @GetMapping("/.alias")
    public Mono<String> alias(@RequestParam String alias, @RequestParam String user, @RequestParam String host) {
        return webfingerRepository.save(new WebfingerAlias(alias, user, host)).thenReturn("added");
    }

    @GetMapping("/aliases/adding")
    public Mono<ResponseEntity<String>> addAliases(@RequestParam String alias, @RequestParam String user,
                                                   @RequestParam String host) {
        return webfingerRepository.save(new WebfingerAlias(alias, user, host))
                .thenReturn(ResponseEntity.ok("Added: " + alias + " " + user + " " + host));
    }

    @GetMapping("/aliases/delete")
    public Mono<ResponseEntity<String>> deleteAlias(@RequestParam String del) {
        var find = webfingerRepository.findItemByName(del);
        return find.flatMap(webfingerRepository::delete)
                .thenReturn(ResponseEntity.ok("Delete: " + del))
                .onErrorReturn(ResponseEntity.ok("Failed to delete: " + del));
    }

    @GetMapping("/passwd/set")
    public Mono<ResponseEntity<String>> setPassword(@RequestParam String user, @RequestParam String password) {
        return userPasswordRepository.save(new UserPassword(user, Util.encodePassword(password)))
                .thenReturn(ResponseEntity.ok("Set password for " + user));
    }
}
