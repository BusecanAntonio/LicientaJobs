package org.example.licientajobs;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobApplicationRepository extends Neo4jRepository<JobApplication, Long> {
    // Keep the @Query method for now, but we will use existsBy for the check
    @Query("MATCH (j:JobApplication) WHERE j.jobTitle = $jobTitle AND j.company = $company RETURN j")
    List<JobApplication> findByJobTitleAndCompany(String jobTitle, String company);

    // New method for existence check
    boolean existsByJobTitleAndCompany(String jobTitle, String company);
}
