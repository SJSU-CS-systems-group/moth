package edu.sjsu.moth.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import edu.sjsu.moth.util.WebFingerUtils;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static edu.sjsu.moth.util.EmailCodeUtils.encodePassword;
import static edu.sjsu.moth.util.EmailCodeUtils.normalizeEmail;

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

        private static Properties loadProperties(File cfg) {
            var props = new Properties();
            try (var is = new FileInputStream(cfg)) {
                props.load(is);
            } catch (IOException e) {
                System.out.printf("could not load properties from %s\n", cfg);
                System.exit(2);
            }
            if (!props.containsKey("db")) {
                System.out.printf("db key missing from %s\n", cfg);
                System.exit(2);
            }
            return props;
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

        @Command(description = "list registered emails", mixinStandardHelpOptions = true)
        int listEmails(@CommandLine.Parameters(paramLabel = "mothConfigFile") File cfg) {
            var props = loadProperties(cfg);

            getEmailRegistrationCollection(props).find().forEach(d -> System.out.println(d.entrySet()));
            return 0;
        }

        private MongoCollection<Document> getEmailRegistrationCollection(Properties props) {
            return MongoClients.create("mongodb://%s:27017/".formatted(props.getProperty("db")))
                    .getDatabase("test")
                    .getCollection("emailregistration");
        }

        @Command(description = "delete the record for an email", mixinStandardHelpOptions = true)
        int deleteEmail(@CommandLine.Parameters(paramLabel = "mothConfigFile") File cfg,
                        @CommandLine.Parameters(paramLabel = "email") String email, @CommandLine.Option(names = "--no"
                + "-dryrun", defaultValue = "False") boolean noDryrun) {
            var props = loadProperties(cfg);
            var regDb = getEmailRegistrationCollection(props);
            var normalizeEmail = normalizeEmail(email);
            if (noDryrun) {
                var rc = regDb.deleteOne(Filters.eq("_id", normalizeEmail));
                if (rc.wasAcknowledged()) System.out.printf("%s deleted\n", normalizeEmail);
                else {
                    System.out.printf("problem deleting %s\n", normalizeEmail);
                    System.exit(2);
                }
            } else {
                var rec = regDb.find(Filters.eq("_id", normalizeEmail)).first();
                if (rec == null) {
                    System.out.printf("%s does not exist\n", normalizeEmail);
                } else {
                    System.out.printf("would delete %s\n", normalizeEmail);
                }
            }
            return 0;
        }

        @Command(description = "add or update the mapping of an email", mixinStandardHelpOptions = true)
        int updateEmail(@CommandLine.Parameters(paramLabel = "mothConfigFile") File cfg,
                        @CommandLine.Parameters(paramLabel = "email") String email, @CommandLine.Option(names = "-u",
                paramLabel = "username", description = "set username") String username,
                        @CommandLine.Parameters(paramLabel = "password") String password, @CommandLine.Option(names =
                "--no-dryrun", defaultValue = "False") boolean noDryrun) {
            var props = loadProperties(cfg);

            var regDb = getEmailRegistrationCollection(props);
            var normalizeEmail = normalizeEmail(email);
            var rec = regDb.find(Filters.eq("_id", normalizeEmail)).first();
            boolean updating = false;
            if (rec == null) {
                System.out.printf("%s is not in the database, will add\n", normalizeEmail);
            } else {
                updating = true;
                System.out.printf("found %s in the database, will update\n", rec.entrySet());
            }
            var newRec = new Document().append("_id", normalizeEmail(email))
                    .append("email", email)
                    .append("saltedPassword", encodePassword(password));
            if (username != null) {
                var prevRec = regDb.find(Filters.eq("username", username)).first();
                if (prevRec != null && !prevRec.get("_id").equals(normalizeEmail)) {
                    System.out.printf("%s is already assigned to %s\n", username, prevRec.get("_id"));
                    System.exit(2);
                }
                newRec.append("username", username);
            }
            if (noDryrun) {
                if (updating) {
                    var rc = regDb.replaceOne(rec, newRec);
                    if (rc.wasAcknowledged()) {
                        System.out.printf("%s updated\n", newRec);
                    } else {
                        System.out.printf("could not update %s\n", newRec);
                        System.exit(2);
                    }
                } else {
                    var rc = regDb.insertOne(newRec);
                    if (rc.wasAcknowledged()) {
                        System.out.printf("%s inserted\n", newRec);
                    } else {
                        System.out.printf("could not insert %s\n", newRec);
                        System.exit(2);
                    }
                }
            } else {
                System.out.printf("would have %s %s\n", updating ? "updated" : "inserted", newRec);
            }
            return 0;
        }
    }
}
