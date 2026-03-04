package com.anubhavauth.venue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "verifier_assignments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_verifier_day",
                        columnNames = {"verifier_id", "day"}
                )
        },
        indexes = {
                @Index(name = "idx_va_verifier_id", columnList = "verifier_id"),
                @Index(name = "idx_va_room_day", columnList = "room_id, day")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifierAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifier_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)   // ← DB-level cascade
    private Verifier verifier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "day", length = 10, nullable = false)
    private String day;
}
