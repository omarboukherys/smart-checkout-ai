package com.smartcheckout.agent.infrastructure.persistence;

import com.smartcheckout.agent.application.port.InvestigationStore;
import com.smartcheckout.agent.domain.Investigation;
import com.smartcheckout.agent.domain.InvestigationReport;
import com.smartcheckout.agent.domain.Recommendation;
import com.smartcheckout.agent.domain.ToolCall;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class MongoInvestigationStore implements InvestigationStore {

    private final InvestigationMongoRepository repository;

    MongoInvestigationStore(InvestigationMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Investigation> findByCheckoutId(String checkoutId) {
        return repository.findById(checkoutId).map(MongoInvestigationStore::toDomain);
    }

    /** save = upsert on the checkout id: a new investigation replaces the previous one. */
    @Override
    public void save(Investigation investigation) {
        repository.save(toDocument(investigation));
    }

    @Override
    public List<Investigation> findRecent(int limit) {
        return repository.findAllByOrderByCompletedAtDesc(Limit.of(limit)).stream()
                .map(MongoInvestigationStore::toDomain)
                .toList();
    }

    private static InvestigationDocument toDocument(Investigation investigation) {
        InvestigationReport report = investigation.report();
        List<InvestigationDocument.ToolCallEntry> toolCalls = investigation.toolCalls().stream()
                .map(call -> new InvestigationDocument.ToolCallEntry(call.tool(), call.input(), call.durationMs(),
                        call.succeeded()))
                .toList();
        return new InvestigationDocument(investigation.checkoutId(), investigation.riskScore(),
                investigation.riskReasons(), report.recommendation().name(), report.confidence(), report.summary(),
                report.keyEvidence(), report.questionsForCustomer(), investigation.generatedBy(),
                investigation.fallbackReason(), toolCalls, investigation.startedAt(), investigation.completedAt());
    }

    private static Investigation toDomain(InvestigationDocument document) {
        InvestigationReport report = new InvestigationReport(Recommendation.valueOf(document.recommendation()),
                document.confidence(), document.summary(), document.keyEvidence(), document.questionsForCustomer());
        List<ToolCall> toolCalls = document.toolCalls() == null ? List.of() : document.toolCalls().stream()
                .map(entry -> new ToolCall(entry.tool(), entry.input(), entry.durationMs(), entry.succeeded()))
                .toList();
        return new Investigation(document.checkoutId(), document.riskScore(), document.riskReasons(), report,
                document.generatedBy(), document.fallbackReason(), toolCalls, document.startedAt(),
                document.completedAt());
    }
}
