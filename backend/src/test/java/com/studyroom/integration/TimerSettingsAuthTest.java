package com.studyroom.integration;

import com.studyroom.config.SecurityConfig;
import com.studyroom.room.Room;
import com.studyroom.room.RoomService;
import com.studyroom.security.JwtAuthenticationFilter;
import com.studyroom.security.JwtService;
import com.studyroom.timer.TimerController;
import com.studyroom.timer.TimerService;
import com.studyroom.timer.TimerState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only the participant who created the room may change shared timer settings. */
@WebMvcTest(TimerController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@TestPropertySource(properties = "STUDYROOM_JWT_SECRET=0123456789abcdef0123456789abcdef-test")
class TimerSettingsAuthTest {

    private static final String BODY =
            "{\"focusMinutes\":50,\"shortBreakMinutes\":10,\"longBreakMinutes\":20,\"autoAdvance\":true}";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean TimerService timers;
    @MockBean RoomService rooms;

    private void roomOwnedBy(UUID owner) {
        Instant now = Instant.now();
        when(rooms.findRoom("ABC123")).thenReturn(
                Optional.of(new Room("ABC123", owner, now, now.plusSeconds(3600))));
        when(timers.updateSettings(eq("ABC123"), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(TimerState.initial("ABC123"));
    }

    @Test
    void ownerCanChangeSettings() throws Exception {
        UUID owner = UUID.randomUUID();
        roomOwnedBy(owner);
        String token = jwt.issueToken(owner, "Owner");
        mvc.perform(put("/api/v1/rooms/ABC123/timer/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(timers).updateSettings("ABC123", 50, 10, 20, true);
    }

    @Test
    void otherParticipantIsForbidden() throws Exception {
        roomOwnedBy(UUID.randomUUID());
        String token = jwt.issueToken(UUID.randomUUID(), "Guest");
        mvc.perform(put("/api/v1/rooms/ABC123/timer/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(timers, never()).updateSettings(any(), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        UUID owner = UUID.randomUUID();
        roomOwnedBy(owner);
        String token = jwt.issueToken(owner, "Owner");
        mvc.perform(put("/api/v1/rooms/ABC123/timer/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"focusMinutes\":50}"))
                .andExpect(status().isBadRequest());
    }
}
