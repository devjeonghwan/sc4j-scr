package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the unit type identities of the SC:R mod, one per units.dat entry, in units.dat order. The mod registers them in this
 * order, so the index of each registered type is its units.dat entry ({@link ScrNativeIds}).
 */
public final class ScrUnits {
    /**
     * The number of units.dat entries: the registered unit types.
     */
    public static final int COUNT = 228;
    /**
     * The units.dat entry that names no unit type, stored as {@link ContentType#NO_INDEX} in the engine tables.
     */
    public static final int NATIVE_NONE = 228;

    private static final @NotNull UnitType @NotNull [] TYPES = new UnitType[COUNT];

    /**
     * Terran Marine (units.dat entry {@code 0}).
     */
    public static final @NotNull UnitType TERRAN_MARINE = create(0, "Terran Marine");
    /**
     * Terran Ghost (units.dat entry {@code 1}).
     */
    public static final @NotNull UnitType TERRAN_GHOST = create(1, "Terran Ghost");
    /**
     * Terran Vulture (units.dat entry {@code 2}).
     */
    public static final @NotNull UnitType TERRAN_VULTURE = create(2, "Terran Vulture");
    /**
     * Terran Goliath (units.dat entry {@code 3}).
     */
    public static final @NotNull UnitType TERRAN_GOLIATH = create(3, "Terran Goliath");
    /**
     * Terran Goliath Turret (units.dat entry {@code 4}).
     */
    public static final @NotNull UnitType TERRAN_GOLIATH_TURRET = create(4, "Terran Goliath Turret");
    /**
     * Terran Siege Tank Tank Mode (units.dat entry {@code 5}).
     */
    public static final @NotNull UnitType TERRAN_SIEGE_TANK_TANK_MODE = create(5, "Terran Siege Tank Tank Mode");
    /**
     * Terran Siege Tank Tank Mode Turret (units.dat entry {@code 6}).
     */
    public static final @NotNull UnitType TERRAN_SIEGE_TANK_TANK_MODE_TURRET = create(6, "Terran Siege Tank Tank Mode Turret");
    /**
     * Terran SCV (units.dat entry {@code 7}).
     */
    public static final @NotNull UnitType TERRAN_SCV = create(7, "Terran SCV");
    /**
     * Terran Wraith (units.dat entry {@code 8}).
     */
    public static final @NotNull UnitType TERRAN_WRAITH = create(8, "Terran Wraith");
    /**
     * Terran Science Vessel (units.dat entry {@code 9}).
     */
    public static final @NotNull UnitType TERRAN_SCIENCE_VESSEL = create(9, "Terran Science Vessel");
    /**
     * Hero Gui Montag (units.dat entry {@code 10}).
     */
    public static final @NotNull UnitType HERO_GUI_MONTAG = create(10, "Hero Gui Montag");
    /**
     * Terran Dropship (units.dat entry {@code 11}).
     */
    public static final @NotNull UnitType TERRAN_DROPSHIP = create(11, "Terran Dropship");
    /**
     * Terran Battlecruiser (units.dat entry {@code 12}).
     */
    public static final @NotNull UnitType TERRAN_BATTLECRUISER = create(12, "Terran Battlecruiser");
    /**
     * Terran Vulture Spider Mine (units.dat entry {@code 13}).
     */
    public static final @NotNull UnitType TERRAN_VULTURE_SPIDER_MINE = create(13, "Terran Vulture Spider Mine");
    /**
     * Terran Nuclear Missile (units.dat entry {@code 14}).
     */
    public static final @NotNull UnitType TERRAN_NUCLEAR_MISSILE = create(14, "Terran Nuclear Missile");
    /**
     * Terran Civilian (units.dat entry {@code 15}).
     */
    public static final @NotNull UnitType TERRAN_CIVILIAN = create(15, "Terran Civilian");
    /**
     * Hero Sarah Kerrigan (units.dat entry {@code 16}).
     */
    public static final @NotNull UnitType HERO_SARAH_KERRIGAN = create(16, "Hero Sarah Kerrigan");
    /**
     * Hero Alan Schezar (units.dat entry {@code 17}).
     */
    public static final @NotNull UnitType HERO_ALAN_SCHEZAR = create(17, "Hero Alan Schezar");
    /**
     * Hero Alan Schezar Turret (units.dat entry {@code 18}).
     */
    public static final @NotNull UnitType HERO_ALAN_SCHEZAR_TURRET = create(18, "Hero Alan Schezar Turret");
    /**
     * Hero Jim Raynor Vulture (units.dat entry {@code 19}).
     */
    public static final @NotNull UnitType HERO_JIM_RAYNOR_VULTURE = create(19, "Hero Jim Raynor Vulture");
    /**
     * Hero Jim Raynor Marine (units.dat entry {@code 20}).
     */
    public static final @NotNull UnitType HERO_JIM_RAYNOR_MARINE = create(20, "Hero Jim Raynor Marine");
    /**
     * Hero Tom Kazansky (units.dat entry {@code 21}).
     */
    public static final @NotNull UnitType HERO_TOM_KAZANSKY = create(21, "Hero Tom Kazansky");
    /**
     * Hero Magellan (units.dat entry {@code 22}).
     */
    public static final @NotNull UnitType HERO_MAGELLAN = create(22, "Hero Magellan");
    /**
     * Hero Edmund Duke Tank Mode (units.dat entry {@code 23}).
     */
    public static final @NotNull UnitType HERO_EDMUND_DUKE_TANK_MODE = create(23, "Hero Edmund Duke Tank Mode");
    /**
     * Hero Edmund Duke Tank Mode Turret (units.dat entry {@code 24}).
     */
    public static final @NotNull UnitType HERO_EDMUND_DUKE_TANK_MODE_TURRET = create(24, "Hero Edmund Duke Tank Mode Turret");
    /**
     * Hero Edmund Duke Siege Mode (units.dat entry {@code 25}).
     */
    public static final @NotNull UnitType HERO_EDMUND_DUKE_SIEGE_MODE = create(25, "Hero Edmund Duke Siege Mode");
    /**
     * Hero Edmund Duke Siege Mode Turret (units.dat entry {@code 26}).
     */
    public static final @NotNull UnitType HERO_EDMUND_DUKE_SIEGE_MODE_TURRET = create(26, "Hero Edmund Duke Siege Mode Turret");
    /**
     * Hero Arcturus Mengsk (units.dat entry {@code 27}).
     */
    public static final @NotNull UnitType HERO_ARCTURUS_MENGSK = create(27, "Hero Arcturus Mengsk");
    /**
     * Hero Hyperion (units.dat entry {@code 28}).
     */
    public static final @NotNull UnitType HERO_HYPERION = create(28, "Hero Hyperion");
    /**
     * Hero Norad II (units.dat entry {@code 29}).
     */
    public static final @NotNull UnitType HERO_NORAD_II = create(29, "Hero Norad II");
    /**
     * Terran Siege Tank Siege Mode (units.dat entry {@code 30}).
     */
    public static final @NotNull UnitType TERRAN_SIEGE_TANK_SIEGE_MODE = create(30, "Terran Siege Tank Siege Mode");
    /**
     * Terran Siege Tank Siege Mode Turret (units.dat entry {@code 31}).
     */
    public static final @NotNull UnitType TERRAN_SIEGE_TANK_SIEGE_MODE_TURRET = create(31, "Terran Siege Tank Siege Mode Turret");
    /**
     * Terran Firebat (units.dat entry {@code 32}).
     */
    public static final @NotNull UnitType TERRAN_FIREBAT = create(32, "Terran Firebat");
    /**
     * Spell Scanner Sweep (units.dat entry {@code 33}).
     */
    public static final @NotNull UnitType SPELL_SCANNER_SWEEP = create(33, "Spell Scanner Sweep");
    /**
     * Terran Medic (units.dat entry {@code 34}).
     */
    public static final @NotNull UnitType TERRAN_MEDIC = create(34, "Terran Medic");
    /**
     * Zerg Larva (units.dat entry {@code 35}).
     */
    public static final @NotNull UnitType ZERG_LARVA = create(35, "Zerg Larva");
    /**
     * Zerg Egg (units.dat entry {@code 36}).
     */
    public static final @NotNull UnitType ZERG_EGG = create(36, "Zerg Egg");
    /**
     * Zerg Zergling (units.dat entry {@code 37}).
     */
    public static final @NotNull UnitType ZERG_ZERGLING = create(37, "Zerg Zergling");
    /**
     * Zerg Hydralisk (units.dat entry {@code 38}).
     */
    public static final @NotNull UnitType ZERG_HYDRALISK = create(38, "Zerg Hydralisk");
    /**
     * Zerg Ultralisk (units.dat entry {@code 39}).
     */
    public static final @NotNull UnitType ZERG_ULTRALISK = create(39, "Zerg Ultralisk");
    /**
     * Zerg Broodling (units.dat entry {@code 40}).
     */
    public static final @NotNull UnitType ZERG_BROODLING = create(40, "Zerg Broodling");
    /**
     * Zerg Drone (units.dat entry {@code 41}).
     */
    public static final @NotNull UnitType ZERG_DRONE = create(41, "Zerg Drone");
    /**
     * Zerg Overlord (units.dat entry {@code 42}).
     */
    public static final @NotNull UnitType ZERG_OVERLORD = create(42, "Zerg Overlord");
    /**
     * Zerg Mutalisk (units.dat entry {@code 43}).
     */
    public static final @NotNull UnitType ZERG_MUTALISK = create(43, "Zerg Mutalisk");
    /**
     * Zerg Guardian (units.dat entry {@code 44}).
     */
    public static final @NotNull UnitType ZERG_GUARDIAN = create(44, "Zerg Guardian");
    /**
     * Zerg Queen (units.dat entry {@code 45}).
     */
    public static final @NotNull UnitType ZERG_QUEEN = create(45, "Zerg Queen");
    /**
     * Zerg Defiler (units.dat entry {@code 46}).
     */
    public static final @NotNull UnitType ZERG_DEFILER = create(46, "Zerg Defiler");
    /**
     * Zerg Scourge (units.dat entry {@code 47}).
     */
    public static final @NotNull UnitType ZERG_SCOURGE = create(47, "Zerg Scourge");
    /**
     * Hero Torrasque (units.dat entry {@code 48}).
     */
    public static final @NotNull UnitType HERO_TORRASQUE = create(48, "Hero Torrasque");
    /**
     * Hero Matriarch (units.dat entry {@code 49}).
     */
    public static final @NotNull UnitType HERO_MATRIARCH = create(49, "Hero Matriarch");
    /**
     * Zerg Infested Terran (units.dat entry {@code 50}).
     */
    public static final @NotNull UnitType ZERG_INFESTED_TERRAN = create(50, "Zerg Infested Terran");
    /**
     * Hero Infested Kerrigan (units.dat entry {@code 51}).
     */
    public static final @NotNull UnitType HERO_INFESTED_KERRIGAN = create(51, "Hero Infested Kerrigan");
    /**
     * Hero Unclean One (units.dat entry {@code 52}).
     */
    public static final @NotNull UnitType HERO_UNCLEAN_ONE = create(52, "Hero Unclean One");
    /**
     * Hero Hunter Killer (units.dat entry {@code 53}).
     */
    public static final @NotNull UnitType HERO_HUNTER_KILLER = create(53, "Hero Hunter Killer");
    /**
     * Hero Devouring One (units.dat entry {@code 54}).
     */
    public static final @NotNull UnitType HERO_DEVOURING_ONE = create(54, "Hero Devouring One");
    /**
     * Hero Kukulza Mutalisk (units.dat entry {@code 55}).
     */
    public static final @NotNull UnitType HERO_KUKULZA_MUTALISK = create(55, "Hero Kukulza Mutalisk");
    /**
     * Hero Kukulza Guardian (units.dat entry {@code 56}).
     */
    public static final @NotNull UnitType HERO_KUKULZA_GUARDIAN = create(56, "Hero Kukulza Guardian");
    /**
     * Hero Yggdrasill (units.dat entry {@code 57}).
     */
    public static final @NotNull UnitType HERO_YGGDRASILL = create(57, "Hero Yggdrasill");
    /**
     * Terran Valkyrie (units.dat entry {@code 58}).
     */
    public static final @NotNull UnitType TERRAN_VALKYRIE = create(58, "Terran Valkyrie");
    /**
     * Zerg Cocoon (units.dat entry {@code 59}).
     */
    public static final @NotNull UnitType ZERG_COCOON = create(59, "Zerg Cocoon");
    /**
     * Protoss Corsair (units.dat entry {@code 60}).
     */
    public static final @NotNull UnitType PROTOSS_CORSAIR = create(60, "Protoss Corsair");
    /**
     * Protoss Dark Templar (units.dat entry {@code 61}).
     */
    public static final @NotNull UnitType PROTOSS_DARK_TEMPLAR = create(61, "Protoss Dark Templar");
    /**
     * Zerg Devourer (units.dat entry {@code 62}).
     */
    public static final @NotNull UnitType ZERG_DEVOURER = create(62, "Zerg Devourer");
    /**
     * Protoss Dark Archon (units.dat entry {@code 63}).
     */
    public static final @NotNull UnitType PROTOSS_DARK_ARCHON = create(63, "Protoss Dark Archon");
    /**
     * Protoss Probe (units.dat entry {@code 64}).
     */
    public static final @NotNull UnitType PROTOSS_PROBE = create(64, "Protoss Probe");
    /**
     * Protoss Zealot (units.dat entry {@code 65}).
     */
    public static final @NotNull UnitType PROTOSS_ZEALOT = create(65, "Protoss Zealot");
    /**
     * Protoss Dragoon (units.dat entry {@code 66}).
     */
    public static final @NotNull UnitType PROTOSS_DRAGOON = create(66, "Protoss Dragoon");
    /**
     * Protoss High Templar (units.dat entry {@code 67}).
     */
    public static final @NotNull UnitType PROTOSS_HIGH_TEMPLAR = create(67, "Protoss High Templar");
    /**
     * Protoss Archon (units.dat entry {@code 68}).
     */
    public static final @NotNull UnitType PROTOSS_ARCHON = create(68, "Protoss Archon");
    /**
     * Protoss Shuttle (units.dat entry {@code 69}).
     */
    public static final @NotNull UnitType PROTOSS_SHUTTLE = create(69, "Protoss Shuttle");
    /**
     * Protoss Scout (units.dat entry {@code 70}).
     */
    public static final @NotNull UnitType PROTOSS_SCOUT = create(70, "Protoss Scout");
    /**
     * Protoss Arbiter (units.dat entry {@code 71}).
     */
    public static final @NotNull UnitType PROTOSS_ARBITER = create(71, "Protoss Arbiter");
    /**
     * Protoss Carrier (units.dat entry {@code 72}).
     */
    public static final @NotNull UnitType PROTOSS_CARRIER = create(72, "Protoss Carrier");
    /**
     * Protoss Interceptor (units.dat entry {@code 73}).
     */
    public static final @NotNull UnitType PROTOSS_INTERCEPTOR = create(73, "Protoss Interceptor");
    /**
     * Hero Dark Templar (units.dat entry {@code 74}).
     */
    public static final @NotNull UnitType HERO_DARK_TEMPLAR = create(74, "Hero Dark Templar");
    /**
     * Hero Zeratul (units.dat entry {@code 75}).
     */
    public static final @NotNull UnitType HERO_ZERATUL = create(75, "Hero Zeratul");
    /**
     * Hero Tassadar Zeratul Archon (units.dat entry {@code 76}).
     */
    public static final @NotNull UnitType HERO_TASSADAR_ZERATUL_ARCHON = create(76, "Hero Tassadar Zeratul Archon");
    /**
     * Hero Fenix Zealot (units.dat entry {@code 77}).
     */
    public static final @NotNull UnitType HERO_FENIX_ZEALOT = create(77, "Hero Fenix Zealot");
    /**
     * Hero Fenix Dragoon (units.dat entry {@code 78}).
     */
    public static final @NotNull UnitType HERO_FENIX_DRAGOON = create(78, "Hero Fenix Dragoon");
    /**
     * Hero Tassadar (units.dat entry {@code 79}).
     */
    public static final @NotNull UnitType HERO_TASSADAR = create(79, "Hero Tassadar");
    /**
     * Hero Mojo (units.dat entry {@code 80}).
     */
    public static final @NotNull UnitType HERO_MOJO = create(80, "Hero Mojo");
    /**
     * Hero Warbringer (units.dat entry {@code 81}).
     */
    public static final @NotNull UnitType HERO_WARBRINGER = create(81, "Hero Warbringer");
    /**
     * Hero Gantrithor (units.dat entry {@code 82}).
     */
    public static final @NotNull UnitType HERO_GANTRITHOR = create(82, "Hero Gantrithor");
    /**
     * Protoss Reaver (units.dat entry {@code 83}).
     */
    public static final @NotNull UnitType PROTOSS_REAVER = create(83, "Protoss Reaver");
    /**
     * Protoss Observer (units.dat entry {@code 84}).
     */
    public static final @NotNull UnitType PROTOSS_OBSERVER = create(84, "Protoss Observer");
    /**
     * Protoss Scarab (units.dat entry {@code 85}).
     */
    public static final @NotNull UnitType PROTOSS_SCARAB = create(85, "Protoss Scarab");
    /**
     * Hero Danimoth (units.dat entry {@code 86}).
     */
    public static final @NotNull UnitType HERO_DANIMOTH = create(86, "Hero Danimoth");
    /**
     * Hero Aldaris (units.dat entry {@code 87}).
     */
    public static final @NotNull UnitType HERO_ALDARIS = create(87, "Hero Aldaris");
    /**
     * Hero Artanis (units.dat entry {@code 88}).
     */
    public static final @NotNull UnitType HERO_ARTANIS = create(88, "Hero Artanis");
    /**
     * Critter Rhynadon (units.dat entry {@code 89}).
     */
    public static final @NotNull UnitType CRITTER_RHYNADON = create(89, "Critter Rhynadon");
    /**
     * Critter Bengalaas (units.dat entry {@code 90}).
     */
    public static final @NotNull UnitType CRITTER_BENGALAAS = create(90, "Critter Bengalaas");
    /**
     * Special Cargo Ship (units.dat entry {@code 91}).
     */
    public static final @NotNull UnitType SPECIAL_CARGO_SHIP = create(91, "Special Cargo Ship");
    /**
     * Special Mercenary Gunship (units.dat entry {@code 92}).
     */
    public static final @NotNull UnitType SPECIAL_MERCENARY_GUNSHIP = create(92, "Special Mercenary Gunship");
    /**
     * Critter Scantid (units.dat entry {@code 93}).
     */
    public static final @NotNull UnitType CRITTER_SCANTID = create(93, "Critter Scantid");
    /**
     * Critter Kakaru (units.dat entry {@code 94}).
     */
    public static final @NotNull UnitType CRITTER_KAKARU = create(94, "Critter Kakaru");
    /**
     * Critter Ragnasaur (units.dat entry {@code 95}).
     */
    public static final @NotNull UnitType CRITTER_RAGNASAUR = create(95, "Critter Ragnasaur");
    /**
     * Critter Ursadon (units.dat entry {@code 96}).
     */
    public static final @NotNull UnitType CRITTER_URSADON = create(96, "Critter Ursadon");
    /**
     * Zerg Lurker Egg (units.dat entry {@code 97}).
     */
    public static final @NotNull UnitType ZERG_LURKER_EGG = create(97, "Zerg Lurker Egg");
    /**
     * Hero Raszagal (units.dat entry {@code 98}).
     */
    public static final @NotNull UnitType HERO_RASZAGAL = create(98, "Hero Raszagal");
    /**
     * Hero Samir Duran (units.dat entry {@code 99}).
     */
    public static final @NotNull UnitType HERO_SAMIR_DURAN = create(99, "Hero Samir Duran");
    /**
     * Hero Alexei Stukov (units.dat entry {@code 100}).
     */
    public static final @NotNull UnitType HERO_ALEXEI_STUKOV = create(100, "Hero Alexei Stukov");
    /**
     * Special Map Revealer (units.dat entry {@code 101}).
     */
    public static final @NotNull UnitType SPECIAL_MAP_REVEALER = create(101, "Special Map Revealer");
    /**
     * Hero Gerard DuGalle (units.dat entry {@code 102}).
     */
    public static final @NotNull UnitType HERO_GERARD_DUGALLE = create(102, "Hero Gerard DuGalle");
    /**
     * Zerg Lurker (units.dat entry {@code 103}).
     */
    public static final @NotNull UnitType ZERG_LURKER = create(103, "Zerg Lurker");
    /**
     * Hero Infested Duran (units.dat entry {@code 104}).
     */
    public static final @NotNull UnitType HERO_INFESTED_DURAN = create(104, "Hero Infested Duran");
    /**
     * Spell Disruption Web (units.dat entry {@code 105}).
     */
    public static final @NotNull UnitType SPELL_DISRUPTION_WEB = create(105, "Spell Disruption Web");
    /**
     * Terran Command Center (units.dat entry {@code 106}).
     */
    public static final @NotNull UnitType TERRAN_COMMAND_CENTER = create(106, "Terran Command Center");
    /**
     * Terran Comsat Station (units.dat entry {@code 107}).
     */
    public static final @NotNull UnitType TERRAN_COMSAT_STATION = create(107, "Terran Comsat Station");
    /**
     * Terran Nuclear Silo (units.dat entry {@code 108}).
     */
    public static final @NotNull UnitType TERRAN_NUCLEAR_SILO = create(108, "Terran Nuclear Silo");
    /**
     * Terran Supply Depot (units.dat entry {@code 109}).
     */
    public static final @NotNull UnitType TERRAN_SUPPLY_DEPOT = create(109, "Terran Supply Depot");
    /**
     * Terran Refinery (units.dat entry {@code 110}).
     */
    public static final @NotNull UnitType TERRAN_REFINERY = create(110, "Terran Refinery");
    /**
     * Terran Barracks (units.dat entry {@code 111}).
     */
    public static final @NotNull UnitType TERRAN_BARRACKS = create(111, "Terran Barracks");
    /**
     * Terran Academy (units.dat entry {@code 112}).
     */
    public static final @NotNull UnitType TERRAN_ACADEMY = create(112, "Terran Academy");
    /**
     * Terran Factory (units.dat entry {@code 113}).
     */
    public static final @NotNull UnitType TERRAN_FACTORY = create(113, "Terran Factory");
    /**
     * Terran Starport (units.dat entry {@code 114}).
     */
    public static final @NotNull UnitType TERRAN_STARPORT = create(114, "Terran Starport");
    /**
     * Terran Control Tower (units.dat entry {@code 115}).
     */
    public static final @NotNull UnitType TERRAN_CONTROL_TOWER = create(115, "Terran Control Tower");
    /**
     * Terran Science Facility (units.dat entry {@code 116}).
     */
    public static final @NotNull UnitType TERRAN_SCIENCE_FACILITY = create(116, "Terran Science Facility");
    /**
     * Terran Covert Ops (units.dat entry {@code 117}).
     */
    public static final @NotNull UnitType TERRAN_COVERT_OPS = create(117, "Terran Covert Ops");
    /**
     * Terran Physics Lab (units.dat entry {@code 118}).
     */
    public static final @NotNull UnitType TERRAN_PHYSICS_LAB = create(118, "Terran Physics Lab");
    /**
     * Unused Terran1 (units.dat entry {@code 119}).
     */
    public static final @NotNull UnitType UNUSED_TERRAN1 = create(119, "Unused Terran1");
    /**
     * Terran Machine Shop (units.dat entry {@code 120}).
     */
    public static final @NotNull UnitType TERRAN_MACHINE_SHOP = create(120, "Terran Machine Shop");
    /**
     * Unused Terran2 (units.dat entry {@code 121}).
     */
    public static final @NotNull UnitType UNUSED_TERRAN2 = create(121, "Unused Terran2");
    /**
     * Terran Engineering Bay (units.dat entry {@code 122}).
     */
    public static final @NotNull UnitType TERRAN_ENGINEERING_BAY = create(122, "Terran Engineering Bay");
    /**
     * Terran Armory (units.dat entry {@code 123}).
     */
    public static final @NotNull UnitType TERRAN_ARMORY = create(123, "Terran Armory");
    /**
     * Terran Missile Turret (units.dat entry {@code 124}).
     */
    public static final @NotNull UnitType TERRAN_MISSILE_TURRET = create(124, "Terran Missile Turret");
    /**
     * Terran Bunker (units.dat entry {@code 125}).
     */
    public static final @NotNull UnitType TERRAN_BUNKER = create(125, "Terran Bunker");
    /**
     * Special Crashed Norad II (units.dat entry {@code 126}).
     */
    public static final @NotNull UnitType SPECIAL_CRASHED_NORAD_II = create(126, "Special Crashed Norad II");
    /**
     * Special Ion Cannon (units.dat entry {@code 127}).
     */
    public static final @NotNull UnitType SPECIAL_ION_CANNON = create(127, "Special Ion Cannon");
    /**
     * Powerup Uraj Crystal (units.dat entry {@code 128}).
     */
    public static final @NotNull UnitType POWERUP_URAJ_CRYSTAL = create(128, "Powerup Uraj Crystal");
    /**
     * Powerup Khalis Crystal (units.dat entry {@code 129}).
     */
    public static final @NotNull UnitType POWERUP_KHALIS_CRYSTAL = create(129, "Powerup Khalis Crystal");
    /**
     * Zerg Infested Command Center (units.dat entry {@code 130}).
     */
    public static final @NotNull UnitType ZERG_INFESTED_COMMAND_CENTER = create(130, "Zerg Infested Command Center");
    /**
     * Zerg Hatchery (units.dat entry {@code 131}).
     */
    public static final @NotNull UnitType ZERG_HATCHERY = create(131, "Zerg Hatchery");
    /**
     * Zerg Lair (units.dat entry {@code 132}).
     */
    public static final @NotNull UnitType ZERG_LAIR = create(132, "Zerg Lair");
    /**
     * Zerg Hive (units.dat entry {@code 133}).
     */
    public static final @NotNull UnitType ZERG_HIVE = create(133, "Zerg Hive");
    /**
     * Zerg Nydus Canal (units.dat entry {@code 134}).
     */
    public static final @NotNull UnitType ZERG_NYDUS_CANAL = create(134, "Zerg Nydus Canal");
    /**
     * Zerg Hydralisk Den (units.dat entry {@code 135}).
     */
    public static final @NotNull UnitType ZERG_HYDRALISK_DEN = create(135, "Zerg Hydralisk Den");
    /**
     * Zerg Defiler Mound (units.dat entry {@code 136}).
     */
    public static final @NotNull UnitType ZERG_DEFILER_MOUND = create(136, "Zerg Defiler Mound");
    /**
     * Zerg Greater Spire (units.dat entry {@code 137}).
     */
    public static final @NotNull UnitType ZERG_GREATER_SPIRE = create(137, "Zerg Greater Spire");
    /**
     * Zerg Queens Nest (units.dat entry {@code 138}).
     */
    public static final @NotNull UnitType ZERG_QUEENS_NEST = create(138, "Zerg Queens Nest");
    /**
     * Zerg Evolution Chamber (units.dat entry {@code 139}).
     */
    public static final @NotNull UnitType ZERG_EVOLUTION_CHAMBER = create(139, "Zerg Evolution Chamber");
    /**
     * Zerg Ultralisk Cavern (units.dat entry {@code 140}).
     */
    public static final @NotNull UnitType ZERG_ULTRALISK_CAVERN = create(140, "Zerg Ultralisk Cavern");
    /**
     * Zerg Spire (units.dat entry {@code 141}).
     */
    public static final @NotNull UnitType ZERG_SPIRE = create(141, "Zerg Spire");
    /**
     * Zerg Spawning Pool (units.dat entry {@code 142}).
     */
    public static final @NotNull UnitType ZERG_SPAWNING_POOL = create(142, "Zerg Spawning Pool");
    /**
     * Zerg Creep Colony (units.dat entry {@code 143}).
     */
    public static final @NotNull UnitType ZERG_CREEP_COLONY = create(143, "Zerg Creep Colony");
    /**
     * Zerg Spore Colony (units.dat entry {@code 144}).
     */
    public static final @NotNull UnitType ZERG_SPORE_COLONY = create(144, "Zerg Spore Colony");
    /**
     * Unused Zerg1 (units.dat entry {@code 145}).
     */
    public static final @NotNull UnitType UNUSED_ZERG1 = create(145, "Unused Zerg1");
    /**
     * Zerg Sunken Colony (units.dat entry {@code 146}).
     */
    public static final @NotNull UnitType ZERG_SUNKEN_COLONY = create(146, "Zerg Sunken Colony");
    /**
     * Special Overmind With Shell (units.dat entry {@code 147}).
     */
    public static final @NotNull UnitType SPECIAL_OVERMIND_WITH_SHELL = create(147, "Special Overmind With Shell");
    /**
     * Special Overmind (units.dat entry {@code 148}).
     */
    public static final @NotNull UnitType SPECIAL_OVERMIND = create(148, "Special Overmind");
    /**
     * Zerg Extractor (units.dat entry {@code 149}).
     */
    public static final @NotNull UnitType ZERG_EXTRACTOR = create(149, "Zerg Extractor");
    /**
     * Special Mature Chrysalis (units.dat entry {@code 150}).
     */
    public static final @NotNull UnitType SPECIAL_MATURE_CHRYSALIS = create(150, "Special Mature Chrysalis");
    /**
     * Special Cerebrate (units.dat entry {@code 151}).
     */
    public static final @NotNull UnitType SPECIAL_CEREBRATE = create(151, "Special Cerebrate");
    /**
     * Special Cerebrate Daggoth (units.dat entry {@code 152}).
     */
    public static final @NotNull UnitType SPECIAL_CEREBRATE_DAGGOTH = create(152, "Special Cerebrate Daggoth");
    /**
     * Unused Zerg2 (units.dat entry {@code 153}).
     */
    public static final @NotNull UnitType UNUSED_ZERG2 = create(153, "Unused Zerg2");
    /**
     * Protoss Nexus (units.dat entry {@code 154}).
     */
    public static final @NotNull UnitType PROTOSS_NEXUS = create(154, "Protoss Nexus");
    /**
     * Protoss Robotics Facility (units.dat entry {@code 155}).
     */
    public static final @NotNull UnitType PROTOSS_ROBOTICS_FACILITY = create(155, "Protoss Robotics Facility");
    /**
     * Protoss Pylon (units.dat entry {@code 156}).
     */
    public static final @NotNull UnitType PROTOSS_PYLON = create(156, "Protoss Pylon");
    /**
     * Protoss Assimilator (units.dat entry {@code 157}).
     */
    public static final @NotNull UnitType PROTOSS_ASSIMILATOR = create(157, "Protoss Assimilator");
    /**
     * Unused Protoss1 (units.dat entry {@code 158}).
     */
    public static final @NotNull UnitType UNUSED_PROTOSS1 = create(158, "Unused Protoss1");
    /**
     * Protoss Observatory (units.dat entry {@code 159}).
     */
    public static final @NotNull UnitType PROTOSS_OBSERVATORY = create(159, "Protoss Observatory");
    /**
     * Protoss Gateway (units.dat entry {@code 160}).
     */
    public static final @NotNull UnitType PROTOSS_GATEWAY = create(160, "Protoss Gateway");
    /**
     * Unused Protoss2 (units.dat entry {@code 161}).
     */
    public static final @NotNull UnitType UNUSED_PROTOSS2 = create(161, "Unused Protoss2");
    /**
     * Protoss Photon Cannon (units.dat entry {@code 162}).
     */
    public static final @NotNull UnitType PROTOSS_PHOTON_CANNON = create(162, "Protoss Photon Cannon");
    /**
     * Protoss Citadel of Adun (units.dat entry {@code 163}).
     */
    public static final @NotNull UnitType PROTOSS_CITADEL_OF_ADUN = create(163, "Protoss Citadel of Adun");
    /**
     * Protoss Cybernetics Core (units.dat entry {@code 164}).
     */
    public static final @NotNull UnitType PROTOSS_CYBERNETICS_CORE = create(164, "Protoss Cybernetics Core");
    /**
     * Protoss Templar Archives (units.dat entry {@code 165}).
     */
    public static final @NotNull UnitType PROTOSS_TEMPLAR_ARCHIVES = create(165, "Protoss Templar Archives");
    /**
     * Protoss Forge (units.dat entry {@code 166}).
     */
    public static final @NotNull UnitType PROTOSS_FORGE = create(166, "Protoss Forge");
    /**
     * Protoss Stargate (units.dat entry {@code 167}).
     */
    public static final @NotNull UnitType PROTOSS_STARGATE = create(167, "Protoss Stargate");
    /**
     * Special Stasis Cell Prison (units.dat entry {@code 168}).
     */
    public static final @NotNull UnitType SPECIAL_STASIS_CELL_PRISON = create(168, "Special Stasis Cell Prison");
    /**
     * Protoss Fleet Beacon (units.dat entry {@code 169}).
     */
    public static final @NotNull UnitType PROTOSS_FLEET_BEACON = create(169, "Protoss Fleet Beacon");
    /**
     * Protoss Arbiter Tribunal (units.dat entry {@code 170}).
     */
    public static final @NotNull UnitType PROTOSS_ARBITER_TRIBUNAL = create(170, "Protoss Arbiter Tribunal");
    /**
     * Protoss Robotics Support Bay (units.dat entry {@code 171}).
     */
    public static final @NotNull UnitType PROTOSS_ROBOTICS_SUPPORT_BAY = create(171, "Protoss Robotics Support Bay");
    /**
     * Protoss Shield Battery (units.dat entry {@code 172}).
     */
    public static final @NotNull UnitType PROTOSS_SHIELD_BATTERY = create(172, "Protoss Shield Battery");
    /**
     * Special Khaydarin Crystal Form (units.dat entry {@code 173}).
     */
    public static final @NotNull UnitType SPECIAL_KHAYDARIN_CRYSTAL_FORM = create(173, "Special Khaydarin Crystal Form");
    /**
     * Special Protoss Temple (units.dat entry {@code 174}).
     */
    public static final @NotNull UnitType SPECIAL_PROTOSS_TEMPLE = create(174, "Special Protoss Temple");
    /**
     * Special XelNaga Temple (units.dat entry {@code 175}).
     */
    public static final @NotNull UnitType SPECIAL_XELNAGA_TEMPLE = create(175, "Special XelNaga Temple");
    /**
     * Resource Mineral Field (units.dat entry {@code 176}).
     */
    public static final @NotNull UnitType RESOURCE_MINERAL_FIELD = create(176, "Resource Mineral Field");
    /**
     * Resource Mineral Field Type 2 (units.dat entry {@code 177}).
     */
    public static final @NotNull UnitType RESOURCE_MINERAL_FIELD_TYPE_2 = create(177, "Resource Mineral Field Type 2");
    /**
     * Resource Mineral Field Type 3 (units.dat entry {@code 178}).
     */
    public static final @NotNull UnitType RESOURCE_MINERAL_FIELD_TYPE_3 = create(178, "Resource Mineral Field Type 3");
    /**
     * Unused Cave (units.dat entry {@code 179}).
     */
    public static final @NotNull UnitType UNUSED_CAVE = create(179, "Unused Cave");
    /**
     * Unused Cave In (units.dat entry {@code 180}).
     */
    public static final @NotNull UnitType UNUSED_CAVE_IN = create(180, "Unused Cave In");
    /**
     * Unused Cantina (units.dat entry {@code 181}).
     */
    public static final @NotNull UnitType UNUSED_CANTINA = create(181, "Unused Cantina");
    /**
     * Unused Mining Platform (units.dat entry {@code 182}).
     */
    public static final @NotNull UnitType UNUSED_MINING_PLATFORM = create(182, "Unused Mining Platform");
    /**
     * Unused Independant Command Center (units.dat entry {@code 183}).
     */
    public static final @NotNull UnitType UNUSED_INDEPENDANT_COMMAND_CENTER = create(183, "Unused Independant Command Center");
    /**
     * Special Independant Starport (units.dat entry {@code 184}).
     */
    public static final @NotNull UnitType SPECIAL_INDEPENDANT_STARPORT = create(184, "Special Independant Starport");
    /**
     * Unused Independant Jump Gate (units.dat entry {@code 185}).
     */
    public static final @NotNull UnitType UNUSED_INDEPENDANT_JUMP_GATE = create(185, "Unused Independant Jump Gate");
    /**
     * Unused Ruins (units.dat entry {@code 186}).
     */
    public static final @NotNull UnitType UNUSED_RUINS = create(186, "Unused Ruins");
    /**
     * Unused Khaydarin Crystal Formation (units.dat entry {@code 187}).
     */
    public static final @NotNull UnitType UNUSED_KHAYDARIN_CRYSTAL_FORMATION = create(187, "Unused Khaydarin Crystal Formation");
    /**
     * Resource Vespene Geyser (units.dat entry {@code 188}).
     */
    public static final @NotNull UnitType RESOURCE_VESPENE_GEYSER = create(188, "Resource Vespene Geyser");
    /**
     * Special Warp Gate (units.dat entry {@code 189}).
     */
    public static final @NotNull UnitType SPECIAL_WARP_GATE = create(189, "Special Warp Gate");
    /**
     * Special Psi Disrupter (units.dat entry {@code 190}).
     */
    public static final @NotNull UnitType SPECIAL_PSI_DISRUPTER = create(190, "Special Psi Disrupter");
    /**
     * Unused Zerg Marker (units.dat entry {@code 191}).
     */
    public static final @NotNull UnitType UNUSED_ZERG_MARKER = create(191, "Unused Zerg Marker");
    /**
     * Unused Terran Marker (units.dat entry {@code 192}).
     */
    public static final @NotNull UnitType UNUSED_TERRAN_MARKER = create(192, "Unused Terran Marker");
    /**
     * Unused Protoss Marker (units.dat entry {@code 193}).
     */
    public static final @NotNull UnitType UNUSED_PROTOSS_MARKER = create(193, "Unused Protoss Marker");
    /**
     * Special Zerg Beacon (units.dat entry {@code 194}).
     */
    public static final @NotNull UnitType SPECIAL_ZERG_BEACON = create(194, "Special Zerg Beacon");
    /**
     * Special Terran Beacon (units.dat entry {@code 195}).
     */
    public static final @NotNull UnitType SPECIAL_TERRAN_BEACON = create(195, "Special Terran Beacon");
    /**
     * Special Protoss Beacon (units.dat entry {@code 196}).
     */
    public static final @NotNull UnitType SPECIAL_PROTOSS_BEACON = create(196, "Special Protoss Beacon");
    /**
     * Special Zerg Flag Beacon (units.dat entry {@code 197}).
     */
    public static final @NotNull UnitType SPECIAL_ZERG_FLAG_BEACON = create(197, "Special Zerg Flag Beacon");
    /**
     * Special Terran Flag Beacon (units.dat entry {@code 198}).
     */
    public static final @NotNull UnitType SPECIAL_TERRAN_FLAG_BEACON = create(198, "Special Terran Flag Beacon");
    /**
     * Special Protoss Flag Beacon (units.dat entry {@code 199}).
     */
    public static final @NotNull UnitType SPECIAL_PROTOSS_FLAG_BEACON = create(199, "Special Protoss Flag Beacon");
    /**
     * Special Power Generator (units.dat entry {@code 200}).
     */
    public static final @NotNull UnitType SPECIAL_POWER_GENERATOR = create(200, "Special Power Generator");
    /**
     * Special Overmind Cocoon (units.dat entry {@code 201}).
     */
    public static final @NotNull UnitType SPECIAL_OVERMIND_COCOON = create(201, "Special Overmind Cocoon");
    /**
     * Spell Dark Swarm (units.dat entry {@code 202}).
     */
    public static final @NotNull UnitType SPELL_DARK_SWARM = create(202, "Spell Dark Swarm");
    /**
     * Special Floor Missile Trap (units.dat entry {@code 203}).
     */
    public static final @NotNull UnitType SPECIAL_FLOOR_MISSILE_TRAP = create(203, "Special Floor Missile Trap");
    /**
     * Special Floor Hatch (units.dat entry {@code 204}).
     */
    public static final @NotNull UnitType SPECIAL_FLOOR_HATCH = create(204, "Special Floor Hatch");
    /**
     * Special Upper Level Door (units.dat entry {@code 205}).
     */
    public static final @NotNull UnitType SPECIAL_UPPER_LEVEL_DOOR = create(205, "Special Upper Level Door");
    /**
     * Special Right Upper Level Door (units.dat entry {@code 206}).
     */
    public static final @NotNull UnitType SPECIAL_RIGHT_UPPER_LEVEL_DOOR = create(206, "Special Right Upper Level Door");
    /**
     * Special Pit Door (units.dat entry {@code 207}).
     */
    public static final @NotNull UnitType SPECIAL_PIT_DOOR = create(207, "Special Pit Door");
    /**
     * Special Right Pit Door (units.dat entry {@code 208}).
     */
    public static final @NotNull UnitType SPECIAL_RIGHT_PIT_DOOR = create(208, "Special Right Pit Door");
    /**
     * Special Floor Gun Trap (units.dat entry {@code 209}).
     */
    public static final @NotNull UnitType SPECIAL_FLOOR_GUN_TRAP = create(209, "Special Floor Gun Trap");
    /**
     * Special Wall Missile Trap (units.dat entry {@code 210}).
     */
    public static final @NotNull UnitType SPECIAL_WALL_MISSILE_TRAP = create(210, "Special Wall Missile Trap");
    /**
     * Special Wall Flame Trap (units.dat entry {@code 211}).
     */
    public static final @NotNull UnitType SPECIAL_WALL_FLAME_TRAP = create(211, "Special Wall Flame Trap");
    /**
     * Special Right Wall Missile Trap (units.dat entry {@code 212}).
     */
    public static final @NotNull UnitType SPECIAL_RIGHT_WALL_MISSILE_TRAP = create(212, "Special Right Wall Missile Trap");
    /**
     * Special Right Wall Flame Trap (units.dat entry {@code 213}).
     */
    public static final @NotNull UnitType SPECIAL_RIGHT_WALL_FLAME_TRAP = create(213, "Special Right Wall Flame Trap");
    /**
     * Special Start Location (units.dat entry {@code 214}).
     */
    public static final @NotNull UnitType SPECIAL_START_LOCATION = create(214, "Special Start Location");
    /**
     * Powerup Flag (units.dat entry {@code 215}).
     */
    public static final @NotNull UnitType POWERUP_FLAG = create(215, "Powerup Flag");
    /**
     * Powerup Young Chrysalis (units.dat entry {@code 216}).
     */
    public static final @NotNull UnitType POWERUP_YOUNG_CHRYSALIS = create(216, "Powerup Young Chrysalis");
    /**
     * Powerup Psi Emitter (units.dat entry {@code 217}).
     */
    public static final @NotNull UnitType POWERUP_PSI_EMITTER = create(217, "Powerup Psi Emitter");
    /**
     * Powerup Data Disk (units.dat entry {@code 218}).
     */
    public static final @NotNull UnitType POWERUP_DATA_DISK = create(218, "Powerup Data Disk");
    /**
     * Powerup Khaydarin Crystal (units.dat entry {@code 219}).
     */
    public static final @NotNull UnitType POWERUP_KHAYDARIN_CRYSTAL = create(219, "Powerup Khaydarin Crystal");
    /**
     * Powerup Mineral Cluster Type 1 (units.dat entry {@code 220}).
     */
    public static final @NotNull UnitType POWERUP_MINERAL_CLUSTER_TYPE_1 = create(220, "Powerup Mineral Cluster Type 1");
    /**
     * Powerup Mineral Cluster Type 2 (units.dat entry {@code 221}).
     */
    public static final @NotNull UnitType POWERUP_MINERAL_CLUSTER_TYPE_2 = create(221, "Powerup Mineral Cluster Type 2");
    /**
     * Powerup Protoss Gas Orb Type 1 (units.dat entry {@code 222}).
     */
    public static final @NotNull UnitType POWERUP_PROTOSS_GAS_ORB_TYPE_1 = create(222, "Powerup Protoss Gas Orb Type 1");
    /**
     * Powerup Protoss Gas Orb Type 2 (units.dat entry {@code 223}).
     */
    public static final @NotNull UnitType POWERUP_PROTOSS_GAS_ORB_TYPE_2 = create(223, "Powerup Protoss Gas Orb Type 2");
    /**
     * Powerup Zerg Gas Sac Type 1 (units.dat entry {@code 224}).
     */
    public static final @NotNull UnitType POWERUP_ZERG_GAS_SAC_TYPE_1 = create(224, "Powerup Zerg Gas Sac Type 1");
    /**
     * Powerup Zerg Gas Sac Type 2 (units.dat entry {@code 225}).
     */
    public static final @NotNull UnitType POWERUP_ZERG_GAS_SAC_TYPE_2 = create(225, "Powerup Zerg Gas Sac Type 2");
    /**
     * Powerup Terran Gas Tank Type 1 (units.dat entry {@code 226}).
     */
    public static final @NotNull UnitType POWERUP_TERRAN_GAS_TANK_TYPE_1 = create(226, "Powerup Terran Gas Tank Type 1");
    /**
     * Powerup Terran Gas Tank Type 2 (units.dat entry {@code 227}).
     */
    public static final @NotNull UnitType POWERUP_TERRAN_GAS_TANK_TYPE_2 = create(227, "Powerup Terran Gas Tank Type 2");

    private static final @NotNull List<@NotNull UnitType> TYPE_LIST;
    /**
     * The units.dat entry of each identity, by identity.
     */
    private static final @NotNull Map<@NotNull UnitType, @NotNull Integer> NATIVE_IDS;

    static {
        TYPE_LIST  = List.of(TYPES);
        NATIVE_IDS = new IdentityHashMap<>();

        for (int nativeId = 0; nativeId < COUNT; nativeId++) {
            NATIVE_IDS.put(TYPES[nativeId], nativeId);
        }
    }

    /**
     * Returns the identity of a units.dat entry.
     *
     * @param nativeId the units.dat entry, from {@code 0} to {@code COUNT - 1}
     * @return the identity
     * @throws IllegalArgumentException if the entry is outside the table
     */
    public static @NotNull UnitType get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid units.dat entry: {0}", nativeId);
        }

        return TYPES[nativeId];
    }

    /**
     * Returns the units.dat entry of an identity, whether or not a content registered it.
     *
     * @param type the identity, or {@code null} for none
     * @return the units.dat entry, {@link #NATIVE_NONE} for {@code null}
     * @throws IllegalArgumentException if the identity is not an SC:R unit type
     */
    public static int getNativeId(@Nullable UnitType type) {
        if (type == null) {
            return NATIVE_NONE;
        }

        Integer nativeId = NATIVE_IDS.get(type);

        if (nativeId == null) {
            throw Exceptions.newIllegalArgumentException("Not an SC:R unit type: {0}", type);
        }

        return nativeId;
    }

    /**
     * @return every identity in units.dat order, as the mod registers them
     */
    public static @NotNull List<@NotNull UnitType> getTypes() {
        return TYPE_LIST;
    }

    private static @NotNull UnitType create(int nativeId, @NotNull String debugName) {
        UnitType type = new UnitType(debugName);

        TYPES[nativeId] = type;

        return type;
    }


    private ScrUnits() {
    }
}
