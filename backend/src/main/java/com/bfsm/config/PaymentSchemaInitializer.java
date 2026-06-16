package com.bfsm.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public PaymentSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumnIfMissing("wallet_txn", "txn_no",
                "ALTER TABLE wallet_txn ADD COLUMN txn_no VARCHAR(96) NULL UNIQUE AFTER id");
        jdbcTemplate.execute("ALTER TABLE payment_order MODIFY COLUMN pay_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL_CASHIER'");
        addColumnIfMissing("payment_order", "close_reason",
                "ALTER TABLE payment_order ADD COLUMN close_reason VARCHAR(255) NULL AFTER notify_content");
        addColumnIfMissing("payment_order", "failure_reason",
                "ALTER TABLE payment_order ADD COLUMN failure_reason VARCHAR(255) NULL AFTER close_reason");
        addColumnIfMissing("payment_refund", "refund_channel",
                "ALTER TABLE payment_refund ADD COLUMN refund_channel VARCHAR(32) NULL AFTER reason");
        addColumnIfMissing("payment_refund", "refund_mode",
                "ALTER TABLE payment_refund ADD COLUMN refund_mode VARCHAR(32) NULL AFTER refund_channel");
        addColumnIfMissing("payment_refund", "notify_content",
                "ALTER TABLE payment_refund ADD COLUMN notify_content TEXT NULL AFTER platform_refund_no");
        addColumnIfMissing("payment_refund", "refunded_time",
                "ALTER TABLE payment_refund ADD COLUMN refunded_time DATETIME NULL AFTER notify_content");
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND column_name = ?
                        """,
                Integer.class,
                tableName,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }
}
