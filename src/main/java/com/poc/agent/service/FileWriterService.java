package com.poc.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
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
                                String className, String content,
                                String serviceClass, String modelClass)
            throws IOException {

        log.debug("Raw LLM output for {}:\n{}", className, content);

        // Step 1 — sanitize (strips markdown, validates package/brace)
        String sanitized = sanitize(content);
        log.debug("After sanitize for {}:\n{}", className, sanitized);

        // Step 2 — fix wrong imports and constructor calls  ← called here
        String fixed = fixTestImports(
                sanitized, basePackage, serviceClass, modelClass);
        log.info("Final content for {}:\n{}", className, fixed);

        // Step 3 — write to disk
        String packagePath = basePackage.replace(".", "/");
        Path dir = Paths.get(outputDir, "src/test/java", packagePath);
        Files.createDirectories(dir);

        Path file = dir.resolve(className + ".java");
        Files.writeString(file, fixed);

        log.info("Written: {}", file.toAbsolutePath());
        return file.toString();
    }

    /**
     * Strips markdown fences and any trailing non-Java text
     * after the last closing brace of the class.
     */
    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "LLM returned empty response");
        }

        // Step 1 — strip markdown fences
        String stripped = raw
                .replaceAll("(?s)```java\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        // Step 2 — find package declaration
        int packageIndex = stripped.indexOf("package ");
        if (packageIndex == -1) {
            // No package found — entire response is explanation text
            throw new IllegalArgumentException(
                    "LLM returned explanation text instead of Java code. "
                            + "Full response was:\n" + stripped);
        }

        // Strip anything before package declaration
        stripped = stripped.substring(packageIndex);

        // Step 3 — strip everything after last closing brace
        int lastBrace = stripped.lastIndexOf("}");
        if (lastBrace == -1) {
            throw new IllegalArgumentException(
                    "LLM response has no closing brace. "
                            + "Response was:\n" + stripped);
        }
        stripped = stripped.substring(0, lastBrace + 1).trim();

        // Step 4 — validate it still starts with package after trimming
        if (!stripped.startsWith("package ")) {
            throw new IllegalArgumentException(
                    "Sanitized output does not start with package declaration. "
                            + "Output was:\n" + stripped);
        }

        return stripped;
    }

    public String fixTestImports(String content,
                                 String basePackage,
                                 String serviceClass,
                                 String modelClass) {

        // ── Fix wrong service import ──────────────────────────────
        String wrongServiceImport =
                "import " + basePackage + "." + serviceClass + ";";
        String correctServiceImport =
                "import " + basePackage + ".service." + serviceClass + ";";

        if (content.contains(wrongServiceImport)) {
            content = content.replace(
                    wrongServiceImport, correctServiceImport);
            log.info("🔧 Fixed wrong service import");
        }

        // ── Fix wrong model import ────────────────────────────────
        String wrongModelImport =
                "import " + basePackage + "." + modelClass + ";";
        String correctModelImport =
                "import " + basePackage + ".model." + modelClass + ";";

        if (content.contains(wrongModelImport)) {
            content = content.replace(
                    wrongModelImport, correctModelImport);
            log.info("🔧 Fixed wrong model import");
        }

        // ── Force inject correct imports if missing entirely ──────
        // This handles the case where LLM skipped imports altogether
        if (!content.contains(correctServiceImport)) {
            content = injectImport(content, correctServiceImport);
            log.info("🔧 Injected missing service import");
        }

        if (!content.contains(correctModelImport)) {
            content = injectImport(content, correctModelImport);
            log.info("🔧 Injected missing model import");
        }

        // ── Fix wrong constructor calls ───────────────────────────
        content = content.replaceAll(
                "new " + modelClass + "\\(([^,)]+),\\s*([^)]+)\\)",
                modelClass + ".builder()"
                        + ".id(System.currentTimeMillis())"
                        + ".name($1)"
                        + ".email($2)"
                        + ".build()");

        content = content.replace(
                "new " + modelClass + "()",
                modelClass + ".builder()"
                        + ".id(1L)"
                        + ".name(\"test\")"
                        + ".email(\"test@test.com\")"
                        + ".build()");

        return content;
    }

    // ── Inject import after the package declaration line ──────────
    private String injectImport(String content, String importStatement) {
        // Find the end of the package declaration line
        int packageEnd = content.indexOf(";");
        if (packageEnd == -1) return content;

        // Check if import already exists anywhere (different format)
        String className = importStatement
                .replace("import ", "")
                .replace(";", "")
                .trim();
        if (content.contains(className)) {
            // Class name exists — just the import path is wrong
            // Already handled above
            return content;
        }

        // Inject after package declaration
        return content.substring(0, packageEnd + 1)
                + "\n"
                + importStatement
                + content.substring(packageEnd + 1);
    }
}

