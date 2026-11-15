package org.fogofwarlabs.sc4j.scr.game.movement;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Formats the digests of native movement captures, and loads or compares captured movement context fields.
 */
public final class MovementCheck {
    /**
     * The digest algorithm of the capture and result digests.
     */
    public static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    /**
     * The bytes of one captured movement context.
     */
    public static final int CONTEXT_CAPTURE_SIZE = 32;
    /**
     * The bytes of one captured unit record.
     */
    public static final int UNIT_CAPTURE_SIZE = 488;
    /**
     * The unit memory offset of the horizontal position WORD.
     */
    public static final int UNIT_POSITION_X_OFFSET = 0x40;
    /**
     * The unit memory offset of the vertical position WORD.
     */
    public static final int UNIT_POSITION_Y_OFFSET = 0x42;
    /**
     * The unit memory offset of the status flags DWORD.
     */
    public static final int UNIT_STATUS_OFFSET = 0x140;
    /**
     * The unit memory offset of the path collision flags BYTE.
     */
    public static final int UNIT_PATH_COLLISION_FLAGS_OFFSET = 0x191;

    /**
     * The capture offsets of the movement context fields.
     */
    private static final int POSITION_X_OFFSET = 0;
    private static final int POSITION_Y_OFFSET = 4;
    private static final int PREVIOUS_POSITION_X_OFFSET = 8;
    private static final int PREVIOUS_POSITION_Y_OFFSET = 12;
    private static final int FIXED_POSITION_X_OFFSET = 16;
    private static final int FIXED_POSITION_Y_OFFSET = 20;
    private static final int SPEED_OFFSET = 24;
    private static final int STARTING_MOVEMENT_OFFSET = 28;
    private static final int STOPPING_MOVEMENT_OFFSET = 29;
    private static final int PRE_MOVEMENT_FLAGS_OFFSET = 30;
    private static final int POST_MOVEMENT_FLAGS_OFFSET = 31;

    /**
     * Formats the {@link #DIGEST_ALGORITHM} digest of a capture.
     *
     * @param capture the capture contents
     * @return the lowercase hexadecimal digest
     * @throws NoSuchAlgorithmException if the runtime has no {@link #DIGEST_ALGORITHM} implementation
     */
    public static @NotNull String formatDigest(byte @NotNull [] capture) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(capture));
    }

    /**
     * Loads captured movement context fields into a context.
     *
     * @param capture the captured context of {@link #CONTEXT_CAPTURE_SIZE} bytes
     * @param context the context receiving the fields
     */
    public static void loadContext(byte @NotNull [] capture, @NotNull UnitMovement.Context context) {
        ByteBuffer input = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);

        context.setPositionX(input.getShort(POSITION_X_OFFSET));
        context.setPositionY(input.getShort(POSITION_Y_OFFSET));
        context.setPreviousPositionX(input.getShort(PREVIOUS_POSITION_X_OFFSET));
        context.setPreviousPositionY(input.getShort(PREVIOUS_POSITION_Y_OFFSET));
        context.setFixedPositionX(input.getInt(FIXED_POSITION_X_OFFSET));
        context.setFixedPositionY(input.getInt(FIXED_POSITION_Y_OFFSET));
        context.setSpeed(input.getInt(SPEED_OFFSET));
        context.setStartingMovement(input.get(STARTING_MOVEMENT_OFFSET));
        context.setStoppingMovement(input.get(STOPPING_MOVEMENT_OFFSET));
        context.setPreMovementFlags(input.get(PRE_MOVEMENT_FLAGS_OFFSET));
        context.setPostMovementFlags(input.get(POST_MOVEMENT_FLAGS_OFFSET));
    }

    /**
     * Compares a context with captured movement context fields.
     *
     * @param capture the captured context of {@link #CONTEXT_CAPTURE_SIZE} bytes
     * @param context the compared context
     */
    public static void assertState(byte @NotNull [] capture, @NotNull UnitMovement.Context context) {
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(expected.getShort(POSITION_X_OFFSET), context.getPositionX(), "positionX");
        Assertions.assertEquals(expected.getShort(POSITION_Y_OFFSET), context.getPositionY(), "positionY");
        Assertions.assertEquals(expected.getShort(PREVIOUS_POSITION_X_OFFSET), context.getPreviousPositionX(), "previousPositionX");
        Assertions.assertEquals(expected.getShort(PREVIOUS_POSITION_Y_OFFSET), context.getPreviousPositionY(), "previousPositionY");
        Assertions.assertEquals(expected.getInt(FIXED_POSITION_X_OFFSET), context.getFixedPositionX(), "fixedPositionX");
        Assertions.assertEquals(expected.getInt(FIXED_POSITION_Y_OFFSET), context.getFixedPositionY(), "fixedPositionY");
        Assertions.assertEquals(expected.getInt(SPEED_OFFSET), context.getSpeed(), "speed");
        Assertions.assertEquals(Byte.toUnsignedInt(expected.get(STARTING_MOVEMENT_OFFSET)), context.getStartingMovement(), "startingMovement");
        Assertions.assertEquals(Byte.toUnsignedInt(expected.get(STOPPING_MOVEMENT_OFFSET)), context.getStoppingMovement(), "stoppingMovement");
        Assertions.assertEquals(Byte.toUnsignedInt(expected.get(PRE_MOVEMENT_FLAGS_OFFSET)), context.getPreMovementFlags(), "preMovementFlags");
        Assertions.assertEquals(Byte.toUnsignedInt(expected.get(POST_MOVEMENT_FLAGS_OFFSET)), context.getPostMovementFlags(), "postMovementFlags");
    }


    /**
     * Creates a {@link MovementCheck} instance.
     */
    private MovementCheck() {
    }
}
