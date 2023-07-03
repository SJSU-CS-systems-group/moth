package edu.sjsu.moth.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.util.WebFingerUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.Arrays;

@SpringBootApplication
public class Main implements CommandLineRunner, ExitCodeGenerator {
    private int exitCode;

    public static void main(String[] args) {
        var app = new SpringApplication(Main.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(new Cli()).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    record UserHost(String user, String host) {}

    @Command(name = "moth-client", description = "utilities to help admin a moth server.")
    static private class Cli {
        private static UserHost extractUserHost(String userAtHost) {
            if (userAtHost.startsWith("@")) userAtHost = userAtHost.substring(1);
            var parts = userAtHost.split("@", 2);
            var userhost = new UserHost(parts[0], parts[1]);
            System.out.println(Arrays.toString(parts));
            return userhost;
        }

        @Command(description = "webfinger a user@host", mixinStandardHelpOptions = true)
        int webfinger(@CommandLine.Parameters(paramLabel = "user@host") String userAtHost) {
            UserHost userHost = extractUserHost(userAtHost);
            System.out.println(WebFingerUtils.finger(userHost.user, userHost.host).block());
            return 0;
        }

        @Command(description = "find account for user@host", mixinStandardHelpOptions = true)
        int resolve(@CommandLine.Parameters(paramLabel = "user@host") String userAtHost) {
            var userHost = extractUserHost(userAtHost);
            var finger = WebFingerUtils.finger(userHost.user, userHost.host).block();
            for (var link : finger.links()) {
                if (link.rel() == WebFingerUtils.RelType.SELF) {
                    var result = WebFingerUtils.resolve(link.href()).block();
                    try {
                        System.out.println(
                                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result.json()));
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
            return 0;
        }
    }
}
