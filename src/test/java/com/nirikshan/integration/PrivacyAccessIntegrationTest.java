package com.nirikshan.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PrivacyAccessIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousUsersCannotAccessOperationalFootage() throws Exception {
        mvc.perform(get("/job-files/1/annotated.mp4")).andExpect(status().isUnauthorized());
    }
}
