-- 手动创建数据库：
-- CREATE DATABASE bfsm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NULL,
    phone VARCHAR(32) NULL,
    avatar_url VARCHAR(255) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_sys_user_phone (phone),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_sur_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_sur_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO sys_role(code, name) VALUES
('USER', '普通用户'),
('TECH', '技师'),
('ADMIN', '管理员');

CREATE TABLE IF NOT EXISTS technician (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    level_name VARCHAR(64) NULL,
    score DECIMAL(4,2) NOT NULL DEFAULT 5.00,
    rating_avg DECIMAL(4,2) NOT NULL DEFAULT 5.00,
    rating_count INT NOT NULL DEFAULT 0,
    score_override DECIMAL(4,2) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tech_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    intro VARCHAR(500) NULL,
    image_url VARCHAR(255) NULL,
    duration_min INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ON',
    CONSTRAINT fk_item_category FOREIGN KEY (category_id) REFERENCES service_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS announcement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL,
    summary VARCHAR(255) NULL,
    cover_image VARCHAR(255) NULL,
    content TEXT NOT NULL,
    publish_status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    publish_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS banner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL,
    subtitle VARCHAR(255) NULL,
    image_url VARCHAR(255) NOT NULL,
    link_url VARCHAR(255) NULL,
    sort_no INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'ON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS technician_service (
    technician_id BIGINT NOT NULL,
    service_item_id BIGINT NOT NULL,
    PRIMARY KEY (technician_id, service_item_id),
    CONSTRAINT fk_ts_tech FOREIGN KEY (technician_id) REFERENCES technician(id),
    CONSTRAINT fk_ts_item FOREIGN KEY (service_item_id) REFERENCES service_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    technician_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'WORKING',
    CONSTRAINT fk_schedule_tech FOREIGN KEY (technician_id) REFERENCES technician(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS leave_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    technician_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_by_user_id BIGINT NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at DATETIME NULL,
    review_remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_leave_record_tech FOREIGN KEY (technician_id) REFERENCES technician(id),
    CONSTRAINT fk_leave_record_creator FOREIGN KEY (created_by_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_leave_record_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS attendance_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    technician_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    check_in_time DATETIME NULL,
    check_out_time DATETIME NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NOT_CHECKED_IN',
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_attendance_record UNIQUE (technician_id, work_date),
    CONSTRAINT fk_attendance_record_tech FOREIGN KEY (technician_id) REFERENCES technician(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(500) NOT NULL,
    target_path VARCHAR(255) NULL,
    read_status VARCHAR(16) NOT NULL DEFAULT 'UNREAD',
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appointment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    technician_id BIGINT NOT NULL,
    service_item_id BIGINT NOT NULL,
    appointment_time DATETIME NOT NULL,
    appointment_end_time DATETIME NULL,
    duration_min_snapshot INT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    assign_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    comment_text VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_appt_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_appt_tech FOREIGN KEY (technician_id) REFERENCES technician(id),
    CONSTRAINT fk_appt_item FOREIGN KEY (service_item_id) REFERENCES service_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appointment_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    from_status VARCHAR(16) NULL,
    to_status VARCHAR(16) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appt_log_appt FOREIGN KEY (appointment_id) REFERENCES appointment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    intro VARCHAR(500) NULL,
    image_url VARCHAR(255) NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ON',
    sort_no INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES product_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    item_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    pay_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
    pay_method VARCHAR(16) NULL,
    pay_no VARCHAR(64) NULL,
    pay_time DATETIME NULL,
    close_time DATETIME NULL,
    receiver_name VARCHAR(64) NULL,
    receiver_phone VARCHAR(32) NULL,
    province VARCHAR(64) NULL,
    city VARCHAR(64) NULL,
    district VARCHAR(64) NULL,
    detail_address VARCHAR(255) NULL,
    address_tag VARCHAR(32) NULL,
    shipping_company VARCHAR(64) NULL,
    tracking_no VARCHAR(64) NULL,
    shipped_time DATETIME NULL,
    after_sale_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    after_sale_reason VARCHAR(255) NULL,
    after_sale_applied_time DATETIME NULL,
    after_sale_handled_time DATETIME NULL,
    after_sale_remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_order_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES product_order(id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shopping_cart_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    selected TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(64) NOT NULL,
    receiver_phone VARCHAR(32) NOT NULL,
    province VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    detail_address VARCHAR(255) NOT NULL,
    tag_name VARCHAR(32) NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NULL,
    appointment_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_appt FOREIGN KEY (appointment_id) REFERENCES appointment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_wallet (
    user_id BIGINT PRIMARY KEY,
    balance DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_recharge DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_gift DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallet_txn (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    txn_no VARCHAR(96) NULL UNIQUE,
    user_id BIGINT NOT NULL,
    txn_type VARCHAR(32) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    balance_after DECIMAL(10,2) NOT NULL,
    related_type VARCHAR(32) NULL,
    related_id BIGINT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_txn_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recharge_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL,
    recharge_amount DECIMAL(10,2) NOT NULL,
    gift_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    payment_no VARCHAR(64) NOT NULL UNIQUE,
    out_trade_no VARCHAR(64) NOT NULL UNIQUE,
    pay_channel VARCHAR(32) NOT NULL,
    pay_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL_CASHIER',
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    subject VARCHAR(128) NULL,
    body_text VARCHAR(255) NULL,
    extra_info VARCHAR(255) NULL,
    platform_trade_no VARCHAR(64) NULL,
    notify_content TEXT NULL,
    close_reason VARCHAR(255) NULL,
    failure_reason VARCHAR(255) NULL,
    expire_time DATETIME NULL,
    paid_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_refund (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_order_id BIGINT NOT NULL,
    refund_no VARCHAR(64) NOT NULL UNIQUE,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    reason VARCHAR(255) NULL,
    refund_channel VARCHAR(32) NULL,
    refund_mode VARCHAR(32) NULL,
    platform_refund_no VARCHAR(64) NULL,
    notify_content TEXT NULL,
    refunded_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_refund_order FOREIGN KEY (payment_order_id) REFERENCES payment_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- For existing databases, apply a one-time migration similar to:
-- ALTER TABLE comment ADD COLUMN product_id BIGINT NULL;
-- ALTER TABLE comment MODIFY COLUMN appointment_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    service_item_id BIGINT NULL,
    product_id BIGINT NULL,
    announcement_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS like_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_msg_session FOREIGN KEY (session_id) REFERENCES chat_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认登录密码均为：123456
INSERT IGNORE INTO sys_user(id, username, password, nickname, phone, enabled) VALUES
(1, 'admin', '$2a$10$B9/5aXNAvVtzwOp8Hv10tuLExeVBIbductUsLqzv7dIvO1u7dUiay', '系统管理员', '13800000001', 1),
(2, 'tech_amy', '$2a$10$B9/5aXNAvVtzwOp8Hv10tuLExeVBIbductUsLqzv7dIvO1u7dUiay', '技师 Amy', '13800000002', 1),
(3, 'tech_bob', '$2a$10$B9/5aXNAvVtzwOp8Hv10tuLExeVBIbductUsLqzv7dIvO1u7dUiay', '技师 Bob', '13800000003', 1),
(4, 'user_tom', '$2a$10$B9/5aXNAvVtzwOp8Hv10tuLExeVBIbductUsLqzv7dIvO1u7dUiay', '用户 Tom', '13800000004', 1),
(5, 'user_lily', '$2a$10$B9/5aXNAvVtzwOp8Hv10tuLExeVBIbductUsLqzv7dIvO1u7dUiay', '用户 Lily', '13800000005', 1),
(6, 'tech_cindy', '$2a$10$B9/5aXNAvVtzwOp8Hv10tuLExeVBIbductUsLqzv7dIvO1u7dUiay', '技师 Cindy', '13800000006', 1),
(7, 'tech_jason', '$2a$10$B9/5aXNAvVtzwOp8Hv10tuLExeVBIbductUsLqzv7dIvO1u7dUiay', '技师 Jason', '13800000007', 1),
(8, 'tech_mia', '$2a$10$B9/5aXNAvVtzwOp8Hv10tuLExeVBIbductUsLqzv7dIvO1u7dUiay', '技师 Mia', '13800000008', 1);

INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT 1, id FROM sys_role WHERE code = 'ADMIN';
INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT 2, id FROM sys_role WHERE code = 'TECH';
INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT 3, id FROM sys_role WHERE code = 'TECH';
INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT 4, id FROM sys_role WHERE code = 'USER';
INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT 5, id FROM sys_role WHERE code = 'USER';
INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT 6, id FROM sys_role WHERE code = 'TECH';
INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT 7, id FROM sys_role WHERE code = 'TECH';
INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT 8, id FROM sys_role WHERE code = 'TECH';

INSERT IGNORE INTO technician(id, user_id, name, level_name, score, rating_avg, rating_count, score_override, status) VALUES
(1, 2, 'Amy', '高级技师', 4.90, 4.90, 0, 4.90, 'ACTIVE'),
(2, 3, 'Bob', '中级技师', 4.70, 4.70, 0, 4.70, 'ACTIVE'),
(3, 6, 'Cindy', '首席造型师', 4.95, 4.95, 0, 4.95, 'ACTIVE'),
(4, 7, 'Jason', '资深护理师', 4.86, 4.86, 0, 4.86, 'ACTIVE'),
(5, 8, 'Mia', '发型设计师', 4.88, 4.88, 0, 4.88, 'ACTIVE');

INSERT IGNORE INTO service_category(id, name, sort_no) VALUES
(1, '面部护理', 1),
(2, '身体理疗', 2),
(3, '发型设计', 3),
(4, '头皮养护', 4);

INSERT IGNORE INTO service_item(id, category_id, name, duration_min, price, status) VALUES
(1, 1, '补水清洁护理', 60, 199.00, 'ON'),
(2, 1, '深层修护护理', 90, 299.00, 'ON'),
(3, 2, '肩颈放松理疗', 45, 159.00, 'ON'),
(4, 1, '舒缓亮肤护理', 75, 239.00, 'ON'),
(5, 1, '紧致焕活护理', 90, 359.00, 'ON'),
(6, 2, '肩背经络舒缓', 60, 219.00, 'ON'),
(7, 2, '腰背放松理疗', 60, 229.00, 'ON'),
(8, 3, '女士精致剪发', 60, 168.00, 'ON'),
(9, 3, '男士清爽剪裁', 45, 128.00, 'ON'),
(10, 3, '轻盈空气烫设计', 150, 599.00, 'ON'),
(11, 3, '自然质感染发', 180, 699.00, 'ON'),
(12, 4, '头皮净澈护理', 45, 169.00, 'ON'),
(13, 4, '头皮舒缓修护', 60, 229.00, 'ON'),
(14, 1, '匀净亮白护理', 75, 269.00, 'ON'),
(15, 1, '深润补水护理', 60, 219.00, 'ON'),
(16, 1, '舒缓修护护理', 70, 249.00, 'ON'),
(17, 2, '芳香精油放松护理', 60, 239.00, 'ON'),
(18, 2, '全身舒压放松理疗', 90, 329.00, 'ON'),
(19, 4, '头皮控油平衡护理', 50, 189.00, 'ON');

INSERT IGNORE INTO technician_service(technician_id, service_item_id) VALUES
(1, 1), (1, 2), (1, 3),
(1, 4), (1, 5), (1, 12),
(2, 1), (2, 3), (2, 6), (2, 7), (2, 12),
(3, 8), (3, 10), (3, 11),
(4, 3), (4, 6), (4, 7), (4, 12), (4, 13),
(5, 4), (5, 8), (5, 9), (5, 13),
(1, 14), (1, 15), (1, 16), (1, 19),
(4, 17), (4, 18), (4, 19),
(5, 14), (5, 15), (5, 16),
(2, 17), (2, 18);

INSERT IGNORE INTO schedule(id, technician_id, work_date, start_time, end_time, status) VALUES
(1, 1, CURDATE(), '09:00:00', '18:00:00', 'WORKING'),
(2, 2, CURDATE(), '10:00:00', '19:00:00', 'WORKING'),
(3, 3, CURDATE(), '11:00:00', '20:00:00', 'WORKING'),
(4, 4, CURDATE(), '09:30:00', '18:30:00', 'WORKING'),
(5, 5, CURDATE(), '12:00:00', '21:00:00', 'WORKING');

INSERT IGNORE INTO announcement(id, title, content, publish_status) VALUES
(1, '系统试运行公告', 'BFSM 系统已进入试运行阶段，欢迎体验预约流程。', 'PUBLISHED'),
(2, '活动通知', '本月新用户首单可享 9 折优惠。', 'PUBLISHED');

INSERT IGNORE INTO product_category(id, name) VALUES
(1, '护肤产品'),
(2, '理疗周边');

INSERT IGNORE INTO product(id, category_id, name, price, stock, status) VALUES
(1, 1, '玻尿酸保湿面膜', 69.00, 100, 'ON'),
(2, 1, '舒缓修护精华', 199.00, 80, 'ON'),
(3, 2, '肩颈热敷贴', 39.00, 200, 'ON'),
(4, 1, '焕亮修护精华', 219.00, 80, 'ON'),
(5, 1, '舒缓补水面膜', 79.00, 150, 'ON'),
(6, 1, '亮肤保湿乳', 129.00, 120, 'ON'),
(7, 2, '薰衣草舒缓精油', 99.00, 60, 'ON'),
(8, 2, '蒸汽舒缓眼罩', 49.00, 200, 'ON');

INSERT IGNORE INTO service_item(id, category_id, name, intro, image_url, duration_min, price, status) VALUES
(20, 2, '深睡肩颈舒压', '围绕肩颈与头部压力点进行舒缓护理，适合睡眠浅、精神紧绷的人群。', 'https://images.unsplash.com/photo-1515377905703-c4788e51af15?auto=format&fit=crop&w=900&q=80', 75, 269.00, 'ON'),
(21, 2, '热石背部放松', '结合温热能量与背部舒缓手法，帮助释放背部僵硬和疲惫感。', 'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?auto=format&fit=crop&w=900&q=80', 80, 299.00, 'ON'),
(22, 2, '足腿循环舒缓', '针对小腿酸胀和久站疲劳进行放松护理，让身体重新轻盈起来。', 'https://images.unsplash.com/photo-1519823551278-64ac92734fb1?auto=format&fit=crop&w=900&q=80', 60, 199.00, 'ON'),
(23, 2, '芳香睡眠放松', '以温和芳香和舒缓节奏帮助进入放松状态，适合高压和睡前放松。', 'https://images.unsplash.com/photo-1600334089648-b0d9d3028eb2?auto=format&fit=crop&w=900&q=80', 70, 259.00, 'ON'),
(24, 1, '玫瑰润泽护理', '补充肌肤水分并提升柔润感，让面部状态更饱满、细腻。', 'https://images.unsplash.com/photo-1616394584738-fc6e612e71b9?auto=format&fit=crop&w=900&q=80', 70, 259.00, 'ON'),
(25, 1, '胶原弹润护理', '围绕弹润与细腻肤感进行护理，适合干纹明显和肤感粗糙人群。', 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=900&q=80', 90, 399.00, 'ON'),
(26, 1, '清透控油护理', '清理多余油脂并帮助维持清爽状态，适合混油和油性肌肤。', 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80', 60, 229.00, 'ON'),
(27, 1, '晒后舒缓修护', '针对日晒后泛红、干燥和紧绷感进行舒缓护理。', 'https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?auto=format&fit=crop&w=900&q=80', 75, 289.00, 'ON'),
(28, 1, '小气泡净肤护理', '温和清洁毛孔并改善粗糙肤感，适合作为周期性清洁护理。', 'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=900&q=80', 50, 189.00, 'ON'),
(29, 4, '头皮深呼吸护理', '帮助头皮恢复清爽舒适，适合头皮闷、痒或压力型紧绷。', 'https://images.unsplash.com/photo-1527799820374-dcf8d9d4a388?auto=format&fit=crop&w=900&q=80', 60, 239.00, 'ON'),
(30, 4, '发根蓬松养护', '改善发根扁塌和油腻观感，让发型更轻盈有空气感。', 'https://images.unsplash.com/photo-1519415943484-9fa1873496d4?auto=format&fit=crop&w=900&q=80', 55, 219.00, 'ON'),
(31, 4, '头皮敏感舒缓', '针对敏感、紧绷、干痒等状态进行温和舒缓护理。', 'https://images.unsplash.com/photo-1533640934426-4080c4506793?auto=format&fit=crop&w=900&q=80', 65, 259.00, 'ON'),
(32, 3, '通勤层次剪裁', '打造自然利落的通勤发型层次，兼顾精致度和日常易打理。', 'https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=900&q=80', 70, 198.00, 'ON'),
(33, 3, '法式空气刘海', '根据脸型修饰额头和脸部轮廓，打造轻盈柔和的刘海效果。', 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=900&q=80', 50, 158.00, 'ON'),
(34, 3, '男士纹理造型', '通过纹理修剪和轻造型提升头型比例，适合清爽自然风格。', 'https://images.unsplash.com/photo-1622286346003-cf0fdbda39c7?auto=format&fit=crop&w=900&q=80', 60, 188.00, 'ON'),
(35, 3, '发尾柔顺护理', '改善发尾毛躁和干枯状态，让发丝更顺滑、有光泽。', 'https://images.unsplash.com/photo-1605497788044-5a32c7078486?auto=format&fit=crop&w=900&q=80', 75, 299.00, 'ON'),
(36, 3, '冷棕护色染发', '选择低调耐看的冷棕色系，并同步关注染后发质护理。', 'https://images.unsplash.com/photo-1527799820374-dcf8d9d4a388?auto=format&fit=crop&w=900&q=80', 160, 659.00, 'ON'),
(37, 3, '蓬松发根烫', '改善发根贴头皮状态，让整体发型更立体自然。', 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=900&q=80', 120, 429.00, 'ON'),
(38, 2, '午间快速舒压', '适合午休或碎片时间的快速放松项目，缓解肩颈和精神疲惫。', 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=900&q=80', 35, 129.00, 'ON'),
(39, 1, '奢宠面颈护理', '同时关注面部与颈部肌肤状态，适合重要场合前的精致护理。', 'https://images.unsplash.com/photo-1598970434795-0c54fe7c0642?auto=format&fit=crop&w=900&q=80', 100, 459.00, 'ON');

INSERT IGNORE INTO technician_service(technician_id, service_item_id) VALUES
(1, 24), (1, 25), (1, 26), (1, 27), (1, 28), (1, 39),
(2, 20), (2, 21), (2, 22), (2, 23), (2, 38),
(3, 32), (3, 33), (3, 36), (3, 37),
(4, 20), (4, 21), (4, 22), (4, 23), (4, 29), (4, 31), (4, 38),
(5, 30), (5, 31), (5, 32), (5, 33), (5, 34), (5, 35), (5, 37);

INSERT IGNORE INTO product(id, category_id, name, price, stock, status) VALUES
(9, 2, '洋甘菊舒缓精油', 119.00, 90, 'ON'),
(10, 2, '薰衣草睡眠喷雾', 89.00, 160, 'ON'),
(11, 2, '肩颈温感按摩霜', 79.00, 120, 'ON'),
(12, 2, '草本热敷颈贴', 59.00, 180, 'ON'),
(13, 2, '足浴舒缓盐', 49.00, 220, 'ON'),
(14, 2, '香薰助眠蜡烛', 99.00, 100, 'ON'),
(15, 1, '积雪草修护面膜', 89.00, 150, 'ON'),
(16, 1, '神经酰胺保湿霜', 169.00, 110, 'ON'),
(17, 1, '烟酰胺亮肤精华', 199.00, 95, 'ON'),
(18, 1, '多肽紧致眼霜', 189.00, 85, 'ON'),
(19, 1, '温和氨基酸洁面', 79.00, 180, 'ON'),
(20, 1, '清透控油爽肤水', 109.00, 140, 'ON'),
(21, 1, '高保湿修护乳', 139.00, 130, 'ON'),
(22, 1, '玻尿酸安瓶精华', 229.00, 70, 'ON'),
(23, 1, '舒缓晒后凝胶', 69.00, 160, 'ON'),
(24, 2, '真丝放松眼罩', 69.00, 200, 'ON'),
(25, 2, '头皮舒缓按摩梳', 59.00, 150, 'ON'),
(26, 2, '护发柔顺精油', 129.00, 100, 'ON'),
(27, 2, '免洗蓬松喷雾', 99.00, 120, 'ON'),
(28, 2, '护色修护发膜', 159.00, 90, 'ON');
