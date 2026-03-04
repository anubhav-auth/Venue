package com.anubhavauth.venue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "seat_assignments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_seat_assignment_student",
                        columnNames = {"student_id"}
                )
        },
        indexes = {
                @Index(name = "idx_sa_room_day", columnList = "room_id, day"),
                @Index(name = "idx_sa_room_seat_day", columnList = "room_id, seat_number, day"),
                @Index(name = "idx_sa_student_id", columnList = "student_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // NULL for overflow students — they have a room but no physical seat
    @Column(name = "seat_number", length = 10, nullable = true)
    private String seatNumber;

    @Column(name = "day", length = 10, nullable = false)
    private String day; // "day1" or "day2"

    @Column(name = "qr_code_data", columnDefinition = "TEXT")
    private String qrCodeData;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
