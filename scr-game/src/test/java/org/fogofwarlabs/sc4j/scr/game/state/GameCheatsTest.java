package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.CampaignMissionScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.visibility.MapVisibility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks cheat enablement, multiplayer gating, resource consumption, the damage gate without a player and the requirement waivers of
 * typed cheats in a running campaign mission.
 */
@RequiresInstallation
final class GameCheatsTest {
    private static final int MAP_WIDTH = 8;
    private static final int MAP_HEIGHT = 8;
    private static final int TILE_COUNT = MAP_WIDTH * MAP_HEIGHT;

    private static final int FIRST_PLAYER = 0;
    private static final int SECOND_PLAYER = 1;
    private static final int THIRD_PLAYER = 2;
    private static final int SINGLE_PLAYER_MODE = 0;
    private static final int MULTIPLAYER_MODE = 1;
    private static final int CHEATS_DISABLED = 0;
    private static final int CHEATS_ENABLED = 1;
    private static final int UNSUPPORTED_CHEAT_OPTION = 2;
    /**
     * The minerals of a human player after Show Me The Money, and the minerals and gas after Show Me The Money with What's Mine Is Mine
     * and Breathe Deep.
     */
    private static final int SHOW_ME_THE_MONEY_MINERALS = 10000;
    private static final int COMBINED_CHEAT_MINERALS = 10500;
    private static final int COMBINED_CHEAT_GAS = 10500;
    /**
     * The flags of the Operation Cwal and Medieval Man cheats.
     */
    private static final int OPERATION_CWAL_FLAG = 0x2;
    private static final int MEDIEVAL_MAN_FLAG = 0x1000;

    /**
     * The campaign mission of the requirement waiver test.
     */
    private static final @NotNull String TERRAN_MISSION = "bw-terran-1";
    private static final int STARTUP_TURNS = 3;
    private static final int CHEAT_TURNS = 2;
    /**
     * The Unix time of a typed line.
     */
    private static final int TYPED_TIME = 0x6543210F;
    private static final int FINAL_TURNS = 30;
    /**
     * The cheat flags of the typed Black Sheep Wall, Operation Cwal, Power Overwhelming, Staying Alive, The Gathering, Medieval Man and War Aint What
     * It Used To Be cheats.
     */
    private static final int TYPED_CHEAT_FLAGS = 0x5907;
    /**
     * The flag of the Modify The Phase Variance cheat, which waives the production requirements.
     */
    private static final int MODIFY_THE_PHASE_VARIANCE_FLAG = 0x2000;
    /**
     * The flag of the Show Me The Money cheat, which the game does not keep.
     */
    private static final int SHOW_ME_THE_MONEY_FLAG = 0x10;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void closeGameData() {
        runtime = null;

        if (gameData != null) {
            gameData.close();
        }
    }

    /**
     * Creates a {@link Game} instance with the frame controls required by these cheat checks.
     *
     * @param mode the chosen visibility-override mode
     * @return the created {@link Game} instance
     * @throws IOException if the fixture terrain records cannot be parsed
     */
    private static @NotNull Game createGame(int mode) throws IOException {
        Game game = Game.create(gameData, new GameMap(MAP_WIDTH, MAP_HEIGHT, 0, new int[TILE_COUNT], new int[TILE_COUNT]), new LcgRandom(1));

        game.getPlayerTable().setPlayer(FIRST_PLAYER, new Player(FIRST_PLAYER, FIRST_PLAYER, PlayerTypes.HUMAN, 0, 0));
        game.getPlayerTable().setPlayer(SECOND_PLAYER, new Player(SECOND_PLAYER, SECOND_PLAYER, PlayerTypes.COMPUTER, 0, 0));
        game.setFrame(GameCheatsScenario.createFrame(mode));

        return game;
    }

    private static @NotNull Unit findUnit(@NotNull Game state, int owner, int type) {
        UnitPool pool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit unit = pool.getUnit(index);

            if (unit.getOwnerIndex() == owner && unit.getUnitId() == type) {
                return unit;
            }
        }

        return Assertions.fail("No unit of type " + type + " owned by player " + owner);
    }


    @Test
    void apply_should_respect_cheat_option_and_block_multiplayer_with_another_human() throws IOException {
        Game game = createGame(SINGLE_PLAYER_MODE);

        game.setCheatsEnabled(CHEATS_DISABLED);
        game.getGameCheats().apply(GameCheats.OPERATION_CWAL | GameCheats.SHOW_ME_THE_MONEY);

        Player disabledCheatsPlayer = game.getPlayerTable().getPlayer(FIRST_PLAYER);

        Assertions.assertNotNull(disabledCheatsPlayer);

        Assertions.assertEquals(0, disabledCheatsPlayer.getMinerals());
        Assertions.assertFalse(game.isOperationCwal());

        game.setCheatsEnabled(UNSUPPORTED_CHEAT_OPTION);
        game.getGameCheats().apply(GameCheats.OPERATION_CWAL | GameCheats.SHOW_ME_THE_MONEY);

        Player unsupportedCheatsPlayer = game.getPlayerTable().getPlayer(FIRST_PLAYER);

        Assertions.assertNotNull(unsupportedCheatsPlayer);

        Assertions.assertEquals(0, unsupportedCheatsPlayer.getMinerals());

        game.setCheatsEnabled(CHEATS_ENABLED);
        game.getGameCheats().apply(GameCheats.OPERATION_CWAL | GameCheats.SHOW_ME_THE_MONEY);

        Player enabledCheatsPlayer = game.getPlayerTable().getPlayer(FIRST_PLAYER);

        Assertions.assertNotNull(enabledCheatsPlayer);

        Assertions.assertEquals(SHOW_ME_THE_MONEY_MINERALS, enabledCheatsPlayer.getMinerals());
        Assertions.assertTrue(game.isOperationCwal());

        Game blocked = createGame(MULTIPLAYER_MODE);

        blocked.getPlayerTable().setPlayer(SECOND_PLAYER, new Player(SECOND_PLAYER, SECOND_PLAYER, PlayerTypes.HUMAN, 0, 0));
        blocked.setCheatsEnabled(CHEATS_ENABLED);
        blocked.getGameCheats().apply(GameCheats.OPERATION_CWAL | GameCheats.SHOW_ME_THE_MONEY);

        Player blockedPlayer = blocked.getPlayerTable().getPlayer(FIRST_PLAYER);

        Assertions.assertNotNull(blockedPlayer);

        Assertions.assertEquals(0, blockedPlayer.getMinerals());
        Assertions.assertFalse(blocked.isOperationCwal());

        Game solo = createGame(MULTIPLAYER_MODE);

        solo.setCheatsEnabled(CHEATS_ENABLED);
        solo.getGameCheats().apply(GameCheats.OPERATION_CWAL | GameCheats.SHOW_ME_THE_MONEY);

        Player soloPlayer = solo.getPlayerTable().getPlayer(FIRST_PLAYER);

        Assertions.assertNotNull(soloPlayer);

        Assertions.assertEquals(SHOW_ME_THE_MONEY_MINERALS, soloPlayer.getMinerals());
    }

    @Test
    void apply_should_grant_resources_to_humans_and_replace_cheat_flags_without_random() throws IOException {
        Game game = createGame(SINGLE_PLAYER_MODE);

        game.setCheatsEnabled(CHEATS_ENABLED);
        game.getPlayerTable().setPlayer(THIRD_PLAYER, new Player(THIRD_PLAYER, THIRD_PLAYER, PlayerTypes.HUMAN, 0, 0));
        game.getGameCheats().apply(GameCheats.OPERATION_CWAL | GameCheats.SHOW_ME_THE_MONEY |
                                   GameCheats.WHATS_MINE_IS_MINE | GameCheats.BREATHE_DEEP);

        for (int playerIndex : new int[]{FIRST_PLAYER, THIRD_PLAYER}) {
            Player humanPlayer = game.getPlayerTable().getPlayer(playerIndex);

            Assertions.assertNotNull(humanPlayer);
            Assertions.assertEquals(COMBINED_CHEAT_MINERALS, humanPlayer.getMinerals());
            Assertions.assertEquals(COMBINED_CHEAT_GAS, humanPlayer.getGas());
        }

        Player computerPlayer = game.getPlayerTable().getPlayer(SECOND_PLAYER);

        Assertions.assertNotNull(computerPlayer);

        Assertions.assertEquals(0, computerPlayer.getMinerals());

        GameFrame activeFrame = game.getFrame();

        Assertions.assertNotNull(activeFrame);

        Assertions.assertEquals(OPERATION_CWAL_FLAG, activeFrame.getVisibility().getCheatFlags());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());

        game.getGameCheats().apply(0);

        Assertions.assertFalse(game.isOperationCwal());

        Player playerAfterClearing = game.getPlayerTable().getPlayer(FIRST_PLAYER);

        Assertions.assertNotNull(playerAfterClearing);

        Assertions.assertEquals(COMBINED_CHEAT_MINERALS, playerAfterClearing.getMinerals());

        game.getGameCheats().apply(GameCheats.MEDIEVAL_MAN);

        GameFrame requirementFrame = game.getFrame();

        Assertions.assertNotNull(requirementFrame);

        Assertions.assertEquals(MEDIEVAL_MAN_FLAG, requirementFrame.getVisibility().getCheatFlags());
    }

    @Test
    void suppresses_damage_should_gate_damage_without_a_player() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            GameFrame cheatFrame = game.getFrame();

            Assertions.assertNotNull(cheatFrame);

            MapVisibility visibility = cheatFrame.getVisibility();
            int flags = visibility.getCheatFlags();

            visibility.setCheatFlags(flags | GameCheats.POWER_OVERWHELMING);

            try {
                Assertions.assertTrue(game.getGameCheats().suppressesDamage(-1, true), "The unit-damage gate treats no player as not human.");
                Assertions.assertThrows(UnsupportedOperationException.class,
                                        () -> game.getGameCheats().suppressesDamage(-1, false),
                                        "The weapon gate reads before the player records.");
            } finally {
                visibility.setCheatFlags(flags);
            }
        });
    }

    @Test
    void requirement_cheats_waive_technology_and_production_requirements() throws IOException {
        CampaignMissionScenario.run(ExternalInput.INSTALLATION.getPath(), TERRAN_MISSION, true, (game, live) -> {
            Game state = live.getGame();

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, STARTUP_TURNS);

            int owner = game.getLocalPlayer();
            Unit center = findUnit(state, owner, ScrUnitIds.TERRAN_COMMAND_CENTER);
            Unit marine = findUnit(state, owner, ScrUnitIds.TERRAN_MARINE);

            Assertions.assertFalse(state.getUnitProduction().canBuild(center, ScrUnitIds.TERRAN_COMSAT_STATION), "a Comsat needs an Academy");
            Assertions.assertFalse(state.getUnitTechnology().canUseTechnology(marine, ScrTechIds.STIM_PACKS), "Stim Packs are not researched");

            for (String cheat : new String[]{"black sheep wall", "operation cwal", "power overwhelming", "show me the money", "staying alive",
                                             "the gathering", "medieval man", "war aint what it used to be", "modify the phase variance"}) {
                GameFrame cheatFrame = state.getFrame();

                Assertions.assertNotNull(cheatFrame);

                game.submitText(cheat, cheatFrame.getVisibility().getCheatFlags(), state.areCheatsEnabled(), state.getRuntimeRandom(), TYPED_TIME);
                CampaignMissionScenario.exchangeTurnsAndStep(game, live, CHEAT_TURNS);
            }

            GameFrame frame = state.getFrame();

            Assertions.assertNotNull(frame);

            int flags = frame.getVisibility().getCheatFlags();

            Assertions.assertEquals(TYPED_CHEAT_FLAGS | MODIFY_THE_PHASE_VARIANCE_FLAG,
                                    flags & ~SHOW_ME_THE_MONEY_FLAG,
                                    "cheat flags " + Integer.toHexString(flags));
            Assertions.assertTrue(state.getUnitProduction().canBuild(center, ScrUnitIds.TERRAN_COMSAT_STATION),
                                  "modify the phase variance waives the Academy");
            Assertions.assertTrue(state.getUnitTechnology().canUseTechnology(marine, ScrTechIds.STIM_PACKS),
                                  "medieval man waives the research");

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, FINAL_TURNS);
        });
    }
}
