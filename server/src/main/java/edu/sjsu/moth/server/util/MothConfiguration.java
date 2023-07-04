package edu.sjsu.moth.server.util;

import edu.sjsu.moth.server.controller.InstanceController;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MothConfiguration {
    private static final List<RequriedProperty> requriedPropertyList = List.of(
            new RequriedProperty("server.port", "server.port is the port to listen to on."),
            new RequriedProperty("server.name", "server.name is the host name of the server."),
            new RequriedProperty("db", "address of mongodb server"),
            new RequriedProperty("account", "account of user"));
    public static MothConfiguration mothConfiguration;
    public final Properties properties = new Properties();

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


    public InstanceController.Rule[] getRules(){
        ArrayList<InstanceController.Rule> rules = new ArrayList<InstanceController.Rule>();
        rules.add(new InstanceController.Rule("1", "Be kind and excellent to each other."));
        rules.add(new InstanceController.Rule("2", "Please be mindful of the content you share to protect your personal information."));
        rules.add(new InstanceController.Rule("3", "Avoid using offensive or vulgar language. Please be mindful of your language when engaging in discussions or commenting on posts."));
        rules.add(new InstanceController.Rule("4", "Be yourself and have fun!"));
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

    record RequriedProperty(String name, String description) {}
}
