package com.finpilot;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FinPilotApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        Integer accountSourceColCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'accounts' AND column_name = 'source'",
                Integer.class
        );
        assertTrue(accountSourceColCount != null && accountSourceColCount > 0, "accounts.source column must exist");

        Integer txSourceColCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'transactions' AND column_name = 'source'",
                Integer.class
        );
        assertTrue(txSourceColCount != null && txSourceColCount > 0, "transactions.source column must exist");
    }
}
