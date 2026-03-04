package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.VerifierAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VerifierAssignmentRepository extends JpaRepository<VerifierAssignment, Long> {

    Optional<VerifierAssignment> findByVerifierIdAndDay(Long verifierId, String day);

    @Modifying
    @Query("DELETE FROM VerifierAssignment va WHERE va.verifier.id = :verifierId AND va.day = :day")
    void deleteByVerifierIdAndDay(@Param("verifierId") Long verifierId, @Param("day") String day);

    long countByVerifierId(Long verifierId);

    @Query("""
        SELECT DISTINCT va.verifier.id FROM VerifierAssignment va
        WHERE va.verifier.id NOT IN (
            SELECT va2.verifier.id FROM VerifierAssignment va2
            WHERE va2.day != :day
        )
        AND va.day = :day
    """)
    List<Long> findVerifierIdsWithOnlyDay(@Param("day") String day);
}
