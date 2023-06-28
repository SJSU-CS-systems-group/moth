package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.CustomEmoji;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Controller
public class TestAccountController {

    static private final String ADD_FORM =
            "</table><input type=\"submit\" value=\"Delete\"><br></form>" +
                    "<form action=/accounts/adding><label for=\"id\">Id:</label><br><input type=\"text\" " +
                    "name=\"id\"><br><br>" +
                    "<label for=\"id\">Username:</label><br><input type=\"text\" name=\"username\"><br><br>" +
                    "<label for=\"acct\">Account:</label><br><input type=\"text\" name=\"acct\"><br><br>" +
                    "<label for=\"url\">Url:</label><br><input type=\"text\" name=\"url\"><br><br>" +
                    //"<label for=\"note\">Note:</label><br><input type=\"text\" name=\"note\"><br><br>" +
                    //"<label for=\"display_name\">Display Name:</label><br><input type=\"text\"
                    // name=\"display_name\"><br><br>" +
                    //"<label for=\"avatar\">Avatar:</label><br><input type=\"text\" name=\"avatar\"><br><br>" +
                    //"<label for=\"avatar_static\">Avatar Static:</label><br><input type=\"text\"
                    // name=\"avatar_static\"><br><br>" +
                    //"<label for=\"header\">Header:</label><br><input type=\"text\" name=\"header\"><br><br>" +
                    //"<label for=\"header_static\">Header Static:</label><br><input type=\"text\"
                    // name=\"header_static\"><br><br>" +
                    //"<label for=\"locked\">Locked:</label><br><input type=\"text\" name=\"locked\"><br><br>" +
                    //"<label for=\"fields\">Fields:</label><br><input type=\"text\" name=\"fields\"><br><br>" +
                    //"<label for=\"emojis\">Emojis:</label><br><input type=\"text\" name=\"emojis\"><br><br>" +
                    //"<label for=\"bot\">Bot:</label><br><input type=\"text\" name=\"bot\"><br><br>" +
                    //"<label for=\"group\">Group:</label><br><input type=\"text\" name=\"group\"><br><br>" +
                    //"<label for=\"discoverable\">Discoverable:</label><br><input type=\"text\"
                    // name=\"discoverable\"><br><br>" +
                    //"<label for=\"noIndex\">No Index:</label><br><input type=\"text\" name=\"noIndex\"><br><br>" +
                    //"<label for=\"moved\">Moved:</label><br><input type=\"text\" name=\"moved\"><br><br>" +
                    //"<label for=\"suspended\">Suspended:</label><br><input type=\"text\"
                    // name=\"suspended\"><br><br>" +
                    //"<label for=\"created_at\">Created At:</label><br><input type=\"text\"
                    // name=\"created_at\"><br><br>" +
                    //"<label for=\"last_status_at\">Last Status At:</label><br><input type=\"text\"
                    // name=\"last_status_at\"><br><br>" +
                    //"<label for=\"statuses_count\">Statuses Count:</label><br><input type=\"text\"
                    // name=\"statuses_count\"><br><br>" +
                    //"<label for=\"followers_count\">Followers Count:</label><br><input type=\"text\"
                    // name=\"followers_count\"><br><br>" +
                    //"<label for=\"following_count\">Following Count:</label><br><input type=\"text\"
                    // name=\"following_count\"><br><br>" +
                    "<input type=\"submit\" value=\"Add\"><br></form>";

    static private final String ACCOUNT_ROW =
            "<tr><td><input type=\"checkbox\" name=\"del\" " + "value=\"%s\"/></td" + "><td>%s</td><td>%s</td><td>%s" +
                    "</td><td>%s" + "</td></tr>";
    @Autowired
    AccountRepository accountRepository;

    @GetMapping("/accounts")
    public Mono<ResponseEntity<String>> getAccounts() {
        return accountRepository.findAll().collect(StringBuilder::new, (sb, a) -> sb.append(
                        ACCOUNT_ROW.formatted(a.acct, a.id, a.acct, a.username, a.url)))
                .map(sb -> ResponseEntity.ok("<form action=/accounts/delete><table>" + sb.toString() + ADD_FORM));
    }

    @GetMapping("/accounts/adding")
    public Mono<ResponseEntity<String>> addAccount(@RequestParam String id, @RequestParam String username,
                                                   @RequestParam String acct,
                                                   @RequestParam String url) {
        AccountField[] af = new AccountField[1];
        CustomEmoji[] ce = new CustomEmoji[1];
        return accountRepository.save(new Account(id, username, acct, url, "", "", "", "", "", "",
                                                  false, af, ce, false, false, false, false, false,
                                                  false, false, "", "", 0, 0, 0))
                .thenReturn(ResponseEntity.ok("Added: " + id + " " + username + acct + url));
    }

    @GetMapping("/accounts/delete")
    public Mono<ResponseEntity<String>> deleteAlias(@RequestParam String del) {
        var find = accountRepository.findItemByAcct(del);
        return find.flatMap(accountRepository::delete)
                .thenReturn(ResponseEntity.ok("Deleted Account: " + del))
                .onErrorReturn(ResponseEntity.ok("Failed to delete: " + del));
    }

}
