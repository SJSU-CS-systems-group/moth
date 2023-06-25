package edu.sjsu.moth.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class MothConfiguration {
    private static final List<RequriedProperty> requriedPropertyList = List.of(
            new RequriedProperty("server.port", "server.port is the port to listen to on."),
            new RequriedProperty("server.name", "server.name is the host name of the server."),
            new RequriedProperty("db", "address of mongodb server"));
    public final Properties properties = new Properties();

    public static MothConfiguration mothConfiguration;

    public MothConfiguration(String file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {

            properties.load(fileInputStream);

            requriedPropertyList.forEach(rp -> {
                if (properties.getProperty(rp.name) == null) {
                    System.out.println("Missing property: " + rp.name + ". Description: " + rp.description);
                    System.exit(1);
                }
            });
            mothConfiguration = this;
        }
    }

    public int getServerPort() {return Integer.parseInt(properties.getProperty("server.port"));}

    public String getServerName() {
        return properties.getProperty("server.name");
    }

    public String getDBServer() {
        return properties.getProperty("db");
    }

    record RequriedProperty(String name, String description) {}
}
