package com.anubhavauth.venue.repository;

import com.anubhavauth.venue.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByRegNo(String regNo);

    boolean existsByRegNo(String regNo);

    List<Student> findByRole(String role);

    @Query("SELECT s.regNo FROM Student s")
    Set<String> findAllRegNos();

    @Query(
            value = "SELECT * FROM students WHERE " +
                    "(:search IS NULL OR name   ILIKE CONCAT('%', :search, '%') OR " +
                    "                    reg_no ILIKE CONCAT('%', :search, '%') OR " +
                    "                    degree ILIKE CONCAT('%', :search, '%'))",
            countQuery = "SELECT COUNT(*) FROM students WHERE " +
                    "(:search IS NULL OR name   ILIKE CONCAT('%', :search, '%') OR " +
                    "                    reg_no ILIKE CONCAT('%', :search, '%') OR " +
                    "                    degree ILIKE CONCAT('%', :search, '%'))",
            nativeQuery = true
    )
    Page<Student> searchStudents(@Param("search") String search, Pageable pageable);
}
