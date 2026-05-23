# AI Canary Sandbox

A business-domain AI agent that tests new LLM/library versions with the same tool-calling patterns as your production app.

## Why this exists

Every engineering team needs a canary that exercises their actual LLM + tool patterns before deploying a library or model upgrade. This sandbox:

1. **Mimics your production domain** — a customer support ticket triage system that uses tool calling, structured output, and multi-turn conversations
2. **Exercises the same LangChain4j contracts** — `@Tool` annotations, tool execution flow, and structured response parsing
3. **Switches models on demand** — run the same prompts against different providers to find breaking changes with minimal token cost

## Quick Start

```bash
# Default: fake model (deterministic, no API keys needed)
mvn spring-boot:run

# Test with Ollama
mvn spring-boot:run -Dspring-boot.run.arguments="--canary.model.provider=ollama --ollama.model.name=gemma4"

# Test with OpenAI
OPENAI_API_KEY=sk-xxx mvn spring-boot:run -Dspring-boot.run.arguments="--canary.model.provider=openai"
```

## How to use for version comparison

```bash
# Step 1: Run with your current model
mvn spring-boot:run -Dspring-boot.run.arguments="--canary.model.provider=ollama --ollama.model.name=mistral:7b"

# Step 2: Run with the candidate model
mvn spring-boot:run -Dspring-boot.run.arguments="--canary.model.provider=ollama --ollama.model.name=llama3"

# Step 3: Compare the tool call patterns and structured output
# Run the eval suite to verify the LangChain4j contract is stable
mvn test -Dtest=CanaryAgentEvalTest
```

## Business Domain

The canary uses a **Customer Support Ticket Triage** scenario that mirrors common enterprise AI patterns:

| Tool | Purpose | Production Equivalent |
|------|---------|-----------------------|
| `lookupCustomer(CUST-XXXX)` | Fetch customer account details | Customer API lookup |
| `lookupTicket(TKT-XXXX)` | Retrieve ticket information | Ticketing system query |
| `listCustomerTickets(CUST-XXXX)` | List a customer's active tickets | Customer history API |
| `updateTicketStatus(TKT-XXXX, STATUS)` | Escalate/close a ticket | Ticketing system write API |
| `listActiveTickets(filter)` | Dashboard of critical issues | Internal ops dashboard |

## LangChain4j Contracts Tested

- `@Tool` annotation parsing and tool discovery
- Tool execution request format (name + arguments)
- Structured output mapping (TriageResult record)
- Multi-turn conversation memory
- ChatModel interface stability

## Configuration

| Property | Default | Description |
| :--- | :--- | :--- |
| `canary.model.provider` | `fake` | `fake`, `ollama`, or `openai` |
| `ollama.base.url` | `http://localhost:11434` | Ollama endpoint |
| `ollama.model.name` | `llama3` | Model to test |
| `openai.api.key` | `demo` | OpenAI API key |
| `langsmith.tracing` | `false` | Enable LangSmith trace capture |

## Running Tests

```bash
# All evals (deterministic, fast)
mvn test -Dtest=CanaryAgentEvalTest

# Live Ollama test (requires OLLAMA_BASE_URL)
OLLAMA_BASE_URL=http://localhost:11434 mvn test -Dtest=CanaryAgentEvalTest#liveOllamaTest

# Live OpenAI test (requires OPENAI_API_KEY)
OPENAI_API_KEY=sk-xxx mvn test -Dtest=CanaryAgentEvalTest#liveOpenAITest
```

## Architecture

```
CanaryAgent (AiService)
  ├── ChatModel (fake/ollama/openai)
  ├── CustomerLookup (@Tool: lookupCustomer, lookupTicket, listCustomerTickets)
  ├── TicketUpdater (@Tool: updateTicketStatus, listActiveTickets)
  └── MockTicketDatabase (deterministic test data)
```

The `FakeChatModel` simulates the full multi-turn LangChain4j agent loop:
1. Detects tool-calling intent in the user prompt (customer ID, ticket ID, or triage query)
2. Returns an `AiMessage` with `ToolExecutionRequest` that AiServices executes
3. On the follow-up turn (after tool results), produces a structured triage summary

This exercises the same contracts that a real LLM provider would — just deterministically.

## Design Philosophy

- **Business domain, not version checking** — the agent does real triage work, not "what version is this?" questions
- **Tool usage is the test** — breaking changes in `@Tool` handling, tool request format, or structured output will trip the evals
- **Deterministic by default** — FakeChatModel gives reproducible results for CI; switch to real models for canary testing
- **No external dependencies** — all data is in-memory; no API keys needed for the default run
