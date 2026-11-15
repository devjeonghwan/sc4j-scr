package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;

/**
 * Holds the native weapon type identifiers (weapons.dat entries) of the SC:R mod as {@code int} constants: the values the SC:R
 * simulation stores and compares, and the values EUD, CHK sections, replays and saved games hold. {@link ScrWeapons} holds the
 * engine identities; {@link ScrNativeIds} maps between the two.
 */
public final class ScrWeaponIds {
    /**
     * Gauss Rifle (weapons.dat entry {@code 0}).
     */
    public static final int GAUSS_RIFLE = 0;
    /**
     * Gauss Rifle Jim Raynor (weapons.dat entry {@code 1}).
     */
    public static final int GAUSS_RIFLE_JIM_RAYNOR = 1;
    /**
     * C 10 Canister Rifle (weapons.dat entry {@code 2}).
     */
    public static final int C_10_CANISTER_RIFLE = 2;
    /**
     * C 10 Canister Rifle Sarah Kerrigan (weapons.dat entry {@code 3}).
     */
    public static final int C_10_CANISTER_RIFLE_SARAH_KERRIGAN = 3;
    /**
     * Fragmentation Grenade (weapons.dat entry {@code 4}).
     */
    public static final int FRAGMENTATION_GRENADE = 4;
    /**
     * Fragmentation Grenade Jim Raynor (weapons.dat entry {@code 5}).
     */
    public static final int FRAGMENTATION_GRENADE_JIM_RAYNOR = 5;
    /**
     * Spider Mines (weapons.dat entry {@code 6}).
     */
    public static final int SPIDER_MINES = 6;
    /**
     * Twin Autocannons (weapons.dat entry {@code 7}).
     */
    public static final int TWIN_AUTOCANNONS = 7;
    /**
     * Hellfire Missile Pack (weapons.dat entry {@code 8}).
     */
    public static final int HELLFIRE_MISSILE_PACK = 8;
    /**
     * Twin Autocannons Alan Schezar (weapons.dat entry {@code 9}).
     */
    public static final int TWIN_AUTOCANNONS_ALAN_SCHEZAR = 9;
    /**
     * Hellfire Missile Pack Alan Schezar (weapons.dat entry {@code 10}).
     */
    public static final int HELLFIRE_MISSILE_PACK_ALAN_SCHEZAR = 10;
    /**
     * Arclite Cannon (weapons.dat entry {@code 11}).
     */
    public static final int ARCLITE_CANNON = 11;
    /**
     * Arclite Cannon Edmund Duke (weapons.dat entry {@code 12}).
     */
    public static final int ARCLITE_CANNON_EDMUND_DUKE = 12;
    /**
     * Fusion Cutter (weapons.dat entry {@code 13}).
     */
    public static final int FUSION_CUTTER = 13;
    /**
     * unknown entry 14 (weapons.dat entry {@code 14}).
     */
    public static final int UNKNOWN_14 = 14;
    /**
     * Gemini Missiles (weapons.dat entry {@code 15}).
     */
    public static final int GEMINI_MISSILES = 15;
    /**
     * Burst Lasers (weapons.dat entry {@code 16}).
     */
    public static final int BURST_LASERS = 16;
    /**
     * Gemini Missiles Tom Kazansky (weapons.dat entry {@code 17}).
     */
    public static final int GEMINI_MISSILES_TOM_KAZANSKY = 17;
    /**
     * Burst Lasers Tom Kazansky (weapons.dat entry {@code 18}).
     */
    public static final int BURST_LASERS_TOM_KAZANSKY = 18;
    /**
     * ATS Laser Battery (weapons.dat entry {@code 19}).
     */
    public static final int ATS_LASER_BATTERY = 19;
    /**
     * ATA Laser Battery (weapons.dat entry {@code 20}).
     */
    public static final int ATA_LASER_BATTERY = 20;
    /**
     * ATS Laser Battery Hero (weapons.dat entry {@code 21}).
     */
    public static final int ATS_LASER_BATTERY_HERO = 21;
    /**
     * ATA Laser Battery Hero (weapons.dat entry {@code 22}).
     */
    public static final int ATA_LASER_BATTERY_HERO = 22;
    /**
     * ATS Laser Battery Hyperion (weapons.dat entry {@code 23}).
     */
    public static final int ATS_LASER_BATTERY_HYPERION = 23;
    /**
     * ATA Laser Battery Hyperion (weapons.dat entry {@code 24}).
     */
    public static final int ATA_LASER_BATTERY_HYPERION = 24;
    /**
     * Flame Thrower (weapons.dat entry {@code 25}).
     */
    public static final int FLAME_THROWER = 25;
    /**
     * Flame Thrower Gui Montag (weapons.dat entry {@code 26}).
     */
    public static final int FLAME_THROWER_GUI_MONTAG = 26;
    /**
     * Arclite Shock Cannon (weapons.dat entry {@code 27}).
     */
    public static final int ARCLITE_SHOCK_CANNON = 27;
    /**
     * Arclite Shock Cannon Edmund Duke (weapons.dat entry {@code 28}).
     */
    public static final int ARCLITE_SHOCK_CANNON_EDMUND_DUKE = 28;
    /**
     * Longbolt Missile (weapons.dat entry {@code 29}).
     */
    public static final int LONGBOLT_MISSILE = 29;
    /**
     * Yamato Gun (weapons.dat entry {@code 30}).
     */
    public static final int YAMATO_GUN = 30;
    /**
     * Nuclear Strike (weapons.dat entry {@code 31}).
     */
    public static final int NUCLEAR_STRIKE = 31;
    /**
     * Lockdown (weapons.dat entry {@code 32}).
     */
    public static final int LOCKDOWN = 32;
    /**
     * EMP Shockwave (weapons.dat entry {@code 33}).
     */
    public static final int EMP_SHOCKWAVE = 33;
    /**
     * Irradiate (weapons.dat entry {@code 34}).
     */
    public static final int IRRADIATE = 34;
    /**
     * Claws (weapons.dat entry {@code 35}).
     */
    public static final int CLAWS = 35;
    /**
     * Claws Devouring One (weapons.dat entry {@code 36}).
     */
    public static final int CLAWS_DEVOURING_ONE = 36;
    /**
     * Claws Infested Kerrigan (weapons.dat entry {@code 37}).
     */
    public static final int CLAWS_INFESTED_KERRIGAN = 37;
    /**
     * Needle Spines (weapons.dat entry {@code 38}).
     */
    public static final int NEEDLE_SPINES = 38;
    /**
     * Needle Spines Hunter Killer (weapons.dat entry {@code 39}).
     */
    public static final int NEEDLE_SPINES_HUNTER_KILLER = 39;
    /**
     * Kaiser Blades (weapons.dat entry {@code 40}).
     */
    public static final int KAISER_BLADES = 40;
    /**
     * Kaiser Blades Torrasque (weapons.dat entry {@code 41}).
     */
    public static final int KAISER_BLADES_TORRASQUE = 41;
    /**
     * Toxic Spores (weapons.dat entry {@code 42}).
     */
    public static final int TOXIC_SPORES = 42;
    /**
     * Spines (weapons.dat entry {@code 43}).
     */
    public static final int SPINES = 43;
    /**
     * unknown entry 44 (weapons.dat entry {@code 44}).
     */
    public static final int UNKNOWN_44 = 44;
    /**
     * unknown entry 45 (weapons.dat entry {@code 45}).
     */
    public static final int UNKNOWN_45 = 45;
    /**
     * Acid Spore (weapons.dat entry {@code 46}).
     */
    public static final int ACID_SPORE = 46;
    /**
     * Acid Spore Kukulza (weapons.dat entry {@code 47}).
     */
    public static final int ACID_SPORE_KUKULZA = 47;
    /**
     * Glave Wurm (weapons.dat entry {@code 48}).
     */
    public static final int GLAVE_WURM = 48;
    /**
     * Glave Wurm Kukulza (weapons.dat entry {@code 49}).
     */
    public static final int GLAVE_WURM_KUKULZA = 49;
    /**
     * unknown entry 50 (weapons.dat entry {@code 50}).
     */
    public static final int UNKNOWN_50 = 50;
    /**
     * unknown entry 51 (weapons.dat entry {@code 51}).
     */
    public static final int UNKNOWN_51 = 51;
    /**
     * Seeker Spores (weapons.dat entry {@code 52}).
     */
    public static final int SEEKER_SPORES = 52;
    /**
     * Subterranean Tentacle (weapons.dat entry {@code 53}).
     */
    public static final int SUBTERRANEAN_TENTACLE = 53;
    /**
     * Suicide Infested Terran (weapons.dat entry {@code 54}).
     */
    public static final int SUICIDE_INFESTED_TERRAN = 54;
    /**
     * Suicide Scourge (weapons.dat entry {@code 55}).
     */
    public static final int SUICIDE_SCOURGE = 55;
    /**
     * Parasite (weapons.dat entry {@code 56}).
     */
    public static final int PARASITE = 56;
    /**
     * Spawn Broodlings (weapons.dat entry {@code 57}).
     */
    public static final int SPAWN_BROODLINGS = 57;
    /**
     * Ensnare (weapons.dat entry {@code 58}).
     */
    public static final int ENSNARE = 58;
    /**
     * Dark Swarm (weapons.dat entry {@code 59}).
     */
    public static final int DARK_SWARM = 59;
    /**
     * Plague (weapons.dat entry {@code 60}).
     */
    public static final int PLAGUE = 60;
    /**
     * Consume (weapons.dat entry {@code 61}).
     */
    public static final int CONSUME = 61;
    /**
     * Particle Beam (weapons.dat entry {@code 62}).
     */
    public static final int PARTICLE_BEAM = 62;
    /**
     * unknown entry 63 (weapons.dat entry {@code 63}).
     */
    public static final int UNKNOWN_63 = 63;
    /**
     * Psi Blades (weapons.dat entry {@code 64}).
     */
    public static final int PSI_BLADES = 64;
    /**
     * Psi Blades Fenix (weapons.dat entry {@code 65}).
     */
    public static final int PSI_BLADES_FENIX = 65;
    /**
     * Phase Disruptor (weapons.dat entry {@code 66}).
     */
    public static final int PHASE_DISRUPTOR = 66;
    /**
     * Phase Disruptor Fenix (weapons.dat entry {@code 67}).
     */
    public static final int PHASE_DISRUPTOR_FENIX = 67;
    /**
     * unknown entry 68 (weapons.dat entry {@code 68}).
     */
    public static final int UNKNOWN_68 = 68;
    /**
     * Psi Assault (weapons.dat entry {@code 69}).
     */
    public static final int PSI_ASSAULT = 69;
    /**
     * Psionic Shockwave (weapons.dat entry {@code 70}).
     */
    public static final int PSIONIC_SHOCKWAVE = 70;
    /**
     * Psionic Shockwave TZ Archon (weapons.dat entry {@code 71}).
     */
    public static final int PSIONIC_SHOCKWAVE_TZ_ARCHON = 71;
    /**
     * unknown entry 72 (weapons.dat entry {@code 72}).
     */
    public static final int UNKNOWN_72 = 72;
    /**
     * Dual Photon Blasters (weapons.dat entry {@code 73}).
     */
    public static final int DUAL_PHOTON_BLASTERS = 73;
    /**
     * Anti Matter Missiles (weapons.dat entry {@code 74}).
     */
    public static final int ANTI_MATTER_MISSILES = 74;
    /**
     * Dual Photon Blasters Mojo (weapons.dat entry {@code 75}).
     */
    public static final int DUAL_PHOTON_BLASTERS_MOJO = 75;
    /**
     * Anti Matter Missiles Mojo (weapons.dat entry {@code 76}).
     */
    public static final int ANTI_MATTER_MISSILES_MOJO = 76;
    /**
     * Phase Disruptor Cannon (weapons.dat entry {@code 77}).
     */
    public static final int PHASE_DISRUPTOR_CANNON = 77;
    /**
     * Phase Disruptor Cannon Danimoth (weapons.dat entry {@code 78}).
     */
    public static final int PHASE_DISRUPTOR_CANNON_DANIMOTH = 78;
    /**
     * Pulse Cannon (weapons.dat entry {@code 79}).
     */
    public static final int PULSE_CANNON = 79;
    /**
     * STS Photon Cannon (weapons.dat entry {@code 80}).
     */
    public static final int STS_PHOTON_CANNON = 80;
    /**
     * STA Photon Cannon (weapons.dat entry {@code 81}).
     */
    public static final int STA_PHOTON_CANNON = 81;
    /**
     * Scarab (weapons.dat entry {@code 82}).
     */
    public static final int SCARAB = 82;
    /**
     * Stasis Field (weapons.dat entry {@code 83}).
     */
    public static final int STASIS_FIELD = 83;
    /**
     * Psionic Storm (weapons.dat entry {@code 84}).
     */
    public static final int PSIONIC_STORM = 84;
    /**
     * Warp Blades Zeratul (weapons.dat entry {@code 85}).
     */
    public static final int WARP_BLADES_ZERATUL = 85;
    /**
     * Warp Blades Hero (weapons.dat entry {@code 86}).
     */
    public static final int WARP_BLADES_HERO = 86;
    /**
     * unknown entry 87 (weapons.dat entry {@code 87}).
     */
    public static final int UNKNOWN_87 = 87;
    /**
     * unknown entry 88 (weapons.dat entry {@code 88}).
     */
    public static final int UNKNOWN_88 = 88;
    /**
     * unknown entry 89 (weapons.dat entry {@code 89}).
     */
    public static final int UNKNOWN_89 = 89;
    /**
     * unknown entry 90 (weapons.dat entry {@code 90}).
     */
    public static final int UNKNOWN_90 = 90;
    /**
     * unknown entry 91 (weapons.dat entry {@code 91}).
     */
    public static final int UNKNOWN_91 = 91;
    /**
     * Platform Laser Battery (weapons.dat entry {@code 92}).
     */
    public static final int PLATFORM_LASER_BATTERY = 92;
    /**
     * Independant Laser Battery (weapons.dat entry {@code 93}).
     */
    public static final int INDEPENDANT_LASER_BATTERY = 93;
    /**
     * unknown entry 94 (weapons.dat entry {@code 94}).
     */
    public static final int UNKNOWN_94 = 94;
    /**
     * unknown entry 95 (weapons.dat entry {@code 95}).
     */
    public static final int UNKNOWN_95 = 95;
    /**
     * Twin Autocannons Floor Trap (weapons.dat entry {@code 96}).
     */
    public static final int TWIN_AUTOCANNONS_FLOOR_TRAP = 96;
    /**
     * Hellfire Missile Pack Wall Trap (weapons.dat entry {@code 97}).
     */
    public static final int HELLFIRE_MISSILE_PACK_WALL_TRAP = 97;
    /**
     * Flame Thrower Wall Trap (weapons.dat entry {@code 98}).
     */
    public static final int FLAME_THROWER_WALL_TRAP = 98;
    /**
     * Hellfire Missile Pack Floor Trap (weapons.dat entry {@code 99}).
     */
    public static final int HELLFIRE_MISSILE_PACK_FLOOR_TRAP = 99;
    /**
     * Neutron Flare (weapons.dat entry {@code 100}).
     */
    public static final int NEUTRON_FLARE = 100;
    /**
     * Disruption Web (weapons.dat entry {@code 101}).
     */
    public static final int DISRUPTION_WEB = 101;
    /**
     * Restoration (weapons.dat entry {@code 102}).
     */
    public static final int RESTORATION = 102;
    /**
     * Halo Rockets (weapons.dat entry {@code 103}).
     */
    public static final int HALO_ROCKETS = 103;
    /**
     * Corrosive Acid (weapons.dat entry {@code 104}).
     */
    public static final int CORROSIVE_ACID = 104;
    /**
     * Mind Control (weapons.dat entry {@code 105}).
     */
    public static final int MIND_CONTROL = 105;
    /**
     * Feedback (weapons.dat entry {@code 106}).
     */
    public static final int FEEDBACK = 106;
    /**
     * Optical Flare (weapons.dat entry {@code 107}).
     */
    public static final int OPTICAL_FLARE = 107;
    /**
     * Maelstrom (weapons.dat entry {@code 108}).
     */
    public static final int MAELSTROM = 108;
    /**
     * Subterranean Spines (weapons.dat entry {@code 109}).
     */
    public static final int SUBTERRANEAN_SPINES = 109;
    /**
     * Warp Blades (weapons.dat entry {@code 110}).
     */
    public static final int WARP_BLADES = 110;
    /**
     * C 10 Canister Rifle Samir Duran (weapons.dat entry {@code 111}).
     */
    public static final int C_10_CANISTER_RIFLE_SAMIR_DURAN = 111;
    /**
     * C 10 Canister Rifle Infested Duran (weapons.dat entry {@code 112}).
     */
    public static final int C_10_CANISTER_RIFLE_INFESTED_DURAN = 112;
    /**
     * Dual Photon Blasters Artanis (weapons.dat entry {@code 113}).
     */
    public static final int DUAL_PHOTON_BLASTERS_ARTANIS = 113;
    /**
     * Anti Matter Missiles Artanis (weapons.dat entry {@code 114}).
     */
    public static final int ANTI_MATTER_MISSILES_ARTANIS = 114;
    /**
     * C 10 Canister Rifle Alexei Stukov (weapons.dat entry {@code 115}).
     */
    public static final int C_10_CANISTER_RIFLE_ALEXEI_STUKOV = 115;
    /**
     * unknown entry 116 (weapons.dat entry {@code 116}).
     */
    public static final int UNKNOWN_116 = 116;
    /**
     * unknown entry 117 (weapons.dat entry {@code 117}).
     */
    public static final int UNKNOWN_117 = 117;
    /**
     * unknown entry 118 (weapons.dat entry {@code 118}).
     */
    public static final int UNKNOWN_118 = 118;
    /**
     * unknown entry 119 (weapons.dat entry {@code 119}).
     */
    public static final int UNKNOWN_119 = 119;
    /**
     * unknown entry 120 (weapons.dat entry {@code 120}).
     */
    public static final int UNKNOWN_120 = 120;
    /**
     * unknown entry 121 (weapons.dat entry {@code 121}).
     */
    public static final int UNKNOWN_121 = 121;
    /**
     * unknown entry 122 (weapons.dat entry {@code 122}).
     */
    public static final int UNKNOWN_122 = 122;
    /**
     * unknown entry 123 (weapons.dat entry {@code 123}).
     */
    public static final int UNKNOWN_123 = 123;
    /**
     * unknown entry 124 (weapons.dat entry {@code 124}).
     */
    public static final int UNKNOWN_124 = 124;
    /**
     * unknown entry 125 (weapons.dat entry {@code 125}).
     */
    public static final int UNKNOWN_125 = 125;
    /**
     * unknown entry 126 (weapons.dat entry {@code 126}).
     */
    public static final int UNKNOWN_126 = 126;
    /**
     * unknown entry 127 (weapons.dat entry {@code 127}).
     */
    public static final int UNKNOWN_127 = 127;
    /**
     * unknown entry 128 (weapons.dat entry {@code 128}).
     */
    public static final int UNKNOWN_128 = 128;
    /**
     * unknown entry 129 (weapons.dat entry {@code 129}).
     */
    public static final int UNKNOWN_129 = 129;
    /**
     * The "none" sentinel index ({@code 130}) that follows the weapons.dat entries.
     */
    public static final int NONE = 130;

    /**
     * The number of values, from {@code 0} through {@link #NONE}.
     */
    private static final int VALUE_COUNT = 131;

    /**
     * @param identifier the value to check
     * @return {@code true} if the value is a weapon type identifier, {@code false} otherwise
     */
    public static boolean isValid(int identifier) {
        return identifier >= 0 && identifier < VALUE_COUNT;
    }

    /**
     * Checks that a value is a weapon type identifier.
     *
     * @param identifier the value to check
     * @return the value
     * @throws IllegalArgumentException if the value is not a weapon type identifier
     */
    public static int requireValid(int identifier) {
        if (!isValid(identifier)) {
            throw Exceptions.newIllegalArgumentException("Invalid weapon type index: {0}", identifier);
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


    private ScrWeaponIds() {
    }
}
