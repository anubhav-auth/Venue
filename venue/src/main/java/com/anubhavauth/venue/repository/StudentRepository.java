package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByRegNo(String regNo);

    boolean existsByRegNo(String regNo);

    List<Student> findByRole(String role);

    @Query("SELECT s.regNo FROM Student s")
    Set<String> findAllRegNos();

}
