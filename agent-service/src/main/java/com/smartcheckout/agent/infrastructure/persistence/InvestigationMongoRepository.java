package com.smartcheckout.agent.infrastructure.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

interface InvestigationMongoRepository extends MongoRepository<InvestigationDocument, String> {

    List<InvestigationDocument> findAllByOrderByCompletedAtDesc(Limit limit);
}
