package edu.sjsu.moth.server;

import edu.sjsu.moth.server.util.MothCommandLine;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

@SpringBootApplication
public class Main implements ApplicationRunner {

    public static void main(String[] args) {
        System.exit(new CommandLine(new MothCommandLine()).execute(args));
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {}
}
