package com.kdh.solvego.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionMigrationTest {
    @Test
    @DisplayName("V4 구독 데이터를 V5로 이관한 뒤 V6에서 legacy 컬럼을 제거한다")
    void migrates_v4_through_v5_and_v6() throws Exception {
        String sourceUrl = environment(
                "TEST_DB_URL",
                "jdbc:mysql://localhost:3306/solvego_test"
        );
        String username = environment("TEST_DB_USERNAME", "root");
        String password = environment("TEST_DB_PASSWORD", "root_password");
        String databaseName = "solvego_migration_" + UUID.randomUUID()
                .toString()
                .replace("-", "");
        JdbcUrls urls = jdbcUrls(sourceUrl, databaseName);

        try (Connection connection = DriverManager.getConnection(
                urls.serverUrl(),
                username,
                password
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName + "`");
        }

        try {
            Flyway.configure()
                    .dataSource(urls.databaseUrl(), username, password)
                    .locations("classpath:db/migration")
                    .target("4")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    urls.databaseUrl(),
                    username,
                    password
            ); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users (
                            username,
                            password,
                            created_at,
                            subscription_plan,
                            subscription_status,
                            subscription_started_at,
                            subscription_expires_at
                        ) VALUES
                        (
                            'legacy-free',
                            'encoded',
                            '2026-01-01 00:00:00',
                            'FREE',
                            'INACTIVE',
                            NULL,
                            NULL
                        ),
                        (
                            'legacy-pro',
                            'encoded',
                            '2026-01-02 00:00:00',
                            'PRO',
                            'ACTIVE',
                            '2026-09-01 00:00:00',
                            '2026-10-01 00:00:00'
                        )
                        """);
            }

            Flyway.configure()
                    .dataSource(urls.databaseUrl(), username, password)
                    .locations("classpath:db/migration")
                    .target("5")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    urls.databaseUrl(),
                    username,
                    password
            ); Statement statement = connection.createStatement()) {
                ResultSet subscriptions = statement.executeQuery("""
                        SELECT
                            users.username,
                            subscriptions.plan,
                            subscriptions.status,
                            subscriptions.current_period_start_at,
                            subscriptions.current_period_end_at,
                            subscriptions.auto_renew
                        FROM subscriptions
                        JOIN users ON users.id = subscriptions.user_id
                        ORDER BY users.username
                        """);

                assertThat(subscriptions.next()).isTrue();
                assertThat(subscriptions.getString("username"))
                        .isEqualTo("legacy-free");
                assertThat(subscriptions.getString("plan")).isEqualTo("FREE");
                assertThat(subscriptions.getString("status")).isEqualTo("INACTIVE");
                assertThat(subscriptions.getTimestamp("current_period_start_at"))
                        .isNull();

                assertThat(subscriptions.next()).isTrue();
                assertThat(subscriptions.getString("username"))
                        .isEqualTo("legacy-pro");
                assertThat(subscriptions.getString("plan")).isEqualTo("PRO");
                assertThat(subscriptions.getString("status")).isEqualTo("ACTIVE");
                assertThat(subscriptions.getTimestamp("current_period_start_at"))
                        .isNotNull();
                assertThat(subscriptions.getTimestamp("current_period_end_at"))
                        .isNotNull();
                assertThat(subscriptions.getBoolean("auto_renew")).isFalse();
                assertThat(subscriptions.next()).isFalse();

                ResultSet payments = statement.executeQuery(
                        "SELECT COUNT(*) FROM payments"
                );
                assertThat(payments.next()).isTrue();
                assertThat(payments.getInt(1)).isZero();
            }

            Flyway.configure()
                    .dataSource(urls.databaseUrl(), username, password)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    urls.databaseUrl(),
                    username,
                    password
            ); Statement statement = connection.createStatement()) {
                ResultSet legacyColumns = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'users'
                          AND column_name IN (
                              'subscription_plan',
                              'subscription_status',
                              'subscription_started_at',
                              'subscription_expires_at'
                          )
                        """);
                assertThat(legacyColumns.next()).isTrue();
                assertThat(legacyColumns.getInt(1)).isZero();

                ResultSet migratedSubscriptions = statement.executeQuery(
                        "SELECT COUNT(*) FROM subscriptions"
                );
                assertThat(migratedSubscriptions.next()).isTrue();
                assertThat(migratedSubscriptions.getInt(1)).isEqualTo(2);

                ResultSet renewalCycleColumn = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'payments'
                          AND column_name = 'billing_cycle_at'
                        """);
                assertThat(renewalCycleColumn.next()).isTrue();
                assertThat(renewalCycleColumn.getInt(1)).isEqualTo(1);

                ResultSet renewalCycleConstraint = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'payments'
                          AND index_name = 'uk_payments_renewal_cycle'
                        """);
                assertThat(renewalCycleConstraint.next()).isTrue();
                assertThat(renewalCycleConstraint.getInt(1)).isEqualTo(3);
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(
                    urls.serverUrl(),
                    username,
                    password
            ); Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
            }
        }
    }

    private String environment(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    private JdbcUrls jdbcUrls(String sourceUrl, String databaseName) {
        int queryIndex = sourceUrl.indexOf('?');
        String query = queryIndex >= 0 ? sourceUrl.substring(queryIndex) : "";
        String urlWithoutQuery = queryIndex >= 0
                ? sourceUrl.substring(0, queryIndex)
                : sourceUrl;
        int databaseSeparator = urlWithoutQuery.lastIndexOf('/');
        String serverPrefix = urlWithoutQuery.substring(0, databaseSeparator + 1);
        return new JdbcUrls(
                serverPrefix + query,
                serverPrefix + databaseName + query
        );
    }

    private record JdbcUrls(String serverUrl, String databaseUrl) {
    }
}
