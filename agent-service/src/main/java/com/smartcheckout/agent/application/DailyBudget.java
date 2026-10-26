package com.smartcheckout.agent.application;

import java.time.LocalDate;

/**
 * Caps the number of AI investigations per day: LLM calls cost money and free tiers have quotas.
 * Beyond the cap, investigations still happen, with the rule-based fallback.
 * Kept in memory: enough for one instance; several instances would share it in Redis or the database.
 */
public class DailyBudget {

    private final int maxPerDay;
    private LocalDate day;
    private int used;

    public DailyBudget(int maxPerDay) {
        this.maxPerDay = maxPerDay;
    }

    public synchronized boolean tryConsume(LocalDate today) {
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
        if (used >= maxPerDay) {
            return false;
        }
        used++;
        return true;
    }

    public synchronized int used() {
        return used;
    }

    public int maxPerDay() {
        return maxPerDay;
    }
}
