package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the game option validation and the conversion between game type ids and SDK types.
 */
final class GameTemplateTest {
    /**
     * A game option length below the 32 bytes of the game options, and the message that rejects it.
     */
    private static final int SHORT_OPTIONS_SIZE = 4;
    private static final @NotNull String SHORT_OPTIONS_MESSAGE = "The game options must hold at least 32 bytes, not 4.";

    private static final int FIRST_GAME_TYPE = 2;
    private static final int LAST_GAME_TYPE = 15;
    private static final int TYPE_WITHOUT_SDK_TYPE = 14;
    private static final int TOP_VS_BOTTOM = 15;
    private static final int TOP_VS_BOTTOM_SDK_TYPE = 13;
    private static final int NO_SDK_TYPE = 0;
    private static final int NO_GAME_TYPE = 0;
    private static final int UNASSIGNED_SDK_TYPE = 14;

    @Test
    void parse_should_name_the_required_and_the_supplied_size_when_the_options_are_short() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> GameTemplate.parse(new byte[SHORT_OPTIONS_SIZE]));

        Assertions.assertEquals(SHORT_OPTIONS_MESSAGE, exception.getMessage());
    }

    @Test
    void game_types_round_trip_through_sdk_types_and_type_14_has_none() {
        for (int gameType = FIRST_GAME_TYPE; gameType <= LAST_GAME_TYPE; gameType++) {
            if (gameType == TYPE_WITHOUT_SDK_TYPE) {
                Assertions.assertEquals(NO_SDK_TYPE, GameTemplate.toSdkType(gameType));

                continue;
            }

            Assertions.assertEquals(gameType, GameTemplate.fromSdkType(GameTemplate.toSdkType(gameType)));
        }

        Assertions.assertEquals(TOP_VS_BOTTOM_SDK_TYPE, GameTemplate.toSdkType(TOP_VS_BOTTOM));
        Assertions.assertEquals(NO_SDK_TYPE, GameTemplate.toSdkType(NO_GAME_TYPE));
        Assertions.assertEquals(NO_GAME_TYPE, GameTemplate.fromSdkType(NO_SDK_TYPE));
        Assertions.assertEquals(NO_GAME_TYPE, GameTemplate.fromSdkType(UNASSIGNED_SDK_TYPE));
    }
}
