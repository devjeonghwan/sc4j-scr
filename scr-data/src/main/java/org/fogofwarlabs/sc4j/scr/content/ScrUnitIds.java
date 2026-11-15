package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;

/**
 * Holds the native unit type identifiers (units.dat entries) of the SC:R mod as {@code int} constants: the values the SC:R
 * simulation stores and compares, and the values EUD, CHK sections, replays and saved games hold. {@link ScrUnits} holds the
 * engine identities; {@link ScrNativeIds} maps between the two.
 */
public final class ScrUnitIds {
    /**
     * Terran Marine (units.dat entry {@code 0}).
     */
    public static final int TERRAN_MARINE = 0;
    /**
     * Terran Ghost (units.dat entry {@code 1}).
     */
    public static final int TERRAN_GHOST = 1;
    /**
     * Terran Vulture (units.dat entry {@code 2}).
     */
    public static final int TERRAN_VULTURE = 2;
    /**
     * Terran Goliath (units.dat entry {@code 3}).
     */
    public static final int TERRAN_GOLIATH = 3;
    /**
     * Terran Goliath Turret (units.dat entry {@code 4}).
     */
    public static final int TERRAN_GOLIATH_TURRET = 4;
    /**
     * Terran Siege Tank Tank Mode (units.dat entry {@code 5}).
     */
    public static final int TERRAN_SIEGE_TANK_TANK_MODE = 5;
    /**
     * Terran Siege Tank Tank Mode Turret (units.dat entry {@code 6}).
     */
    public static final int TERRAN_SIEGE_TANK_TANK_MODE_TURRET = 6;
    /**
     * Terran SCV (units.dat entry {@code 7}).
     */
    public static final int TERRAN_SCV = 7;
    /**
     * Terran Wraith (units.dat entry {@code 8}).
     */
    public static final int TERRAN_WRAITH = 8;
    /**
     * Terran Science Vessel (units.dat entry {@code 9}).
     */
    public static final int TERRAN_SCIENCE_VESSEL = 9;
    /**
     * Hero Gui Montag (units.dat entry {@code 10}).
     */
    public static final int HERO_GUI_MONTAG = 10;
    /**
     * Terran Dropship (units.dat entry {@code 11}).
     */
    public static final int TERRAN_DROPSHIP = 11;
    /**
     * Terran Battlecruiser (units.dat entry {@code 12}).
     */
    public static final int TERRAN_BATTLECRUISER = 12;
    /**
     * Terran Vulture Spider Mine (units.dat entry {@code 13}).
     */
    public static final int TERRAN_VULTURE_SPIDER_MINE = 13;
    /**
     * Terran Nuclear Missile (units.dat entry {@code 14}).
     */
    public static final int TERRAN_NUCLEAR_MISSILE = 14;
    /**
     * Terran Civilian (units.dat entry {@code 15}).
     */
    public static final int TERRAN_CIVILIAN = 15;
    /**
     * Hero Sarah Kerrigan (units.dat entry {@code 16}).
     */
    public static final int HERO_SARAH_KERRIGAN = 16;
    /**
     * Hero Alan Schezar (units.dat entry {@code 17}).
     */
    public static final int HERO_ALAN_SCHEZAR = 17;
    /**
     * Hero Alan Schezar Turret (units.dat entry {@code 18}).
     */
    public static final int HERO_ALAN_SCHEZAR_TURRET = 18;
    /**
     * Hero Jim Raynor Vulture (units.dat entry {@code 19}).
     */
    public static final int HERO_JIM_RAYNOR_VULTURE = 19;
    /**
     * Hero Jim Raynor Marine (units.dat entry {@code 20}).
     */
    public static final int HERO_JIM_RAYNOR_MARINE = 20;
    /**
     * Hero Tom Kazansky (units.dat entry {@code 21}).
     */
    public static final int HERO_TOM_KAZANSKY = 21;
    /**
     * Hero Magellan (units.dat entry {@code 22}).
     */
    public static final int HERO_MAGELLAN = 22;
    /**
     * Hero Edmund Duke Tank Mode (units.dat entry {@code 23}).
     */
    public static final int HERO_EDMUND_DUKE_TANK_MODE = 23;
    /**
     * Hero Edmund Duke Tank Mode Turret (units.dat entry {@code 24}).
     */
    public static final int HERO_EDMUND_DUKE_TANK_MODE_TURRET = 24;
    /**
     * Hero Edmund Duke Siege Mode (units.dat entry {@code 25}).
     */
    public static final int HERO_EDMUND_DUKE_SIEGE_MODE = 25;
    /**
     * Hero Edmund Duke Siege Mode Turret (units.dat entry {@code 26}).
     */
    public static final int HERO_EDMUND_DUKE_SIEGE_MODE_TURRET = 26;
    /**
     * Hero Arcturus Mengsk (units.dat entry {@code 27}).
     */
    public static final int HERO_ARCTURUS_MENGSK = 27;
    /**
     * Hero Hyperion (units.dat entry {@code 28}).
     */
    public static final int HERO_HYPERION = 28;
    /**
     * Hero Norad II (units.dat entry {@code 29}).
     */
    public static final int HERO_NORAD_II = 29;
    /**
     * Terran Siege Tank Siege Mode (units.dat entry {@code 30}).
     */
    public static final int TERRAN_SIEGE_TANK_SIEGE_MODE = 30;
    /**
     * Terran Siege Tank Siege Mode Turret (units.dat entry {@code 31}).
     */
    public static final int TERRAN_SIEGE_TANK_SIEGE_MODE_TURRET = 31;
    /**
     * Terran Firebat (units.dat entry {@code 32}).
     */
    public static final int TERRAN_FIREBAT = 32;
    /**
     * Spell Scanner Sweep (units.dat entry {@code 33}).
     */
    public static final int SPELL_SCANNER_SWEEP = 33;
    /**
     * Terran Medic (units.dat entry {@code 34}).
     */
    public static final int TERRAN_MEDIC = 34;
    /**
     * Zerg Larva (units.dat entry {@code 35}).
     */
    public static final int ZERG_LARVA = 35;
    /**
     * Zerg Egg (units.dat entry {@code 36}).
     */
    public static final int ZERG_EGG = 36;
    /**
     * Zerg Zergling (units.dat entry {@code 37}).
     */
    public static final int ZERG_ZERGLING = 37;
    /**
     * Zerg Hydralisk (units.dat entry {@code 38}).
     */
    public static final int ZERG_HYDRALISK = 38;
    /**
     * Zerg Ultralisk (units.dat entry {@code 39}).
     */
    public static final int ZERG_ULTRALISK = 39;
    /**
     * Zerg Broodling (units.dat entry {@code 40}).
     */
    public static final int ZERG_BROODLING = 40;
    /**
     * Zerg Drone (units.dat entry {@code 41}).
     */
    public static final int ZERG_DRONE = 41;
    /**
     * Zerg Overlord (units.dat entry {@code 42}).
     */
    public static final int ZERG_OVERLORD = 42;
    /**
     * Zerg Mutalisk (units.dat entry {@code 43}).
     */
    public static final int ZERG_MUTALISK = 43;
    /**
     * Zerg Guardian (units.dat entry {@code 44}).
     */
    public static final int ZERG_GUARDIAN = 44;
    /**
     * Zerg Queen (units.dat entry {@code 45}).
     */
    public static final int ZERG_QUEEN = 45;
    /**
     * Zerg Defiler (units.dat entry {@code 46}).
     */
    public static final int ZERG_DEFILER = 46;
    /**
     * Zerg Scourge (units.dat entry {@code 47}).
     */
    public static final int ZERG_SCOURGE = 47;
    /**
     * Hero Torrasque (units.dat entry {@code 48}).
     */
    public static final int HERO_TORRASQUE = 48;
    /**
     * Hero Matriarch (units.dat entry {@code 49}).
     */
    public static final int HERO_MATRIARCH = 49;
    /**
     * Zerg Infested Terran (units.dat entry {@code 50}).
     */
    public static final int ZERG_INFESTED_TERRAN = 50;
    /**
     * Hero Infested Kerrigan (units.dat entry {@code 51}).
     */
    public static final int HERO_INFESTED_KERRIGAN = 51;
    /**
     * Hero Unclean One (units.dat entry {@code 52}).
     */
    public static final int HERO_UNCLEAN_ONE = 52;
    /**
     * Hero Hunter Killer (units.dat entry {@code 53}).
     */
    public static final int HERO_HUNTER_KILLER = 53;
    /**
     * Hero Devouring One (units.dat entry {@code 54}).
     */
    public static final int HERO_DEVOURING_ONE = 54;
    /**
     * Hero Kukulza Mutalisk (units.dat entry {@code 55}).
     */
    public static final int HERO_KUKULZA_MUTALISK = 55;
    /**
     * Hero Kukulza Guardian (units.dat entry {@code 56}).
     */
    public static final int HERO_KUKULZA_GUARDIAN = 56;
    /**
     * Hero Yggdrasill (units.dat entry {@code 57}).
     */
    public static final int HERO_YGGDRASILL = 57;
    /**
     * Terran Valkyrie (units.dat entry {@code 58}).
     */
    public static final int TERRAN_VALKYRIE = 58;
    /**
     * Zerg Cocoon (units.dat entry {@code 59}).
     */
    public static final int ZERG_COCOON = 59;
    /**
     * Protoss Corsair (units.dat entry {@code 60}).
     */
    public static final int PROTOSS_CORSAIR = 60;
    /**
     * Protoss Dark Templar (units.dat entry {@code 61}).
     */
    public static final int PROTOSS_DARK_TEMPLAR = 61;
    /**
     * Zerg Devourer (units.dat entry {@code 62}).
     */
    public static final int ZERG_DEVOURER = 62;
    /**
     * Protoss Dark Archon (units.dat entry {@code 63}).
     */
    public static final int PROTOSS_DARK_ARCHON = 63;
    /**
     * Protoss Probe (units.dat entry {@code 64}).
     */
    public static final int PROTOSS_PROBE = 64;
    /**
     * Protoss Zealot (units.dat entry {@code 65}).
     */
    public static final int PROTOSS_ZEALOT = 65;
    /**
     * Protoss Dragoon (units.dat entry {@code 66}).
     */
    public static final int PROTOSS_DRAGOON = 66;
    /**
     * Protoss High Templar (units.dat entry {@code 67}).
     */
    public static final int PROTOSS_HIGH_TEMPLAR = 67;
    /**
     * Protoss Archon (units.dat entry {@code 68}).
     */
    public static final int PROTOSS_ARCHON = 68;
    /**
     * Protoss Shuttle (units.dat entry {@code 69}).
     */
    public static final int PROTOSS_SHUTTLE = 69;
    /**
     * Protoss Scout (units.dat entry {@code 70}).
     */
    public static final int PROTOSS_SCOUT = 70;
    /**
     * Protoss Arbiter (units.dat entry {@code 71}).
     */
    public static final int PROTOSS_ARBITER = 71;
    /**
     * Protoss Carrier (units.dat entry {@code 72}).
     */
    public static final int PROTOSS_CARRIER = 72;
    /**
     * Protoss Interceptor (units.dat entry {@code 73}).
     */
    public static final int PROTOSS_INTERCEPTOR = 73;
    /**
     * Hero Dark Templar (units.dat entry {@code 74}).
     */
    public static final int HERO_DARK_TEMPLAR = 74;
    /**
     * Hero Zeratul (units.dat entry {@code 75}).
     */
    public static final int HERO_ZERATUL = 75;
    /**
     * Hero Tassadar Zeratul Archon (units.dat entry {@code 76}).
     */
    public static final int HERO_TASSADAR_ZERATUL_ARCHON = 76;
    /**
     * Hero Fenix Zealot (units.dat entry {@code 77}).
     */
    public static final int HERO_FENIX_ZEALOT = 77;
    /**
     * Hero Fenix Dragoon (units.dat entry {@code 78}).
     */
    public static final int HERO_FENIX_DRAGOON = 78;
    /**
     * Hero Tassadar (units.dat entry {@code 79}).
     */
    public static final int HERO_TASSADAR = 79;
    /**
     * Hero Mojo (units.dat entry {@code 80}).
     */
    public static final int HERO_MOJO = 80;
    /**
     * Hero Warbringer (units.dat entry {@code 81}).
     */
    public static final int HERO_WARBRINGER = 81;
    /**
     * Hero Gantrithor (units.dat entry {@code 82}).
     */
    public static final int HERO_GANTRITHOR = 82;
    /**
     * Protoss Reaver (units.dat entry {@code 83}).
     */
    public static final int PROTOSS_REAVER = 83;
    /**
     * Protoss Observer (units.dat entry {@code 84}).
     */
    public static final int PROTOSS_OBSERVER = 84;
    /**
     * Protoss Scarab (units.dat entry {@code 85}).
     */
    public static final int PROTOSS_SCARAB = 85;
    /**
     * Hero Danimoth (units.dat entry {@code 86}).
     */
    public static final int HERO_DANIMOTH = 86;
    /**
     * Hero Aldaris (units.dat entry {@code 87}).
     */
    public static final int HERO_ALDARIS = 87;
    /**
     * Hero Artanis (units.dat entry {@code 88}).
     */
    public static final int HERO_ARTANIS = 88;
    /**
     * Critter Rhynadon (units.dat entry {@code 89}).
     */
    public static final int CRITTER_RHYNADON = 89;
    /**
     * Critter Bengalaas (units.dat entry {@code 90}).
     */
    public static final int CRITTER_BENGALAAS = 90;
    /**
     * Special Cargo Ship (units.dat entry {@code 91}).
     */
    public static final int SPECIAL_CARGO_SHIP = 91;
    /**
     * Special Mercenary Gunship (units.dat entry {@code 92}).
     */
    public static final int SPECIAL_MERCENARY_GUNSHIP = 92;
    /**
     * Critter Scantid (units.dat entry {@code 93}).
     */
    public static final int CRITTER_SCANTID = 93;
    /**
     * Critter Kakaru (units.dat entry {@code 94}).
     */
    public static final int CRITTER_KAKARU = 94;
    /**
     * Critter Ragnasaur (units.dat entry {@code 95}).
     */
    public static final int CRITTER_RAGNASAUR = 95;
    /**
     * Critter Ursadon (units.dat entry {@code 96}).
     */
    public static final int CRITTER_URSADON = 96;
    /**
     * Zerg Lurker Egg (units.dat entry {@code 97}).
     */
    public static final int ZERG_LURKER_EGG = 97;
    /**
     * Hero Raszagal (units.dat entry {@code 98}).
     */
    public static final int HERO_RASZAGAL = 98;
    /**
     * Hero Samir Duran (units.dat entry {@code 99}).
     */
    public static final int HERO_SAMIR_DURAN = 99;
    /**
     * Hero Alexei Stukov (units.dat entry {@code 100}).
     */
    public static final int HERO_ALEXEI_STUKOV = 100;
    /**
     * Special Map Revealer (units.dat entry {@code 101}).
     */
    public static final int SPECIAL_MAP_REVEALER = 101;
    /**
     * Hero Gerard DuGalle (units.dat entry {@code 102}).
     */
    public static final int HERO_GERARD_DUGALLE = 102;
    /**
     * Zerg Lurker (units.dat entry {@code 103}).
     */
    public static final int ZERG_LURKER = 103;
    /**
     * Hero Infested Duran (units.dat entry {@code 104}).
     */
    public static final int HERO_INFESTED_DURAN = 104;
    /**
     * Spell Disruption Web (units.dat entry {@code 105}).
     */
    public static final int SPELL_DISRUPTION_WEB = 105;
    /**
     * Terran Command Center (units.dat entry {@code 106}).
     */
    public static final int TERRAN_COMMAND_CENTER = 106;
    /**
     * Terran Comsat Station (units.dat entry {@code 107}).
     */
    public static final int TERRAN_COMSAT_STATION = 107;
    /**
     * Terran Nuclear Silo (units.dat entry {@code 108}).
     */
    public static final int TERRAN_NUCLEAR_SILO = 108;
    /**
     * Terran Supply Depot (units.dat entry {@code 109}).
     */
    public static final int TERRAN_SUPPLY_DEPOT = 109;
    /**
     * Terran Refinery (units.dat entry {@code 110}).
     */
    public static final int TERRAN_REFINERY = 110;
    /**
     * Terran Barracks (units.dat entry {@code 111}).
     */
    public static final int TERRAN_BARRACKS = 111;
    /**
     * Terran Academy (units.dat entry {@code 112}).
     */
    public static final int TERRAN_ACADEMY = 112;
    /**
     * Terran Factory (units.dat entry {@code 113}).
     */
    public static final int TERRAN_FACTORY = 113;
    /**
     * Terran Starport (units.dat entry {@code 114}).
     */
    public static final int TERRAN_STARPORT = 114;
    /**
     * Terran Control Tower (units.dat entry {@code 115}).
     */
    public static final int TERRAN_CONTROL_TOWER = 115;
    /**
     * Terran Science Facility (units.dat entry {@code 116}).
     */
    public static final int TERRAN_SCIENCE_FACILITY = 116;
    /**
     * Terran Covert Ops (units.dat entry {@code 117}).
     */
    public static final int TERRAN_COVERT_OPS = 117;
    /**
     * Terran Physics Lab (units.dat entry {@code 118}).
     */
    public static final int TERRAN_PHYSICS_LAB = 118;
    /**
     * Unused Terran1 (units.dat entry {@code 119}).
     */
    public static final int UNUSED_TERRAN1 = 119;
    /**
     * Terran Machine Shop (units.dat entry {@code 120}).
     */
    public static final int TERRAN_MACHINE_SHOP = 120;
    /**
     * Unused Terran2 (units.dat entry {@code 121}).
     */
    public static final int UNUSED_TERRAN2 = 121;
    /**
     * Terran Engineering Bay (units.dat entry {@code 122}).
     */
    public static final int TERRAN_ENGINEERING_BAY = 122;
    /**
     * Terran Armory (units.dat entry {@code 123}).
     */
    public static final int TERRAN_ARMORY = 123;
    /**
     * Terran Missile Turret (units.dat entry {@code 124}).
     */
    public static final int TERRAN_MISSILE_TURRET = 124;
    /**
     * Terran Bunker (units.dat entry {@code 125}).
     */
    public static final int TERRAN_BUNKER = 125;
    /**
     * Special Crashed Norad II (units.dat entry {@code 126}).
     */
    public static final int SPECIAL_CRASHED_NORAD_II = 126;
    /**
     * Special Ion Cannon (units.dat entry {@code 127}).
     */
    public static final int SPECIAL_ION_CANNON = 127;
    /**
     * Powerup Uraj Crystal (units.dat entry {@code 128}).
     */
    public static final int POWERUP_URAJ_CRYSTAL = 128;
    /**
     * Powerup Khalis Crystal (units.dat entry {@code 129}).
     */
    public static final int POWERUP_KHALIS_CRYSTAL = 129;
    /**
     * Zerg Infested Command Center (units.dat entry {@code 130}).
     */
    public static final int ZERG_INFESTED_COMMAND_CENTER = 130;
    /**
     * Zerg Hatchery (units.dat entry {@code 131}).
     */
    public static final int ZERG_HATCHERY = 131;
    /**
     * Zerg Lair (units.dat entry {@code 132}).
     */
    public static final int ZERG_LAIR = 132;
    /**
     * Zerg Hive (units.dat entry {@code 133}).
     */
    public static final int ZERG_HIVE = 133;
    /**
     * Zerg Nydus Canal (units.dat entry {@code 134}).
     */
    public static final int ZERG_NYDUS_CANAL = 134;
    /**
     * Zerg Hydralisk Den (units.dat entry {@code 135}).
     */
    public static final int ZERG_HYDRALISK_DEN = 135;
    /**
     * Zerg Defiler Mound (units.dat entry {@code 136}).
     */
    public static final int ZERG_DEFILER_MOUND = 136;
    /**
     * Zerg Greater Spire (units.dat entry {@code 137}).
     */
    public static final int ZERG_GREATER_SPIRE = 137;
    /**
     * Zerg Queens Nest (units.dat entry {@code 138}).
     */
    public static final int ZERG_QUEENS_NEST = 138;
    /**
     * Zerg Evolution Chamber (units.dat entry {@code 139}).
     */
    public static final int ZERG_EVOLUTION_CHAMBER = 139;
    /**
     * Zerg Ultralisk Cavern (units.dat entry {@code 140}).
     */
    public static final int ZERG_ULTRALISK_CAVERN = 140;
    /**
     * Zerg Spire (units.dat entry {@code 141}).
     */
    public static final int ZERG_SPIRE = 141;
    /**
     * Zerg Spawning Pool (units.dat entry {@code 142}).
     */
    public static final int ZERG_SPAWNING_POOL = 142;
    /**
     * Zerg Creep Colony (units.dat entry {@code 143}).
     */
    public static final int ZERG_CREEP_COLONY = 143;
    /**
     * Zerg Spore Colony (units.dat entry {@code 144}).
     */
    public static final int ZERG_SPORE_COLONY = 144;
    /**
     * Unused Zerg1 (units.dat entry {@code 145}).
     */
    public static final int UNUSED_ZERG1 = 145;
    /**
     * Zerg Sunken Colony (units.dat entry {@code 146}).
     */
    public static final int ZERG_SUNKEN_COLONY = 146;
    /**
     * Special Overmind With Shell (units.dat entry {@code 147}).
     */
    public static final int SPECIAL_OVERMIND_WITH_SHELL = 147;
    /**
     * Special Overmind (units.dat entry {@code 148}).
     */
    public static final int SPECIAL_OVERMIND = 148;
    /**
     * Zerg Extractor (units.dat entry {@code 149}).
     */
    public static final int ZERG_EXTRACTOR = 149;
    /**
     * Special Mature Chrysalis (units.dat entry {@code 150}).
     */
    public static final int SPECIAL_MATURE_CHRYSALIS = 150;
    /**
     * Special Cerebrate (units.dat entry {@code 151}).
     */
    public static final int SPECIAL_CEREBRATE = 151;
    /**
     * Special Cerebrate Daggoth (units.dat entry {@code 152}).
     */
    public static final int SPECIAL_CEREBRATE_DAGGOTH = 152;
    /**
     * Unused Zerg2 (units.dat entry {@code 153}).
     */
    public static final int UNUSED_ZERG2 = 153;
    /**
     * Protoss Nexus (units.dat entry {@code 154}).
     */
    public static final int PROTOSS_NEXUS = 154;
    /**
     * Protoss Robotics Facility (units.dat entry {@code 155}).
     */
    public static final int PROTOSS_ROBOTICS_FACILITY = 155;
    /**
     * Protoss Pylon (units.dat entry {@code 156}).
     */
    public static final int PROTOSS_PYLON = 156;
    /**
     * Protoss Assimilator (units.dat entry {@code 157}).
     */
    public static final int PROTOSS_ASSIMILATOR = 157;
    /**
     * Unused Protoss1 (units.dat entry {@code 158}).
     */
    public static final int UNUSED_PROTOSS1 = 158;
    /**
     * Protoss Observatory (units.dat entry {@code 159}).
     */
    public static final int PROTOSS_OBSERVATORY = 159;
    /**
     * Protoss Gateway (units.dat entry {@code 160}).
     */
    public static final int PROTOSS_GATEWAY = 160;
    /**
     * Unused Protoss2 (units.dat entry {@code 161}).
     */
    public static final int UNUSED_PROTOSS2 = 161;
    /**
     * Protoss Photon Cannon (units.dat entry {@code 162}).
     */
    public static final int PROTOSS_PHOTON_CANNON = 162;
    /**
     * Protoss Citadel of Adun (units.dat entry {@code 163}).
     */
    public static final int PROTOSS_CITADEL_OF_ADUN = 163;
    /**
     * Protoss Cybernetics Core (units.dat entry {@code 164}).
     */
    public static final int PROTOSS_CYBERNETICS_CORE = 164;
    /**
     * Protoss Templar Archives (units.dat entry {@code 165}).
     */
    public static final int PROTOSS_TEMPLAR_ARCHIVES = 165;
    /**
     * Protoss Forge (units.dat entry {@code 166}).
     */
    public static final int PROTOSS_FORGE = 166;
    /**
     * Protoss Stargate (units.dat entry {@code 167}).
     */
    public static final int PROTOSS_STARGATE = 167;
    /**
     * Special Stasis Cell Prison (units.dat entry {@code 168}).
     */
    public static final int SPECIAL_STASIS_CELL_PRISON = 168;
    /**
     * Protoss Fleet Beacon (units.dat entry {@code 169}).
     */
    public static final int PROTOSS_FLEET_BEACON = 169;
    /**
     * Protoss Arbiter Tribunal (units.dat entry {@code 170}).
     */
    public static final int PROTOSS_ARBITER_TRIBUNAL = 170;
    /**
     * Protoss Robotics Support Bay (units.dat entry {@code 171}).
     */
    public static final int PROTOSS_ROBOTICS_SUPPORT_BAY = 171;
    /**
     * Protoss Shield Battery (units.dat entry {@code 172}).
     */
    public static final int PROTOSS_SHIELD_BATTERY = 172;
    /**
     * Special Khaydarin Crystal Form (units.dat entry {@code 173}).
     */
    public static final int SPECIAL_KHAYDARIN_CRYSTAL_FORM = 173;
    /**
     * Special Protoss Temple (units.dat entry {@code 174}).
     */
    public static final int SPECIAL_PROTOSS_TEMPLE = 174;
    /**
     * Special XelNaga Temple (units.dat entry {@code 175}).
     */
    public static final int SPECIAL_XELNAGA_TEMPLE = 175;
    /**
     * Resource Mineral Field (units.dat entry {@code 176}).
     */
    public static final int RESOURCE_MINERAL_FIELD = 176;
    /**
     * Resource Mineral Field Type 2 (units.dat entry {@code 177}).
     */
    public static final int RESOURCE_MINERAL_FIELD_TYPE_2 = 177;
    /**
     * Resource Mineral Field Type 3 (units.dat entry {@code 178}).
     */
    public static final int RESOURCE_MINERAL_FIELD_TYPE_3 = 178;
    /**
     * Unused Cave (units.dat entry {@code 179}).
     */
    public static final int UNUSED_CAVE = 179;
    /**
     * Unused Cave In (units.dat entry {@code 180}).
     */
    public static final int UNUSED_CAVE_IN = 180;
    /**
     * Unused Cantina (units.dat entry {@code 181}).
     */
    public static final int UNUSED_CANTINA = 181;
    /**
     * Unused Mining Platform (units.dat entry {@code 182}).
     */
    public static final int UNUSED_MINING_PLATFORM = 182;
    /**
     * Unused Independant Command Center (units.dat entry {@code 183}).
     */
    public static final int UNUSED_INDEPENDANT_COMMAND_CENTER = 183;
    /**
     * Special Independant Starport (units.dat entry {@code 184}).
     */
    public static final int SPECIAL_INDEPENDANT_STARPORT = 184;
    /**
     * Unused Independant Jump Gate (units.dat entry {@code 185}).
     */
    public static final int UNUSED_INDEPENDANT_JUMP_GATE = 185;
    /**
     * Unused Ruins (units.dat entry {@code 186}).
     */
    public static final int UNUSED_RUINS = 186;
    /**
     * Unused Khaydarin Crystal Formation (units.dat entry {@code 187}).
     */
    public static final int UNUSED_KHAYDARIN_CRYSTAL_FORMATION = 187;
    /**
     * Resource Vespene Geyser (units.dat entry {@code 188}).
     */
    public static final int RESOURCE_VESPENE_GEYSER = 188;
    /**
     * Special Warp Gate (units.dat entry {@code 189}).
     */
    public static final int SPECIAL_WARP_GATE = 189;
    /**
     * Special Psi Disrupter (units.dat entry {@code 190}).
     */
    public static final int SPECIAL_PSI_DISRUPTER = 190;
    /**
     * Unused Zerg Marker (units.dat entry {@code 191}).
     */
    public static final int UNUSED_ZERG_MARKER = 191;
    /**
     * Unused Terran Marker (units.dat entry {@code 192}).
     */
    public static final int UNUSED_TERRAN_MARKER = 192;
    /**
     * Unused Protoss Marker (units.dat entry {@code 193}).
     */
    public static final int UNUSED_PROTOSS_MARKER = 193;
    /**
     * Special Zerg Beacon (units.dat entry {@code 194}).
     */
    public static final int SPECIAL_ZERG_BEACON = 194;
    /**
     * Special Terran Beacon (units.dat entry {@code 195}).
     */
    public static final int SPECIAL_TERRAN_BEACON = 195;
    /**
     * Special Protoss Beacon (units.dat entry {@code 196}).
     */
    public static final int SPECIAL_PROTOSS_BEACON = 196;
    /**
     * Special Zerg Flag Beacon (units.dat entry {@code 197}).
     */
    public static final int SPECIAL_ZERG_FLAG_BEACON = 197;
    /**
     * Special Terran Flag Beacon (units.dat entry {@code 198}).
     */
    public static final int SPECIAL_TERRAN_FLAG_BEACON = 198;
    /**
     * Special Protoss Flag Beacon (units.dat entry {@code 199}).
     */
    public static final int SPECIAL_PROTOSS_FLAG_BEACON = 199;
    /**
     * Special Power Generator (units.dat entry {@code 200}).
     */
    public static final int SPECIAL_POWER_GENERATOR = 200;
    /**
     * Special Overmind Cocoon (units.dat entry {@code 201}).
     */
    public static final int SPECIAL_OVERMIND_COCOON = 201;
    /**
     * Spell Dark Swarm (units.dat entry {@code 202}).
     */
    public static final int SPELL_DARK_SWARM = 202;
    /**
     * Special Floor Missile Trap (units.dat entry {@code 203}).
     */
    public static final int SPECIAL_FLOOR_MISSILE_TRAP = 203;
    /**
     * Special Floor Hatch (units.dat entry {@code 204}).
     */
    public static final int SPECIAL_FLOOR_HATCH = 204;
    /**
     * Special Upper Level Door (units.dat entry {@code 205}).
     */
    public static final int SPECIAL_UPPER_LEVEL_DOOR = 205;
    /**
     * Special Right Upper Level Door (units.dat entry {@code 206}).
     */
    public static final int SPECIAL_RIGHT_UPPER_LEVEL_DOOR = 206;
    /**
     * Special Pit Door (units.dat entry {@code 207}).
     */
    public static final int SPECIAL_PIT_DOOR = 207;
    /**
     * Special Right Pit Door (units.dat entry {@code 208}).
     */
    public static final int SPECIAL_RIGHT_PIT_DOOR = 208;
    /**
     * Special Floor Gun Trap (units.dat entry {@code 209}).
     */
    public static final int SPECIAL_FLOOR_GUN_TRAP = 209;
    /**
     * Special Wall Missile Trap (units.dat entry {@code 210}).
     */
    public static final int SPECIAL_WALL_MISSILE_TRAP = 210;
    /**
     * Special Wall Flame Trap (units.dat entry {@code 211}).
     */
    public static final int SPECIAL_WALL_FLAME_TRAP = 211;
    /**
     * Special Right Wall Missile Trap (units.dat entry {@code 212}).
     */
    public static final int SPECIAL_RIGHT_WALL_MISSILE_TRAP = 212;
    /**
     * Special Right Wall Flame Trap (units.dat entry {@code 213}).
     */
    public static final int SPECIAL_RIGHT_WALL_FLAME_TRAP = 213;
    /**
     * Special Start Location (units.dat entry {@code 214}).
     */
    public static final int SPECIAL_START_LOCATION = 214;
    /**
     * Powerup Flag (units.dat entry {@code 215}).
     */
    public static final int POWERUP_FLAG = 215;
    /**
     * Powerup Young Chrysalis (units.dat entry {@code 216}).
     */
    public static final int POWERUP_YOUNG_CHRYSALIS = 216;
    /**
     * Powerup Psi Emitter (units.dat entry {@code 217}).
     */
    public static final int POWERUP_PSI_EMITTER = 217;
    /**
     * Powerup Data Disk (units.dat entry {@code 218}).
     */
    public static final int POWERUP_DATA_DISK = 218;
    /**
     * Powerup Khaydarin Crystal (units.dat entry {@code 219}).
     */
    public static final int POWERUP_KHAYDARIN_CRYSTAL = 219;
    /**
     * Powerup Mineral Cluster Type 1 (units.dat entry {@code 220}).
     */
    public static final int POWERUP_MINERAL_CLUSTER_TYPE_1 = 220;
    /**
     * Powerup Mineral Cluster Type 2 (units.dat entry {@code 221}).
     */
    public static final int POWERUP_MINERAL_CLUSTER_TYPE_2 = 221;
    /**
     * Powerup Protoss Gas Orb Type 1 (units.dat entry {@code 222}).
     */
    public static final int POWERUP_PROTOSS_GAS_ORB_TYPE_1 = 222;
    /**
     * Powerup Protoss Gas Orb Type 2 (units.dat entry {@code 223}).
     */
    public static final int POWERUP_PROTOSS_GAS_ORB_TYPE_2 = 223;
    /**
     * Powerup Zerg Gas Sac Type 1 (units.dat entry {@code 224}).
     */
    public static final int POWERUP_ZERG_GAS_SAC_TYPE_1 = 224;
    /**
     * Powerup Zerg Gas Sac Type 2 (units.dat entry {@code 225}).
     */
    public static final int POWERUP_ZERG_GAS_SAC_TYPE_2 = 225;
    /**
     * Powerup Terran Gas Tank Type 1 (units.dat entry {@code 226}).
     */
    public static final int POWERUP_TERRAN_GAS_TANK_TYPE_1 = 226;
    /**
     * Powerup Terran Gas Tank Type 2 (units.dat entry {@code 227}).
     */
    public static final int POWERUP_TERRAN_GAS_TANK_TYPE_2 = 227;
    /**
     * The "none" sentinel index ({@code 228}) that follows the units.dat entries.
     */
    public static final int NONE = 228;

    /**
     * The number of values, from {@code 0} through {@link #NONE}.
     */
    private static final int VALUE_COUNT = 229;

    /**
     * @param identifier the value to check
     * @return {@code true} if the value is a unit type identifier, {@code false} otherwise
     */
    public static boolean isValid(int identifier) {
        return identifier >= 0 && identifier < VALUE_COUNT;
    }

    /**
     * Checks that a value is a unit type identifier.
     *
     * @param identifier the value to check
     * @return the value
     * @throws IllegalArgumentException if the value is not a unit type identifier
     */
    public static int requireValid(int identifier) {
        if (!isValid(identifier)) {
            throw Exceptions.newIllegalArgumentException("Invalid unit type index: {0}", identifier);
        }

        return identifier;
    }

    /**
     * Checks that a value is a unit type identifier or an id of the added unit type range ({@link ScrAddedUnitTypes}), for a {@code switch} over
     * the native types: an added id becomes {@link #NONE}, so the {@code switch} takes the branch of a type without a native rule.
     *
     * @param identifier the value to check
     * @return the value if it is a unit type identifier, {@link #NONE} for an id of the added range
     * @throws IllegalArgumentException if the value is neither a unit type identifier nor an id of the added range
     */
    public static int requireValidOrAdded(int identifier) {
        if (identifier >= ScrAddedUnitTypes.FIRST_ID && identifier <= ScrAddedUnitTypes.LAST_ID) {
            return NONE;
        }

        return requireValid(identifier);
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


    private ScrUnitIds() {
    }
}
