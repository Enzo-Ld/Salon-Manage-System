package com.bfsm.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public AdminSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumnIfMissing("product_category", "sort_no",
                "ALTER TABLE product_category ADD COLUMN sort_no INT NOT NULL DEFAULT 0 AFTER name");
        addColumnIfMissing("product_category", "status",
                "ALTER TABLE product_category ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ON' AFTER sort_no");
        addColumnIfMissing("product_category", "created_at",
                "ALTER TABLE product_category ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER status");
        addColumnIfMissing("product_category", "updated_at",
                "ALTER TABLE product_category ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");

        addColumnIfMissing("product", "intro",
                "ALTER TABLE product ADD COLUMN intro VARCHAR(500) NULL AFTER name");
        addColumnIfMissing("product", "image_url",
                "ALTER TABLE product ADD COLUMN image_url VARCHAR(255) NULL AFTER intro");
        addColumnIfMissing("product", "sort_no",
                "ALTER TABLE product ADD COLUMN sort_no INT NOT NULL DEFAULT 0 AFTER status");
        addColumnIfMissing("product", "created_at",
                "ALTER TABLE product ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER sort_no");
        addColumnIfMissing("product", "updated_at",
                "ALTER TABLE product ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");
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
