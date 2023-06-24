package edu.sjsu.moth.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.stream.Collectors;

@Controller
public class TestAliasController {

    @Autowired
    WebfingerRepository webs;

    @GetMapping("/aliases")
    public ResponseEntity<String> getAliases() {
        return new ResponseEntity<>("<table>" + webs.findAll().stream().map(
                a -> "<form action=/aliases/delete><tr><td><input type=\"checkbox\" name=\"del\" value=\"" + a.alias + "\"></td><td>" + a.alias + "</td><td>" + a.user + "</td><td>" + a.host + "</td></tr>").collect(
                Collectors.joining(
                        "")) + "</table>" + "<input type=\"submit\" value=\"Delete\"></form><form " +
                                            "action=/aliases/adding><label for=\"alias\">Alias:</label><br><input " +
                                            "type=\"text\" name=\"alias\"><br><br>" +
                                            "<label for=\"user\">User:</label><br><input type=\"text\" " +
                                            "name=\"user\"><br><br>" +
                                            "<label for=\"host\">Host:</label><br><input type=\"text\" " +
                                            "name=\"host\"><br><br>  <input type=\"submit\" value=\"Add\"><br>" +
                                            "</form><br>",
                                    HttpStatus.OK);
    }

    @GetMapping("/aliases/adding")
    public ResponseEntity<String> addAliases(@RequestParam String alias, @RequestParam String user,
                                             @RequestParam String host) {
        webs.save(new WebfingerAlias(alias, user, host));
        return new ResponseEntity<>("Added: " + alias + " " + user + " " + host, HttpStatus.OK);
    }

    @GetMapping("/aliases/delete")
    public ResponseEntity<String> deleteAlias(@RequestParam String del) {
        webs.delete(webs.findItemByName(del));
        return new ResponseEntity<>("Delete: " + del, HttpStatus.OK);
    }
}
