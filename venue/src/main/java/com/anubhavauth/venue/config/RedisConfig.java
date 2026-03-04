package com.anubhavauth.venue.config;

import com.anubhavauth.venue.event.CheckInEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, CheckInEvent> checkInRedisTemplate(
            RedisConnectionFactory factory, ObjectMapper objectMapper) {

        RedisTemplate<String, CheckInEvent> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, CheckInEvent.class));
        return template;
    }

    @Bean
    public RedisMessageListenerContainer listenerContainer(
            RedisConnectionFactory factory,
            MessageListenerAdapter listenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(listenerAdapter, new PatternTopic("checkin:*"));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(
            SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {

        return new MessageListenerAdapter((org.springframework.data.redis.connection.MessageListener)
                (message, pattern) -> {
                    try {
                        String channel = new String(message.getChannel());
                        String body = new String(message.getBody());
                        // Strip wrapping quotes added by Redis serializer
                        if (body.startsWith("\"")) {
                            body = objectMapper.readValue(body, String.class);
                        }
                        CheckInEvent event = objectMapper.readValue(body, CheckInEvent.class);

                        // Extract day from channel: "checkin:day1" → "day1"
                        String day = channel.contains(":") ? channel.split(":")[1] : channel;
                        // Broadcast to WebSocket topic: /topic/checkin/day1
                        messagingTemplate.convertAndSend("/topic/checkin/" + day, event);
                    } catch (Exception e) {
                        System.err.println("Redis listener error: " + e.getMessage());
                    }
                });
    }
}
