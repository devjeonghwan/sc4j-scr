package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ClientSessionScenario;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.production.PlacementResult;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks per-tile building placement on Bottleneck: beside the starting Command Center, on fogged tiles judged from the remembered mineral
 * fields and geysers, and on the parent footprint of a blocked add-on site.
 */
@RequiresInstallation
final class PlacementPreviewTest {
    private static final int FASTEST_SPEED = 6;
    private static final int START_SLOT_COUNT = 2;
    private static final int CENTER_DIVISOR = 2;
    private static final int CENTER_ROW = 1;
    private static final @NotNull String MAP = "(2)Bottleneck.scm";
    private static final int TILE_SIZE = 32;
    private static final int TILE_INSET = 4;
    private static final int FRAMES = 2;
    /**
     * The Barracks footprint, {@code 4} by {@code 3} tiles.
     */
    private static final int COLUMNS = 4;
    private static final int ROWS = 3;
    private static final int MINERAL_FIELD_FIRST = ScrUnitIds.RESOURCE_MINERAL_FIELD;
    private static final int MINERAL_FIELD_LAST = ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3;
    /**
     * The Command Center footprint, {@code 4} by {@code 3} tiles.
     */
    private static final int TOWN_HALL_COLUMNS = 4;
    private static final int TOWN_HALL_ROWS = 3;
    /**
     * The tiles around a remembered resource's placement box that keep a resource depot away.
     */
    private static final int RESOURCE_TILE_DISTANCE = 3;
    private static final int LEFT_INDEX = 0;
    private static final int TOP_INDEX = 1;
    private static final int RIGHT_INDEX = 2;
    private static final int BOTTOM_INDEX = 3;

    private static boolean overlapsMineralField(@NotNull Game game, int tileX, int tileY) {
        DatTable units = game.getGameData().getUnits();
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit unit = pool.getUnit(index);

            if (unit.getUnitId() < MINERAL_FIELD_FIRST || unit.getUnitId() > MINERAL_FIELD_LAST) {
                continue;
            }

            int width = units.get("Placement Box Width", unit.getUnitId());
            int height = units.get("Placement Box Height", unit.getUnitId());
            int left = (unit.getPositionX() - width / CENTER_DIVISOR) / TILE_SIZE;
            int top = (unit.getPositionY() - height / CENTER_DIVISOR) / TILE_SIZE;

            if (tileX >= left && tileX < left + width / TILE_SIZE && tileY >= top && tileY < top + height / TILE_SIZE) {
                return true;
            }
        }

        return false;
    }

    private static @NotNull List<Unit> findUnits(@NotNull Game game, int firstType, int lastType) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        List<Unit> found = new ArrayList<>();

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (pool.isAllocated(index) && pool.getUnit(index).getUnitId() >= firstType && pool.getUnit(index).getUnitId() <= lastType) {
                found.add(pool.getUnit(index));
            }
        }

        return found;
    }

    private static @NotNull List<Unit> findSeenUnits(@NotNull Game game, int vision, int firstType, int lastType) {
        List<Unit> seen = new ArrayList<>();

        for (Unit unit : findUnits(game, firstType, lastType)) {
            if ((unit.requireSprite().getVisibilityFlags() & vision) != 0) {
                seen.add(unit);
            }
        }

        Assertions.assertFalse(seen.isEmpty(), "the player sees a unit of type " + firstType);

        return seen;
    }

    /**
     * Returns the tiles within the resource distance of a unit's placement box: left, top, right and bottom, the right and bottom exclusive.
     */
    private static int @NotNull [] findResourceRange(@NotNull Game game, @NotNull Unit unit) {
        DatTable units = game.getGameData().getUnits();
        int width = units.get("Placement Box Width", unit.getUnitId());
        int height = units.get("Placement Box Height", unit.getUnitId());
        int left = (unit.getPositionX() - width / CENTER_DIVISOR) / TILE_SIZE;
        int top = (unit.getPositionY() - height / CENTER_DIVISOR) / TILE_SIZE;

        return new int[]{left - RESOURCE_TILE_DISTANCE, top - RESOURCE_TILE_DISTANCE,
                         left + width / TILE_SIZE + RESOURCE_TILE_DISTANCE, top + height / TILE_SIZE + RESOURCE_TILE_DISTANCE};
    }

    private static boolean contains(int @NotNull [] range, int tileX, int tileY) {
        return tileX >= range[LEFT_INDEX] && tileX < range[RIGHT_INDEX] && tileY >= range[TOP_INDEX] && tileY < range[BOTTOM_INDEX];
    }

    private static @NotNull SpriteEffectPool getRememberedImages(@NotNull Game game) {
        GameFrame frame = game.getFrame();

        Assertions.assertNotNull(frame);

        return frame.getSecondarySpriteEffects();
    }

    /**
     * Updates the preview with the pointer inside a footprint tile, the camera at the map origin and the whole map in the view.
     */
    private static void updatePreview(@NotNull PlacementPreview preview,
                                      @NotNull Game game,
                                      @NotNull Unit worker,
                                      int player,
                                      int type,
                                      int tileX, int tileY) {
        GameMap map = game.getMap();

        preview.update(new ScrGameView(game),
                       worker,
                       player,
                       type,
                       tileX * TILE_SIZE + TILE_INSET, tileY * TILE_SIZE + TILE_INSET,
                       0, 0,
                       map.getWidth() * TILE_SIZE, map.getHeight() * TILE_SIZE);
    }

    private static @NotNull ClientSession startMelee(int slot) throws IOException {
        Path gameDirectory = ExternalInput.INSTALLATION.getPath();
        Path map = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP);
        MeleeGameSetup setup = MeleeGameSetup.load(map, "tester", "terran", "zerg", slot, FASTEST_SPEED);
        ClientSession session = ClientSession.startMelee(gameDirectory, map, setup, ClientSessionScenario.GAME_SEED, null);

        session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
        session.startReplay();

        for (int frame = 0; frame < FRAMES; frame++) {
            session.stepFrame(false);
        }

        return session;
    }

    private static @NotNull Unit findUnit(@NotNull Game game, int player, int type) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit unit = pool.getUnit(index);

            if (unit.getOwnerIndex() == player && unit.getUnitId() == type) {
                return unit;
            }
        }

        throw Exceptions.newAssertionError("No unit of type {0} for player {1}", type, player);
    }

    @Test
    void barracks_preview_beside_command_center_blocks_only_mineral_cells() throws IOException {
        for (int slot = 0; slot < START_SLOT_COUNT; slot++) {
            try (ClientSession session = startMelee(slot)) {
                this.checkPlacement(session.getGame(), session.getLocalPlayer());
            }
        }
    }

    @Test
    void command_center_preview_keeps_away_from_remembered_mineral_fields_only() throws IOException {
        try (ClientSession session = startMelee(0)) {
            Game game = session.getGame();
            GameMap map = game.getMap();
            int player = session.getLocalPlayer();
            int vision = 1 << player;
            List<Unit> fields = findSeenUnits(game, vision, MINERAL_FIELD_FIRST, MINERAL_FIELD_LAST);
            Unit rememberedField = fields.getFirst();
            Unit unseenField = rememberedField;
            int unseenDistance = 0;

            for (Unit field : fields) {
                int distance = Math.abs(field.getPositionX() - rememberedField.getPositionX()) +
                               Math.abs(field.getPositionY() - rememberedField.getPositionY());

                if (distance > unseenDistance) {
                    unseenField    = field;
                    unseenDistance = distance;
                }
            }

            Sprite unseenSprite = unseenField.requireSprite();
            SpriteEffectPool remembered = getRememberedImages(game);
            int rememberedCount = remembered.getAllocatedCount();

            // The player loses sight of every tile; it has seen one mineral field, while another one was never in its sight.
            unseenSprite.setVisibilityFlags(unseenSprite.getVisibilityFlags() & ~vision);
            map.resetVisibility();
            game.getSpriteVisibility().update(rememberedField, map, vision, RunningGameScenario.INSTRUCTION_LIMIT);
            game.getSpriteVisibility().update(unseenField, map, vision, RunningGameScenario.INSTRUCTION_LIMIT);
            Assertions.assertEquals(rememberedCount + 1, remembered.getAllocatedCount(), "only the seen mineral field is remembered");

            int[] rememberedRange = findResourceRange(game, rememberedField);
            int[] unseenRange = findResourceRange(game, unseenField);

            // Every mineral field is mined out; only the remembered image of the seen one stays.
            for (Unit field : findUnits(game, MINERAL_FIELD_FIRST, MINERAL_FIELD_LAST)) {
                game.getUnitLifecycle().die(field, RunningGameScenario.INSTRUCTION_LIMIT);
            }

            Assertions.assertEquals(rememberedCount + 1, remembered.getAllocatedCount(), "the remembered image outlives its mineral field");

            Unit worker = findUnit(game, player, ScrUnitIds.TERRAN_SCV);
            PlacementPreview preview = new PlacementPreview();
            int changedTiles = 0;
            int unseenTiles = 0;

            for (int tileY = Math.max(0, Math.min(rememberedRange[TOP_INDEX], unseenRange[TOP_INDEX]) - TOWN_HALL_ROWS);
                 tileY < Math.max(rememberedRange[BOTTOM_INDEX], unseenRange[BOTTOM_INDEX]);
                 tileY++) {
                for (int tileX = Math.max(0, Math.min(rememberedRange[LEFT_INDEX], unseenRange[LEFT_INDEX]) - TOWN_HALL_COLUMNS);
                     tileX < Math.max(rememberedRange[RIGHT_INDEX], unseenRange[RIGHT_INDEX]);
                     tileX++) {
                    updatePreview(preview, game, worker, player, ScrUnitIds.TERRAN_COMMAND_CENTER, tileX, tileY);

                    PlacementResult unremembered = game.getUnitPlacement().evaluateBuilding(worker,
                                                                                            player,
                                                                                            ScrUnitIds.TERRAN_COMMAND_CENTER,
                                                                                            tileX, tileY,
                                                                                            true,
                                                                                            false,
                                                                                            false,
                                                                                            null);

                    for (int row = 0; row < TOWN_HALL_ROWS; row++) {
                        for (int column = 0; column < TOWN_HALL_COLUMNS; column++) {
                            int code = unremembered.getTileCode(column, row);
                            boolean near = contains(rememberedRange, tileX + column, tileY + row);
                            int expected = code == PlacementResult.VALID && near ? PlacementResult.NEAR_RESOURCES : code;

                            Assertions.assertEquals(expected,
                                                    preview.getTileCode(row, column),
                                                    "site " + tileX + "," + tileY + " cell " + row + "," + column);

                            if (expected != code) {
                                changedTiles++;
                            } else if (code == PlacementResult.VALID && contains(unseenRange, tileX + column, tileY + row)) {
                                unseenTiles++;
                            }
                        }
                    }
                }
            }

            Assertions.assertTrue(changedTiles > 0, "the remembered mineral field keeps the Command Center away");
            Assertions.assertTrue(unseenTiles > 0, "the unseen mineral field does not");
        }
    }

    @Test
    void refinery_preview_accepts_fogged_geyser_only_when_remembered() throws IOException {
        try (ClientSession session = startMelee(0)) {
            Game game = session.getGame();
            GameMap map = game.getMap();
            DatTable units = game.getGameData().getUnits();
            int player = session.getLocalPlayer();
            int vision = 1 << player;
            Unit geyser = findSeenUnits(game, vision, ScrUnitIds.RESOURCE_VESPENE_GEYSER, ScrUnitIds.RESOURCE_VESPENE_GEYSER).getFirst();
            int tileX = (geyser.getPositionX() - units.get("Placement Box Width", geyser.getUnitId()) / CENTER_DIVISOR) / TILE_SIZE;
            int tileY = (geyser.getPositionY() - units.get("Placement Box Height", geyser.getUnitId()) / CENTER_DIVISOR) / TILE_SIZE;
            Unit worker = findUnit(game, player, ScrUnitIds.TERRAN_SCV);
            PlacementPreview preview = new PlacementPreview();
            SpriteEffectPool remembered = getRememberedImages(game);
            int rememberedCount = remembered.getAllocatedCount();

            // The player loses sight of the geyser's tiles before its image is remembered.
            map.resetVisibility();
            updatePreview(preview, game, worker, player, ScrUnitIds.TERRAN_REFINERY, tileX, tileY);
            Assertions.assertEquals(PlacementResult.NO_GEYSER, preview.getCode(), "a fogged geyser that is not remembered");

            game.getSpriteVisibility().update(geyser, map, vision, RunningGameScenario.INSTRUCTION_LIMIT);
            Assertions.assertEquals(rememberedCount + 1, remembered.getAllocatedCount(), "the geyser is remembered");

            updatePreview(preview, game, worker, player, ScrUnitIds.TERRAN_REFINERY, tileX, tileY);
            Assertions.assertEquals(PlacementResult.VALID, preview.getCode(), "the remembered geyser");

            updatePreview(preview, game, worker, player, ScrUnitIds.TERRAN_REFINERY, tileX + 1, tileY);
            Assertions.assertEquals(PlacementResult.NO_GEYSER, preview.getCode(), "the remembered geyser is not centered on the next site");
        }
    }

    @Test
    void comsat_station_preview_draws_parent_tiles_with_their_own_codes_while_addon_is_blocked() throws IOException {
        try (ClientSession session = startMelee(0)) {
            Game game = session.getGame();
            GameMap map = game.getMap();
            ScrUnitTypeView units = game.requireGameData().getUnitTypes();
            int player = session.getLocalPlayer();
            int type = ScrUnitIds.TERRAN_COMSAT_STATION;
            int parentType = ScrUnitIds.TERRAN_COMMAND_CENTER;
            Unit commandCenter = findUnit(game, player, parentType);
            int parentX = (commandCenter.getPositionX() - units.getPlacementWidth(parentType) / CENTER_DIVISOR) / TILE_SIZE;
            int parentY = (commandCenter.getPositionY() - units.getPlacementHeight(parentType) / CENTER_DIVISOR) / TILE_SIZE;
            int addonX = parentX + units.getAddonHorizontalOffset(type) / TILE_SIZE;
            int addonY = parentY + units.getAddonVerticalOffset(type) / TILE_SIZE;
            int addonTile = addonY * map.getWidth() + addonX;

            // One add-on tile becomes unbuildable; the Command Center's own site is unchanged.
            map.restoreTileFlags(addonTile, map.getTileFlags(addonTile) | MapTileFlags.UNBUILDABLE);

            PlacementPreview preview = new PlacementPreview();

            updatePreview(preview, game, commandCenter, player, type, addonX, addonY);

            PlacementResult parent = game.getUnitPlacement().evaluateBuilding(commandCenter,
                                                                              player,
                                                                              parentType,
                                                                              parentX, parentY,
                                                                              true,
                                                                              false,
                                                                              false,
                                                                              new RememberedResources(new ScrGameView(game)));
            int validTiles = 0;

            Assertions.assertEquals(PlacementResult.BLOCKED, preview.getCode(), "the blocked add-on site");
            Assertions.assertEquals(parentType, preview.getParentType());

            for (int row = 0; row < TOWN_HALL_ROWS; row++) {
                for (int column = 0; column < TOWN_HALL_COLUMNS; column++) {
                    int code = parent.getTileCode(column, row);

                    Assertions.assertEquals(code, preview.getParentTileCode(row, column), "parent cell " + row + "," + column);

                    if (code == PlacementResult.VALID) {
                        validTiles++;
                    }
                }
            }

            Assertions.assertTrue(validTiles > 0, "the valid parent tiles are drawn valid");
        }
    }

    private void checkPlacement(@NotNull Game game, int player) {
        DatTable units = game.getGameData().getUnits();
        Unit commandCenter = findUnit(game, player, ScrUnitIds.TERRAN_COMMAND_CENTER);
        Unit worker = findUnit(game, player, ScrUnitIds.TERRAN_SCV);
        int type = ScrUnitIds.TERRAN_BARRACKS;
        int tileX = (commandCenter.getPositionX() + units.get("Placement Box Width", commandCenter.getUnitId()) / CENTER_DIVISOR) / TILE_SIZE;
        int tileY = (commandCenter.getPositionY() - units.get("Placement Box Height", commandCenter.getUnitId()) / CENTER_DIVISOR) / TILE_SIZE;
        int mapWidth = game.getMap().getWidth() * TILE_SIZE;
        int mapHeight = game.getMap().getHeight() * TILE_SIZE;
        PlacementPreview preview = new PlacementPreview();

        preview.update(new ScrGameView(game), worker, player, type, tileX * TILE_SIZE + TILE_INSET, tileY * TILE_SIZE + TILE_INSET, 0, 0, mapWidth, mapHeight);

        Assertions.assertEquals(tileX, preview.getTileX());
        Assertions.assertEquals(tileY, preview.getTileY());

        // With minerals east of the base, the two overlapping right cells are blocked and the middle right cell is open.
        // With minerals west of the base, every cell is open.
        boolean east = commandCenter.getPositionX() > mapWidth / CENTER_DIVISOR;

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                boolean blocked = overlapsMineralField(game, tileX + column, tileY + row);

                Assertions.assertEquals(blocked, preview.getTileCode(row, column) != 0, "cell " + row + "," + column);
                Assertions.assertEquals(east && column == COLUMNS - 1 && row != CENTER_ROW, blocked, "native cell " + row + "," + column);
            }
        }
    }
}
