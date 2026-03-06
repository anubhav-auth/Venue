package com.anubhavauth.venue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "rooms",
        indexes = {
                @Index(name = "idx_rooms_day_room_name", columnList = "day, room_name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "room_seq")
    @SequenceGenerator(name = "room_seq", sequenceName = "room_seq", allocationSize = 100)
    private Long id;

    @Column(name = "room_name", length = 255, nullable = false)
    private String roomName;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "seats_per_row")
    @Builder.Default
    private Integer seatsPerRow = 10;

    @Column(name = "building", length = 255)
    private String building;

    @Column(name = "floor", length = 50)
    private String floor;

    @Column(name = "day", length = 10, nullable = false)
    private String day;  // "day1" or "day2"

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
