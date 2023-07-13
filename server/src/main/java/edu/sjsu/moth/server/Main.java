package edu.sjsu.moth.server;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import picocli.CommandLine;

@SpringBootApplication
@ComponentScan(basePackages = "edu.sjsu.moth.controllers")
public class Main implements ApplicationRunner {

    private static final MothCommandLine mothCli = new MothCommandLine();

    public static void main(String[] args) {
        System.exit(new CommandLine(mothCli).execute(args));
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        mothCli.awaitTermination();
    }
}
