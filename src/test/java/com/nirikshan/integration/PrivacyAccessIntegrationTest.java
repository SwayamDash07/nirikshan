package com.nirikshan.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PrivacyAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void anonymousUsersCannotAccessOperationalFootage() throws Exception {
        mvc.perform(get("/job-files/1/annotated.mp4")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedVenueRequestsPassRecordedFootageSecurityMatchers() throws Exception {
        String signup = mapper.writeValueAsString(java.util.Map.of(
                "name", "Venue API Probe",
                "email", "venue-api-probe@example.com",
                "password", "StrongPass123!"));
        String body = mvc.perform(post("/api/auth/signup").contentType("application/json").content(signup))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode token = mapper.readTree(body).get("token");
        mvc.perform(get("/api/venues").header("Authorization", "Bearer " + token.asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("KIIT Campus 25"));
    }
}
