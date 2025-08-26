package util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class EnvLoader {
    private static Properties properties = null;

    public static Properties getEnv() {
        if (properties == null) {
            properties = new Properties();
            try (FileInputStream fis = new FileInputStream(".env")) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("No se pudo cargar el archivo .env: " + e.getMessage());
            }
        }
        return properties;
    }

    public static String get(String key) {
        return getEnv().getProperty(key);
    }
}
