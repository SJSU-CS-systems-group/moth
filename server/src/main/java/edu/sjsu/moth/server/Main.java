package edu.sjsu.moth.server;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;

@SpringBootApplication
public class Main implements ApplicationRunner {

    public static void main(String[] args) {
        try {
            Configuration config;
            if (args.length != 1) {
                System.out.println("Only one argument allowed: Configuration File.");
                System.exit(1);
            }
            config = new Configuration(args[0]);
            HashMap<String, Object> defaults = new HashMap<String, Object>();
            defaults.put("server.port", config.getServerPort());
            defaults.put("server.name", config.getServerName());
            defaults.put("spring.data.mongodb.host", config.getDBServer());

            SpringApplication moth = new SpringApplication(Main.class);
            moth.setDefaultProperties(defaults);
            moth.run(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

    }
}
