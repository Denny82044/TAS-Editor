package io.github.jadefalke2;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {

    private static final String BUILD_TIME;

    static {
        Properties properties = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/build.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignored) {
        }

        String value = properties.getProperty("build.timestamp", "Unknown").trim();
        BUILD_TIME = value.isEmpty() ? "Unknown" : value;
    }

    private BuildInfo() {
    }

    public static String getDisplayText() {
        return "Built on " + BUILD_TIME;
    }
}
