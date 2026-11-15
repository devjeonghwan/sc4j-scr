package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.engine.content.type.UpgradeType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds the upgrade type identities of the SC:R mod, one per upgrades.dat entry, in upgrades.dat order. The mod registers them in this
 * order, so the index of each registered type is its upgrades.dat entry ({@link ScrNativeIds}).
 */
public final class ScrUpgrades {
    /**
     * The number of upgrades.dat entries: the registered upgrade types.
     */
    public static final int COUNT = 61;
    /**
     * The upgrades.dat entry that names no upgrade type, stored as {@link ContentType#NO_INDEX} in the engine tables.
     */
    public static final int NATIVE_NONE = 61;

    private static final @NotNull UpgradeType @NotNull [] TYPES = new UpgradeType[COUNT];

    /**
     * Terran Infantry Armor (upgrades.dat entry {@code 0}).
     */
    public static final @NotNull UpgradeType TERRAN_INFANTRY_ARMOR = create(0, "Terran Infantry Armor");
    /**
     * Terran Vehicle Plating (upgrades.dat entry {@code 1}).
     */
    public static final @NotNull UpgradeType TERRAN_VEHICLE_PLATING = create(1, "Terran Vehicle Plating");
    /**
     * Terran Ship Plating (upgrades.dat entry {@code 2}).
     */
    public static final @NotNull UpgradeType TERRAN_SHIP_PLATING = create(2, "Terran Ship Plating");
    /**
     * Zerg Carapace (upgrades.dat entry {@code 3}).
     */
    public static final @NotNull UpgradeType ZERG_CARAPACE = create(3, "Zerg Carapace");
    /**
     * Zerg Flyer Carapace (upgrades.dat entry {@code 4}).
     */
    public static final @NotNull UpgradeType ZERG_FLYER_CARAPACE = create(4, "Zerg Flyer Carapace");
    /**
     * Protoss Ground Armor (upgrades.dat entry {@code 5}).
     */
    public static final @NotNull UpgradeType PROTOSS_GROUND_ARMOR = create(5, "Protoss Ground Armor");
    /**
     * Protoss Air Armor (upgrades.dat entry {@code 6}).
     */
    public static final @NotNull UpgradeType PROTOSS_AIR_ARMOR = create(6, "Protoss Air Armor");
    /**
     * Terran Infantry Weapons (upgrades.dat entry {@code 7}).
     */
    public static final @NotNull UpgradeType TERRAN_INFANTRY_WEAPONS = create(7, "Terran Infantry Weapons");
    /**
     * Terran Vehicle Weapons (upgrades.dat entry {@code 8}).
     */
    public static final @NotNull UpgradeType TERRAN_VEHICLE_WEAPONS = create(8, "Terran Vehicle Weapons");
    /**
     * Terran Ship Weapons (upgrades.dat entry {@code 9}).
     */
    public static final @NotNull UpgradeType TERRAN_SHIP_WEAPONS = create(9, "Terran Ship Weapons");
    /**
     * Zerg Melee Attacks (upgrades.dat entry {@code 10}).
     */
    public static final @NotNull UpgradeType ZERG_MELEE_ATTACKS = create(10, "Zerg Melee Attacks");
    /**
     * Zerg Missile Attacks (upgrades.dat entry {@code 11}).
     */
    public static final @NotNull UpgradeType ZERG_MISSILE_ATTACKS = create(11, "Zerg Missile Attacks");
    /**
     * Zerg Flyer Attacks (upgrades.dat entry {@code 12}).
     */
    public static final @NotNull UpgradeType ZERG_FLYER_ATTACKS = create(12, "Zerg Flyer Attacks");
    /**
     * Protoss Ground Weapons (upgrades.dat entry {@code 13}).
     */
    public static final @NotNull UpgradeType PROTOSS_GROUND_WEAPONS = create(13, "Protoss Ground Weapons");
    /**
     * Protoss Air Weapons (upgrades.dat entry {@code 14}).
     */
    public static final @NotNull UpgradeType PROTOSS_AIR_WEAPONS = create(14, "Protoss Air Weapons");
    /**
     * Protoss Plasma Shields (upgrades.dat entry {@code 15}).
     */
    public static final @NotNull UpgradeType PROTOSS_PLASMA_SHIELDS = create(15, "Protoss Plasma Shields");
    /**
     * U 238 Shells (upgrades.dat entry {@code 16}).
     */
    public static final @NotNull UpgradeType U_238_SHELLS = create(16, "U 238 Shells");
    /**
     * Ion Thrusters (upgrades.dat entry {@code 17}).
     */
    public static final @NotNull UpgradeType ION_THRUSTERS = create(17, "Ion Thrusters");
    /**
     * Unknown entry 18 (upgrades.dat entry {@code 18}).
     */
    public static final @NotNull UpgradeType UNKNOWN_18 = create(18, "unknown entry 18");
    /**
     * Titan Reactor (upgrades.dat entry {@code 19}).
     */
    public static final @NotNull UpgradeType TITAN_REACTOR = create(19, "Titan Reactor");
    /**
     * Ocular Implants (upgrades.dat entry {@code 20}).
     */
    public static final @NotNull UpgradeType OCULAR_IMPLANTS = create(20, "Ocular Implants");
    /**
     * Moebius Reactor (upgrades.dat entry {@code 21}).
     */
    public static final @NotNull UpgradeType MOEBIUS_REACTOR = create(21, "Moebius Reactor");
    /**
     * Apollo Reactor (upgrades.dat entry {@code 22}).
     */
    public static final @NotNull UpgradeType APOLLO_REACTOR = create(22, "Apollo Reactor");
    /**
     * Colossus Reactor (upgrades.dat entry {@code 23}).
     */
    public static final @NotNull UpgradeType COLOSSUS_REACTOR = create(23, "Colossus Reactor");
    /**
     * Ventral Sacs (upgrades.dat entry {@code 24}).
     */
    public static final @NotNull UpgradeType VENTRAL_SACS = create(24, "Ventral Sacs");
    /**
     * Antennae (upgrades.dat entry {@code 25}).
     */
    public static final @NotNull UpgradeType ANTENNAE = create(25, "Antennae");
    /**
     * Pneumatized Carapace (upgrades.dat entry {@code 26}).
     */
    public static final @NotNull UpgradeType PNEUMATIZED_CARAPACE = create(26, "Pneumatized Carapace");
    /**
     * Metabolic Boost (upgrades.dat entry {@code 27}).
     */
    public static final @NotNull UpgradeType METABOLIC_BOOST = create(27, "Metabolic Boost");
    /**
     * Adrenal Glands (upgrades.dat entry {@code 28}).
     */
    public static final @NotNull UpgradeType ADRENAL_GLANDS = create(28, "Adrenal Glands");
    /**
     * Muscular Augments (upgrades.dat entry {@code 29}).
     */
    public static final @NotNull UpgradeType MUSCULAR_AUGMENTS = create(29, "Muscular Augments");
    /**
     * Grooved Spines (upgrades.dat entry {@code 30}).
     */
    public static final @NotNull UpgradeType GROOVED_SPINES = create(30, "Grooved Spines");
    /**
     * Gamete Meiosis (upgrades.dat entry {@code 31}).
     */
    public static final @NotNull UpgradeType GAMETE_MEIOSIS = create(31, "Gamete Meiosis");
    /**
     * Metasynaptic Node (upgrades.dat entry {@code 32}).
     */
    public static final @NotNull UpgradeType METASYNAPTIC_NODE = create(32, "Metasynaptic Node");
    /**
     * Singularity Charge (upgrades.dat entry {@code 33}).
     */
    public static final @NotNull UpgradeType SINGULARITY_CHARGE = create(33, "Singularity Charge");
    /**
     * Leg Enhancements (upgrades.dat entry {@code 34}).
     */
    public static final @NotNull UpgradeType LEG_ENHANCEMENTS = create(34, "Leg Enhancements");
    /**
     * Scarab Damage (upgrades.dat entry {@code 35}).
     */
    public static final @NotNull UpgradeType SCARAB_DAMAGE = create(35, "Scarab Damage");
    /**
     * Reaver Capacity (upgrades.dat entry {@code 36}).
     */
    public static final @NotNull UpgradeType REAVER_CAPACITY = create(36, "Reaver Capacity");
    /**
     * Gravitic Drive (upgrades.dat entry {@code 37}).
     */
    public static final @NotNull UpgradeType GRAVITIC_DRIVE = create(37, "Gravitic Drive");
    /**
     * Sensor Array (upgrades.dat entry {@code 38}).
     */
    public static final @NotNull UpgradeType SENSOR_ARRAY = create(38, "Sensor Array");
    /**
     * Gravitic Boosters (upgrades.dat entry {@code 39}).
     */
    public static final @NotNull UpgradeType GRAVITIC_BOOSTERS = create(39, "Gravitic Boosters");
    /**
     * Khaydarin Amulet (upgrades.dat entry {@code 40}).
     */
    public static final @NotNull UpgradeType KHAYDARIN_AMULET = create(40, "Khaydarin Amulet");
    /**
     * Apial Sensors (upgrades.dat entry {@code 41}).
     */
    public static final @NotNull UpgradeType APIAL_SENSORS = create(41, "Apial Sensors");
    /**
     * Gravitic Thrusters (upgrades.dat entry {@code 42}).
     */
    public static final @NotNull UpgradeType GRAVITIC_THRUSTERS = create(42, "Gravitic Thrusters");
    /**
     * Carrier Capacity (upgrades.dat entry {@code 43}).
     */
    public static final @NotNull UpgradeType CARRIER_CAPACITY = create(43, "Carrier Capacity");
    /**
     * Khaydarin Core (upgrades.dat entry {@code 44}).
     */
    public static final @NotNull UpgradeType KHAYDARIN_CORE = create(44, "Khaydarin Core");
    /**
     * Unknown entry 45 (upgrades.dat entry {@code 45}).
     */
    public static final @NotNull UpgradeType UNKNOWN_45 = create(45, "unknown entry 45");
    /**
     * Unknown entry 46 (upgrades.dat entry {@code 46}).
     */
    public static final @NotNull UpgradeType UNKNOWN_46 = create(46, "unknown entry 46");
    /**
     * Argus Jewel (upgrades.dat entry {@code 47}).
     */
    public static final @NotNull UpgradeType ARGUS_JEWEL = create(47, "Argus Jewel");
    /**
     * Unknown entry 48 (upgrades.dat entry {@code 48}).
     */
    public static final @NotNull UpgradeType UNKNOWN_48 = create(48, "unknown entry 48");
    /**
     * Argus Talisman (upgrades.dat entry {@code 49}).
     */
    public static final @NotNull UpgradeType ARGUS_TALISMAN = create(49, "Argus Talisman");
    /**
     * Unknown entry 50 (upgrades.dat entry {@code 50}).
     */
    public static final @NotNull UpgradeType UNKNOWN_50 = create(50, "unknown entry 50");
    /**
     * Caduceus Reactor (upgrades.dat entry {@code 51}).
     */
    public static final @NotNull UpgradeType CADUCEUS_REACTOR = create(51, "Caduceus Reactor");
    /**
     * Chitinous Plating (upgrades.dat entry {@code 52}).
     */
    public static final @NotNull UpgradeType CHITINOUS_PLATING = create(52, "Chitinous Plating");
    /**
     * Anabolic Synthesis (upgrades.dat entry {@code 53}).
     */
    public static final @NotNull UpgradeType ANABOLIC_SYNTHESIS = create(53, "Anabolic Synthesis");
    /**
     * Charon Boosters (upgrades.dat entry {@code 54}).
     */
    public static final @NotNull UpgradeType CHARON_BOOSTERS = create(54, "Charon Boosters");
    /**
     * Unused 55 (upgrades.dat entry {@code 55}).
     */
    public static final @NotNull UpgradeType UNUSED_55 = create(55, "Unused 55");
    /**
     * Unused 56 (upgrades.dat entry {@code 56}).
     */
    public static final @NotNull UpgradeType UNUSED_56 = create(56, "Unused 56");
    /**
     * Unused 57 (upgrades.dat entry {@code 57}).
     */
    public static final @NotNull UpgradeType UNUSED_57 = create(57, "Unused 57");
    /**
     * Unused 58 (upgrades.dat entry {@code 58}).
     */
    public static final @NotNull UpgradeType UNUSED_58 = create(58, "Unused 58");
    /**
     * Unused 59 (upgrades.dat entry {@code 59}).
     */
    public static final @NotNull UpgradeType UNUSED_59 = create(59, "Unused 59");
    /**
     * Unused 60 (upgrades.dat entry {@code 60}).
     */
    public static final @NotNull UpgradeType UNUSED_60 = create(60, "Unused 60");

    private static final @NotNull List<@NotNull UpgradeType> TYPE_LIST;

    static {
        TYPE_LIST = List.of(TYPES);
    }

    /**
     * Returns the identity of a upgrades.dat entry.
     *
     * @param nativeId the upgrades.dat entry, from {@code 0} to {@code COUNT - 1}
     * @return the identity
     * @throws IllegalArgumentException if the entry is outside the table
     */
    public static @NotNull UpgradeType get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid upgrades.dat entry: {0}", nativeId);
        }

        return TYPES[nativeId];
    }

    /**
     * @return every identity in upgrades.dat order, as the mod registers them
     */
    public static @NotNull List<@NotNull UpgradeType> getTypes() {
        return TYPE_LIST;
    }

    private static @NotNull UpgradeType create(int nativeId, @NotNull String debugName) {
        UpgradeType type = new UpgradeType(debugName);

        TYPES[nativeId] = type;

        return type;
    }


    private ScrUpgrades() {
    }
}
