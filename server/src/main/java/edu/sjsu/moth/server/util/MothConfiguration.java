package edu.sjsu.moth.server.util;

import edu.sjsu.moth.server.controller.InstanceController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MothConfiguration {
    private static final List<RequiredProperty> REQUIRED_PROPERTY_LIST = List.of(
            new RequiredProperty("server.port", "server.port is the port to listen to on."),
            new RequiredProperty("server.name", "server.name is the host name of the server."),
            new RequiredProperty("db", "address of mongodb server"), new RequiredProperty("account", "account of user"),
            new RequiredProperty("contact.email", "email of contact account"));

    public static MothConfiguration mothConfiguration;
    public final Properties properties = new Properties();

    public MothConfiguration(File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {

            properties.load(fileInputStream);

            REQUIRED_PROPERTY_LIST.forEach(rp -> {
                if (properties.getProperty(rp.name) == null) {
                    System.out.println("Missing property: " + rp.name + ". Description: " + rp.description);
                    System.exit(1);
                }
            });
            mothConfiguration = this;
        }
    }

    private static String getHost(String hostPort) {
        if (hostPort == null) return null;
        int lastColon = hostPort.lastIndexOf(':');
        return hostPort.substring(0, lastColon);
    }

    private static int getPort(String hostPort) {
        if (hostPort == null) return -1;
        int lastColon = hostPort.lastIndexOf(':');
        return Integer.parseInt(hostPort.substring(lastColon + 1));
    }

    public InstanceController.Rule[] getRules() {
        ArrayList<InstanceController.Rule> rules = new ArrayList<>();
        String r = "instance.rule.";
        String index = "1";

        while (properties.getProperty(r + index) != null) {
            rules.add(new InstanceController.Rule(index, properties.getProperty(r + index)));
            index = Integer.toString(Integer.parseInt(index) + 1);
        }

        if (rules.isEmpty()) {
            rules.add(new InstanceController.Rule("1", "Be yourself and have fun!"));
        }

        return rules.toArray(new InstanceController.Rule[0]);
    }

    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port"));
    }

    public String getServerName() {
        return properties.getProperty("server.name");
    }

    public String getDBServer() {
        return properties.getProperty("db");
    }

    public String getAccountName() {
        return properties.getProperty("account");
    }

    public String getContactEmail() {return properties.getProperty("contact.email");}

    public int getSMTPLocalPort() {return Integer.parseInt(properties.getProperty("smtp.localPort", "-1"));}

    public String getSMTPServerHost() {return getHost(properties.getProperty("smtp.server"));}

    public int getSMTPServerPort() {return getPort(properties.getProperty("smtp.server"));}

    record RequiredProperty(String name, String description) {}
}
