# GET_STARTED — Canary Sandbox Developer Guide

> **Prerequisite:** Read [README.md](./README.md) first for project purpose, architecture, and quick start commands. This guide assumes you've done that.

## What This Is

A deterministic test harness that exercises LangChain4j's tool-calling agent patterns (the same ones used in production) so you can detect breaking changes **before** upgrading your real business applications. The `sandbox-ai-canary` is the **canary in the coal mine** — if the evals pass here, the upgrade is low-risk for your production apps.

## The Canary Workflow

```
You discover a new version / want to change library  →  Bump version in pom.xml
                                                      →  Run mvn test (24 evals)
                                                      →  All pass?  →  Safe to upgrade production
                                                      →  Any fail?  →  Investigate breaking change
```

This repo is meant to be kept **separate from your main business applications**. You fork/clone it, make the version change, run evals, and only proceed with the production upgrade once the canary sings.

## How to Make Changes

### 1. Bump a Library Version

The most common change is testing a new LangChain4j (or Spring Boot, etc.) release:

```bash
# Edit pom.xml — change the version property
<langchain4j.version>1.12.2</langchain4j.version>   →   1.13.0

# Then test
mvn test
```

All library versions are centralized in `<properties>` in `pom.xml`.

### 2. Add or Modify an Eval

If you suspect a particular LangChain4j contract may break (e.g., a new tool signature format), add a test in `src/test/java/com/codetoculture/canary/evals/CanaryAgentEvalTest.java`.

- Each eval is a `@Test` method annotated with `@Order(n)` and a `@DisplayName("EVAL-NN: ...")`
- Follow the existing pattern: construct a scenario, call the agent, assert on the response
- The `FakeChatModel` is deterministic — assertions are stable across runs

After adding, update the test count in README.md if needed.

### 3. Modify the Business Domain (Tools / Data)

- **Add a `@Tool`:** Create a new class in `src/main/java/com/codetoculture/canary/tools/` with `@Tool` annotated methods. Spring will pick it up automatically via component scanning.
- **Modify mock data:** Edit `MockTicketDatabase.java` to add new customers, tickets, or statuses.
- **Change the structured output model:** Edit `TriageResult.java` (the record the agent returns). Update evals that assert on its fields.

### 4. Swap the `FakeChatModel`

If you need to test a `ChatModel` implementation change, modify `AgentConfig.java` where the bean is wired:

```java
// AgentConfig.java — the @Bean method that creates the ChatModel
@Bean
public ChatModel chatModel(...) { ... }
```

The fake model lives in `FakeChatModel.java` — edit its behavior to simulate different LLM response patterns (tool call detection, JSON output format, multi-turn memory).

## How to Test

### Baseline (mandatory — no API keys needed)

```bash
mvn clean test
```

This runs 22 tests (18 evals + 2 live provider tests that are gated by env vars) in seconds. Always do this first after any change.

### Run a single eval class or method

```bash
# Single class
mvn test -Dtest=CanaryAgentEvalTest

# Single method
mvn test -Dtest=CanaryAgentEvalTest#triageTicket
```

### Live provider tests (for real canary validation)

```bash
# Ollama
OLLAMA_BASE_URL=http://localhost:11434 mvn test -Dtest=CanaryAgentEvalTest#liveOllamaTest

# OpenAI
OPENAI_API_KEY=sk-xxx mvn test -Dtest=CanaryAgentEvalTest#liveOpenAITest
```

### Full Spring Boot run (prints evaluation report to console + starts API)

```bash
# Fake model (default)
mvn spring-boot:run

# With a specific provider
mvn spring-boot:run -Dspring-boot.run.arguments="--canary.model.provider=ollama --ollama.model.name=gemma4"
```

## Interpreting Results

When `mvn test` passes: the LangChain4j contracts this canary exercises are **stable** at the new version.

When a test fails:
1. Read the test name — it maps to an EVAL ID (README.md has the full inventory)
2. Check which contract it validates (`@Tool` discovery, tool execution format, structured output mapping, multi-turn memory)
3. Compare the `FakeChatModel` expectations — the test asserts on specific tool request names, argument structures, and response JSON field names
4. If the change is intentional (new LangChain4j API), update the test expectations and/or `FakeChatModel` to match

**Common failure patterns:**

| Failure | Likely root cause |
|---------|-------------------|
| Tool method count mismatch | `@Tool` annotation contract changed |
| Tool request name different | LangChain4j changed how tool names are derived |
| Tool argument structure different | Tool execution request format changed |
| Structured output field mismatch | `TriageResult` JSON parsing changed |
| Multi-turn context lost | `ChatMemory` API changed |

## Pull Request Process

1. Create a feature branch from `main`:
   ```bash
   git checkout -b chore/bump-lc4j-1.13.0
   ```
2. Make your change (version bump, eval addition, tool change, etc.)
3. Commit with a conventional commit message:
   ```
   chore: bump langchain4j from 1.12.2 to 1.13.0
   ```
   or
   ```
   test: add EVAL-19 for tool streaming contract
   ```
4. Push and open a PR against `main`
5. Ensure CI (`mvn test`) passes — the GitHub Actions workflow runs `mvn test` automatically
6. Get review, merge, and verify in production

### CI Pipeline

See `.github/workflows/autonomous-upgrade.yml`. The daily cron job:
- Runs `mvn test` against the **current** pinned version (baseline)
- Fetches the latest LangChain4j release from Maven Central
- If newer: bumps `pom.xml`, runs `mvn test` again, creates a PR with passing results

You can also trigger this manually via GitHub UI (`workflow_dispatch`).

## Making Changes to the Canary Itself

If you need to modify the canary code (not just bump versions), always:

1. **Run `mvn clean test`** before opening the PR to confirm no regressions
2. **Run with `fake` provider** (default) — real model tests are too slow and non-deterministic for CI
3. If adding new source files, ensure they're in the correct package:
   - `agent/` — agent construction, model config, `ChatModel` implementations
   - `evals/` — evaluation logic
   - `model/` — data records (`TriageResult`)
   - `tools/` — `@Tool` annotated business methods

## Key Files Reference

| File | What it controls |
|------|------------------|
| `pom.xml` | Library versions (langchain4j, spring-boot) |
| `application.yml` | Active provider (fake/ollama/openai) |
| `FakeChatModel.java` | Deterministic LLM simulation (tool detection + JSON output) |
| `CanaryAgentEvalTest.java` | Master eval test class (22 tests) |
| `CanaryEvaluator.java` | Full suite report generator |
| `MockTicketDatabase.java` | Deterministic test data |
| `.github/workflows/autonomous-upgrade.yml` | Daily upgrade canary CI |
