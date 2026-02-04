package dev.tracekit.example;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email
     * This query will be automatically traced by OpenTelemetry JDBC instrumentation
     */
    Optional<User> findByEmail(String email);

    /**
     * Find users by age greater than specified value
     * This query will be automatically traced by OpenTelemetry JDBC instrumentation
     */
    java.util.List<User> findByAgeGreaterThan(Integer age);
}
