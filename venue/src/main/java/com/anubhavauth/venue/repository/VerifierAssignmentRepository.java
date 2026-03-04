package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.VerifierAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerifierAssignmentRepository extends JpaRepository<VerifierAssignment, Long> {

    Optional<VerifierAssignment> findByVerifierIdAndDay(Long verifierId, String day);

    long countByVerifierId(Long verifierId);

    void deleteByVerifierIdAndDay(Long verifierId, String day);

    // Returns verifiers who have assignments ONLY on the given day (no other days)
    @Query("""
        SELECT va FROM VerifierAssignment va
        WHERE va.verifier.id NOT IN (
            SELECT va2.verifier.id FROM VerifierAssignment va2
            WHERE va2.day != :day
        )
        AND va.day = :day
    """)
    List<VerifierAssignment> findVerifiersWithOnlyDay(@Param("day") String day);
}
