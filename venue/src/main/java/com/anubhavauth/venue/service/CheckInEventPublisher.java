package com.anubhavauth.venue.service;

import com.anubhavauth.venue.event.CheckInEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckInEventPublisher {

    private final RedisTemplate<String, CheckInEvent> checkInRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(CheckInEvent event) {
        String channel = "checkin:" + event.getDay();
        try {
            // Publish as JSON string so listener can deserialize
            String json = objectMapper.writeValueAsString(event);
            checkInRedisTemplate.convertAndSend(channel, json);
        } catch (Exception e) {
            System.err.println("Failed to publish check-in event: " + e.getMessage());
        }
    }
}
