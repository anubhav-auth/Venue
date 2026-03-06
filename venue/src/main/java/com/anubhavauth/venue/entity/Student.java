package com.anubhavauth.venue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "students",
        indexes = {
                @Index(name = "idx_students_email", columnList = "email"),
                @Index(name = "idx_students_role", columnList = "role"),
                @Index(name = "idx_students_is_promoted", columnList = "is_promoted")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "student_seq")
    @SequenceGenerator(name = "student_seq", sequenceName = "student_seq", allocationSize = 100)
    private Long id;

    @Column(name = "reg_no", length = 50, nullable = false, unique = true)
    private String regNo;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "passout_year")
    private Integer passoutYear;

    @Column(name = "degree", length = 10)
    private String degree;

    @Column(name = "contact_no", length = 15)
    private String contactNo;

    @Column(name = "role", length = 10)
    @Builder.Default
    private String role = "AUDIENCE";

    @Column(name = "qr_code_data", columnDefinition = "TEXT")
    private String qrCodeData;

    @Column(name = "is_promoted")
    @Builder.Default
    private Boolean isPromoted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
