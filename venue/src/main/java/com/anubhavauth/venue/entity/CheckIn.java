package com.anubhavauth.venue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "check_ins",
        indexes = {
                @Index(name = "idx_ci_student_day_deleted", columnList = "student_id, day, deleted_at"),
                @Index(name = "idx_ci_room_day_deleted", columnList = "room_id, day, deleted_at"),
                @Index(name = "idx_ci_verifier_day", columnList = "verifier_id, day"),
                @Index(name = "idx_ci_deleted_at", columnList = "deleted_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    // NULL for volunteer check-ins (no room assignment)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = true)
    private Room room;

    // NULL for volunteer check-ins
    @Column(name = "seat_number", length = 10, nullable = true)
    private String seatNumber;

    // NULL for admin scans (bootstrap flow)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifier_id", nullable = true)
    private Verifier verifier;

    @CreationTimestamp
    @Column(name = "check_in_time", updatable = false)
    private LocalDateTime checkInTime;

    // "qrscan" or "adminScan"
    @Column(name = "method", length = 20, nullable = false)
    private String method;

    @Column(name = "day", length = 10, nullable = false)
    private String day;

    // NULL = active check-in, non-NULL = soft deleted
    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;
}
