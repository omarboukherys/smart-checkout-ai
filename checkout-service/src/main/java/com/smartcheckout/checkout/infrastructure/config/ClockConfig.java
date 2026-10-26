package com.smartcheckout.checkout.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ClockConfig {

    /** One clock for the whole service, replaced by a fixed clock in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
