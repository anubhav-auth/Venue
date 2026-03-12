package com.anubhavauth.venue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "room_roster",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_roster", columnNames = {"room_id", "student_id", "day"})
        },
        indexes = {
                @Index(name = "idx_roster_room_day",    columnList = "room_id, day"),
                @Index(name = "idx_roster_student_day", columnList = "student_id, day")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomRoster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "day", length = 10, nullable = false)
    private String day; // "day1" or "day2"

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;
}
