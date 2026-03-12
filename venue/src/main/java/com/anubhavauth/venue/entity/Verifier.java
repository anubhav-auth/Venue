package com.anubhavauth.venue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "verifiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Verifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    // Stores the volunteer's regNo — used as login username
    @Column(name = "username", length = 100, nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * When true: this verifier is a team lead (ROLE_TEAM_LEAD).
     * When false: regular verifier (ROLE_VERIFIER).
     * Takes effect on next login (JWT is stateless).
     */
    @Column(name = "is_team_lead", nullable = false)
    @Builder.Default
    private boolean isTeamLead = false;

    /**
     * The room this team lead is assigned to manage.
     * Null for regular verifiers or unassigned team leads.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_room_id")
    private Room assignedRoom;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // VerifierAssignment rows (one per day+room pair for legacy flow)
    @OneToMany(mappedBy = "verifier", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VerifierAssignment> assignments = new ArrayList<>();
}
