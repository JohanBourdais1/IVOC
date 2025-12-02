package fr.epita.assistants.ping.utils;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

@ApplicationScoped
public class Logger {
    private static final String RESET_TEXT = "\u001B[0m";
    private static final String RED_TEXT = "\u001B[31m";
    private static final String GREEN_TEXT = "\u001B[32m";

    @ConfigProperty(name = "LOG_FILE", defaultValue = "")
    String logFilePath;

    @ConfigProperty(name = "ERROR_LOG_FILE", defaultValue = "")
    String errorLogFilePath;

    private static String timestamp() {
        return new SimpleDateFormat("dd/MM/yy - HH:mm:ss")
                .format(Calendar.getInstance().getTime());
    }
    public void info(String message) {
        log(message, false);
    }
    public void error(String message) {
        log(message, true);
    }

    private void log(String message, boolean isError) {
        String color = isError ? RED_TEXT : GREEN_TEXT;
        String targetPath = isError ? errorLogFilePath : logFilePath;
        String formatted = String.format("%s [%s] %s %s", color, timestamp(), message, RESET_TEXT);

        // System.out.println(targetPath);

        if (targetPath != null && !targetPath.isBlank()) {
            try (PrintWriter out = new PrintWriter(new FileWriter(targetPath, true))) {
                out.println(formatted);
                return;
            } catch (IOException e) {
                if (isError) {
                    System.err.println(formatted);
                } else {
                    System.out.println(formatted);
                }
                return;
            }
        }

        if (isError) {
            System.err.println(formatted);
        } else {
            System.out.println(formatted);
        }
    }
}
