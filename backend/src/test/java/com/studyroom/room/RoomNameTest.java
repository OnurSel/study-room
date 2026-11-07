package com.studyroom.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RoomNameTest {

    @Test
    void blankOrMissingNamesBecomeNull() {
        assertNull(RoomService.cleanName(null));
        assertNull(RoomService.cleanName("   \n\t "));
    }

    @Test
    void namesAreTrimmedCollapsedAndLimited() {
        assertEquals("Finals prep", RoomService.cleanName("  Finals \n  prep "));
        assertEquals(RoomService.MAX_NAME_LENGTH, RoomService.cleanName("x".repeat(100)).length());
    }
}
