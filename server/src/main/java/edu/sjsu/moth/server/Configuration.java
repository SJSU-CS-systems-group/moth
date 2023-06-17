package edu.sjsu.moth.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

public class Configuration {
    private final Properties properties = new Properties();
    //Logger LOG = Logger.getLogger(Configuration.class.getName());

    public Configuration(String file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            properties.load(fileInputStream);
        }
    }

    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port"));
    }

    public String getServerName() {
        return properties.getProperty("server.name");
    }

    public String getDBServer() {
        return properties.getProperty("db.name");
    }
}
