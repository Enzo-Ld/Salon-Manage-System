package com.bfsm.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiConsultSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public AiConsultSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_consult_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    image_url VARCHAR(255) NULL,
                    input_text TEXT NULL,
                    summary_text TEXT NULL,
                    face_summary TEXT NULL,
                    hairstyle_json LONGTEXT NULL,
                    service_json LONGTEXT NULL,
                    product_json LONGTEXT NULL,
                    care_tips_json LONGTEXT NULL,
                    disclaimer TEXT NULL,
                    analysis_mode VARCHAR(32) NULL,
                    raw_response LONGTEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_ai_consult_user_created (user_id, created_at DESC)
                )
                """);
        jdbcTemplate.execute("ALTER TABLE ai_consult_record MODIFY image_url VARCHAR(255) NULL");
        addColumnIfMissing("ai_consult_record", "title", "ALTER TABLE ai_consult_record ADD COLUMN title VARCHAR(120) NULL AFTER user_id");
        addColumnIfMissing("ai_consult_record", "stage", "ALTER TABLE ai_consult_record ADD COLUMN stage VARCHAR(32) NULL AFTER title");
        addColumnIfMissing("ai_consult_record", "product_json", "ALTER TABLE ai_consult_record ADD COLUMN product_json LONGTEXT NULL AFTER service_json");
        addColumnIfMissing("ai_consult_record", "intent_tags_json", "ALTER TABLE ai_consult_record ADD COLUMN intent_tags_json LONGTEXT NULL AFTER care_tips_json");
        addColumnIfMissing("ai_consult_record", "preference_json", "ALTER TABLE ai_consult_record ADD COLUMN preference_json LONGTEXT NULL AFTER intent_tags_json");
        addColumnIfMissing("ai_consult_record", "recommendation_snapshot_json", "ALTER TABLE ai_consult_record ADD COLUMN recommendation_snapshot_json LONGTEXT NULL AFTER preference_json");
        addColumnIfMissing("ai_consult_record", "last_message_at", "ALTER TABLE ai_consult_record ADD COLUMN last_message_at DATETIME NULL AFTER recommendation_snapshot_json");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_consult_message (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    record_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    role VARCHAR(24) NOT NULL,
                    content TEXT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_ai_consult_message_record_created (record_id, created_at, id),
                    INDEX idx_ai_consult_message_user (user_id)
                )
                """);
        addColumnIfMissing("ai_consult_message", "image_url", "ALTER TABLE ai_consult_message ADD COLUMN image_url VARCHAR(255) NULL AFTER content");
        addColumnIfMissing("ai_consult_message", "metadata_json", "ALTER TABLE ai_consult_message ADD COLUMN metadata_json LONGTEXT NULL AFTER image_url");
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
