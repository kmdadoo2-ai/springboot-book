package com.study.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc // MockMvc 객체를 자동으로 주입받기 위해 추가
class ApplicationTests {

	@Autowired
	private MockMvc mvc; // 테스트 실행에 필요한 MockMvc 필드 주입

	@Test
	void contextLoads() {
	}

	@Test
	void eachRequestHasItsOwnRequestId() throws Exception {
		String first = mvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(header().exists("X-Request-ID"))
				.andReturn().getResponse().getHeader("X-Request-ID");

		String second = mvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getHeader("X-Request-ID");

		assertThat(first).isNotBlank();
		assertThat(second).isNotBlank().isNotEqualTo(first);
	}
}
