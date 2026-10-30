-- V2__create_accounts_table.sql
-- Accounts table definition and indexes

CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    institution_name VARCHAR(100),
    account_number_last4 VARCHAR(4),
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_accounts_user_id (user_id),
    INDEX idx_accounts_type (type),
    INDEX idx_accounts_is_active (is_active)
);
