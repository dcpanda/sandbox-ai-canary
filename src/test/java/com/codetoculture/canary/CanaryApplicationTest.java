package com.codetoculture.canary;

import com.codetoculture.canary.agent.CanaryAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CanaryApplicationTest {

    @Autowired
    private CanaryAgent agent;

    @Test
    void contextLoads() {
        assertThat(agent).isNotNull();
    }

    @Test
    void agentRespondsWithFakeModel() {
        var result = agent.ask("Hello");
        assertThat(result).isNotNull();
    }
}