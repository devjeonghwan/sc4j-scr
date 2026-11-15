package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Raises the maximum energy of a caster with its energy upgrade ({@link Stat#MAXIMUM_ENERGY}, with eight fraction bits) to
 * {@link #UPGRADED_MAXIMUM_ENERGY} when the owner has researched it: the reactors of the Terran casters, Gamete Meiosis, Metasynaptic Node, the
 * Argus upgrades and the Khaydarin upgrades.
 */
public final class ScrMaximumEnergyModifier extends ScrStatModifier {
    /**
     * The maximum energy of a hero or an upgraded caster ({@code 250}), with eight fraction bits.
     */
    public static final int UPGRADED_MAXIMUM_ENERGY = 0xFA00;

    private final @NotNull Game game;

    /**
     * Creates a {@link ScrMaximumEnergyModifier} instance for one game.
     *
     * @param game the game whose upgrade levels the modifier reads
     */
    public ScrMaximumEnergyModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values.
     *
     * @param player          the player slot index of the owner
     * @param unitTypeIndex   the index of the unit type, its units.dat entry
     * @param weaponTypeIndex unused, {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX}
     * @param value           the maximum energy before the upgrade, with eight fraction bits
     * @return the maximum energy with the upgrade
     * @throws IllegalStateException if the unit type has an energy upgrade and the player upgrade tables are not loaded
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        int upgrade = switch (ScrUnitIds.requireValidOrAdded(this.game.getBaseType(unitTypeIndex))) {
            case ScrUnitIds.TERRAN_GHOST -> ScrUpgradeIds.MOEBIUS_REACTOR;
            case ScrUnitIds.TERRAN_WRAITH -> ScrUpgradeIds.APOLLO_REACTOR;
            case ScrUnitIds.TERRAN_SCIENCE_VESSEL -> ScrUpgradeIds.TITAN_REACTOR;
            case ScrUnitIds.TERRAN_BATTLECRUISER -> ScrUpgradeIds.COLOSSUS_REACTOR;
            case ScrUnitIds.TERRAN_MEDIC -> ScrUpgradeIds.CADUCEUS_REACTOR;
            case ScrUnitIds.ZERG_QUEEN -> ScrUpgradeIds.GAMETE_MEIOSIS;
            case ScrUnitIds.ZERG_DEFILER -> ScrUpgradeIds.METASYNAPTIC_NODE;
            case ScrUnitIds.PROTOSS_CORSAIR -> ScrUpgradeIds.ARGUS_JEWEL;
            case ScrUnitIds.PROTOSS_DARK_ARCHON -> ScrUpgradeIds.ARGUS_TALISMAN;
            case ScrUnitIds.PROTOSS_HIGH_TEMPLAR -> ScrUpgradeIds.KHAYDARIN_AMULET;
            case ScrUnitIds.PROTOSS_ARBITER -> ScrUpgradeIds.KHAYDARIN_CORE;
            default -> -1;
        };

        if (upgrade < 0) {
            return value;
        }

        PlayerUpgrades upgrades = this.game.getPlayerTable().getPlayerUpgrades();

        if (upgrades == null) {
            throw Exceptions.newIllegalStateException("The player upgrade tables are loaded before unit type {0} exists.", unitTypeIndex);
        }

        return upgrades.getLevel(player, upgrade) != 0 ? UPGRADED_MAXIMUM_ENERGY : value;
    }
}
