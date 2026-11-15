package org.fogofwarlabs.screcorder.capture.starcraft;

import org.jetbrains.annotations.NotNull;

/**
 * Defines the supported capture layout and executable identity.
 */
public final class StarCraftLayout {
    public static final @NotNull String BUILD = "1.23.10.13515";

    public static final long CONTEXT_POINTER = 0xDB00D8;
    public static final long CONTEXT_KEY = 0x3703DB00831BDFE2L;
    public static final int FRAME_OFFSET = 0x14C;
    public static final int SCREEN_TILE_X_OFFSET = 0xE0;
    public static final int SCREEN_TILE_Y_OFFSET = 0xE2;
    public static final int ELAPSED_SECONDS_OFFSET = 0xE608;

    public static final long REPLAY_FLAG = 0x1095864;
    public static final long REPLAY_HEADER = 0x1095870;
    public static final int REPLAY_HEADER_SIZE = 0x279;

    public static final long RANDOM_BLOCK = 0x10936A0;
    public static final int RANDOM_BLOCK_SIZE = 0x410;
    public static final int RANDOM_TOTAL_OFFSET = 8;
    public static final int RANDOM_GATE_OFFSET = 12;
    public static final int RANDOM_COUNTERS_OFFSET = 16;

    public static final long TRIGGER_CYCLE = 0x10B0D30;

    public static final long UNIT_POOL_POINTER = 0xE78060;
    public static final long UNIT_POOL_SLOTS = 0xE78068;
    public static final int UNIT_STRIDE = 0x1E8;
    public static final int MAXIMUM_UNITS = 3400;

    public static final int UNIT_HIT_POINTS_OFFSET = 0x10;
    public static final int UNIT_ACTIVE_OFFSET = 0x18;
    public static final int UNIT_MOVEMENT_FLAGS_OFFSET = 0x38;
    public static final int UNIT_HEADING_OFFSET = 0x39;
    public static final int UNIT_POSITION_X_OFFSET = 0x40;
    public static final int UNIT_POSITION_Y_OFFSET = 0x42;
    public static final int UNIT_EXACT_POSITION_X_OFFSET = 0x44;
    public static final int UNIT_EXACT_POSITION_Y_OFFSET = 0x48;
    public static final int UNIT_CURRENT_SPEED_OFFSET = 0x50;
    public static final int UNIT_OWNER_OFFSET = 0x68;
    public static final int UNIT_ORDER_OFFSET = 0x69;
    public static final int UNIT_ORDER_STATE_OFFSET = 0x6A;
    public static final int UNIT_ORDER_TARGET_X_OFFSET = 0x78;
    public static final int UNIT_ORDER_TARGET_Y_OFFSET = 0x7A;
    public static final int UNIT_ORDER_TARGET_IDENTIFIER_OFFSET = 0x80;
    public static final int UNIT_SHIELDS_OFFSET = 0x88;
    public static final int UNIT_TYPE_OFFSET = 0x8C;
    public static final int UNIT_AUTO_TARGET_OFFSET = 0xB8;
    public static final int UNIT_ORDER_TIMER_OFFSET = 0xC9;
    public static final int UNIT_MOVEMENT_STATE_OFFSET = 0xDB;
    public static final int UNIT_ENERGY_OFFSET = 0xE6;
    public static final int UNIT_GENERATION_OFFSET = 0xE9;
    public static final int UNIT_SECONDARY_ORDER_OFFSET = 0xEA;
    public static final int UNIT_REMAINING_BUILD_TIME_OFFSET = 0xF0;
    public static final int UNIT_UNION_OFFSET = 0x128;
    public static final int UNIT_STATUS_OFFSET = 0x140;
    public static final int UNIT_CARRYING_FLAGS_OFFSET = 0x144;
    public static final int UNIT_RECORD_ADDRESS_OFFSET = 0x1C8;
    public static final int UNIT_AIR_STRENGTH_OFFSET = 0x1D0;
    public static final int UNIT_GROUND_STRENGTH_OFFSET = 0x1D2;

    public static final int MAXIMUM_READ = 64 * 1024 * 1024;

    public static final int IMAGE_TIMESTAMP = 0x6747C80C;
    public static final int IMAGE_SIZE = 0x1450000;
    public static final int IMAGE_MACHINE = 0x8664;


    private StarCraftLayout() {
    }
}
