package com.studyroom.integration;

import com.studyroom.config.SecurityConfig;
import com.studyroom.config.SpaForwardController;
import com.studyroom.room.Room;
import com.studyroom.room.RoomController;
import com.studyroom.room.RoomService;
import com.studyroom.security.JwtAuthenticationFilter;
import com.studyroom.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves the JWT filter is actually wired into the security chain (not just unit-tested alone). */
@WebMvcTest({RoomController.class, SpaForwardController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@TestPropertySource(properties = "STUDYROOM_JWT_SECRET=0123456789abcdef0123456789abcdef-test")
class SecurityWiringTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean RoomService rooms;

    @Test
    void apiWithoutTokenIsRejected() throws Exception {
        int s = mvc.perform(get("/api/v1/rooms/ABC123")).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403, "status was " + s);
    }

    @Test
    void apiWithGarbageTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/rooms/ABC123").header("Authorization", "Bearer nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiWithValidTokenReachesController() throws Exception {
        when(rooms.findRoom("ABC123")).thenReturn(Optional.empty());
        String token = jwt.issueToken(UUID.randomUUID(), "Ada");
        mvc.perform(get("/api/v1/rooms/ABC123").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()); // reached controller; room just doesn't exist
    }

    @Test
    void spaDeepLinkForwardsToIndex() throws Exception {
        mvc.perform(get("/room/ABC123")).andExpect(status().isOk());
    }

    @Test
    void unknownPathsStayClosed() throws Exception {
        int s = mvc.perform(get("/secret")).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403, "status was " + s);
    }
}
