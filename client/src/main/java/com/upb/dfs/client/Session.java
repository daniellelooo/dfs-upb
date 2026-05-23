package com.upb.dfs.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class Session {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Path sessionFile() {
        String home = System.getenv("DFS_HOME");
        Path base;
        if (home != null && !home.isBlank()) {
            base = Paths.get(home);
        } else {
            base = Paths.get(System.getProperty("user.home"), ".dfs");
        }
        return base.resolve("session.json");
    }

    public static Map<String, Object> load() {
        Path f = sessionFile();
        if (!Files.exists(f)) return new HashMap<>();
        try {
            return MAPPER.readValue(Files.readAllBytes(f), Map.class);
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    public static void save(Map<String, Object> data) throws IOException {
        Path f = sessionFile();
        Files.createDirectories(f.getParent());
        Files.write(f, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(data));
    }

    public static void clear() throws IOException {
        Path f = sessionFile();
        Files.deleteIfExists(f);
    }
}
