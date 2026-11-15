package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;

/**
 * Holds the native upgrade identifiers (upgrades.dat entries) of the SC:R mod as {@code int} constants: the values the SC:R
 * simulation stores and compares, and the values EUD, CHK sections, replays and saved games hold. {@link ScrUpgrades} holds the
 * engine identities; {@link ScrNativeIds} maps between the two.
 */
public final class ScrUpgradeIds {
    /**
     * Terran Infantry Armor (upgrades.dat entry {@code 0}).
     */
    public static final int TERRAN_INFANTRY_ARMOR = 0;
    /**
     * Terran Vehicle Plating (upgrades.dat entry {@code 1}).
     */
    public static final int TERRAN_VEHICLE_PLATING = 1;
    /**
     * Terran Ship Plating (upgrades.dat entry {@code 2}).
     */
    public static final int TERRAN_SHIP_PLATING = 2;
    /**
     * Zerg Carapace (upgrades.dat entry {@code 3}).
     */
    public static final int ZERG_CARAPACE = 3;
    /**
     * Zerg Flyer Carapace (upgrades.dat entry {@code 4}).
     */
    public static final int ZERG_FLYER_CARAPACE = 4;
    /**
     * Protoss Ground Armor (upgrades.dat entry {@code 5}).
     */
    public static final int PROTOSS_GROUND_ARMOR = 5;
    /**
     * Protoss Air Armor (upgrades.dat entry {@code 6}).
     */
    public static final int PROTOSS_AIR_ARMOR = 6;
    /**
     * Terran Infantry Weapons (upgrades.dat entry {@code 7}).
     */
    public static final int TERRAN_INFANTRY_WEAPONS = 7;
    /**
     * Terran Vehicle Weapons (upgrades.dat entry {@code 8}).
     */
    public static final int TERRAN_VEHICLE_WEAPONS = 8;
    /**
     * Terran Ship Weapons (upgrades.dat entry {@code 9}).
     */
    public static final int TERRAN_SHIP_WEAPONS = 9;
    /**
     * Zerg Melee Attacks (upgrades.dat entry {@code 10}).
     */
    public static final int ZERG_MELEE_ATTACKS = 10;
    /**
     * Zerg Missile Attacks (upgrades.dat entry {@code 11}).
     */
    public static final int ZERG_MISSILE_ATTACKS = 11;
    /**
     * Zerg Flyer Attacks (upgrades.dat entry {@code 12}).
     */
    public static final int ZERG_FLYER_ATTACKS = 12;
    /**
     * Protoss Ground Weapons (upgrades.dat entry {@code 13}).
     */
    public static final int PROTOSS_GROUND_WEAPONS = 13;
    /**
     * Protoss Air Weapons (upgrades.dat entry {@code 14}).
     */
    public static final int PROTOSS_AIR_WEAPONS = 14;
    /**
     * Protoss Plasma Shields (upgrades.dat entry {@code 15}).
     */
    public static final int PROTOSS_PLASMA_SHIELDS = 15;
    /**
     * U 238 Shells (upgrades.dat entry {@code 16}).
     */
    public static final int U_238_SHELLS = 16;
    /**
     * Ion Thrusters (upgrades.dat entry {@code 17}).
     */
    public static final int ION_THRUSTERS = 17;
    /**
     * Unknown entry 18 (upgrades.dat entry {@code 18}).
     */
    public static final int UNKNOWN_18 = 18;
    /**
     * Titan Reactor (upgrades.dat entry {@code 19}).
     */
    public static final int TITAN_REACTOR = 19;
    /**
     * Ocular Implants (upgrades.dat entry {@code 20}).
     */
    public static final int OCULAR_IMPLANTS = 20;
    /**
     * Moebius Reactor (upgrades.dat entry {@code 21}).
     */
    public static final int MOEBIUS_REACTOR = 21;
    /**
     * Apollo Reactor (upgrades.dat entry {@code 22}).
     */
    public static final int APOLLO_REACTOR = 22;
    /**
     * Colossus Reactor (upgrades.dat entry {@code 23}).
     */
    public static final int COLOSSUS_REACTOR = 23;
    /**
     * Ventral Sacs (upgrades.dat entry {@code 24}).
     */
    public static final int VENTRAL_SACS = 24;
    /**
     * Antennae (upgrades.dat entry {@code 25}).
     */
    public static final int ANTENNAE = 25;
    /**
     * Pneumatized Carapace (upgrades.dat entry {@code 26}).
     */
    public static final int PNEUMATIZED_CARAPACE = 26;
    /**
     * Metabolic Boost (upgrades.dat entry {@code 27}).
     */
    public static final int METABOLIC_BOOST = 27;
    /**
     * Adrenal Glands (upgrades.dat entry {@code 28}).
     */
    public static final int ADRENAL_GLANDS = 28;
    /**
     * Muscular Augments (upgrades.dat entry {@code 29}).
     */
    public static final int MUSCULAR_AUGMENTS = 29;
    /**
     * Grooved Spines (upgrades.dat entry {@code 30}).
     */
    public static final int GROOVED_SPINES = 30;
    /**
     * Gamete Meiosis (upgrades.dat entry {@code 31}).
     */
    public static final int GAMETE_MEIOSIS = 31;
    /**
     * Metasynaptic Node (upgrades.dat entry {@code 32}).
     */
    public static final int METASYNAPTIC_NODE = 32;
    /**
     * Singularity Charge (upgrades.dat entry {@code 33}).
     */
    public static final int SINGULARITY_CHARGE = 33;
    /**
     * Leg Enhancements (upgrades.dat entry {@code 34}).
     */
    public static final int LEG_ENHANCEMENTS = 34;
    /**
     * Scarab Damage (upgrades.dat entry {@code 35}).
     */
    public static final int SCARAB_DAMAGE = 35;
    /**
     * Reaver Capacity (upgrades.dat entry {@code 36}).
     */
    public static final int REAVER_CAPACITY = 36;
    /**
     * Gravitic Drive (upgrades.dat entry {@code 37}).
     */
    public static final int GRAVITIC_DRIVE = 37;
    /**
     * Sensor Array (upgrades.dat entry {@code 38}).
     */
    public static final int SENSOR_ARRAY = 38;
    /**
     * Gravitic Boosters (upgrades.dat entry {@code 39}).
     */
    public static final int GRAVITIC_BOOSTERS = 39;
    /**
     * Khaydarin Amulet (upgrades.dat entry {@code 40}).
     */
    public static final int KHAYDARIN_AMULET = 40;
    /**
     * Apial Sensors (upgrades.dat entry {@code 41}).
     */
    public static final int APIAL_SENSORS = 41;
    /**
     * Gravitic Thrusters (upgrades.dat entry {@code 42}).
     */
    public static final int GRAVITIC_THRUSTERS = 42;
    /**
     * Carrier Capacity (upgrades.dat entry {@code 43}).
     */
    public static final int CARRIER_CAPACITY = 43;
    /**
     * Khaydarin Core (upgrades.dat entry {@code 44}).
     */
    public static final int KHAYDARIN_CORE = 44;
    /**
     * Unknown entry 45 (upgrades.dat entry {@code 45}).
     */
    public static final int UNKNOWN_45 = 45;
    /**
     * Unknown entry 46 (upgrades.dat entry {@code 46}).
     */
    public static final int UNKNOWN_46 = 46;
    /**
     * Argus Jewel (upgrades.dat entry {@code 47}).
     */
    public static final int ARGUS_JEWEL = 47;
    /**
     * Unknown entry 48 (upgrades.dat entry {@code 48}).
     */
    public static final int UNKNOWN_48 = 48;
    /**
     * Argus Talisman (upgrades.dat entry {@code 49}).
     */
    public static final int ARGUS_TALISMAN = 49;
    /**
     * Unknown entry 50 (upgrades.dat entry {@code 50}).
     */
    public static final int UNKNOWN_50 = 50;
    /**
     * Caduceus Reactor (upgrades.dat entry {@code 51}).
     */
    public static final int CADUCEUS_REACTOR = 51;
    /**
     * Chitinous Plating (upgrades.dat entry {@code 52}).
     */
    public static final int CHITINOUS_PLATING = 52;
    /**
     * Anabolic Synthesis (upgrades.dat entry {@code 53}).
     */
    public static final int ANABOLIC_SYNTHESIS = 53;
    /**
     * Charon Boosters (upgrades.dat entry {@code 54}).
     */
    public static final int CHARON_BOOSTERS = 54;
    /**
     * Unused 55 (upgrades.dat entry {@code 55}).
     */
    public static final int UNUSED_55 = 55;
    /**
     * Unused 56 (upgrades.dat entry {@code 56}).
     */
    public static final int UNUSED_56 = 56;
    /**
     * Unused 57 (upgrades.dat entry {@code 57}).
     */
    public static final int UNUSED_57 = 57;
    /**
     * Unused 58 (upgrades.dat entry {@code 58}).
     */
    public static final int UNUSED_58 = 58;
    /**
     * Unused 59 (upgrades.dat entry {@code 59}).
     */
    public static final int UNUSED_59 = 59;
    /**
     * Unused 60 (upgrades.dat entry {@code 60}).
     */
    public static final int UNUSED_60 = 60;
    /**
     * The "none" sentinel index ({@code 61}) that follows the upgrades.dat entries.
     */
    public static final int NONE = 61;

    /**
     * The number of values, from {@code 0} through {@link #NONE}.
     */
    private static final int VALUE_COUNT = 62;

    /**
     * @param identifier the value to check
     * @return {@code true} if the value is a upgrade identifier, {@code false} otherwise
     */
    public static boolean isValid(int identifier) {
        return identifier >= 0 && identifier < VALUE_COUNT;
    }

    /**
     * Checks that a value is a upgrade identifier.
     *
     * @param identifier the value to check
     * @return the value
     * @throws IllegalArgumentException if the value is not a upgrade identifier
     */
    public static int requireValid(int identifier) {
        if (!isValid(identifier)) {
            throw Exceptions.newIllegalArgumentException("Invalid upgrade type index: {0}", identifier);
        }

        return identifier;
    }

    /**
     * Returns a value, or {@link #NONE} for a value outside the table: a {@code switch} over the result without a
     * {@code NONE} case keeps its {@code default} branch for values read from replays, triggers or scripts.
     *
     * @param identifier the value
     * @return the value if it is valid, {@link #NONE} otherwise
     */
    public static int orNone(int identifier) {
        return isValid(identifier) ? identifier : NONE;
    }


    private ScrUpgradeIds() {
    }
}
