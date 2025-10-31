package com.cart.ecom_proj;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;  // ← ADD THIS

@SpringBootTest
@ActiveProfiles("test")  // ← ADD THIS LINE
class EcomProjApplicationTests {

    @Test
    void contextLoads() {
        // This will now PASS with H2 in-memory DB
    }
}
