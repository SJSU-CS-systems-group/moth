package edu.sjsu.moth.util;

import edu.sjsu.moth.controllers.InstanceControllerTest;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;

public class MothTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            var fullName = InstanceControllerTest.class.getResource("/test.cfg").getFile();
            System.out.println(fullName);
            MothConfiguration mothConfiguration = new MothConfiguration(new File(fullName));
            applicationContext.getBeanFactory().registerSingleton("mothConfiguration", mothConfiguration);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }
}
