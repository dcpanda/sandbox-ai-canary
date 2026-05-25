package com.codetoculture.canary;

import com.codetoculture.canary.evals.CanaryEvaluator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CanaryApplication {
    public static void main(String[] args) {
        SpringApplication.run(CanaryApplication.class, args);
    }

    @Bean
    public CommandLineRunner runCanary(CanaryEvaluator evaluator) {
        return args -> {
            try {
                System.out.println("\n" + evaluator.runFullSuiteReport() + "\n");
            } catch (Exception e) {
                System.err.println("\n=== Canary Evaluation Report (FAILED) ===");
                System.err.println("  Error: " + e.getMessage());
                System.err.println("  The model returned non-JSON output. Structured output contract NOT verified.");
                System.err.println("===========================================\n");
            }
        };
    }
}
