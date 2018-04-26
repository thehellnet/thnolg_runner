package org.thehellnet.onlinegaming.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thehellnet.onlinegaming.runner.binary.ServerRunner;

import java.io.File;

public class ThnOlgRunner {

    private static final Logger logger = LoggerFactory.getLogger(ThnOlgRunner.class);

    private Process process;

    private Thread stdoutThread;
    private Thread stderrThread;

    public static void main(String[] args) {
        ThnOlgRunner thnOlgRunner = new ThnOlgRunner();

        try {
            logger.info("START");
            thnOlgRunner.run(args);
            logger.info("END");
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private void run(String[] args) throws Exception {
        String[] command = new String[]{"/opt/cod4_lnxded/cod4_lnxded"};
        File workDir = new File("/opt/cod4_lnxded");

        ServerRunner serverRunner = new ServerRunner(command, workDir, logger::info);
        serverRunner.start();
        serverRunner.join();
    }
}
