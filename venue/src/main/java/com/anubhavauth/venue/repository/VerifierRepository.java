package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.Verifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerifierRepository extends JpaRepository<Verifier, Long> {

    Optional<Verifier> findByUsername(String username);
}
