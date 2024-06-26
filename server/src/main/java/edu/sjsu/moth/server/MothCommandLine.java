package edu.sjsu.moth.server;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Command(name = "moth-server", mixinStandardHelpOptions = true)
public class MothCommandLine implements Runnable {
    @Parameters(index = "0", description = "Config file")
    private File configFile;
    @CommandLine.Option(names = { "-v", "--verify" }, description = "verify")
    private boolean verification;
    public final Properties properties = new Properties();
    @Parameters(index = "1..*", description = "extra spring arguments")
    private String[] springArgs;

    public void run() {
        final var prefix = "spring.";
        try {
            MothConfiguration config;
            config = new MothConfiguration(configFile);
            if (verification) {
                ConnectionString connString =
                        new ConnectionString("mongodb://" + config.getDBServer() + "/test?w=majority");
                MongoClientSettings settings =
                        MongoClientSettings.builder().applyConnectionString(connString).retryWrites(true).build();
                var latch2 = new CountDownLatch(1);
                MongoClient mongoClient1 = MongoClients.create(settings);
                mongoClient1.listDatabaseNames().subscribe(new Subscriber<String>() {
                    @Override
                    public void onSubscribe(org.reactivestreams.Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(String s) {
                        // all good, we are getting stuff!
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        // Handle error (optional)
                    }

                    @Override
                    public void onComplete() {
                        // all good, got everything
                        latch2.countDown();
                    }
                });

                if (latch2.await(1, TimeUnit.SECONDS)) {
                    System.out.println("verified connection to " + config.getDBServer());
                    MongoDatabase database = mongoClient1.getDatabase("test");
                    MongoCollection<Document> collection = database.getCollection("account");
                    String accountName =
                            config.getAccountName(); // Replace with the actual account name from the config file
                    Document query = new Document("acct", accountName);
                    FindPublisher<Document> documents = collection.find(query);
                    var latch = new CountDownLatch(1);
                    documents.subscribe(new Subscriber<Document>() {
                        @Override
                        public void onSubscribe(Subscription subscription) {
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(Document document) {
                            //System.out.println(document.toJson());
                            if (document != null) {
                                System.out.println("%s exists in the collection ".formatted(accountName));
                            } else {
                                System.out.println("Account does not exist in the collection: ");
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            System.out.println("error fetching account: " + throwable.getMessage());
                        }

                        @Override
                        public void onComplete() {
                            latch.countDown();
                        }
                    });
                    //Document document = new Document();
                    latch.await();
                } else {
                    System.out.println("couldn't contact the database in 1 second");
                    System.exit(0);
                }

                //  Block of code to try
                InetAddress.getByName(config.getServerName());
                System.out.println("Verified " + config.getServerName());
                System.exit(0);

            }
            HashMap<String, Object> defaults = new HashMap<String, Object>();
            defaults.put("server.port", config.getServerPort());
            defaults.put("server.name", config.getServerName());
            defaults.put("spring.data.mongodb.host", config.getDBServer());
            // add all the properties that start with "spring." into the defaults
            config.properties.entrySet().stream().filter(e -> e.getKey().toString().startsWith(prefix)).forEach(e -> {
                defaults.put(e.getKey().toString().substring(prefix.length()), e.getValue());
            });
            SpringApplication moth = new SpringApplication(MothServerMain.class);
            moth.setDefaultProperties(defaults);
            moth.setWebApplicationType(WebApplicationType.REACTIVE);
            moth.run(springArgs == null ? new String[0] : springArgs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        var rc = new CommandLine(new MothCommandLine()).execute(args);
        // SpringApplication.run() will return when the application
        // starts up, so even though execute returns, that doesn't mean
        // that the application is done. thus, we should only exit if
        // we get a non-zero return code.
        if (rc != 0) System.exit(rc);
    }
}

