package com.shop.supportservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
@SpringBootTest
class SupportServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
