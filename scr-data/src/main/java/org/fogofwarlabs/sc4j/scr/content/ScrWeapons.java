package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.engine.content.type.WeaponType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds the weapon type identities of the SC:R mod, one per weapons.dat entry, in weapons.dat order. The mod registers them in this
 * order, so the index of each registered type is its weapons.dat entry ({@link ScrNativeIds}).
 */
public final class ScrWeapons {
    /**
     * The number of weapons.dat entries: the registered weapon types.
     */
    public static final int COUNT = 130;
    /**
     * The weapons.dat entry that names no weapon type, stored as {@link ContentType#NO_INDEX} in the engine tables.
     */
    public static final int NATIVE_NONE = 130;

    private static final @NotNull WeaponType @NotNull [] TYPES = new WeaponType[COUNT];

    /**
     * Gauss Rifle (weapons.dat entry {@code 0}).
     */
    public static final @NotNull WeaponType GAUSS_RIFLE = create(0, "Gauss Rifle");
    /**
     * Gauss Rifle Jim Raynor (weapons.dat entry {@code 1}).
     */
    public static final @NotNull WeaponType GAUSS_RIFLE_JIM_RAYNOR = create(1, "Gauss Rifle Jim Raynor");
    /**
     * C 10 Canister Rifle (weapons.dat entry {@code 2}).
     */
    public static final @NotNull WeaponType C_10_CANISTER_RIFLE = create(2, "C 10 Canister Rifle");
    /**
     * C 10 Canister Rifle Sarah Kerrigan (weapons.dat entry {@code 3}).
     */
    public static final @NotNull WeaponType C_10_CANISTER_RIFLE_SARAH_KERRIGAN = create(3, "C 10 Canister Rifle Sarah Kerrigan");
    /**
     * Fragmentation Grenade (weapons.dat entry {@code 4}).
     */
    public static final @NotNull WeaponType FRAGMENTATION_GRENADE = create(4, "Fragmentation Grenade");
    /**
     * Fragmentation Grenade Jim Raynor (weapons.dat entry {@code 5}).
     */
    public static final @NotNull WeaponType FRAGMENTATION_GRENADE_JIM_RAYNOR = create(5, "Fragmentation Grenade Jim Raynor");
    /**
     * Spider Mines (weapons.dat entry {@code 6}).
     */
    public static final @NotNull WeaponType SPIDER_MINES = create(6, "Spider Mines");
    /**
     * Twin Autocannons (weapons.dat entry {@code 7}).
     */
    public static final @NotNull WeaponType TWIN_AUTOCANNONS = create(7, "Twin Autocannons");
    /**
     * Hellfire Missile Pack (weapons.dat entry {@code 8}).
     */
    public static final @NotNull WeaponType HELLFIRE_MISSILE_PACK = create(8, "Hellfire Missile Pack");
    /**
     * Twin Autocannons Alan Schezar (weapons.dat entry {@code 9}).
     */
    public static final @NotNull WeaponType TWIN_AUTOCANNONS_ALAN_SCHEZAR = create(9, "Twin Autocannons Alan Schezar");
    /**
     * Hellfire Missile Pack Alan Schezar (weapons.dat entry {@code 10}).
     */
    public static final @NotNull WeaponType HELLFIRE_MISSILE_PACK_ALAN_SCHEZAR = create(10, "Hellfire Missile Pack Alan Schezar");
    /**
     * Arclite Cannon (weapons.dat entry {@code 11}).
     */
    public static final @NotNull WeaponType ARCLITE_CANNON = create(11, "Arclite Cannon");
    /**
     * Arclite Cannon Edmund Duke (weapons.dat entry {@code 12}).
     */
    public static final @NotNull WeaponType ARCLITE_CANNON_EDMUND_DUKE = create(12, "Arclite Cannon Edmund Duke");
    /**
     * Fusion Cutter (weapons.dat entry {@code 13}).
     */
    public static final @NotNull WeaponType FUSION_CUTTER = create(13, "Fusion Cutter");
    /**
     * unknown entry 14 (weapons.dat entry {@code 14}).
     */
    public static final @NotNull WeaponType UNKNOWN_14 = create(14, "unknown entry 14");
    /**
     * Gemini Missiles (weapons.dat entry {@code 15}).
     */
    public static final @NotNull WeaponType GEMINI_MISSILES = create(15, "Gemini Missiles");
    /**
     * Burst Lasers (weapons.dat entry {@code 16}).
     */
    public static final @NotNull WeaponType BURST_LASERS = create(16, "Burst Lasers");
    /**
     * Gemini Missiles Tom Kazansky (weapons.dat entry {@code 17}).
     */
    public static final @NotNull WeaponType GEMINI_MISSILES_TOM_KAZANSKY = create(17, "Gemini Missiles Tom Kazansky");
    /**
     * Burst Lasers Tom Kazansky (weapons.dat entry {@code 18}).
     */
    public static final @NotNull WeaponType BURST_LASERS_TOM_KAZANSKY = create(18, "Burst Lasers Tom Kazansky");
    /**
     * ATS Laser Battery (weapons.dat entry {@code 19}).
     */
    public static final @NotNull WeaponType ATS_LASER_BATTERY = create(19, "ATS Laser Battery");
    /**
     * ATA Laser Battery (weapons.dat entry {@code 20}).
     */
    public static final @NotNull WeaponType ATA_LASER_BATTERY = create(20, "ATA Laser Battery");
    /**
     * ATS Laser Battery Hero (weapons.dat entry {@code 21}).
     */
    public static final @NotNull WeaponType ATS_LASER_BATTERY_HERO = create(21, "ATS Laser Battery Hero");
    /**
     * ATA Laser Battery Hero (weapons.dat entry {@code 22}).
     */
    public static final @NotNull WeaponType ATA_LASER_BATTERY_HERO = create(22, "ATA Laser Battery Hero");
    /**
     * ATS Laser Battery Hyperion (weapons.dat entry {@code 23}).
     */
    public static final @NotNull WeaponType ATS_LASER_BATTERY_HYPERION = create(23, "ATS Laser Battery Hyperion");
    /**
     * ATA Laser Battery Hyperion (weapons.dat entry {@code 24}).
     */
    public static final @NotNull WeaponType ATA_LASER_BATTERY_HYPERION = create(24, "ATA Laser Battery Hyperion");
    /**
     * Flame Thrower (weapons.dat entry {@code 25}).
     */
    public static final @NotNull WeaponType FLAME_THROWER = create(25, "Flame Thrower");
    /**
     * Flame Thrower Gui Montag (weapons.dat entry {@code 26}).
     */
    public static final @NotNull WeaponType FLAME_THROWER_GUI_MONTAG = create(26, "Flame Thrower Gui Montag");
    /**
     * Arclite Shock Cannon (weapons.dat entry {@code 27}).
     */
    public static final @NotNull WeaponType ARCLITE_SHOCK_CANNON = create(27, "Arclite Shock Cannon");
    /**
     * Arclite Shock Cannon Edmund Duke (weapons.dat entry {@code 28}).
     */
    public static final @NotNull WeaponType ARCLITE_SHOCK_CANNON_EDMUND_DUKE = create(28, "Arclite Shock Cannon Edmund Duke");
    /**
     * Longbolt Missile (weapons.dat entry {@code 29}).
     */
    public static final @NotNull WeaponType LONGBOLT_MISSILE = create(29, "Longbolt Missile");
    /**
     * Yamato Gun (weapons.dat entry {@code 30}).
     */
    public static final @NotNull WeaponType YAMATO_GUN = create(30, "Yamato Gun");
    /**
     * Nuclear Strike (weapons.dat entry {@code 31}).
     */
    public static final @NotNull WeaponType NUCLEAR_STRIKE = create(31, "Nuclear Strike");
    /**
     * Lockdown (weapons.dat entry {@code 32}).
     */
    public static final @NotNull WeaponType LOCKDOWN = create(32, "Lockdown");
    /**
     * EMP Shockwave (weapons.dat entry {@code 33}).
     */
    public static final @NotNull WeaponType EMP_SHOCKWAVE = create(33, "EMP Shockwave");
    /**
     * Irradiate (weapons.dat entry {@code 34}).
     */
    public static final @NotNull WeaponType IRRADIATE = create(34, "Irradiate");
    /**
     * Claws (weapons.dat entry {@code 35}).
     */
    public static final @NotNull WeaponType CLAWS = create(35, "Claws");
    /**
     * Claws Devouring One (weapons.dat entry {@code 36}).
     */
    public static final @NotNull WeaponType CLAWS_DEVOURING_ONE = create(36, "Claws Devouring One");
    /**
     * Claws Infested Kerrigan (weapons.dat entry {@code 37}).
     */
    public static final @NotNull WeaponType CLAWS_INFESTED_KERRIGAN = create(37, "Claws Infested Kerrigan");
    /**
     * Needle Spines (weapons.dat entry {@code 38}).
     */
    public static final @NotNull WeaponType NEEDLE_SPINES = create(38, "Needle Spines");
    /**
     * Needle Spines Hunter Killer (weapons.dat entry {@code 39}).
     */
    public static final @NotNull WeaponType NEEDLE_SPINES_HUNTER_KILLER = create(39, "Needle Spines Hunter Killer");
    /**
     * Kaiser Blades (weapons.dat entry {@code 40}).
     */
    public static final @NotNull WeaponType KAISER_BLADES = create(40, "Kaiser Blades");
    /**
     * Kaiser Blades Torrasque (weapons.dat entry {@code 41}).
     */
    public static final @NotNull WeaponType KAISER_BLADES_TORRASQUE = create(41, "Kaiser Blades Torrasque");
    /**
     * Toxic Spores (weapons.dat entry {@code 42}).
     */
    public static final @NotNull WeaponType TOXIC_SPORES = create(42, "Toxic Spores");
    /**
     * Spines (weapons.dat entry {@code 43}).
     */
    public static final @NotNull WeaponType SPINES = create(43, "Spines");
    /**
     * unknown entry 44 (weapons.dat entry {@code 44}).
     */
    public static final @NotNull WeaponType UNKNOWN_44 = create(44, "unknown entry 44");
    /**
     * unknown entry 45 (weapons.dat entry {@code 45}).
     */
    public static final @NotNull WeaponType UNKNOWN_45 = create(45, "unknown entry 45");
    /**
     * Acid Spore (weapons.dat entry {@code 46}).
     */
    public static final @NotNull WeaponType ACID_SPORE = create(46, "Acid Spore");
    /**
     * Acid Spore Kukulza (weapons.dat entry {@code 47}).
     */
    public static final @NotNull WeaponType ACID_SPORE_KUKULZA = create(47, "Acid Spore Kukulza");
    /**
     * Glave Wurm (weapons.dat entry {@code 48}).
     */
    public static final @NotNull WeaponType GLAVE_WURM = create(48, "Glave Wurm");
    /**
     * Glave Wurm Kukulza (weapons.dat entry {@code 49}).
     */
    public static final @NotNull WeaponType GLAVE_WURM_KUKULZA = create(49, "Glave Wurm Kukulza");
    /**
     * unknown entry 50 (weapons.dat entry {@code 50}).
     */
    public static final @NotNull WeaponType UNKNOWN_50 = create(50, "unknown entry 50");
    /**
     * unknown entry 51 (weapons.dat entry {@code 51}).
     */
    public static final @NotNull WeaponType UNKNOWN_51 = create(51, "unknown entry 51");
    /**
     * Seeker Spores (weapons.dat entry {@code 52}).
     */
    public static final @NotNull WeaponType SEEKER_SPORES = create(52, "Seeker Spores");
    /**
     * Subterranean Tentacle (weapons.dat entry {@code 53}).
     */
    public static final @NotNull WeaponType SUBTERRANEAN_TENTACLE = create(53, "Subterranean Tentacle");
    /**
     * Suicide Infested Terran (weapons.dat entry {@code 54}).
     */
    public static final @NotNull WeaponType SUICIDE_INFESTED_TERRAN = create(54, "Suicide Infested Terran");
    /**
     * Suicide Scourge (weapons.dat entry {@code 55}).
     */
    public static final @NotNull WeaponType SUICIDE_SCOURGE = create(55, "Suicide Scourge");
    /**
     * Parasite (weapons.dat entry {@code 56}).
     */
    public static final @NotNull WeaponType PARASITE = create(56, "Parasite");
    /**
     * Spawn Broodlings (weapons.dat entry {@code 57}).
     */
    public static final @NotNull WeaponType SPAWN_BROODLINGS = create(57, "Spawn Broodlings");
    /**
     * Ensnare (weapons.dat entry {@code 58}).
     */
    public static final @NotNull WeaponType ENSNARE = create(58, "Ensnare");
    /**
     * Dark Swarm (weapons.dat entry {@code 59}).
     */
    public static final @NotNull WeaponType DARK_SWARM = create(59, "Dark Swarm");
    /**
     * Plague (weapons.dat entry {@code 60}).
     */
    public static final @NotNull WeaponType PLAGUE = create(60, "Plague");
    /**
     * Consume (weapons.dat entry {@code 61}).
     */
    public static final @NotNull WeaponType CONSUME = create(61, "Consume");
    /**
     * Particle Beam (weapons.dat entry {@code 62}).
     */
    public static final @NotNull WeaponType PARTICLE_BEAM = create(62, "Particle Beam");
    /**
     * unknown entry 63 (weapons.dat entry {@code 63}).
     */
    public static final @NotNull WeaponType UNKNOWN_63 = create(63, "unknown entry 63");
    /**
     * Psi Blades (weapons.dat entry {@code 64}).
     */
    public static final @NotNull WeaponType PSI_BLADES = create(64, "Psi Blades");
    /**
     * Psi Blades Fenix (weapons.dat entry {@code 65}).
     */
    public static final @NotNull WeaponType PSI_BLADES_FENIX = create(65, "Psi Blades Fenix");
    /**
     * Phase Disruptor (weapons.dat entry {@code 66}).
     */
    public static final @NotNull WeaponType PHASE_DISRUPTOR = create(66, "Phase Disruptor");
    /**
     * Phase Disruptor Fenix (weapons.dat entry {@code 67}).
     */
    public static final @NotNull WeaponType PHASE_DISRUPTOR_FENIX = create(67, "Phase Disruptor Fenix");
    /**
     * unknown entry 68 (weapons.dat entry {@code 68}).
     */
    public static final @NotNull WeaponType UNKNOWN_68 = create(68, "unknown entry 68");
    /**
     * Psi Assault (weapons.dat entry {@code 69}).
     */
    public static final @NotNull WeaponType PSI_ASSAULT = create(69, "Psi Assault");
    /**
     * Psionic Shockwave (weapons.dat entry {@code 70}).
     */
    public static final @NotNull WeaponType PSIONIC_SHOCKWAVE = create(70, "Psionic Shockwave");
    /**
     * Psionic Shockwave TZ Archon (weapons.dat entry {@code 71}).
     */
    public static final @NotNull WeaponType PSIONIC_SHOCKWAVE_TZ_ARCHON = create(71, "Psionic Shockwave TZ Archon");
    /**
     * unknown entry 72 (weapons.dat entry {@code 72}).
     */
    public static final @NotNull WeaponType UNKNOWN_72 = create(72, "unknown entry 72");
    /**
     * Dual Photon Blasters (weapons.dat entry {@code 73}).
     */
    public static final @NotNull WeaponType DUAL_PHOTON_BLASTERS = create(73, "Dual Photon Blasters");
    /**
     * Anti Matter Missiles (weapons.dat entry {@code 74}).
     */
    public static final @NotNull WeaponType ANTI_MATTER_MISSILES = create(74, "Anti Matter Missiles");
    /**
     * Dual Photon Blasters Mojo (weapons.dat entry {@code 75}).
     */
    public static final @NotNull WeaponType DUAL_PHOTON_BLASTERS_MOJO = create(75, "Dual Photon Blasters Mojo");
    /**
     * Anti Matter Missiles Mojo (weapons.dat entry {@code 76}).
     */
    public static final @NotNull WeaponType ANTI_MATTER_MISSILES_MOJO = create(76, "Anti Matter Missiles Mojo");
    /**
     * Phase Disruptor Cannon (weapons.dat entry {@code 77}).
     */
    public static final @NotNull WeaponType PHASE_DISRUPTOR_CANNON = create(77, "Phase Disruptor Cannon");
    /**
     * Phase Disruptor Cannon Danimoth (weapons.dat entry {@code 78}).
     */
    public static final @NotNull WeaponType PHASE_DISRUPTOR_CANNON_DANIMOTH = create(78, "Phase Disruptor Cannon Danimoth");
    /**
     * Pulse Cannon (weapons.dat entry {@code 79}).
     */
    public static final @NotNull WeaponType PULSE_CANNON = create(79, "Pulse Cannon");
    /**
     * STS Photon Cannon (weapons.dat entry {@code 80}).
     */
    public static final @NotNull WeaponType STS_PHOTON_CANNON = create(80, "STS Photon Cannon");
    /**
     * STA Photon Cannon (weapons.dat entry {@code 81}).
     */
    public static final @NotNull WeaponType STA_PHOTON_CANNON = create(81, "STA Photon Cannon");
    /**
     * Scarab (weapons.dat entry {@code 82}).
     */
    public static final @NotNull WeaponType SCARAB = create(82, "Scarab");
    /**
     * Stasis Field (weapons.dat entry {@code 83}).
     */
    public static final @NotNull WeaponType STASIS_FIELD = create(83, "Stasis Field");
    /**
     * Psionic Storm (weapons.dat entry {@code 84}).
     */
    public static final @NotNull WeaponType PSIONIC_STORM = create(84, "Psionic Storm");
    /**
     * Warp Blades Zeratul (weapons.dat entry {@code 85}).
     */
    public static final @NotNull WeaponType WARP_BLADES_ZERATUL = create(85, "Warp Blades Zeratul");
    /**
     * Warp Blades Hero (weapons.dat entry {@code 86}).
     */
    public static final @NotNull WeaponType WARP_BLADES_HERO = create(86, "Warp Blades Hero");
    /**
     * unknown entry 87 (weapons.dat entry {@code 87}).
     */
    public static final @NotNull WeaponType UNKNOWN_87 = create(87, "unknown entry 87");
    /**
     * unknown entry 88 (weapons.dat entry {@code 88}).
     */
    public static final @NotNull WeaponType UNKNOWN_88 = create(88, "unknown entry 88");
    /**
     * unknown entry 89 (weapons.dat entry {@code 89}).
     */
    public static final @NotNull WeaponType UNKNOWN_89 = create(89, "unknown entry 89");
    /**
     * unknown entry 90 (weapons.dat entry {@code 90}).
     */
    public static final @NotNull WeaponType UNKNOWN_90 = create(90, "unknown entry 90");
    /**
     * unknown entry 91 (weapons.dat entry {@code 91}).
     */
    public static final @NotNull WeaponType UNKNOWN_91 = create(91, "unknown entry 91");
    /**
     * Platform Laser Battery (weapons.dat entry {@code 92}).
     */
    public static final @NotNull WeaponType PLATFORM_LASER_BATTERY = create(92, "Platform Laser Battery");
    /**
     * Independant Laser Battery (weapons.dat entry {@code 93}).
     */
    public static final @NotNull WeaponType INDEPENDANT_LASER_BATTERY = create(93, "Independant Laser Battery");
    /**
     * unknown entry 94 (weapons.dat entry {@code 94}).
     */
    public static final @NotNull WeaponType UNKNOWN_94 = create(94, "unknown entry 94");
    /**
     * unknown entry 95 (weapons.dat entry {@code 95}).
     */
    public static final @NotNull WeaponType UNKNOWN_95 = create(95, "unknown entry 95");
    /**
     * Twin Autocannons Floor Trap (weapons.dat entry {@code 96}).
     */
    public static final @NotNull WeaponType TWIN_AUTOCANNONS_FLOOR_TRAP = create(96, "Twin Autocannons Floor Trap");
    /**
     * Hellfire Missile Pack Wall Trap (weapons.dat entry {@code 97}).
     */
    public static final @NotNull WeaponType HELLFIRE_MISSILE_PACK_WALL_TRAP = create(97, "Hellfire Missile Pack Wall Trap");
    /**
     * Flame Thrower Wall Trap (weapons.dat entry {@code 98}).
     */
    public static final @NotNull WeaponType FLAME_THROWER_WALL_TRAP = create(98, "Flame Thrower Wall Trap");
    /**
     * Hellfire Missile Pack Floor Trap (weapons.dat entry {@code 99}).
     */
    public static final @NotNull WeaponType HELLFIRE_MISSILE_PACK_FLOOR_TRAP = create(99, "Hellfire Missile Pack Floor Trap");
    /**
     * Neutron Flare (weapons.dat entry {@code 100}).
     */
    public static final @NotNull WeaponType NEUTRON_FLARE = create(100, "Neutron Flare");
    /**
     * Disruption Web (weapons.dat entry {@code 101}).
     */
    public static final @NotNull WeaponType DISRUPTION_WEB = create(101, "Disruption Web");
    /**
     * Restoration (weapons.dat entry {@code 102}).
     */
    public static final @NotNull WeaponType RESTORATION = create(102, "Restoration");
    /**
     * Halo Rockets (weapons.dat entry {@code 103}).
     */
    public static final @NotNull WeaponType HALO_ROCKETS = create(103, "Halo Rockets");
    /**
     * Corrosive Acid (weapons.dat entry {@code 104}).
     */
    public static final @NotNull WeaponType CORROSIVE_ACID = create(104, "Corrosive Acid");
    /**
     * Mind Control (weapons.dat entry {@code 105}).
     */
    public static final @NotNull WeaponType MIND_CONTROL = create(105, "Mind Control");
    /**
     * Feedback (weapons.dat entry {@code 106}).
     */
    public static final @NotNull WeaponType FEEDBACK = create(106, "Feedback");
    /**
     * Optical Flare (weapons.dat entry {@code 107}).
     */
    public static final @NotNull WeaponType OPTICAL_FLARE = create(107, "Optical Flare");
    /**
     * Maelstrom (weapons.dat entry {@code 108}).
     */
    public static final @NotNull WeaponType MAELSTROM = create(108, "Maelstrom");
    /**
     * Subterranean Spines (weapons.dat entry {@code 109}).
     */
    public static final @NotNull WeaponType SUBTERRANEAN_SPINES = create(109, "Subterranean Spines");
    /**
     * Warp Blades (weapons.dat entry {@code 110}).
     */
    public static final @NotNull WeaponType WARP_BLADES = create(110, "Warp Blades");
    /**
     * C 10 Canister Rifle Samir Duran (weapons.dat entry {@code 111}).
     */
    public static final @NotNull WeaponType C_10_CANISTER_RIFLE_SAMIR_DURAN = create(111, "C 10 Canister Rifle Samir Duran");
    /**
     * C 10 Canister Rifle Infested Duran (weapons.dat entry {@code 112}).
     */
    public static final @NotNull WeaponType C_10_CANISTER_RIFLE_INFESTED_DURAN = create(112, "C 10 Canister Rifle Infested Duran");
    /**
     * Dual Photon Blasters Artanis (weapons.dat entry {@code 113}).
     */
    public static final @NotNull WeaponType DUAL_PHOTON_BLASTERS_ARTANIS = create(113, "Dual Photon Blasters Artanis");
    /**
     * Anti Matter Missiles Artanis (weapons.dat entry {@code 114}).
     */
    public static final @NotNull WeaponType ANTI_MATTER_MISSILES_ARTANIS = create(114, "Anti Matter Missiles Artanis");
    /**
     * C 10 Canister Rifle Alexei Stukov (weapons.dat entry {@code 115}).
     */
    public static final @NotNull WeaponType C_10_CANISTER_RIFLE_ALEXEI_STUKOV = create(115, "C 10 Canister Rifle Alexei Stukov");
    /**
     * unknown entry 116 (weapons.dat entry {@code 116}).
     */
    public static final @NotNull WeaponType UNKNOWN_116 = create(116, "unknown entry 116");
    /**
     * unknown entry 117 (weapons.dat entry {@code 117}).
     */
    public static final @NotNull WeaponType UNKNOWN_117 = create(117, "unknown entry 117");
    /**
     * unknown entry 118 (weapons.dat entry {@code 118}).
     */
    public static final @NotNull WeaponType UNKNOWN_118 = create(118, "unknown entry 118");
    /**
     * unknown entry 119 (weapons.dat entry {@code 119}).
     */
    public static final @NotNull WeaponType UNKNOWN_119 = create(119, "unknown entry 119");
    /**
     * unknown entry 120 (weapons.dat entry {@code 120}).
     */
    public static final @NotNull WeaponType UNKNOWN_120 = create(120, "unknown entry 120");
    /**
     * unknown entry 121 (weapons.dat entry {@code 121}).
     */
    public static final @NotNull WeaponType UNKNOWN_121 = create(121, "unknown entry 121");
    /**
     * unknown entry 122 (weapons.dat entry {@code 122}).
     */
    public static final @NotNull WeaponType UNKNOWN_122 = create(122, "unknown entry 122");
    /**
     * unknown entry 123 (weapons.dat entry {@code 123}).
     */
    public static final @NotNull WeaponType UNKNOWN_123 = create(123, "unknown entry 123");
    /**
     * unknown entry 124 (weapons.dat entry {@code 124}).
     */
    public static final @NotNull WeaponType UNKNOWN_124 = create(124, "unknown entry 124");
    /**
     * unknown entry 125 (weapons.dat entry {@code 125}).
     */
    public static final @NotNull WeaponType UNKNOWN_125 = create(125, "unknown entry 125");
    /**
     * unknown entry 126 (weapons.dat entry {@code 126}).
     */
    public static final @NotNull WeaponType UNKNOWN_126 = create(126, "unknown entry 126");
    /**
     * unknown entry 127 (weapons.dat entry {@code 127}).
     */
    public static final @NotNull WeaponType UNKNOWN_127 = create(127, "unknown entry 127");
    /**
     * unknown entry 128 (weapons.dat entry {@code 128}).
     */
    public static final @NotNull WeaponType UNKNOWN_128 = create(128, "unknown entry 128");
    /**
     * unknown entry 129 (weapons.dat entry {@code 129}).
     */
    public static final @NotNull WeaponType UNKNOWN_129 = create(129, "unknown entry 129");

    private static final @NotNull List<@NotNull WeaponType> TYPE_LIST;

    static {
        TYPE_LIST = List.of(TYPES);
    }

    /**
     * Returns the identity of a weapons.dat entry.
     *
     * @param nativeId the weapons.dat entry, from {@code 0} to {@code COUNT - 1}
     * @return the identity
     * @throws IllegalArgumentException if the entry is outside the table
     */
    public static @NotNull WeaponType get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid weapons.dat entry: {0}", nativeId);
        }

        return TYPES[nativeId];
    }

    /**
     * @return every identity in weapons.dat order, as the mod registers them
     */
    public static @NotNull List<@NotNull WeaponType> getTypes() {
        return TYPE_LIST;
    }

    private static @NotNull WeaponType create(int nativeId, @NotNull String debugName) {
        WeaponType type = new WeaponType(debugName);

        TYPES[nativeId] = type;

        return type;
    }


    private ScrWeapons() {
    }
}
