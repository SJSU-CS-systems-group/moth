package edu.sjsu.moth.server;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

@SpringBootApplication
public class Main implements ApplicationRunner {

    private static MothCommandLine mothCli;

    public static void main(String[] args) {
        mothCli = new MothCommandLine();
        System.exit(new CommandLine(mothCli).execute(args));
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        mothCli.awaitTermination();
    }
}
