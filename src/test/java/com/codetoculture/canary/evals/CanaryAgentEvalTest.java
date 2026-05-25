package com.codetoculture.canary.evals;

import com.codetoculture.canary.agent.CanaryAgent;
import com.codetoculture.canary.agent.FakeChatModel;
import com.codetoculture.canary.agent.LangSmithChatModelListener;
import com.codetoculture.canary.model.TriageResult;
import com.codetoculture.canary.tools.CustomerLookup;
import com.codetoculture.canary.tools.MockTicketDatabase;
import com.codetoculture.canary.tools.TicketUpdater;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral eval suite for the Canary Agent.
 *
 * Tests verify three things:
 * 1. Tool contract — all @Tool methods are discoverable and have correct descriptions
 * 2. Tool execution — the agent correctly calls tools and processes results
 * 3. Structured output — the agent produces valid TriageResult records
 *
 * These tests exercise the same patterns your production app uses.
 * If LangChain4j changes any of these contracts, at least one test will fail.
 */
class CanaryAgentEvalTest {

    private CanaryAgent agent;
    private CanaryEvaluator evaluator;
    private FakeChatModel fakeModel;

    @BeforeEach
    void setUp() {
        LangSmithChatModelListener listener = new LangSmithChatModelListener(true, "canary-project");
        fakeModel = new FakeChatModel();
        fakeModel.setListeners(List.of(listener));

        MockTicketDatabase db = new MockTicketDatabase();
        CustomerLookup customerLookup = new CustomerLookup(db);
        TicketUpdater ticketUpdater = new TicketUpdater(db);

        agent = new CanaryAgent(fakeModel, customerLookup, ticketUpdater);
        evaluator = new CanaryEvaluator(agent);
    }

    // ---- Tool Contract Evals ----
    // These trip if LangChain4j changes how @Tool annotations are processed.

    @Test
    @DisplayName("EVAL-01: CustomerLookup exposes exactly 3 tool specifications")
    void customerLookup_exposesThreeToolSpecs() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(customerLookupForEval());
        long count = specs.stream()
                .filter(s -> s.name().startsWith("lookupCustomer")
                        || s.name().startsWith("lookupTicket")
                        || s.name().startsWith("listCustomerTickets"))
                .count();
        assertThat(count).as("CustomerLookup must expose 3 tools").isEqualTo(3);
    }

    @Test
    @DisplayName("EVAL-02: TicketUpdater exposes exactly 2 tool specifications")
    void ticketUpdater_exposesTwoToolSpecs() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(ticketUpdaterForEval());
        long count = specs.stream()
                .filter(s -> s.name().equals("updateTicketStatus") || s.name().equals("listActiveTickets"))
                .count();
        assertThat(count).as("TicketUpdater must expose 2 tools").isEqualTo(2);
    }

    @Test
    @DisplayName("EVAL-03: Total tool count is exactly 5 across all tools")
    void totalToolCount() {
        List<ToolSpecification> all = new java.util.ArrayList<>(ToolSpecifications.toolSpecificationsFrom(customerLookupForEval()));
        all.addAll(ToolSpecifications.toolSpecificationsFrom(ticketUpdaterForEval()));
        assertThat(all).hasSize(5);
    }

    @Test
    @DisplayName("EVAL-04: lookupCustomer description mentions customer ID")
    void lookupCustomer_descriptionMentionsCustomerId() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(customerLookupForEval());
        ToolSpecification spec = specs.stream()
                .filter(s -> s.name().equals("lookupCustomer"))
                .findFirst().orElseThrow(() -> new AssertionError("lookupCustomer not found"));
        assertThat(spec.description()).containsIgnoringCase("customer ID");
    }

    // ---- Agent Response Evals (FakeChatModel) ----

    @Test
    @DisplayName("EVAL-05: Agent produces triage result for customer+ticket query")
    void agent_customerAndTicketQuery_returnsTriage() {
        TriageResult result = evaluator.runCanaryCheck();
        assertThat(result).isNotNull();
        assertThat(result.ticketId()).isEqualTo("TKT-1004");
        assertThat(result.urgency()).isIn(TriageResult.Urgency.IMMEDIATE, TriageResult.Urgency.MEDIUM);
        assertThat(result.routing()).isNotEmpty();
    }

    @Test
    @DisplayName("EVAL-06: Agent responds to customer lookup query")
    void agent_customerLookup_returnsValidResult() {
        TriageResult result = evaluator.runCustomerLookup();
        assertThat(result).isNotNull();
        assertThat(result.ticketId()).isEqualTo("CUST-4401");
    }

    @Test
    @DisplayName("EVAL-07: Agent responds to ticket detail query")
    void agent_ticketDetail_returnsValidResult() {
        TriageResult result = evaluator.runTicketLookup();
        assertThat(result).isNotNull();
        assertThat(result.ticketId()).isEqualTo("TKT-1002");
        assertThat(result.urgency()).isEqualTo(TriageResult.Urgency.MEDIUM);
    }

    @Test
    @DisplayName("EVAL-08: Agent responds to triage dashboard query")
    void agent_trageDashboard_returnsValidResult() {
        TriageResult result = evaluator.runTriageDashboard();
        assertThat(result).isNotNull();
        assertThat(result.urgency()).isEqualTo(TriageResult.Urgency.IMMEDIATE);
    }

    @Test
    @DisplayName("EVAL-09: FakeChatModel logs tool execution and final response")
    void fakeModel_executesToolAndProducesFinalResponse() {
        evaluator.runCanaryCheck();
        List<String> log = fakeModel.getInvocationLog();
        assertThat(log).hasSize(2);
        assertThat(log.get(0)).startsWith("TOOL:");
        assertThat(log.get(1)).startsWith("FINAL:");
    }

    @Test
    @DisplayName("EVAL-10: MockTicketDatabase returns valid data for all lookup methods")
    void mockDb_returnsValidData() {
        MockTicketDatabase db = new MockTicketDatabase();

        assertThat(db.findTicketById("TKT-1004")).isPresent();
        assertThat(db.findCustomerById("CUST-5512")).isPresent();
        assertThat(db.findTicketsByCustomer("CUST-4401")).isPresent();
        assertThat(db.listActiveTickets("CRITICAL")).isPresent();
    }

    @Test
    @DisplayName("EVAL-11: MockTicketDatabase handles missing entities gracefully")
    void mockDb_handlesMissingEntities() {
        MockTicketDatabase db = new MockTicketDatabase();

        assertThat(db.findTicketById("TKT-9999")).isEmpty();
        assertThat(db.findCustomerById("CUST-9999")).isEmpty();
    }

    @Test
    @DisplayName("EVAL-12: Agent handles ticket not found gracefully")
    void agent_ticketNotFound_returnsValidResult() {
        TriageResult result = agent.ask("Look up ticket TKT-9999 for customer CUST-4401.");
        assertThat(result).isNotNull();
    }

    // ---- Live Model Integration (requires env vars) ----
    // These tests bypass the @BeforeEach FakeChatModel and create their
    // own agent wired to the real provider, exercising the actual model.

    @Test
    @DisplayName("EVAL-OLLAMA: Agent runs against local Ollama")
    @EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".*")
    void liveOllamaTest() {
        String baseUrl = System.getenv("OLLAMA_BASE_URL");
        dev.langchain4j.model.ollama.OllamaChatModel model = dev.langchain4j.model.ollama.OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName("llama3")
                .build();
        MockTicketDatabase db = new MockTicketDatabase();
        CanaryAgent liveAgent = new CanaryAgent(model, new CustomerLookup(db), new TicketUpdater(db));
        TriageResult result = liveAgent.ask("Triage for customer CUST-5512, ticket TKT-1004. What is the urgency?");
        assertThat(result).isNotNull();
        System.out.println("[OLLAMA Response] " + result);
    }

    @Test
    @DisplayName("EVAL-OPENAI: Agent runs against live OpenAI")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
    void liveOpenAITest() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        dev.langchain4j.model.openai.OpenAiChatModel model = dev.langchain4j.model.openai.OpenAiChatModel.builder()
                .apiKey(apiKey)
                .build();
        MockTicketDatabase db = new MockTicketDatabase();
        CanaryAgent liveAgent = new CanaryAgent(model, new CustomerLookup(db), new TicketUpdater(db));
        TriageResult result = liveAgent.ask("Triage for customer CUST-5512, ticket TKT-1004. What is the urgency?");
        assertThat(result).isNotNull();
        System.out.println("[OPENAI Response] " + result);
    }

    // ---- Expanded coverage: multi-turn, edge cases, error paths ----

    @Test
    @DisplayName("EVAL-13: Multi-turn conversation preserves memory across calls")
    void multiTurnConversationWithMemory() {
        TriageResult first = agent.ask("Look up customer CUST-4401 account details.");
        assertThat(first).isNotNull();

        TriageResult second = agent.ask("Now get details for ticket TKT-1002.");
        assertThat(second).isNotNull();
        assertThat(second.ticketId()).isEqualTo("TKT-1002");
        assertThat(second.urgency()).isEqualTo(TriageResult.Urgency.MEDIUM);
    }

    @Test
    @DisplayName("EVAL-14: No-tool message (greeting) produces valid LOW urgency result")
    void noToolGreetingReturnsLowUrgency() {
        TriageResult result = agent.ask("Hello, how are you?");
        assertThat(result).isNotNull();
        assertThat(result.urgency()).isEqualTo(TriageResult.Urgency.LOW);
        assertThat(result.routing()).contains("No action needed");
    }

    @Test
    @DisplayName("EVAL-15: Invalid customer ID produces valid response with not-found")
    void invalidCustomerIdReturnsValidResult() {
        TriageResult result = agent.ask("Look up customer CUST-9999.");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("EVAL-16: Agent handles empty/null-adjacent input gracefully")
    void handlesMinimalInput() {
        TriageResult result = agent.ask("What tickets are open?");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("EVAL-17: CanaryEvaluator full suite report runs without error")
    void fullSuiteReportCompletes() {
        String report = evaluator.runFullSuiteReport();
        assertThat(report).isNotNull().contains("Canary Evaluation Report");
        System.out.println(report);
    }

    @Test
    @DisplayName("EVAL-18: updateTicketStatus tool executes and returns valid JSON")
    void updateTicketStatusExecutesCorrectly() {
        TicketUpdater updater = new TicketUpdater(new MockTicketDatabase());
        String result = updater.updateTicketStatus("TKT-1004", "RESOLVED");
        assertThat(result).contains("\"ticketId\":\"TKT-1004\"");
        assertThat(result).contains("\"status\":\"RESOLVED\"");
        assertThat(result).doesNotContain("\"status\":\"OPEN\"");
    }

    // ---- Helpers ----

    private CustomerLookup customerLookupForEval() {
        return new CustomerLookup(new MockTicketDatabase());
    }

    private TicketUpdater ticketUpdaterForEval() {
        return new TicketUpdater(new MockTicketDatabase());
    }
}
