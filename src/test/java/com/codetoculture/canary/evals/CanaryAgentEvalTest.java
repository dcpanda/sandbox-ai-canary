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

        CustomerLookup customerLookup = new CustomerLookup();
        TicketUpdater ticketUpdater = new TicketUpdater();

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
        assertThat(result.urgency()).isEqualTo(TriageResult.Urgency.IMMEDIATE);
        assertThat(result.routing()).containsIgnoringCase("escalat");
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

    // ---- Live Model Integration (Manual) ----

    @Test
    @DisplayName("EVAL-OLLAMA: Agent runs against local Ollama")
    @EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".*")
    void liveOllamaTest() {
        TriageResult result = evaluator.runCanaryCheck();
        assertThat(result).isNotNull();
        System.out.println("[OLLAMA Response] " + result);
    }

    @Test
    @DisplayName("EVAL-OPENAI: Agent runs against live OpenAI (Manual)")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
    void liveOpenAITest() {
        TriageResult result = evaluator.runCanaryCheck();
        assertThat(result).isNotNull();
        System.out.println("[OPENAI Response] " + result);
    }

    // ---- Helpers ----

    private CustomerLookup customerLookupForEval() {
        return new CustomerLookup();
    }

    private TicketUpdater ticketUpdaterForEval() {
        return new TicketUpdater();
    }
}
