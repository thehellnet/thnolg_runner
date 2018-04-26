package org.thehellnet.onlinegaming.runner.binary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class ServerRunner {

    private static final Logger logger = LoggerFactory.getLogger(ServerRunner.class);

    private final Object SYNC = new Object();
    private final Object SYNC_INTERNAL = new Object();

    private String[] command;
    private File workDir;

    private ServerRunnerCallback callback;

    private Process process;

    private boolean isStopped;

    private Thread governorThread;
    private Thread stdoutThread;
    private Thread stderrThread;

    private BufferedReader stdoutBufferedReader;
    private BufferedReader stderrBufferedReader;
    private PrintWriter stdinPrintWriter;

    public ServerRunner(String[] command, File workDir, ServerRunnerCallback callback) {
        this.command = command;
        this.workDir = workDir;
        this.callback = callback;
    }

    public void start() {
        synchronized (SYNC) {
            if (governorThread != null) {
                return;
            }

            isStopped = false;

            governorThread = new Thread(() -> {
                while (!isStopped) {
                    startProcess();

                    try {
                        process.waitFor();
                    } catch (InterruptedException e) {
                        logger.warn(e.getMessage());
                    }

                    stopProcess();
                }
            });
            governorThread.start();
        }
    }

    public void join() {
        if (governorThread == null) {
            return;
        }

        try {
            governorThread.join();
        } catch (InterruptedException e) {
            logger.warn(e.getMessage());
        }
    }

    public void stop() {
        synchronized (SYNC) {
            if (governorThread == null) {
                return;
            }

            isStopped = true;
            governorThread.interrupt();

            try {
                governorThread.join();
            } catch (InterruptedException ignored) {
            }

            stopProcess();

            governorThread = null;
        }
    }

    public void sendCmd(String cmd) {
        synchronized (SYNC) {
            if (stdinPrintWriter == null) {
                return;
            }

            stdinPrintWriter.println(cmd);
            stdinPrintWriter.flush();
        }
    }

    private void startProcess() {
        synchronized (SYNC_INTERNAL) {
            if (process != null) {
                return;
            }

            String[] envp = new String[]{};

            try {
                process = Runtime.getRuntime().exec(command, envp, workDir);
            } catch (IOException e) {
                logger.error(e.getMessage());
                return;
            }

            InputStream stdoutInputStream = process.getInputStream();
            InputStream stderrInputStream = process.getErrorStream();
            OutputStream stdinOutputStream = process.getOutputStream();

            InputStreamReader stdoutInputStreamReader = new InputStreamReader(stdoutInputStream);
            InputStreamReader stderrInputStreamReader = new InputStreamReader(stderrInputStream);
            OutputStreamWriter stdinOutputStreamWriter = new OutputStreamWriter(stdinOutputStream);

            stdoutBufferedReader = new BufferedReader(stdoutInputStreamReader);
            stderrBufferedReader = new BufferedReader(stderrInputStreamReader);
            stdinPrintWriter = new PrintWriter(stdinOutputStreamWriter);

            stdoutThread = generateReadLineThread(stdoutBufferedReader);
            stderrThread = generateReadLineThread(stderrBufferedReader);

            stdoutThread.setDaemon(true);
            stderrThread.setDaemon(true);

            stdoutThread.start();
            stderrThread.start();
        }
    }

    private void stopProcess() {
        synchronized (SYNC_INTERNAL) {
            process.destroy();

            try {
                stdoutBufferedReader.close();
                stderrBufferedReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            stdinPrintWriter.close();

            stdoutThread.interrupt();
            stderrThread.interrupt();

            try {
                stdoutThread.join();
                stderrThread.join();
            } catch (InterruptedException ignored) {
            }

            process = null;

            stdoutBufferedReader = null;
            stderrBufferedReader = null;
            stdinPrintWriter = null;

            stdoutThread = null;
            stderrThread = null;
        }
    }

    private static Thread generateReadLineThread(BufferedReader bufferedReader) {
        Thread thread = new Thread(generateReadLineRunnable(bufferedReader));
        thread.setDaemon(true);
        return thread;
    }

    private static Runnable generateReadLineRunnable(BufferedReader bufferedReader) {
        return () -> {
            try {
                while (true) {
                    String line = bufferedReader.readLine();
                    if (line == null) {
                        break;
                    }
                    logger.info(String.format("New rawLine: %s", line));
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        };
    }
}
