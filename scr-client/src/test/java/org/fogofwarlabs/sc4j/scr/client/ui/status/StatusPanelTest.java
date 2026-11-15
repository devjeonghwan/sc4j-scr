package org.fogofwarlabs.sc4j.scr.client.ui.status;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.client.ClientReplayScenario;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ui.command.CommandInput;
import org.fogofwarlabs.sc4j.scr.client.ui.command.InterfaceSelection;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicHostValueRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalInterfaceState;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests the native selection panel views against real replay states.
 */
@RequiresInstallation
final class StatusPanelTest {
    private static final @NotNull String LOCALE = "enUS";

    /**
     * The Baekdudaegan frame at which a Command Center trains an SCV.
     */
    private static final int PRODUCTION_FRAME = 6000;
    /**
     * The Baekdudaegan frame at which a Factory builds its add-on.
     */
    private static final int ADDON_FRAME = 7400;
    /**
     * The Baekdudaegan frame at which a Dropship carries several passengers.
     */
    private static final int CARGO_FRAME = 10600;
    private static final int SUPPLY_FRAME = 200;
    /**
     * The Grand Line frame at which a Pylon is under construction.
     */
    private static final int CONSTRUCTION_FRAME = 1400;
    /**
     * The Judgement Day frame at which an Archon warps in.
     */
    private static final int ARCHON_FRAME = 18800;

    /**
     * The Terran players of the Baekdudaegan replay.
     */
    private static final int FIRST_PLAYER = 0;
    private static final int SECOND_PLAYER = 1;
    /**
     * The Zerg player of the Grand Line replay.
     */
    private static final int ZERG_PLAYER = 3;
    /**
     * The Protoss player of the Grand Line replay.
     */
    private static final int PROTOSS_PLAYER = 1;
    /**
     * The Protoss player of the Judgement Day replay.
     */
    private static final int ARCHON_PLAYER = 3;

    private static final int MULTI_SELECTION_MODE = 1;
    private static final int UNIT_MODE = 2;
    private static final int BUILDING_MODE = 4;
    private static final int PRODUCTION_MODE = 5;
    private static final int CONSTRUCTION_MODE = 6;
    private static final int ADDON_MODE = 9;
    private static final int EGG_MODE = 10;
    private static final int CARGO_MODE = 11;
    private static final int SUPPLY_MODE = 12;

    private static final int UNIT_NAME_CONTROL = -5;
    private static final int HEALTH_CONTROL = -7;
    private static final int SUPPLY_USED_CONTROL = -10;
    private static final int SUPPLY_PROVIDED_CONTROL = -11;
    private static final int SUPPLY_CAP_CONTROL = -13;
    private static final int QUEUE_LABEL_CONTROL = -15;
    private static final int STATUS_CONTROL = -20;
    private static final int KILLS_CONTROL = -21;
    private static final int CONSTRUCTION_LABEL_CONTROL = -30;
    private static final int EGG_ADDON_LABEL_CONTROL = -40;
    private static final int QUEUE_PROGRESS_CONTROL = 7;
    private static final int ARMOR_ICON_CONTROL = 9;
    private static final int SECOND_STAT_ICON_CONTROL = 10;
    private static final int THIRD_STAT_ICON_CONTROL = 11;
    private static final int CONSTRUCTION_PROGRESS_CONTROL = 13;
    private static final int EGG_ADDON_PROGRESS_CONTROL = 16;
    private static final int FIRST_CARGO_CONTROL = 18;
    private static final int LAST_CARGO_CONTROL = 32;
    private static final int FIRST_MULTI_SELECTION_CONTROL = 33;

    private static final int TERRAN_TRAINING_LABEL_STRING = 775;
    private static final int PRIVATE_RANK_STRING = 1304;
    private static final int KILLS_LABEL_STRING = 764;
    private static final int ZERG_SUPPLY_USED_STRING = 821;
    private static final int ZERG_SUPPLY_PROVIDED_STRING = 815;
    private static final int ZERG_MORPH_LABEL_STRING = 771;
    private static final int PROTOSS_CONSTRUCTION_LABEL_STRING = 801;
    private static final int TERRAN_ADDON_LABEL_STRING = 788;
    private static final int ARCHON_LABEL_STRING = 790;

    private static final int MAXIMUM_PERCENT = 100;
    /**
     * The status cache DWORD of the first loaded unit; the DWORD before it holds the transport's hit points.
     */
    private static final int FIRST_CARGO_CACHE_INDEX = 1;
    /**
     * The value of status cache DWORD {@code n}: this plus {@code n}.
     */
    private static final int CACHE_SENTINEL_BASE = 0x12345600;

    /**
     * The game-context offset of the Zerg supply cap DWORD of player {@code 0}; each player slot adds a DWORD.
     */
    private static final int ZERG_SUPPLY_CAPS_OFFSET = 0x30B4;
    /**
     * Hit points below zero, with eight fraction bits, whose negative percentage is neither low nor medium health.
     */
    private static final int NEGATIVE_HIT_POINTS = -100 << 8;
    /**
     * A half-supply cap with the sign bit set, and its unsigned halving rounded up.
     */
    private static final int SUPPLY_CAP_WITH_SIGN_BIT = 0x80000001;
    private static final @NotNull String HALVED_SUPPLY_CAP_WITH_SIGN_BIT = " 1073741825";
    /**
     * The icon frame of a training SCV: its unit type.
     */
    private static final int SCV_ICON_FRAME = 7;
    /**
     * The icon kinds of a weapon, an armor and a unit.
     */
    private static final int WEAPON_ICON = 0;
    private static final int ARMOR_ICON = 1;
    private static final int UNIT_ICON = 3;
    /**
     * The color code of full health.
     */
    private static final char GREEN_CODE = 7;
    /**
     * The classic unit table, the size of its entries and the entry offset of the owner byte.
     */
    private static final int CLASSIC_UNIT_TABLE = 0x59CCA8;
    private static final int CLASSIC_UNIT_SIZE = 336;
    private static final int CLASSIC_UNIT_OWNER = 0x4C;
    /**
     * The mask of the owner byte in the DWORD at its entry offset.
     */
    private static final int OWNER_MASK = 0xFF;
    /**
     * The game-data block, the offset of its classic upgrade levels in it and the classic upgrades of a level row.
     */
    private static final int GAME_DATA = 0x57F0F0;
    private static final int CLASSIC_LEVELS = 0xE1C0;
    private static final int CLASSIC_UPGRADES = 46;
    /**
     * An owner byte past the player slots, whose classic level row lies outside the upgrade tables.
     */
    private static final int WRITTEN_OWNER = 14;
    /**
     * The level byte written into that row, and the armor icon text that shows it.
     */
    private static final int WRITTEN_LEVEL = 9;
    private static final @NotNull String WRITTEN_LEVEL_TEXT = "9 ";

    /**
     * Checks that the updated fixture panel exposes the required control.
     *
     * @param panel      the updated selection panel
     * @param identifier the required control identifier
     * @return the visible control
     */
    private static @NotNull StatusPanel.Control requireControl(@NotNull StatusPanel panel, int identifier) {
        StatusPanel.Control control = panel.getControl(identifier);

        Assertions.assertNotNull(control, "control " + identifier);

        return control;
    }

    /**
     * Builds and updates a panel for the first visible unit matching the requested state.
     *
     * @param session   the running replay session
     * @param owner     the required player
     * @param unitType  the required type identifier
     * @param completed the required completion state
     * @return the updated panel
     * @throws IOException    if the installed status text cannot be read
     * @throws AssertionError if no matching unit exists
     */
    private static @NotNull StatusPanel showMatchingUnit(@NotNull ClientSession session, int owner, int unitType, boolean completed) throws IOException {
        Game game = session.getGame();
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        StatusPanel panel = new StatusPanel(StatText.load(session.getStorage(), LOCALE));
        GameData gameData = game.getGameData();

        Assertions.assertNotNull(gameData);

        InterfaceSelection selection = new InterfaceSelection(gameData.getUnitTypes());

        for (int index = 1; index <= pool.getCapacity(); index++) {
            Unit unit = pool.getUnit(index);

            if (pool.isAllocated(index) && unit.getOwnerIndex() == owner && unit.getUnitId() == unitType && unit.getSpriteOrNull() != null &&
                ((unit.getStatus() & UnitStatusFlags.COMPLETED) != 0) == completed) {
                selection.set(owner, unit);
                panel.update(new ScrGameView(game), new ScrPresentation(game), selection, true, FIRST_PLAYER);

                return panel;
            }
        }

        throw Exceptions.newAssertionError("No unit of type {0} for player {1}", unitType, owner);
    }

    @Test
    void update_should_show_production_stat_icons_and_multi_selection() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN, PRODUCTION_FRAME)) {
            Game game = session.getGame();
            StatText text = StatText.load(session.getStorage(), LOCALE);
            StatusPanel panel = new StatusPanel(text);
            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            InterfaceSelection selection = new InterfaceSelection(gameData.getUnitTypes());

            Unit commandCenter = ClientReplayScenario.findCompletedUnits(game, FIRST_PLAYER, ScrUnitIds.TERRAN_COMMAND_CENTER).getFirst();

            selection.set(FIRST_PLAYER, commandCenter);
            panel.update(new ScrGameView(game), new ScrPresentation(game), selection, true, FIRST_PLAYER);

            Assertions.assertEquals(PRODUCTION_MODE, panel.getMode(), "a Command Center training an SCV shows the production queue");
            Assertions.assertEquals(text.getString(TERRAN_TRAINING_LABEL_STRING), requireControl(panel, QUEUE_LABEL_CONTROL).getText());

            StatusPanel.Icon trainingIcon = requireControl(panel, CommandInput.FIRST_QUEUE_CONTROL).getIcon();

            Assertions.assertNotNull(trainingIcon);

            Assertions.assertEquals(SCV_ICON_FRAME, trainingIcon.getFrame());
            Assertions.assertTrue(requireControl(panel, QUEUE_PROGRESS_CONTROL).getValue() >= 0 &&
                                  requireControl(panel, QUEUE_PROGRESS_CONTROL).getValue() <= MAXIMUM_PERCENT);
            Assertions.assertEquals("Terran Command Center", requireControl(panel, UNIT_NAME_CONTROL).getText());
            Assertions.assertTrue(requireControl(panel, HEALTH_CONTROL).getText().endsWith("1500/1500"));

            Unit marine = ClientReplayScenario.findCompletedUnits(game, SECOND_PLAYER, ScrUnitIds.TERRAN_MARINE).getFirst();

            selection.set(SECOND_PLAYER, marine);
            panel.update(new ScrGameView(game), new ScrPresentation(game), selection, true, FIRST_PLAYER);

            Assertions.assertEquals(UNIT_MODE, panel.getMode());
            Assertions.assertEquals(text.getString(PRIVATE_RANK_STRING),
                                    requireControl(panel, STATUS_CONTROL).getText(),
                                    "a Marine's rank is Private");
            Assertions.assertTrue(requireControl(panel, KILLS_CONTROL).getText().startsWith(text.getString(KILLS_LABEL_STRING)));
            Assertions.assertNotNull(panel.getControl(ARMOR_ICON_CONTROL), "armor icon");

            StatusPanel.Icon weaponIcon = requireControl(panel, SECOND_STAT_ICON_CONTROL).getIcon();

            Assertions.assertNotNull(weaponIcon);

            Assertions.assertEquals(WEAPON_ICON, weaponIcon.getKind(), "then the ground weapon");
            Assertions.assertNull(panel.getControl(THIRD_STAT_ICON_CONTROL));

            Unit other = ClientReplayScenario.findCompletedUnits(game, SECOND_PLAYER, ScrUnitIds.TERRAN_SCV).getFirst();

            selection.set(SECOND_PLAYER, marine, other);
            panel.update(new ScrGameView(game), new ScrPresentation(game), selection, true, FIRST_PLAYER);

            Assertions.assertEquals(MULTI_SELECTION_MODE, panel.getMode());
            Assertions.assertSame(marine, requireControl(panel, FIRST_MULTI_SELECTION_CONTROL).getUnit());
            Assertions.assertSame(other, requireControl(panel, FIRST_MULTI_SELECTION_CONTROL + 1).getUnit());
            Assertions.assertNull(panel.getControl(FIRST_MULTI_SELECTION_CONTROL + 2));
        }
    }

    @Test
    void update_should_show_the_context_byte_as_the_armor_level_of_an_owner_past_the_players() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN, PRODUCTION_FRAME)) {
            Game game = session.getGame();
            StatusPanel panel = new StatusPanel(StatText.load(session.getStorage(), LOCALE));
            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            InterfaceSelection selection = new InterfaceSelection(gameData.getUnitTypes());
            Unit marine = ClientReplayScenario.findCompletedUnits(game, SECOND_PLAYER, ScrUnitIds.TERRAN_MARINE).getFirst();
            ClassicAddressSpace space = ClassicAddressSpace.create(game);
            int ownerAddress = CLASSIC_UNIT_TABLE + (marine.getIndex() - 1) * CLASSIC_UNIT_SIZE + CLASSIC_UNIT_OWNER;
            int armorUpgrade = gameData.getUnitTypes().getArmorUpgrade(marine.getUnitId());

            space.writeDword(ownerAddress, space.readDword(ownerAddress) & ~OWNER_MASK | WRITTEN_OWNER);
            space.writeDword(GAME_DATA + CLASSIC_LEVELS + WRITTEN_OWNER * CLASSIC_UPGRADES + armorUpgrade, WRITTEN_LEVEL);

            selection.set(SECOND_PLAYER, marine);
            panel.update(new ScrGameView(game), new ScrPresentation(game), selection, true, FIRST_PLAYER);

            Assertions.assertEquals(UNIT_MODE, panel.getMode());
            Assertions.assertEquals(WRITTEN_LEVEL_TEXT, requireControl(panel, ARMOR_ICON_CONTROL).getText(),
                                    "the armor icon shows the game-context byte that the owner's level row addresses");
        }
    }

    @Test
    void update_should_hide_other_players_production_outside_replay() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN, PRODUCTION_FRAME)) {
            Game game = session.getGame();
            StatusPanel panel = new StatusPanel(StatText.load(session.getStorage(), LOCALE));
            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            InterfaceSelection selection = new InterfaceSelection(gameData.getUnitTypes());
            Unit commandCenter = ClientReplayScenario.findCompletedUnits(game, FIRST_PLAYER, ScrUnitIds.TERRAN_COMMAND_CENTER).getFirst();

            selection.set(SECOND_PLAYER, commandCenter);
            panel.update(new ScrGameView(game), new ScrPresentation(game), selection, false, SECOND_PLAYER);

            Assertions.assertEquals(BUILDING_MODE, panel.getMode(), "an enemy building shows only its name and hit points");
            Assertions.assertNull(panel.getControl(QUEUE_LABEL_CONTROL));
            Assertions.assertNotNull(panel.getControl(HEALTH_CONTROL));
        }
    }

    @Test
    void update_should_show_supply_egg_and_construction_views() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_PVZ_GRAND_LINE, SUPPLY_FRAME)) {
            StatText text = StatText.load(session.getStorage(), LOCALE);
            StatusPanel overlord = showMatchingUnit(session, ZERG_PLAYER, ScrUnitIds.ZERG_OVERLORD, true);

            Assertions.assertEquals(SUPPLY_MODE, overlord.getMode(), "an empty Overlord shows the supply view");
            Assertions.assertTrue(requireControl(overlord, SUPPLY_USED_CONTROL).getText().startsWith(text.getString(ZERG_SUPPLY_USED_STRING)),
                                  "Control Used:");

            String provided = requireControl(overlord, SUPPLY_PROVIDED_CONTROL).getText();

            Assertions.assertTrue(provided.startsWith(text.getString(ZERG_SUPPLY_PROVIDED_STRING)));

            StatusPanel.Icon armorIcon = requireControl(overlord, ARMOR_ICON_CONTROL).getIcon();

            Assertions.assertNotNull(armorIcon);

            Assertions.assertEquals(ARMOR_ICON, armorIcon.getKind(), "the Overlord shows its armor icon");

            StatusPanel egg = showMatchingUnit(session, ZERG_PLAYER, ScrUnitIds.ZERG_EGG, false);

            Assertions.assertEquals(EGG_MODE, egg.getMode());
            Assertions.assertEquals(text.getString(ZERG_MORPH_LABEL_STRING), requireControl(egg, EGG_ADDON_LABEL_CONTROL).getText(), "Morphing");

            StatusPanel.Icon morphIcon = requireControl(egg, CommandInput.EGG_ADDON_CONTROL).getIcon();

            Assertions.assertNotNull(morphIcon);

            Assertions.assertEquals(UNIT_ICON, morphIcon.getKind());
            Assertions.assertTrue(requireControl(egg, EGG_ADDON_PROGRESS_CONTROL).getValue() >= 0);
        }

        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_PVZ_GRAND_LINE, CONSTRUCTION_FRAME)) {
            StatusPanel pylon = showMatchingUnit(session, PROTOSS_PLAYER, ScrUnitIds.PROTOSS_PYLON, false);

            Assertions.assertEquals(CONSTRUCTION_MODE, pylon.getMode(), "a Pylon under construction shows the construction view");
            Assertions.assertEquals(StatText.load(session.getStorage(), LOCALE).getString(PROTOSS_CONSTRUCTION_LABEL_STRING),
                                    requireControl(pylon, CONSTRUCTION_LABEL_CONTROL).getText(),
                                    "Opening Warp Rift");
            Assertions.assertTrue(requireControl(pylon, CONSTRUCTION_PROGRESS_CONTROL).getValue() >= 0 &&
                                  requireControl(pylon, CONSTRUCTION_PROGRESS_CONTROL).getValue() <= MAXIMUM_PERCENT);
        }
    }

    @Test
    void update_should_halve_a_supply_cap_with_the_sign_bit_as_an_unsigned_value() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_PVZ_GRAND_LINE, SUPPLY_FRAME)) {
            UnitAccounting accounting = session.getGame().getPlayerTable().getUnitAccounting();

            Assertions.assertNotNull(accounting);

            accounting.writeContextDword(ZERG_SUPPLY_CAPS_OFFSET + ZERG_PLAYER * Integer.BYTES, SUPPLY_CAP_WITH_SIGN_BIT);

            StatusPanel overlord = showMatchingUnit(session, ZERG_PLAYER, ScrUnitIds.ZERG_OVERLORD, true);
            String cap = requireControl(overlord, SUPPLY_CAP_CONTROL).getText();

            Assertions.assertTrue(cap.endsWith(HALVED_SUPPLY_CAP_WITH_SIGN_BIT), cap);
        }
    }

    @Test
    void update_should_color_negative_hit_points_like_full_health() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_PVZ_GRAND_LINE, SUPPLY_FRAME)) {
            Unit drone = ClientReplayScenario.findCompletedUnits(session.getGame(), ZERG_PLAYER, ScrUnitIds.ZERG_DRONE).getFirst();

            drone.setHitPoints(NEGATIVE_HIT_POINTS);

            StatusPanel panel = showMatchingUnit(session, ZERG_PLAYER, ScrUnitIds.ZERG_DRONE, true);

            Assertions.assertEquals(GREEN_CODE, requireControl(panel, HEALTH_CONTROL).getText().charAt(0));
        }
    }

    @Test
    void update_should_show_addon_and_cargo_views() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN, ADDON_FRAME)) {
            StatusPanel factory = showMatchingUnit(session, FIRST_PLAYER, ScrUnitIds.TERRAN_FACTORY, true);

            Assertions.assertEquals(ADDON_MODE, factory.getMode(), "a Factory building its add-on shows the add-on view");
            Assertions.assertEquals(StatText.load(session.getStorage(), LOCALE).getString(TERRAN_ADDON_LABEL_STRING),
                                    requireControl(factory, EGG_ADDON_LABEL_CONTROL).getText(),
                                    "Adding On");

            StatusPanel.Icon addonIcon = requireControl(factory, CommandInput.EGG_ADDON_CONTROL).getIcon();

            Assertions.assertNotNull(addonIcon);

            Assertions.assertEquals(UNIT_ICON, addonIcon.getKind());
        }

        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN, CARGO_FRAME)) {
            StatusPanel dropship = showMatchingUnit(session, FIRST_PLAYER, ScrUnitIds.TERRAN_DROPSHIP, true);

            Assertions.assertEquals(CARGO_MODE, dropship.getMode(), "a loaded Dropship shows its cargo");

            boolean cargo = false;

            for (int identifier = FIRST_CARGO_CONTROL; identifier <= LAST_CARGO_CONTROL; identifier++) {
                StatusPanel.Control control = dropship.getControl(identifier);

                if (control != null) {
                    cargo |= control.getUnit() != null;
                }
            }

            Assertions.assertTrue(cargo);
        }
    }

    @Test
    void update_should_clear_empty_cargo_hit_point_cache_slots() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN, CARGO_FRAME)) {
            Game game = session.getGame();
            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            Unit transport = ClientReplayScenario.findCompletedUnits(game, FIRST_PLAYER, ScrUnitIds.TERRAN_DROPSHIP).getFirst();
            InterfaceSelection selection = new InterfaceSelection(gameData.getUnitTypes());
            StatusPanel panel = new StatusPanel(StatText.load(session.getStorage(), LOCALE));
            LocalInterfaceState interfaceState = game.getInterfaceLink().getLocalInterface();
            int[] sentinels = new int[LocalInterfaceState.STATUS_PANEL_CACHE_DWORDS];

            for (int index = 0; index < sentinels.length; index++) {
                sentinels[index] = CACHE_SENTINEL_BASE + index;
            }

            interfaceState.setStatusPanelCache(sentinels);

            int[] cache = interfaceState.getStatusPanelCache();
            ClassicAddressSpace space = new ClassicAddressSpace();

            space.addAll(ClassicHostValueRegion.createLocalInterfaceRegions(game));
            selection.set(FIRST_PLAYER, transport);
            panel.update(new ScrGameView(game), new ScrPresentation(game), selection, true, FIRST_PLAYER);

            Assertions.assertEquals(CARGO_MODE, panel.getMode());
            Assertions.assertSame(cache, interfaceState.getStatusPanelCache());
            Assertions.assertEquals(transport.getHitPoints(), cache[0]);

            int emptySlots = 0;

            for (int slot = 0; slot < Unit.LOADED_UNIT_SLOTS; slot++) {
                Unit carried = game.getUnitTransport().getLoadedUnit(transport, slot);
                int expected = carried == null ? 0 : carried.getHitPoints();
                int cacheIndex = FIRST_CARGO_CACHE_INDEX + slot;

                emptySlots += carried == null ? 1 : 0;

                Assertions.assertEquals(expected, cache[cacheIndex], "Raw cargo HP in slot " + slot);
                Assertions.assertEquals(expected, space.readDword(LocalInterfaceState.STATUS_PANEL_CACHE_ADDRESS + cacheIndex * Integer.BYTES));
            }

            Assertions.assertTrue(emptySlots > 0);

            for (int index = FIRST_CARGO_CACHE_INDEX + Unit.LOADED_UNIT_SLOTS; index < cache.length; index++) {
                Assertions.assertEquals(sentinels[index], cache[index]);
            }
        }
    }

    @Test
    void update_should_clear_removed_cargo_hit_points_while_another_passenger_remains() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN, CARGO_FRAME)) {
            Game game = session.getGame();
            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            Unit transport = ClientReplayScenario.findCompletedUnits(game, FIRST_PLAYER, ScrUnitIds.TERRAN_DROPSHIP).getFirst();
            InterfaceSelection selection = new InterfaceSelection(gameData.getUnitTypes());
            StatusPanel panel = new StatusPanel(StatText.load(session.getStorage(), LOCALE));
            int removedSlot = -1;
            int occupiedSlots = 0;

            for (int slot = 0; slot < Unit.LOADED_UNIT_SLOTS; slot++) {
                if (game.getUnitTransport().getLoadedUnit(transport, slot) != null) {
                    removedSlot = slot;
                    occupiedSlots++;
                }
            }

            Assertions.assertTrue(occupiedSlots > 1);

            selection.set(FIRST_PLAYER, transport);
            panel.update(new ScrGameView(game), new ScrPresentation(game), selection, true, FIRST_PLAYER);

            int[] cache = game.getInterfaceLink().getLocalInterface().getStatusPanelCache();
            int removedCacheIndex = FIRST_CARGO_CACHE_INDEX + removedSlot;

            Assertions.assertNotEquals(0, cache[removedCacheIndex]);

            transport.setLoadedUnitHandle(removedSlot, 0);
            transport.setLoadedUnitHandleExtensionBits(transport.getLoadedUnitHandleExtensionBits() &
                                                       ~(Unit.LOADED_HANDLE_EXTENSION_MASK << (removedSlot * Unit.LOADED_HANDLE_EXTENSION_BITS)));

            Assertions.assertTrue(game.getUnitTransport().hasLoadedUnits(transport));

            panel.update(new ScrGameView(game), new ScrPresentation(game), selection, true, FIRST_PLAYER);

            Assertions.assertEquals(CARGO_MODE, panel.getMode());
            Assertions.assertSame(cache, game.getInterfaceLink().getLocalInterface().getStatusPanelCache());
            Assertions.assertEquals(0, cache[removedCacheIndex], "A removed cargo handle clears its cached HP.");

            ClassicAddressSpace space = new ClassicAddressSpace();

            space.addAll(ClassicHostValueRegion.createLocalInterfaceRegions(game));

            Assertions.assertEquals(0, space.readDword(LocalInterfaceState.STATUS_PANEL_CACHE_ADDRESS + removedCacheIndex * Integer.BYTES));

            for (int slot = 0; slot < Unit.LOADED_UNIT_SLOTS; slot++) {
                Unit carried = game.getUnitTransport().getLoadedUnit(transport, slot);

                if (carried != null) {
                    Assertions.assertEquals(carried.getHitPoints(), cache[FIRST_CARGO_CACHE_INDEX + slot]);
                }
            }
        }
    }

    @Test
    void update_should_show_archon_summoning_view() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_PVZ_JUDGEMENT_DAY, ARCHON_FRAME)) {
            StatusPanel archon = showMatchingUnit(session, ARCHON_PLAYER, ScrUnitIds.PROTOSS_ARCHON, true);

            Assertions.assertEquals(CONSTRUCTION_MODE, archon.getMode(), "an Archon warping in shows the summoning view");
            Assertions.assertEquals(StatText.load(session.getStorage(), LOCALE).getString(ARCHON_LABEL_STRING),
                                    requireControl(archon, CONSTRUCTION_LABEL_CONTROL).getText(),
                                    "Summoning");
        }
    }
}
