package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks the alliance bytes and shared-vision masks of initialized player relations (melee players, map-settings forces, Top vs Bottom teams and
 * allied computers), the alliance and shared-vision commands of an issuer and the game options that allow alliances.
 */
final class PlayerRelationsTest {
    /**
     * The number of captured player relation cases.
     */
    private static final int CASE_COUNT = 512;
    /**
     * The period of the case bit that selects a player's type.
     */
    private static final int TYPE_SHIFT_PERIOD = 8;
    /**
     * The player type distance between neighboring players.
     */
    private static final int TYPE_STEP = 3;
    /**
     * The number of player types that the cases cover.
     */
    private static final int TYPE_COUNT = 10;
    /**
     * The case bit that selects allied computer players.
     */
    private static final int ALLIED_COMPUTERS_CASE_BIT = 1;
    /**
     * The allies option value that allows alliances.
     */
    private static final int ALLIES_ON = 1;
    /**
     * The allies option value that forbids alliances.
     */
    private static final int ALLIES_OFF = 0;
    /**
     * An allies option value other than {@link #ALLIES_ON} and {@link #ALLIES_OFF}.
     */
    private static final int OTHER_ALLIES_VALUE = 2;
    /**
     * The tournament option value of a tournament game.
     */
    private static final int TOURNAMENT_ON = 1;
    /**
     * The tournament option value of a game outside tournament mode.
     */
    private static final int TOURNAMENT_OFF = 0;
    /**
     * The alliance value of an enemy.
     */
    private static final int ENEMY = 0;
    /**
     * The alliance value of an ally.
     */
    private static final int ALLIED = 1;
    /**
     * The alliance value of an ally sharing victory.
     */
    private static final int ALLIED_VICTORY = 2;
    /**
     * The force flag that allies the force members.
     */
    private static final int FORCE_ALLIED = 0x02;
    /**
     * The force flag that makes the force members share victory.
     */
    private static final int FORCE_ALLIED_VICTORY = 0x04;
    /**
     * The force flag that makes the force members share vision.
     */
    private static final int FORCE_SHARED_VISION = 0x08;
    /**
     * The vision of players {@code 0} and {@code 1}.
     */
    private static final int FIRST_TWO_PLAYERS_VISION = 0b11;
    /**
     * The vision of player {@code 0} alone.
     */
    private static final int FIRST_PLAYER_VISION = 0b1;
    /**
     * The vision of player {@code 2} alone.
     */
    private static final int THIRD_PLAYER_VISION = 0b100;
    /**
     * The vision of player {@code 3} alone.
     */
    private static final int FOURTH_PLAYER_VISION = 0b1000;
    /**
     * The vision of players {@code 0} and {@code 3}.
     */
    private static final int FIRST_AND_FOURTH_PLAYERS_VISION = 0b1001;
    /**
     * The force BYTE of a player outside every force.
     */
    private static final int NO_FORCE = 0;
    /**
     * The force BYTE of a first force member.
     */
    private static final int FIRST_FORCE = 1;
    /**
     * The force BYTE of a second force member.
     */
    private static final int SECOND_FORCE = 2;
    /**
     * The FORC flag BYTE of a force without alliance and vision flags.
     */
    private static final int NO_FORCE_FLAGS = 0;
    /**
     * The first player slot.
     */
    private static final int FIRST_PLAYER = 0;
    /**
     * The second player slot.
     */
    private static final int SECOND_PLAYER = 1;
    /**
     * The third player slot.
     */
    private static final int THIRD_PLAYER = 2;
    /**
     * The fourth player slot.
     */
    private static final int FOURTH_PLAYER = 3;
    /**
     * The fifth player slot.
     */
    private static final int FIFTH_PLAYER = 4;
    /**
     * The neutral player slot.
     */
    private static final int NEUTRAL_PLAYER = 11;
    /**
     * The bits of one player's alliance value in the recorded alliance DWORD.
     */
    private static final int ALLIANCE_BITS = 2;
    /**
     * The mask of one player's alliance value.
     */
    private static final int ALLIANCE_MASK = 3;
    /**
     * A recorded alliance DWORD that allies players {@code 1}, {@code 5} and {@code 11} and allies player {@code 3} with shared victory.
     */
    private static final int PACKED_ALLIANCES = 0x00400484;
    /**
     * The length of an alliance row shorter than the player slots.
     */
    private static final int SHORT_ROW_LENGTH = 11;
    /**
     * The shared vision of the issuer before the command.
     */
    private static final int INITIAL_VISION = 0x0302;
    /**
     * The shared vision that the command requests.
     */
    private static final int COMMAND_VISION = 0x0105;
    /**
     * The low byte of a shared-vision DWORD, which the command replaces.
     */
    private static final int LOW_BYTE_MASK = 0xFF;
    /**
     * The second byte of a shared-vision DWORD, which the command keeps.
     */
    private static final int SECOND_BYTE_MASK = 0xFF00;
    /**
     * The shared vision of the issuer after the command.
     */
    private static final int UPDATED_VISION = 0x0305;
    /**
     * The map width of the sight coverage.
     */
    private static final int MAP_SIZE = 64;
    /**
     * The human player that issues the alliance and vision commands.
     */
    private static final int ISSUER = 1;
    /**
     * The player that {@link #PACKED_ALLIANCES} allies with shared victory.
     */
    private static final int ALLIED_VICTORY_PLAYER = 3;
    /**
     * A player other than the issuer that {@link #PACKED_ALLIANCES} allies.
     */
    private static final int ALLIED_PLAYER = 5;
    /**
     * The shared vision of a sight coverage before the world creation stores the game's masks.
     */
    private static final int NO_SHARED_VISION = 0;
    /**
     * The human player slot of the allied-computer game.
     */
    private static final int HUMAN_SLOT = 0;
    /**
     * The first computer player slot of the allied-computer game.
     */
    private static final int FIRST_COMPUTER_SLOT = 1;
    /**
     * The second computer player slot of the allied-computer game.
     */
    private static final int SECOND_COMPUTER_SLOT = 2;

    private static @NotNull PlayerRelations createRelations() {
        int[] playerTypes = new int[PlayerTable.PLAYER_SLOTS];

        playerTypes[FIRST_PLAYER] = PlayerTypes.HUMAN;
        playerTypes[ISSUER]       = PlayerTypes.HUMAN;

        return new PlayerRelations(playerTypes, false, false, false);
    }


    @Test
    void matches_native_player_relation_initialization_cases() throws IOException {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PLAYER_RELATIONS);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);

        for (int caseIndex = 0; caseIndex < CASE_COUNT; caseIndex++) {
            int[] playerTypes = new int[PlayerTable.PLAYER_SLOTS];

            for (int playerIndex = 0; playerIndex < PlayerTable.PLAYER_SLOTS; playerIndex++) {
                playerTypes[playerIndex] = ((caseIndex >> (playerIndex % TYPE_SHIFT_PERIOD)) + playerIndex * TYPE_STEP) % TYPE_COUNT;
            }

            PlayerRelations relations = new PlayerRelations(playerTypes, (caseIndex & ALLIED_COMPUTERS_CASE_BIT) != 0, false, false);
            byte[] alliances = new byte[PlayerTable.PLAYER_SLOTS * PlayerTable.PLAYER_SLOTS];

            expected.get(alliances);

            Assertions.assertArrayEquals(alliances, relations.getAllianceBytes(), "case=" + caseIndex);

            int[] sharing = relations.getSharedVisionMasks();

            for (int playerIndex = 0; playerIndex < PlayerTable.PLAYER_SLOTS; playerIndex++) {
                Assertions.assertEquals(expected.getInt(), sharing[playerIndex], "case=" + caseIndex + ",player=" + playerIndex);
            }
        }

        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void are_alliances_allowed_should_return_true_only_with_allies_on_and_tournament_off() {
        Assertions.assertTrue(PlayerRelations.areAlliancesAllowed(ALLIES_ON, TOURNAMENT_OFF));
        Assertions.assertFalse(PlayerRelations.areAlliancesAllowed(ALLIES_OFF, TOURNAMENT_OFF));
        Assertions.assertFalse(PlayerRelations.areAlliancesAllowed(OTHER_ALLIES_VALUE, TOURNAMENT_OFF), "Only the allies value 1 allows alliances.");
        Assertions.assertFalse(PlayerRelations.areAlliancesAllowed(ALLIES_ON, TOURNAMENT_ON));
    }

    @Test
    void force_flags_apply_alliance_and_vision_to_human_force_members() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.COMPUTER, PlayerTypes.HUMAN,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NEUTRAL};
        int[] forces = {FIRST_FORCE, FIRST_FORCE, SECOND_FORCE, FIRST_FORCE, SECOND_FORCE,
                        NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE};

        // Force 1: allied without allied victory and shared vision. Force 2: allied victory without vision.
        int[] forceFlags = {FORCE_ALLIED | FORCE_SHARED_VISION, FORCE_ALLIED | FORCE_ALLIED_VICTORY, NO_FORCE_FLAGS, NO_FORCE_FLAGS};
        PlayerRelations relations = new PlayerRelations(types, forces, false, forceFlags, false);

        Assertions.assertEquals(ALLIED, relations.getAlliance(FIRST_PLAYER, SECOND_PLAYER));
        Assertions.assertEquals(ALLIED, relations.getAlliance(FIRST_PLAYER, FIRST_PLAYER), "The diagonal is written with the force value.");
        Assertions.assertEquals(ENEMY, relations.getAlliance(FIRST_PLAYER, FOURTH_PLAYER), "Computer force members are excluded.");
        Assertions.assertEquals(ALLIED_VICTORY, relations.getAlliance(THIRD_PLAYER, FIFTH_PLAYER));
        Assertions.assertEquals(ALLIED_VICTORY, relations.getAlliance(FIFTH_PLAYER, FIFTH_PLAYER));
        Assertions.assertEquals(ENEMY, relations.getAlliance(FIRST_PLAYER, THIRD_PLAYER));
        Assertions.assertEquals(ALLIED, relations.getAlliance(FIRST_PLAYER, NEUTRAL_PLAYER), "The neutral relation is kept.");
        Assertions.assertEquals(FIRST_TWO_PLAYERS_VISION, relations.getSharedVision(FIRST_PLAYER));
        Assertions.assertEquals(FIRST_TWO_PLAYERS_VISION, relations.getSharedVision(SECOND_PLAYER));
        Assertions.assertEquals(THIRD_PLAYER_VISION, relations.getSharedVision(THIRD_PLAYER), "Force 2 has no vision flag.");
        Assertions.assertEquals(FOURTH_PLAYER_VISION, relations.getSharedVision(FOURTH_PLAYER));
    }

    @Test
    void force_alliances_clear_without_allied_flag_but_keep_diagonal() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.HUMAN,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        int[] forces = {FIRST_FORCE, FIRST_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE};
        PlayerRelations relations = new PlayerRelations(types, forces, false, new int[PlayerTable.FORCE_COUNT], false);

        Assertions.assertEquals(ALLIED, relations.getAlliance(FIRST_PLAYER, FIRST_PLAYER));
        Assertions.assertEquals(ENEMY, relations.getAlliance(FIRST_PLAYER, SECOND_PLAYER));
        Assertions.assertEquals(FIRST_PLAYER_VISION, relations.getSharedVision(FIRST_PLAYER));
    }

    @Test
    void top_versus_bottom_allies_teams_including_the_diagonal() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.HUMAN, PlayerTypes.HUMAN,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        int[] forces = {FIRST_FORCE, FIRST_FORCE, SECOND_FORCE, FIRST_FORCE,
                        NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE, NO_FORCE};
        PlayerRelations relations = new PlayerRelations(types, forces, false, null, true);

        Assertions.assertEquals(ALLIED_VICTORY, relations.getAlliance(FIRST_PLAYER, FOURTH_PLAYER));
        Assertions.assertEquals(ALLIED_VICTORY, relations.getAlliance(FIRST_PLAYER, FIRST_PLAYER));
        Assertions.assertEquals(ENEMY, relations.getAlliance(FIRST_PLAYER, THIRD_PLAYER));
        Assertions.assertEquals(ALLIED, relations.getAlliance(SECOND_PLAYER, SECOND_PLAYER), "Players without a type keep the default diagonal.");
        Assertions.assertEquals(FIRST_AND_FOURTH_PLAYERS_VISION, relations.getSharedVision(FIRST_PLAYER));
        Assertions.assertEquals(THIRD_PLAYER_VISION, relations.getSharedVision(THIRD_PLAYER));
    }

    @Test
    void set_alliances_should_replace_only_issuer_row_from_two_bit_fields() {
        PlayerRelations relations = createRelations();
        int[] alliances = new int[PlayerTable.PLAYER_SLOTS];

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            alliances[player] = (PACKED_ALLIANCES >>> (player * ALLIANCE_BITS)) & ALLIANCE_MASK;
        }

        relations.setAlliances(ISSUER, alliances);

        Assertions.assertEquals(ENEMY, relations.getAlliance(ISSUER, FIRST_PLAYER));
        Assertions.assertEquals(ALLIED, relations.getAlliance(ISSUER, ISSUER));
        Assertions.assertEquals(ALLIED_VICTORY, relations.getAlliance(ISSUER, ALLIED_VICTORY_PLAYER));
        Assertions.assertEquals(ALLIED, relations.getAlliance(ISSUER, ALLIED_PLAYER));
        Assertions.assertEquals(ALLIED, relations.getAlliance(ISSUER, NEUTRAL_PLAYER));
        Assertions.assertEquals(ALLIED, relations.getAlliance(FIRST_PLAYER, FIRST_PLAYER), "Other rows are unchanged.");
        Assertions.assertEquals(ENEMY, relations.getAlliance(FIRST_PLAYER, ISSUER));
        Assertions.assertThrows(IllegalArgumentException.class, () -> relations.setAlliances(ISSUER, new int[SHORT_ROW_LENGTH]));
    }

    @Test
    void shared_vision_update_replaces_only_low_byte_in_relations_and_sight() {
        PlayerRelations relations = createRelations();

        relations.setSharedVision(ISSUER, INITIAL_VISION);

        SightCoverage sight = new SightCoverage(MAP_SIZE);

        sight.setSharedVisionMasks(relations.getSharedVisionMasks());

        int mask = (COMMAND_VISION & LOW_BYTE_MASK) | (relations.getSharedVision(ISSUER) & SECOND_BYTE_MASK);

        relations.setSharedVision(ISSUER, mask);
        sight.setSharedVisionMask(ISSUER, mask);

        Assertions.assertEquals(UPDATED_VISION, relations.getSharedVision(ISSUER));
        Assertions.assertEquals(UPDATED_VISION, sight.getSharedVisionMask(ISSUER));
        Assertions.assertEquals(FIRST_PLAYER_VISION, sight.getSharedVisionMask(FIRST_PLAYER));

        // The shared-vision DWORDs are game context storage: zero until the world creation stores the game's masks.
        SightCoverage fresh = new SightCoverage(MAP_SIZE);

        Assertions.assertEquals(NO_SHARED_VISION, fresh.getSharedVisionMask(FIRST_PLAYER));
    }

    @Test
    void allied_computers_become_mutual_allies_but_not_of_humans() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.COMPUTER, PlayerTypes.COMPUTER,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        PlayerRelations allied = new PlayerRelations(types, new int[PlayerTable.PLAYER_SLOTS], true, null, false);

        Assertions.assertEquals(ALLIED_VICTORY, allied.getAlliance(FIRST_COMPUTER_SLOT, SECOND_COMPUTER_SLOT));
        Assertions.assertEquals(ALLIED_VICTORY, allied.getAlliance(SECOND_COMPUTER_SLOT, FIRST_COMPUTER_SLOT));
        Assertions.assertEquals(ENEMY, allied.getAlliance(HUMAN_SLOT, FIRST_COMPUTER_SLOT));
        Assertions.assertEquals(ENEMY, allied.getAlliance(FIRST_COMPUTER_SLOT, HUMAN_SLOT));

        PlayerRelations apart = new PlayerRelations(types, new int[PlayerTable.PLAYER_SLOTS], false, null, false);

        Assertions.assertEquals(ENEMY, apart.getAlliance(FIRST_COMPUTER_SLOT, SECOND_COMPUTER_SLOT));
    }
}
