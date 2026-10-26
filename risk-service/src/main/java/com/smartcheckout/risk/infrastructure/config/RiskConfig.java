package com.smartcheckout.risk.infrastructure.config;

import com.smartcheckout.risk.domain.RiskEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class RiskConfig {

    /** The domain engine stays a plain Java object: Spring only creates it here. */
    @Bean
    RiskEngine riskEngine() {
        return RiskEngine.withDefaultRules();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
