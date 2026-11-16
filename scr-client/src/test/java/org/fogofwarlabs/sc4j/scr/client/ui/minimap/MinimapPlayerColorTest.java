package org.fogofwarlabs.sc4j.scr.client.ui.minimap;

import org.fogofwarlabs.sc4j.scr.client.ui.PlayerColorUtils;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerColorRows;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceAssets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Starts melee games with computer players and checks that each player's minimap dots show the minimap color of the color slot its units use.
 */
@RequiresInstallation
final class MinimapPlayerColorTest {
    private static final @NotNull String MAP_NAME = "(4)Blood Bath.scm";
    private static final @NotNull String SCENARIO = "staredit\\scenario.chk";
    private static final @NotNull String HOST_NAME = "host";
    private static final int SEED = 0x2468ACE;
    private static final int HOST_SLOT = 0;
    private static final int HOST_PLAYER = 0;
    private static final int START_SLOTS = 4;
    private static final int MELEE_FORCE = 1;
    /**
     * A local player that matches no slot.
     */
    private static final int NO_LOCAL_PLAYER = -1;
    private static final int TILE_SIZE = 32;
    /**
     * The nearest-color target of the resource dots.
     */
    private static final int RESOURCE_TARGET = 0x00FFFF;
    /**
     * Halves a placement box size to its center offset.
     */
    private static final int CENTER_DIVISOR = 2;
    /**
     * The mask of the byte-sized placement sizes of {@code units.dat}.
     */
    private static final int PLACEMENT_SIZE_MASK = 0xFF;
    private static final LobbyResolution.@NotNull Options FIXED_START_MELEE = new LobbyResolution.Options(true, false, 0, true, false);

    /**
     * Starts a live game with every player's vision.
     */
    private static @NotNull Game start(@NotNull LiveGame live, @NotNull GameData gameData, boolean network) throws IOException {
        ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), ChkFile.parseTolerant(live.getSetup().getMapData()), gameData,
                                                             live.getLocalPlayer(), network,
                                                             RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                             RunningGameScenario.INSTRUCTION_LIMIT);

        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        Game game = runtime.getGame();

        game.getLocalVision().setComparisonMask(LocalVision.ALL_PLAYERS);

        return game;
    }

    /**
     * Returns the starting town hall of a player, or {@code null}.
     */
    private static @Nullable Unit findTownHall(@NotNull Game game, int player) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit unit = pool.getUnit(index);
            int type = unit.getUnitId();

            if (unit.getOwnerIndex() == player &&
                (type == ScrUnitIds.TERRAN_COMMAND_CENTER || type == ScrUnitIds.ZERG_HATCHERY || type == ScrUnitIds.PROTOSS_NEXUS)) {
                return unit;
            }
        }

        return null;
    }

    /**
     * Draws the minimap of the game with every player's dots in its own color.
     */
    private static @NotNull MinimapImage draw(@NotNull Game game, @NotNull InterfaceAssets assets) throws IOException {
        return MinimapImage.build(new ScrGameView(game), assets, NO_LOCAL_PLAYER, true);
    }

    /**
     * Returns the dot color inside the top left corner of each player's town hall, {@code 0} for a player without one.
     */
    private static int @NotNull [] readDotColors(@NotNull Game game, @NotNull MinimapImage image) {
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();
        int[] dots = image.getDotLayer();
        int[] colors = new int[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        Assertions.assertNotNull(dots);

        for (int player = 0; player < colors.length; player++) {
            Unit townHall = findTownHall(game, player);

            if (townHall == null) {
                continue;
            }

            Sprite sprite = townHall.requireSprite();
            int left = sprite.getX() - (units.getPlacementWidth(townHall.getUnitId()) & PLACEMENT_SIZE_MASK) / CENTER_DIVISOR;
            int top = sprite.getY() - (units.getPlacementHeight(townHall.getUnitId()) & PLACEMENT_SIZE_MASK) / CENTER_DIVISOR;
            int x = left * image.getScale() / TILE_SIZE + 1;
            int y = top * image.getScale() / TILE_SIZE + 1;

            colors[player] = dots[y * image.getTextureWidth() + x];
        }

        return colors;
    }

    private static @NotNull Path getMapPath() {
        return ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);
    }

    private static @NotNull InterfaceAssets loadAssets(@NotNull CascStorage storage, @NotNull Game game) throws IOException {
        return InterfaceAssets.load(ScrResourceProvider.borrow(storage), game.getMap().getTileset());
    }

    /**
     * Creates the lobby table of a network melee game: the host in slot 0 and computers in the other start slots, none with a lobby color.
     */
    private static LobbyResolution.@NotNull Table createNetworkTable() {
        LobbyResolution.Table table = new LobbyResolution.Table();

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            table.type[slot]         = slot < START_SLOTS ? MeleeGameSetup.TYPE_COMPUTER : LobbyResolution.TYPE_CLOSED;
            table.templateType[slot] = LobbyResolution.TYPE_OPEN;
            table.savedType[slot]    = LobbyResolution.TYPE_OPEN;
            table.race[slot]         = ScrRaceIds.ZERG;
            table.templateRace[slot] = ScrRaceIds.ZERG;
            table.team[slot]         = MELEE_FORCE;
            table.templateTeam[slot] = MELEE_FORCE;
        }

        table.playerId[HOST_SLOT] = HOST_PLAYER;
        table.type[HOST_SLOT]     = MeleeGameSetup.TYPE_HUMAN;
        table.race[HOST_SLOT]     = ScrRaceIds.TERRAN;
        table.name[HOST_SLOT]     = HOST_NAME;

        return table;
    }

    @Test
    void computers_show_on_the_minimap_in_the_minimap_color_of_their_units_color() throws IOException {
        MeleeGameSetup setup = MeleeGameSetup.load(getMapPath(), HOST_NAME, "terran", "zerg,protoss,terran", HOST_SLOT, GameSpeed.FASTEST);
        LiveGame live = new LiveGame(setup, SEED);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
             CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            Game game = start(live, gameData, false);
            InterfaceAssets assets = loadAssets(storage, game);
            PlayerColorRows rows = game.getPlayerTable().getPlayerColorRows();
            int[][] tables = gameData.getPlayerColorTables();
            int[] dots = readDotColors(game, draw(game, assets));
            int computers = 0;

            Assertions.assertNotNull(rows);
            Assertions.assertFalse(rows.isCustomColorsActive(), "a single-player game keeps no custom colors");

            for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
                Player player = game.getPlayerTable().getPlayer(slot);

                if (player == null || findTownHall(game, slot) == null) {
                    continue;
                }

                int color = player.getColor();

                Assertions.assertEquals(tables[color][0], rows.getUnitColor(slot, 0), "slot " + slot + " units use the row of its color");
                Assertions.assertEquals(assets.getPaletteColor(tables[color][PlayerColorRows.UNIT_ROW]), dots[slot],
                                        "slot " + slot + " dots use the minimap color of the same color");

                computers += slot == live.getLocalPlayer() ? 0 : 1;
            }

            Assertions.assertEquals(START_SLOTS - 1, computers, "every computer has a town hall");
        }
    }

    @Test
    void computers_show_on_the_minimap_in_their_custom_color_when_the_game_keeps_custom_colors() throws IOException {
        byte[] mapData = MpqArchive.open(getMapPath()).readFile(SCENARIO);
        MeleeGameSetup setup = MeleeGameSetup.createNetwork(mapData, HOST_NAME, createNetworkTable(), FIXED_START_MELEE, GameTemplate.MELEE,
                                                            GameSpeed.FASTEST, true);
        LiveGame live = new LiveGame(setup, SEED, HOST_PLAYER);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
             CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            Game game = start(live, gameData, true);
            InterfaceAssets assets = loadAssets(storage, game);
            PlayerColorRows rows = game.getPlayerTable().getPlayerColorRows();
            MinimapImage image = draw(game, assets);
            int[] dots = readDotColors(game, image);
            List<Integer> checked = new ArrayList<>();

            Assertions.assertNotNull(rows);
            Assertions.assertTrue(rows.isCustomColorsActive(), "a network melee game keeps its custom colors");

            for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
                if (findTownHall(game, slot) == null) {
                    continue;
                }

                // The dots take the slot's custom color.
                Assertions.assertEquals(rows.getPlayerColor(slot, assets::getPaletteColor), dots[slot], "slot " + slot);
                Assertions.assertEquals(rows.getCustomColor(slot), dots[slot], "slot " + slot);
                // The leaderboard box takes the minimap index of the row.
                Assertions.assertEquals(assets.getPaletteColor(rows.getMinimapColor(slot)),
                                        PlayerColorUtils.getMinimapIndexColor(game.getPlayerTable(), assets, slot), "slot " + slot);

                checked.add(slot);
            }

            Assertions.assertEquals(START_SLOTS, checked.size(), "every player has a town hall");

            // The slots past the playable ones take their empty custom entries.
            for (int slot = PlayerTable.PLAYABLE_PLAYER_SLOTS; slot < PlayerTable.PLAYER_SLOTS; slot++) {
                Assertions.assertEquals(0, rows.getMinimapPlayerColor(slot, assets::getPaletteColor), "slot " + slot);
            }

            int resourceColor = assets.getNearestPaletteColor(RESOURCE_TARGET);
            int[] layer = image.getDotLayer();

            Assertions.assertNotNull(layer);
            Assertions.assertTrue(Arrays.stream(layer).anyMatch(color -> color == resourceColor), "the neutral resources keep their dots");
        }
    }
}
