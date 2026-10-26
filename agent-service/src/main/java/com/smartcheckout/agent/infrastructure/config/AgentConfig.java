package com.smartcheckout.agent.infrastructure.config;

import com.smartcheckout.agent.application.DailyBudget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class AgentConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Maximum number of AI investigations per day (cost and free-tier quota). */
    @Bean
    DailyBudget dailyBudget(@Value("${smartcheckout.agent.daily-budget:50}") int maxPerDay) {
        return new DailyBudget(maxPerDay);
    }
}
