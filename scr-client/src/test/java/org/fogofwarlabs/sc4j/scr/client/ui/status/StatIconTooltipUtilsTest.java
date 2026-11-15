package org.fogofwarlabs.sc4j.scr.client.ui.status;

import org.fogofwarlabs.sc4j.scr.client.ClientReplayScenario;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the weapon, armor and unit stat icon tooltips against replay state.
 */
@RequiresInstallation
final class StatIconTooltipUtilsTest {
    private static final @NotNull String REPLAY = GameTestResources.REPLAY_TVT_BAEKDUDAEGAN;
    private static final int REPLAY_START_FRAME = 6000;
    private static final int FIRST_PLAYER = 0;
    private static final int ICON_FRAME = 0;
    /**
     * The classic unit table, the size of its entries, the entry offset of the owner byte and its mask in the DWORD there.
     */
    private static final int CLASSIC_UNIT_TABLE = 0x59CCA8;
    private static final int CLASSIC_UNIT_SIZE = 336;
    private static final int CLASSIC_UNIT_OWNER = 0x4C;
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
     * The level byte written into that row, and the armor line of a Marine (armor {@code 0}) with that bonus.
     */
    private static final int WRITTEN_LEVEL = 9;
    private static final @NotNull String WRITTEN_LEVEL_ARMOR_LINE = "\nArmor: 0+9";

    @Test
    void build_should_format_weapon_armor_and_unit_icons() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(REPLAY, REPLAY_START_FRAME)) {
            Game game = session.getGame();
            StatText text = StatText.load(session.getStorage(), "enUS");
            Unit marine = ClientReplayScenario.findCompletedUnits(game, FIRST_PLAYER,
                                                                  ScrUnitIds.TERRAN_MARINE).stream().findFirst().orElse(null);

            Assertions.assertNotNull(marine, "The replay state has a marine.");

            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            DatTable weapons = gameData.getWeapons();

            Assertions.assertNotNull(weapons);

            DatTable unitTable = gameData.getUnits();

            Assertions.assertNotNull(unitTable);

            int weapon = unitTable.get("Ground Weapon", ScrUnitIds.TERRAN_MARINE);
            String damage = StatIconTooltipUtils.build(new ScrGameView(game), text, marine, new StatusPanel.Icon(ICON_FRAME, StatusPanel.Icon.WEAPON, weapon));
            String name = text.getString(weapons.get("Label", weapon));

            Assertions.assertTrue(damage.startsWith(name + "\nDamage: " + weapons.get("Damage Amount", weapon)), damage);

            int armorUpgrade = unitTable.get("Armor Upgrade", ScrUnitIds.TERRAN_MARINE);
            String armor = StatIconTooltipUtils.build(new ScrGameView(game), text, marine,
                                                      new StatusPanel.Icon(ICON_FRAME, StatusPanel.Icon.ARMOR, armorUpgrade));

            DatTable upgrades = gameData.getUpgrades();

            Assertions.assertNotNull(upgrades);
            Assertions.assertTrue(armor.startsWith(text.getString(upgrades.get("Label", armorUpgrade)) + "\nArmor: "), armor);

            int nuclearMissile = ScrUnitIds.TERRAN_NUCLEAR_MISSILE;
            int spiderMine = ScrUnitIds.TERRAN_VULTURE_SPIDER_MINE;
            String nukes = StatIconTooltipUtils.build(new ScrGameView(game), text, marine,
                                                      new StatusPanel.Icon(nuclearMissile, StatusPanel.Icon.UNIT, nuclearMissile));
            String spiderMines = StatIconTooltipUtils.build(new ScrGameView(game), text, marine,
                                                            new StatusPanel.Icon(spiderMine, StatusPanel.Icon.UNIT, spiderMine));

            Assertions.assertEquals("Nukes", nukes);
            Assertions.assertTrue(spiderMines.startsWith("Spider Mines\nDamage: "), spiderMines);
        }
    }

    @Test
    void build_should_add_the_context_byte_as_the_armor_bonus_of_an_owner_past_the_players() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(REPLAY, REPLAY_START_FRAME)) {
            Game game = session.getGame();
            StatText text = StatText.load(session.getStorage(), "enUS");
            Unit marine = ClientReplayScenario.findCompletedUnits(game, FIRST_PLAYER, ScrUnitIds.TERRAN_MARINE).getFirst();
            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            DatTable upgrades = gameData.getUpgrades();

            Assertions.assertNotNull(upgrades);

            ClassicAddressSpace space = ClassicAddressSpace.create(game);
            int ownerAddress = CLASSIC_UNIT_TABLE + (marine.getIndex() - 1) * CLASSIC_UNIT_SIZE + CLASSIC_UNIT_OWNER;
            int armorUpgrade = gameData.getUnitTypes().getArmorUpgrade(marine.getUnitId());

            space.writeDword(ownerAddress, space.readDword(ownerAddress) & ~OWNER_MASK | WRITTEN_OWNER);
            space.writeDword(GAME_DATA + CLASSIC_LEVELS + WRITTEN_OWNER * CLASSIC_UPGRADES + armorUpgrade, WRITTEN_LEVEL);

            String armor = StatIconTooltipUtils.build(new ScrGameView(game), text, marine,
                                                      new StatusPanel.Icon(ICON_FRAME, StatusPanel.Icon.ARMOR, armorUpgrade));

            Assertions.assertEquals(text.getString(upgrades.get("Label", armorUpgrade)) + WRITTEN_LEVEL_ARMOR_LINE, armor);
        }
    }
}
