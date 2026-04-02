package org.example.licientajobs;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobApplicationRepository extends Neo4jRepository<JobApplication, Long> {
    Optional<JobApplication> findByJobTitleAndCompany(String jobTitle, String company);
}
