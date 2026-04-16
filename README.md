# 🤖 LangChain4j Agent POC

A **fully local, autonomous AI agent** that reads instructions from a YAML file and automatically generates Java code, writes tests, validates them, and raises a GitHub Pull Request — all without human intervention.

Built with **Spring Boot**, **LangChain4j**, and **Ollama** (local LLM inference).

---

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Running the App](#running-the-app)
- [Writing Instructions](#writing-instructions)
- [Instruction Sources](#instruction-sources)
- [Agentic Loop & Self-Healing](#agentic-loop--self-healing)
- [GitHub Integration](#github-integration)
- [Troubleshooting](#troubleshooting)

---

## Overview

This POC demonstrates an **agentic orchestration layer** where:

1. You drop a YAML instruction file describing what code to generate
2. A **Coder Agent** generates the Java classes via a local LLM
3. A **Tester Agent** generates JUnit 5 tests for the generated code
4. The app compiles and runs the tests automatically
5. If tests fail, a **Reviewer Agent** reads the errors and self-heals the code
6. On success, the code is pushed to GitHub and a **Pull Request is created automatically**

All LLM inference runs **locally via Ollama** — no data leaves your machine.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Instruction Sources                │
│                                                     │
│   📁 File Watcher    🌐 REST API                    │
│   📨 Message Queue (RabbitMQ)                       │
└──────────────────────┬──────────────────────────────┘
                       ↓
           InstructionDispatcher
           (BlockingQueue — single-threaded)
                       ↓
             AgentOrchestrator
                       ↓
        ┌──────────────┼──────────────┐
        ↓              ↓              ↓
   CoderAgent     TesterAgent   ReviewerAgent
   (generate)     (test gen)    (self-heal)
        ↓              ↓              ↓
        └──────────────┴──────────────┘
                       ↓
              Ollama (local LLM)
              deepseek-coder:6.7b
                       ↓
             FileWriterService
             (writes .java files)
                       ↓
           TestExecutorService
           (Maven compile + test)
                       ↓
               ✅ Pass   ❌ Fail
               ↓              ↓
           GitService     RetryLoop
           push + PR      (max 3x)
```

---

## Project Structure

```
langchain4j-agent-poc/
├── build.gradle.kts
├── settings.gradle.kts
├── instructions.yaml                          # Sample instruction file
│
└── src/
    ├── main/
    │   ├── java/com/poc/agent/
    │   │   ├── PocApplication.java
    │   │   ├── config/
    │   │   │   └── OllamaConfig.java          # LLM bean definitions
    │   │   ├── controller/
    │   │   │   └── InstructionController.java # REST API source
    │   │   ├── model/
    │   │   │   ├── InstructionPlan.java        # YAML-mapped POJOs
    │   │   │   ├── PendingInstruction.java     # Instruction wrapper
    │   │   │   ├── TaskResult.java             # Per-task result
    │   │   │   └── TestExecutionResult.java    # Maven test result
    │   │   ├── parser/
    │   │   │   └── YamlInstructionParser.java
    │   │   ├── agent/
    │   │   │   ├── CoderAgent.java             # Code generation AI interface
    │   │   │   ├── TesterAgent.java            # Test generation AI interface
    │   │   │   └── ReviewerAgent.java          # Self-healing AI interface
    │   │   ├── orchestrator/
    │   │   │   └── AgentOrchestrator.java      # Core loop
    │   │   └── service/
    │   │       ├── FileWatcherService.java     # File-based source
    │   │       ├── FileWriterService.java      # Writes generated files
    │   │       ├── GitService.java             # GitHub push + PR
    │   │       ├── InstructionDispatcher.java  # Central hub
    │   │       ├── MessageQueueListener.java   # RabbitMQ source
    │   │       └── TestExecutorService.java    # Maven invoker
    │   └── resources/
    │       ├── application.yml
    │       └── application-local.yml
    └── test/
        └── java/com/poc/agent/
            └── orchestrator/
                └── AgentOrchestratorTest.java
```

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 17+ | Runtime |
| Gradle | 8+ | Build tool |
| Ollama | Latest | Local LLM inference |
| Maven | 3.8+ | Test execution inside generated projects |
| Git | Any | GitHub push & PR creation |

### Install & Start Ollama

```bash
# Install Ollama (macOS/Linux)
curl -fsSL https://ollama.ai/install.sh | sh

# Pull the recommended model
ollama pull deepseek-coder:6.7b

# Start Ollama server
ollama serve

# Verify it's running
curl http://localhost:11434
```

> **Alternative model:** `ollama pull codellama` works too — `deepseek-coder:6.7b` produces cleaner Java.

---

## Configuration

### `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: langchain4j-agent-poc

ollama:
  base-url: http://localhost:11434
  coder-model: deepseek-coder:6.7b
  tester-model: deepseek-coder:6.7b
  timeout-minutes: 3

git:
  repo-path: ./generated
  github-token: ${GITHUB_TOKEN}       # set as environment variable
  repo-owner: your-github-username
  repo-name: your-repo-name

instruction:
  sources:
    file-watcher:
      enabled: true
      watch-dir: ./instructions/inbox
      processed-dir: ./instructions/processed
      failed-dir: ./instructions/failed
    rest-api:
      enabled: true
    message-queue:
      enabled: false
      queue-name: agent.instructions

logging:
  level:
    com.poc.agent: DEBUG
    dev.langchain4j: INFO
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `GITHUB_TOKEN` | Yes (for PR) | GitHub Personal Access Token with `repo` scope |

```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
```

---

## Running the App

```bash
# 1. Clone the repo
git clone https://github.com/your-org/langchain4j-agent-poc.git
cd langchain4j-agent-poc

# 2. Make sure Ollama is running
ollama serve

# 3. Build
./gradlew build -x test

# 4. Run
./gradlew bootRun

# 5. Run with local profile
./gradlew bootRun -Dspring.profiles.active=local

# 6. Build fat JAR and run
./gradlew bootJar
java -jar build/libs/langchain4j-agent-poc.jar
```

### Useful Gradle Commands

```bash
./gradlew clean                    # Clean build output
./gradlew compileJava              # Compile only
./gradlew test                     # Run tests
./gradlew build -x test            # Build, skip tests
./gradlew tasks                    # List all tasks
```

---

## Writing Instructions

Instructions are YAML files that tell the agents what to generate.

### Full Example — `instructions.yaml`

```yaml
project:
  name: UserServicePOC
  basePackage: com.poc.userservice
  outputDir: ./generated

pipeline:
  maxRetries: 3
  pushToGithub: true
  createPR: true
  targetBranch: main
  workingBranch: agent/generated-

tasks:
  - id: task-001
    type: CODE_GENERATION
    description: >
      Create a UserService class with these exact methods:
      1. createUser(String name, String email) — void, throws IllegalArgumentException if name or email is null
      2. getUserById(Long id) — returns User, throws IllegalArgumentException if id is null
      3. deleteUser(Long id) — void, throws IllegalArgumentException if id is null.
      Use @Service annotation. Import com.poc.userservice.model.User.
      Use a private in-memory List<User> as data store.
    targetClass: UserService
    layer: service

  - id: task-002
    type: CODE_GENERATION
    description: >
      Create a User model class with fields:
      id (Long), name (String), email (String).
      Include Lombok @Data and @Builder annotations.
    targetClass: User
    layer: model

  - id: task-003
    type: TEST_GENERATION
    description: >
      Generate JUnit 5 unit tests for UserService.
      Cover: happy path for createUser, null validation for name and email,
      getUserById for existing and non-existing IDs, deleteUser happy path.
      Use Mockito for mocking. Include all imports.
    targetClass: UserServiceTest
    testsFor: task-001

  - id: task-004
    type: TEST_EXECUTION
    dependsOn: [task-001, task-002, task-003]

  - id: task-005
    type: GIT_PUSH
    dependsOn: [task-004]
```

### Task Types

| Type | Description |
|------|-------------|
| `CODE_GENERATION` | Generate a Java class via CoderAgent |
| `TEST_GENERATION` | Generate a JUnit 5 test class via TesterAgent |
| `TEST_EXECUTION` | Compile and run all generated tests via Maven |
| `GIT_PUSH` | Push generated code to GitHub and create a PR |

### `dependsOn` Rules

- Tasks with `dependsOn` will only execute if all listed tasks **succeeded**
- If a dependency failed, the dependent task is **skipped** automatically
- Always put `TEST_EXECUTION` after all code and test generation tasks
- Always put `GIT_PUSH` after `TEST_EXECUTION`

---

## Instruction Sources

The app runs as a **long-running service** and accepts new instructions from three sources simultaneously.

### 1. 📁 File Watcher *(default: enabled)*

Drop any `.yaml` file into the watch directory:

```bash
cp my-feature-instructions.yaml ./instructions/inbox/
```

The app detects the file automatically, processes it, and moves it to `./instructions/processed/` on success or `./instructions/failed/` on error.

### 2. 🌐 REST API *(default: enabled)*

POST a YAML payload directly:

```bash
curl -X POST http://localhost:8080/api/instructions \
  -H "Content-Type: application/x-yaml" \
  --data-binary @instructions.yaml
```

Check queue status:

```bash
curl http://localhost:8080/api/instructions/status
```

Response:
```json
{
  "queueSize": 0,
  "status": "RUNNING"
}
```

### 3. 📨 Message Queue — RabbitMQ *(default: disabled)*

Add the dependency to `build.gradle.kts`:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-amqp")
```

Enable in `application.yml`:

```yaml
instruction:
  sources:
    message-queue:
      enabled: true
      queue-name: agent.instructions
```

Publish a YAML string to the `agent.instructions` queue — the listener dispatches it automatically.

---

## Agentic Loop & Self-Healing

The orchestrator runs a **retry loop** for every task before giving up.

```
Attempt 1 → CoderAgent generates fresh code
            ↓ compile check
         ❌ fail → capture errors

Attempt 2 → ReviewerAgent reads (original code + errors) → fixes
            ↓ compile check
         ❌ fail → capture errors

Attempt 3 → ReviewerAgent reads (attempt 2 code + errors) → fixes
            ↓ compile check
         ❌ fail → mark task FAILED, skip dependents

         ✅ pass (any attempt) → proceed to next task
```

The same loop applies to **test execution** — if Maven tests fail, the ReviewerAgent reads the test output and rewrites the test class before retrying.

Configure max retries in your YAML:

```yaml
pipeline:
  maxRetries: 3    # increase for complex tasks
```

---

## GitHub Integration

On successful test execution, the app:

1. Initialises a Git repo in the `outputDir`
2. Creates a timestamped branch: `agent/generated-20250416-143022`
3. Commits all generated files with a descriptive message
4. Pushes the branch to your remote
5. Opens a Pull Request via the GitHub REST API

### Setup

1. Create a GitHub Personal Access Token with `repo` scope at [github.com/settings/tokens](https://github.com/settings/tokens)
2. Set it as an environment variable:

```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
```

3. Update `application.yml`:

```yaml
git:
  repo-owner: your-github-username
  repo-name: your-target-repo
  target-branch: main
```

4. Set `pushToGithub: true` and `createPR: true` in your instruction YAML.

### Disable Git Push (local dev)

```yaml
pipeline:
  pushToGithub: false
  createPR: false
```

---

## Troubleshooting

### App exits immediately on startup
The app is now a long-running service — it should stay alive. If it exits, check that at least one instruction source is enabled in `application.yml`.

### Ollama connection refused
```bash
# Make sure Ollama is running
ollama serve

# Verify
curl http://localhost:11434

# Check model is available
ollama list
```

### Generated code has markdown backticks
The `FileWriterService` sanitizer strips these automatically. If you still see them, tighten the `@SystemMessage` in `CoderAgent.java` and re-run.

### Wrong package in generated files
Ensure your YAML `description` explicitly states the full package and the `layer` field matches your intended sub-package. The prompt passes `{{basePackage}}.{{layer}}` to the LLM.

### Maven test execution fails with "pom.xml not found"
The `TestExecutorService` looks for a `pom.xml` in `outputDir`. Either add one manually or extend `FileWriterService` to generate a minimal `pom.xml` as part of project scaffolding.

### GitHub PR creation returns 422
Ensure the branch does not already exist on the remote. Each run generates a timestamped branch name to avoid this — check your `workingBranch` config in the YAML.

### Slow code generation
Ollama runs locally — generation time depends on your hardware. Expected times per task:

| Hardware | Time per task |
|----------|--------------|
| M2 MacBook Pro | ~15–30 sec |
| CPU only (16GB RAM) | ~60–120 sec |
| GPU (8GB VRAM) | ~10–20 sec |

Increase `ollama.timeout-minutes` in `application.yml` if you hit timeouts.

---

## License

MIT License — see `LICENSE` for details.
