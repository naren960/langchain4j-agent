package com.poc.agent.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileWriterService {

    public String writeJavaFile(String outputDir, String basePackage,
                                String layer, String className, String content) throws IOException {

        String packagePath = basePackage.replace(".", "/") + "/" + layer;
        Path dir = Paths.get(outputDir, "src/main/java", packagePath);
        Files.createDirectories(dir);

        Path file = dir.resolve(className + ".java");
        Files.writeString(file, sanitize(content));
        return file.toString();
    }

    public String writeTestFile(String outputDir, String basePackage,
                                String className, String content) throws IOException {

        String packagePath = basePackage.replace(".", "/");
        Path dir = Paths.get(outputDir, "src/test/java", packagePath);
        Files.createDirectories(dir);

        Path file = dir.resolve(className + ".java");
        Files.writeString(file, sanitize(content));
        return file.toString();
    }

    /**
     * Strips markdown fences and any trailing non-Java text
     * after the last closing brace of the class.
     */
    private String sanitize(String raw) {
        // Step 1 — strip markdown backtick fences
        String stripped = raw
                .replaceAll("```java\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        // Step 2 — strip anything after the last closing brace
        int lastBrace = stripped.lastIndexOf("}");
        if (lastBrace != -1) {
            stripped = stripped.substring(0, lastBrace + 1).trim();
        }

        // Step 3 — ensure package declaration is the first line
        if (!stripped.startsWith("package")) {
            int packageIndex = stripped.indexOf("package");
            if (packageIndex != -1) {
                stripped = stripped.substring(packageIndex);
            }
        }

        return stripped;
    }
}

