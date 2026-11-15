package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Checks relocation, carrier hangar limits and blocked unit creation.
 */
@RequiresInstallation
final class MapTriggerUnitActionTest {
    private static final int HOST_TICK_INTERVAL = 42;

    /**
     * The frames run before a test looks for a unit.
     */
    private static final int SETUP_FRAMES = 60;
    /**
     * The frames run after a test loads its trigger.
     */
    private static final int ACTION_FRAMES = 40;

    /**
     * The size of the short location section.
     */
    private static final int SHORT_LOCATION_SECTION_SIZE = 0x500;
    /**
     * The number of bounds of a location record: left, top, right and bottom.
     */
    private static final int LOCATION_BOUND_COUNT = 4;

    /**
     * The distance Move Unit sends a unit.
     */
    private static final int MOVE_DISTANCE = 320;
    /**
     * The half extent of the Move Unit destination location around its center.
     */
    private static final int DESTINATION_HALF_EXTENT = 16;
    /**
     * The tolerance around the destination center.
     */
    private static final int PLACEMENT_TOLERANCE = 96;
    /**
     * The least distance a moved unit has traveled.
     */
    private static final int MINIMUM_TRAVEL = 128;

    /**
     * The 1-based {@code stat_txt} identifier of the Terran Command Center name.
     */
    private static final int COMMAND_CENTER_TEXT_IDENTIFIER = 107;
    /**
     * The half extent of the Create Unit location around the anchor unit of the hangar test.
     */
    private static final int ANCHOR_HALF_EXTENT = 48;
    /**
     * The fixture host clock in milliseconds.
     */
    private static final int HOST_CLOCK_MILLISECONDS = 1000;
    /**
     * The Interceptors of a Carrier without the capacity upgrade.
     */
    private static final int CARRIER_CAPACITY = 4;
    /**
     * The Interceptors of a Carrier with the capacity upgrade.
     */
    private static final int UPGRADED_CARRIER_CAPACITY = 8;
    /**
     * The unit type of an Interceptor.
     */
    private static final int INTERCEPTOR_TYPE = 73;
    /**
     * The sprite flag of a hidden sprite.
     */
    private static final int HIDDEN_SPRITE_FLAG = 0x20;
    /**
     * The status flag of a completed unit.
     */
    private static final int COMPLETED_STATUS_FLAG = 0x1;

    private static @NotNull ReplayRuntime createRuntime(@NotNull GameData gameData) throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_UMS_TEAM_FIGHTING_SPIRIT)));
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        return runtime;
    }

    private static void runFrames(@NotNull ReplayRuntime runtime, int frames) {
        int targetFrame = runtime.getGame().getCurrentFrame() + frames;
        int hostTimeMilliseconds = 0;

        while (runtime.getGame().getCurrentFrame() < targetFrame) {
            Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, hostTimeMilliseconds += HOST_TICK_INTERVAL));
            Assertions.assertNull(runtime.getFailure());
        }
    }

    /**
     * Creates two location records with the supplied ordered bounds.
     *
     * @param first  the left, top, right and bottom bounds of location 0
     * @param second the left, top, right and bottom bounds of location 1
     * @return the location section bytes
     */
    private static byte @NotNull [] createLocations(int @NotNull [] first, int @NotNull [] second) {
        ByteBuffer buffer = ByteBuffer.allocate(SHORT_LOCATION_SECTION_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int bound = 0; bound < LOCATION_BOUND_COUNT; bound++) {
            buffer.putInt(bound * Integer.BYTES, first[bound]);
        }

        for (int bound = 0; bound < LOCATION_BOUND_COUNT; bound++) {
            buffer.putInt(TriggerContext.LOCATION_RECORD_SIZE + bound * Integer.BYTES, second[bound]);
        }

        return buffer.array();
    }

    private static @NotNull ByteBuffer createRecord(byte @NotNull [] records, int owner) {
        ByteBuffer record = TriggerRecordScenario.createRecord(records, 0);

        record.put(TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) TriggerConditionIds.ALWAYS);
        record.put(TriggerRecordCodec.OWNERS_OFFSET + owner, (byte) 1);

        return record;
    }

    private static void writeAction(@NotNull ByteBuffer record, int index, int type, int secondary, int unitType, int modifier) {
        int base = TriggerRecordCodec.ACTIONS_OFFSET + index * TriggerRecordCodec.ACTION_SIZE;

        record.putInt(base + TriggerRecordCodec.ACTION_LOCATION_FIELD, 1);
        record.putInt(base + TriggerRecordCodec.ACTION_PLAYER_FIELD, TriggerPlayerSelectors.CURRENT_PLAYER);
        record.putInt(base + TriggerRecordCodec.ACTION_SECONDARY_FIELD, secondary);
        record.putShort(base + TriggerRecordCodec.ACTION_UNIT_FIELD, (short) unitType);
        record.put(base + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) type);
        record.put(base + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) modifier);
    }

    private static @Nullable Unit findFirstMobileUnit(@NotNull Game game, int owner) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
            Unit unit = pool.getUnit(index);

            if ((unit.getStatus() & UnitStatusFlags.CAN_MOVE) != 0 &&
                (unit.getStatus() & UnitStatusFlags.GROUNDED_BUILDING) == 0 && (unit.getStatus() & UnitStatusFlags.COMPLETED) != 0 &&
                unit.getSpriteOrNull() != null) {
                Sprite visibleSprite = unit.getSpriteOrNull();

                Assertions.assertNotNull(visibleSprite);

                if ((visibleSprite.getFlags() & Sprite.FLAG_HIDDEN) == 0) {
                    return unit;
                }
            }
        }

        return null;
    }


    @Test
    void move_unit_relocates_one_unit_near_destination_center() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = createRuntime(gameData);

            runFrames(runtime, SETUP_FRAMES);

            Game game = runtime.getGame();
            int owner = -1;
            Unit unit = null;

            for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS && unit == null; player++) {
                unit  = findFirstMobileUnit(game, player);
                owner = player;
            }

            Assertions.assertNotNull(unit, "The replay has a mobile unit.");

            Sprite sourceSprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sourceSprite);

            int x = sourceSprite.getX();
            int y = sourceSprite.getY();
            int mapWidth = game.getMap().getWidth() * TerrainTileset.TILE_SIZE;
            int targetX = x < mapWidth / 2 ? x + MOVE_DISTANCE : x - MOVE_DISTANCE;
            TriggerContext context = game.getTriggerContext();

            Assertions.assertNotNull(context);

            context.loadLocations(createLocations(new int[]{x - 1, y - 1, x + 1, y + 1},
                                                  new int[]{targetX - DESTINATION_HALF_EXTENT, y - DESTINATION_HALF_EXTENT,
                                                            targetX + DESTINATION_HALF_EXTENT, y + DESTINATION_HALF_EXTENT}));

            byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
            ByteBuffer record = createRecord(records, owner);

            // Move Unit: location byte 1 (source 0), selector 13, destination 2 (location 1), all types (229), count 1.
            writeAction(record, 0, TriggerActionIds.MOVE_UNIT, 2, TriggerUnitGroups.ANY_UNIT, 1);
            context.loadRecords(records);
            runFrames(runtime, ACTION_FRAMES);

            Sprite relocatedSprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(relocatedSprite);

            int movedX = relocatedSprite.getX();
            int movedY = relocatedSprite.getY();

            Assertions.assertEquals(0, relocatedSprite.getFlags() & Sprite.FLAG_HIDDEN, "Move Unit shows the unit again.");
            Assertions.assertTrue(Math.abs(movedX - targetX) <= PLACEMENT_TOLERANCE && Math.abs(movedY - y) <= PLACEMENT_TOLERANCE,
                                  "Move Unit places the unit near the destination center: " + movedX + "," + movedY + " vs " + targetX + "," + y);
            Assertions.assertTrue(Math.abs(movedX - x) > MINIMUM_TRAVEL, "The unit left its source location.");
        }
    }

    @Test
    void modify_hangar_count_fills_created_carrier_up_to_capacity() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = createRuntime(gameData);

            runFrames(runtime, SETUP_FRAMES);

            Game game = runtime.getGame();
            int owner = -1;
            Unit anchor = null;

            for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS && anchor == null; player++) {
                anchor = findFirstMobileUnit(game, player);
                owner  = player;
            }

            Assertions.assertNotNull(anchor);

            Sprite anchorSprite = anchor.getSpriteOrNull();

            Assertions.assertNotNull(anchorSprite);

            int x = anchorSprite.getX();
            int y = anchorSprite.getY();
            TriggerContext context = game.getTriggerContext();

            Assertions.assertNotNull(context);

            context.loadLocations(createLocations(new int[]{x - ANCHOR_HALF_EXTENT, y - ANCHOR_HALF_EXTENT,
                                                            x + ANCHOR_HALF_EXTENT, y + ANCHOR_HALF_EXTENT},
                                                  new int[]{0, 0, 0, 0}));

            byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
            ByteBuffer record = createRecord(records, owner);

            // Create Unit: one Carrier (72) at location 0; Modify Hanger Count: all Carriers there (count 0 = 0xFFFF), 10 fighters.
            writeAction(record, 0, TriggerActionIds.CREATE_UNIT, 0, ScrUnitIds.PROTOSS_CARRIER, 1);
            writeAction(record, 1, TriggerActionIds.MODIFY_UNIT_HANGAR_COUNT, 10, ScrUnitIds.PROTOSS_CARRIER, 0);
            context.loadRecords(records);
            runFrames(runtime, ACTION_FRAMES);

            Unit carrier = null;
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
                if (pool.getUnit(index).getUnitId() == ScrUnitIds.PROTOSS_CARRIER) {
                    carrier = pool.getUnit(index);
                }
            }

            Assertions.assertNotNull(carrier, "Create Unit made the Carrier.");

            PlayerUpgrades playerUpgrades = game.getPlayerTable().getPlayerUpgrades();

            Assertions.assertNotNull(playerUpgrades);

            boolean upgraded = playerUpgrades.getLevel(owner, ScrUpgradeIds.CARRIER_CAPACITY) != 0;

            Assertions.assertEquals(upgraded ? UPGRADED_CARRIER_CAPACITY : CARRIER_CAPACITY, carrier.getHangarCount(false));

            int fighters = 0;

            for (Unit fighter = carrier.getFirstHangarUnit(false); fighter != null; fighter = fighter.getNextHangarUnit()) {
                fighters++;

                Assertions.assertEquals(INTERCEPTOR_TYPE, fighter.getUnitId());

                Sprite fighterSprite = fighter.getSpriteOrNull();

                Assertions.assertNotNull(fighterSprite);
                Assertions.assertEquals(HIDDEN_SPRITE_FLAG, fighterSprite.getFlags() & Sprite.FLAG_HIDDEN,
                                        "Filling the hangar hides the Interceptor.");
                Assertions.assertEquals(COMPLETED_STATUS_FLAG, fighter.getStatus() & UnitStatusFlags.COMPLETED,
                                        "Filling the hangar completes the Interceptor.");
            }

            Assertions.assertEquals(carrier.getHangarCount(false), fighters);
        }
    }

    @Test
    void create_unit_on_blocked_site_shows_unplaceable_warning() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = createRuntime(gameData);

            runFrames(runtime, SETUP_FRAMES);

            Game game = runtime.getGame();
            int owner = -1;
            Unit anchor = null;

            for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS && anchor == null; player++) {
                anchor = findFirstMobileUnit(game, player);
                owner  = player;
            }

            Assertions.assertNotNull(anchor);

            Sprite anchorSprite = anchor.getSpriteOrNull();

            Assertions.assertNotNull(anchorSprite);

            int x = anchorSprite.getX();
            int y = anchorSprite.getY();
            MessageLines lines = game.getInterfaceLink().getMessageLines();

            lines.setLayout(text -> new int[]{text.length});
            lines.setHostClock(() -> HOST_CLOCK_MILLISECONDS);
            lines.setStatusTexts(new MessageLines.StatusTexts() {
                /**
                 * Resolves a localized keyed text.
                 *
                 * @param key the string key
                 * @return the localized text bytes, or {@code null} when the key is unknown
                 * @implSpec Returns fixture warning bytes for the recognized key.
                 */
                @Override
                public byte @Nullable [] resolveKeyedText(@NotNull String key) {
                    return key.equals("errCANTPLACE_sdd") ? "WARNING: Unit unplaceable! (%s) (%d,%d)".getBytes(StandardCharsets.UTF_8) : null;
                }

                /**
                 * Resolves a localized numbered text.
                 *
                 * @param textIdentifier the 1-based {@code stat_txt} id
                 * @return the localized text bytes, or {@code null} when the id is unknown
                 * @implSpec Returns fixture unit-name bytes for the recognized identifier.
                 */
                @Override
                public byte @Nullable [] resolveNumberedText(int textIdentifier) {
                    return textIdentifier == COMMAND_CENTER_TEXT_IDENTIFIER ? "Terran Command Center\0*\0".getBytes(StandardCharsets.UTF_8) : null;
                }
            });

            TriggerContext context = game.getTriggerContext();

            Assertions.assertNotNull(context);

            context.loadLocations(createLocations(new int[]{x - 1, y - 1, x + 1, y + 1}, new int[]{0, 0, 0, 0}));

            byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
            ByteBuffer record = createRecord(records, owner);

            writeAction(record, 0, TriggerActionIds.CREATE_UNIT, 0, ScrUnitIds.TERRAN_COMMAND_CENTER, 1);
            context.loadRecords(records);
            runFrames(runtime, ACTION_FRAMES);

            int created = 0;
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
                if (pool.getUnit(index).getUnitId() == ScrUnitIds.TERRAN_COMMAND_CENTER) {
                    created++;
                }
            }

            Assertions.assertEquals(0, created, "The blocked site creates no Command Center.");

            String shown = null;

            for (int line = 0; line < MessageLines.ROTATING_LINES; line++) {
                byte[] bytes = lines.getLine(line);
                int end = 0;

                while (end < bytes.length && bytes[end] != 0) {
                    end++;
                }

                String text = new String(bytes, 0, end, StandardCharsets.UTF_8);

                if (text.startsWith("WARNING")) {
                    shown = text;
                }
            }

            Assertions.assertNotNull(shown, "Create Unit writes the unplaceable warning.");
            Assertions.assertTrue(shown.startsWith("WARNING: Unit unplaceable! (Terran Command Center) ("), shown);
        }
    }
}
