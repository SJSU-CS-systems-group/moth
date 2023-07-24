package edu.sjsu.moth.server;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClients;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.reactivestreams.Subscriber;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.FileInputStream;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Command(name = "moth-server", mixinStandardHelpOptions = true)
public class MothCommandLine implements Runnable {
    @Parameters(index = "0", description = "Config file")
    private File configFile;

    @CommandLine.Option(names = {"-v","--verify"} ,description = "verify")
    private boolean verification;

    public final Properties properties = new Properties();

    String connectionString = "mongodb://localhost:279";

    @Parameters(index = "1..*", description = "extra spring arguments")
    private String[] springArgs;

    public static void main(String[] args) {
        var rc = new CommandLine(new MothCommandLine()).execute(args);
        // SpringApplication.run() will return when the application
        // starts up, so even though execute returns, that doesn't mean
        // that the application is done. thus, we should only exit if
        // we get a non-zero return code.
        if (rc != 0) System.exit(rc);
    }

    public void run() {
        final var prefix = "spring.";
        try {
            MothConfiguration config;
            config = new MothConfiguration(configFile);
            if(verification){
                File f = new File(configFile.toURI());
                if(f.isFile()) {
                    FileInputStream fileInputStream = new FileInputStream(f);
                    properties.load(fileInputStream);
                    ConnectionString connString = new ConnectionString(
                            "mongodb://" + config.getDBServer() + "/test?w=majority");
                    MongoClientSettings settings = MongoClientSettings.builder()
                            .applyConnectionString(connString)
                            .retryWrites(true)
                            .build();
                    var latch = new CountDownLatch(1);
                    MongoClients.create(settings).listDatabaseNames().subscribe(new Subscriber<String>() {
                        @Override
                        public void onSubscribe(org.reactivestreams.Subscription subscription) {
                            subscription.request(1);
                        }

                        @Override
                        public void onNext(String s) {
                            // all good, we are getting stuff!
                            latch.countDown();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            // Handle error (optional)
                        }

                        @Override
                        public void onComplete() {
                            // all good, got everything
                            latch.countDown();
                        }
                    });

                    if (latch.await(1, TimeUnit.SECONDS)) {
                        System.out.println("good");
                        System.exit(1);
                    } else {
                        System.out.println("couldn't contact the database in 1 second");
                        System.exit(0);
                    }
                    try {
                        //  Block of code to try
                        InetAddress.getByName(config.getServerName());
                        System.out.println("VERIFIED");
                        System.exit(0);
                    } catch (UnknownHostException e) {
                        //  Block of code to handle errors
                        System.out.println("error " + e.getMessage());
                        System.exit(1);
                    }

                } else {
                    System.out.println("File not found, Please input proper configuration file");
                    System.exit(1);
                }
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
}


