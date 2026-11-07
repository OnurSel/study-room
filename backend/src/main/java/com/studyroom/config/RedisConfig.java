package com.studyroom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Shared Redis serialization configuration for live room data.
 *
 * Keys and values are stored as UTF-8 strings. Services may serialize
 * structured room snapshots to JSON explicitly, without Java-specific
 * serialization metadata. Spring Boot configures the Redis connection from
 * spring.data.redis.* in application.yml.
 */
@Configuration
public class RedisConfig {

    @Bean("roomRedisTemplate")
    public RedisTemplate<String, String> roomRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        StringRedisSerializer serializer = new StringRedisSerializer();

        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.setEnableDefaultSerializer(false);
        template.afterPropertiesSet();

        return template;
    }
}
