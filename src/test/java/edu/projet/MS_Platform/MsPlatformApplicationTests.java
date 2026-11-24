package edu.projet.MS_Platform;

import edu.projet.MS_Platform.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application against real PostgreSQL, Redis and Kafka containers.
 * Flyway runs the migrations and Hibernate validates the schema against the entities.
 * Requires Docker.
 */
@Testcontainers
@SpringBootTest(properties = "jwt.secret=test-secret-key-for-integration-tests-only-32b")
class MsPlatformApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.4.0");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void contextLoads() {
        // Flyway migrated the schema and JPA can query it
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void redisIsReachable() {
        redisTemplate.opsForValue().set("healthcheck", "ok");
        assertThat(redisTemplate.opsForValue().get("healthcheck")).isEqualTo("ok");
    }
}
