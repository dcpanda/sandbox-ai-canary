package com.codetoculture.canary;

import com.codetoculture.canary.agent.CanaryAgent;
import com.codetoculture.canary.model.TriageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canary")
public class AgentController {

    private final CanaryAgent agent;

    public AgentController(CanaryAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/ask")
    public TriageResult ask(@RequestBody String question) {
        return agent.ask(question);
    }

    @GetMapping("/ask")
    public TriageResult askGet(@RequestParam("q") String question) {
        return agent.ask(question);
    }

    @GetMapping("/check")
    public TriageResult check() {
        return agent.ask("Triage for customer CUST-5512, ticket TKT-1004. What is the urgency and routing?");
    }
}
