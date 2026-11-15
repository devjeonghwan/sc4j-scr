package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.engine.content.type.OrderType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds the order type identities of the SC:R mod, one per orders.dat entry, in orders.dat order. The mod registers them in this
 * order, so the index of each registered type is its orders.dat entry ({@link ScrNativeIds}).
 */
public final class ScrOrders {
    /**
     * The number of orders.dat entries: the registered order types.
     */
    public static final int COUNT = 189;
    /**
     * The orders.dat entry that names no order type, stored as {@link ContentType#NO_INDEX} in the engine tables.
     */
    public static final int NATIVE_NONE = 189;

    private static final @NotNull OrderType @NotNull [] TYPES = new OrderType[COUNT];

    /**
     * Die (orders.dat entry {@code 0}).
     */
    public static final @NotNull OrderType DIE = create(0, "Die");
    /**
     * Stop (orders.dat entry {@code 1}).
     */
    public static final @NotNull OrderType STOP = create(1, "Stop");
    /**
     * Guard (orders.dat entry {@code 2}).
     */
    public static final @NotNull OrderType GUARD = create(2, "Guard");
    /**
     * PlayerGuard (orders.dat entry {@code 3}).
     */
    public static final @NotNull OrderType PLAYER_GUARD = create(3, "PlayerGuard");
    /**
     * TurretGuard (orders.dat entry {@code 4}).
     */
    public static final @NotNull OrderType TURRET_GUARD = create(4, "TurretGuard");
    /**
     * BunkerGuard (orders.dat entry {@code 5}).
     */
    public static final @NotNull OrderType BUNKER_GUARD = create(5, "BunkerGuard");
    /**
     * Move (orders.dat entry {@code 6}).
     */
    public static final @NotNull OrderType MOVE = create(6, "Move");
    /**
     * ReaverStop (orders.dat entry {@code 7}).
     */
    public static final @NotNull OrderType REAVER_STOP = create(7, "ReaverStop");
    /**
     * AttackDefault (orders.dat entry {@code 8}).
     */
    public static final @NotNull OrderType ATTACK_DEFAULT = create(8, "AttackDefault");
    /**
     * MoveToAttack (orders.dat entry {@code 9}).
     */
    public static final @NotNull OrderType MOVE_TO_ATTACK = create(9, "MoveToAttack");
    /**
     * AttackUnit (orders.dat entry {@code 10}).
     */
    public static final @NotNull OrderType ATTACK_UNIT = create(10, "AttackUnit");
    /**
     * AttackFixedRange (orders.dat entry {@code 11}).
     */
    public static final @NotNull OrderType ATTACK_FIXED_RANGE = create(11, "AttackFixedRange");
    /**
     * AttackTile (orders.dat entry {@code 12}).
     */
    public static final @NotNull OrderType ATTACK_TILE = create(12, "AttackTile");
    /**
     * Hover (orders.dat entry {@code 13}).
     */
    public static final @NotNull OrderType HOVER = create(13, "Hover");
    /**
     * AttackMove (orders.dat entry {@code 14}).
     */
    public static final @NotNull OrderType ATTACK_MOVE = create(14, "AttackMove");
    /**
     * InfestedCommandCenter (orders.dat entry {@code 15}).
     */
    public static final @NotNull OrderType INFESTED_COMMAND_CENTER = create(15, "InfestedCommandCenter");
    /**
     * UnusedNothing (orders.dat entry {@code 16}).
     */
    public static final @NotNull OrderType UNUSED_NOTHING = create(16, "UnusedNothing");
    /**
     * UnusedPowerup (orders.dat entry {@code 17}).
     */
    public static final @NotNull OrderType UNUSED_POWERUP = create(17, "UnusedPowerup");
    /**
     * TowerGuard (orders.dat entry {@code 18}).
     */
    public static final @NotNull OrderType TOWER_GUARD = create(18, "TowerGuard");
    /**
     * TowerAttack (orders.dat entry {@code 19}).
     */
    public static final @NotNull OrderType TOWER_ATTACK = create(19, "TowerAttack");
    /**
     * SpiderMine (orders.dat entry {@code 20}).
     */
    public static final @NotNull OrderType SPIDER_MINE = create(20, "SpiderMine");
    /**
     * StayInRange (orders.dat entry {@code 21}).
     */
    public static final @NotNull OrderType STAY_IN_RANGE = create(21, "StayInRange");
    /**
     * TurretAttack (orders.dat entry {@code 22}).
     */
    public static final @NotNull OrderType TURRET_ATTACK = create(22, "TurretAttack");
    /**
     * Nothing (orders.dat entry {@code 23}).
     */
    public static final @NotNull OrderType NOTHING = create(23, "Nothing");
    /**
     * NothingWait (orders.dat entry {@code 24}).
     */
    public static final @NotNull OrderType NOTHING_WAIT = create(24, "NothingWait");
    /**
     * DroneStartBuild (orders.dat entry {@code 25}).
     */
    public static final @NotNull OrderType DRONE_START_BUILD = create(25, "DroneStartBuild");
    /**
     * DroneBuild (orders.dat entry {@code 26}).
     */
    public static final @NotNull OrderType DRONE_BUILD = create(26, "DroneBuild");
    /**
     * CastInfestation (orders.dat entry {@code 27}).
     */
    public static final @NotNull OrderType CAST_INFESTATION = create(27, "CastInfestation");
    /**
     * MoveToInfest (orders.dat entry {@code 28}).
     */
    public static final @NotNull OrderType MOVE_TO_INFEST = create(28, "MoveToInfest");
    /**
     * InfestingCommandCenter (orders.dat entry {@code 29}).
     */
    public static final @NotNull OrderType INFESTING_COMMAND_CENTER = create(29, "InfestingCommandCenter");
    /**
     * PlaceBuilding (orders.dat entry {@code 30}).
     */
    public static final @NotNull OrderType PLACE_BUILDING = create(30, "PlaceBuilding");
    /**
     * PlaceProtossBuilding (orders.dat entry {@code 31}).
     */
    public static final @NotNull OrderType PLACE_PROTOSS_BUILDING = create(31, "PlaceProtossBuilding");
    /**
     * CreateProtossBuilding (orders.dat entry {@code 32}).
     */
    public static final @NotNull OrderType CREATE_PROTOSS_BUILDING = create(32, "CreateProtossBuilding");
    /**
     * ConstructingBuilding (orders.dat entry {@code 33}).
     */
    public static final @NotNull OrderType CONSTRUCTING_BUILDING = create(33, "ConstructingBuilding");
    /**
     * Repair (orders.dat entry {@code 34}).
     */
    public static final @NotNull OrderType REPAIR = create(34, "Repair");
    /**
     * MoveToRepair (orders.dat entry {@code 35}).
     */
    public static final @NotNull OrderType MOVE_TO_REPAIR = create(35, "MoveToRepair");
    /**
     * PlaceAddon (orders.dat entry {@code 36}).
     */
    public static final @NotNull OrderType PLACE_ADDON = create(36, "PlaceAddon");
    /**
     * BuildAddon (orders.dat entry {@code 37}).
     */
    public static final @NotNull OrderType BUILD_ADDON = create(37, "BuildAddon");
    /**
     * Train (orders.dat entry {@code 38}).
     */
    public static final @NotNull OrderType TRAIN = create(38, "Train");
    /**
     * RallyPointUnit (orders.dat entry {@code 39}).
     */
    public static final @NotNull OrderType RALLY_POINT_UNIT = create(39, "RallyPointUnit");
    /**
     * RallyPointTile (orders.dat entry {@code 40}).
     */
    public static final @NotNull OrderType RALLY_POINT_TILE = create(40, "RallyPointTile");
    /**
     * ZergBirth (orders.dat entry {@code 41}).
     */
    public static final @NotNull OrderType ZERG_BIRTH = create(41, "ZergBirth");
    /**
     * ZergUnitMorph (orders.dat entry {@code 42}).
     */
    public static final @NotNull OrderType ZERG_UNIT_MORPH = create(42, "ZergUnitMorph");
    /**
     * ZergBuildingMorph (orders.dat entry {@code 43}).
     */
    public static final @NotNull OrderType ZERG_BUILDING_MORPH = create(43, "ZergBuildingMorph");
    /**
     * IncompleteBuilding (orders.dat entry {@code 44}).
     */
    public static final @NotNull OrderType INCOMPLETE_BUILDING = create(44, "IncompleteBuilding");
    /**
     * IncompleteMorphing (orders.dat entry {@code 45}).
     */
    public static final @NotNull OrderType INCOMPLETE_MORPHING = create(45, "IncompleteMorphing");
    /**
     * BuildNydusExit (orders.dat entry {@code 46}).
     */
    public static final @NotNull OrderType BUILD_NYDUS_EXIT = create(46, "BuildNydusExit");
    /**
     * EnterNydusCanal (orders.dat entry {@code 47}).
     */
    public static final @NotNull OrderType ENTER_NYDUS_CANAL = create(47, "EnterNydusCanal");
    /**
     * IncompleteWarping (orders.dat entry {@code 48}).
     */
    public static final @NotNull OrderType INCOMPLETE_WARPING = create(48, "IncompleteWarping");
    /**
     * Follow (orders.dat entry {@code 49}).
     */
    public static final @NotNull OrderType FOLLOW = create(49, "Follow");
    /**
     * Carrier (orders.dat entry {@code 50}).
     */
    public static final @NotNull OrderType CARRIER = create(50, "Carrier");
    /**
     * ReaverCarrierMove (orders.dat entry {@code 51}).
     */
    public static final @NotNull OrderType REAVER_CARRIER_MOVE = create(51, "ReaverCarrierMove");
    /**
     * CarrierStop (orders.dat entry {@code 52}).
     */
    public static final @NotNull OrderType CARRIER_STOP = create(52, "CarrierStop");
    /**
     * CarrierAttack (orders.dat entry {@code 53}).
     */
    public static final @NotNull OrderType CARRIER_ATTACK = create(53, "CarrierAttack");
    /**
     * CarrierMoveToAttack (orders.dat entry {@code 54}).
     */
    public static final @NotNull OrderType CARRIER_MOVE_TO_ATTACK = create(54, "CarrierMoveToAttack");
    /**
     * CarrierIgnore2 (orders.dat entry {@code 55}).
     */
    public static final @NotNull OrderType CARRIER_IGNORE2 = create(55, "CarrierIgnore2");
    /**
     * CarrierFight (orders.dat entry {@code 56}).
     */
    public static final @NotNull OrderType CARRIER_FIGHT = create(56, "CarrierFight");
    /**
     * CarrierHoldPosition (orders.dat entry {@code 57}).
     */
    public static final @NotNull OrderType CARRIER_HOLD_POSITION = create(57, "CarrierHoldPosition");
    /**
     * Reaver (orders.dat entry {@code 58}).
     */
    public static final @NotNull OrderType REAVER = create(58, "Reaver");
    /**
     * ReaverAttack (orders.dat entry {@code 59}).
     */
    public static final @NotNull OrderType REAVER_ATTACK = create(59, "ReaverAttack");
    /**
     * ReaverMoveToAttack (orders.dat entry {@code 60}).
     */
    public static final @NotNull OrderType REAVER_MOVE_TO_ATTACK = create(60, "ReaverMoveToAttack");
    /**
     * ReaverFight (orders.dat entry {@code 61}).
     */
    public static final @NotNull OrderType REAVER_FIGHT = create(61, "ReaverFight");
    /**
     * ReaverHoldPosition (orders.dat entry {@code 62}).
     */
    public static final @NotNull OrderType REAVER_HOLD_POSITION = create(62, "ReaverHoldPosition");
    /**
     * TrainFighter (orders.dat entry {@code 63}).
     */
    public static final @NotNull OrderType TRAIN_FIGHTER = create(63, "TrainFighter");
    /**
     * InterceptorAttack (orders.dat entry {@code 64}).
     */
    public static final @NotNull OrderType INTERCEPTOR_ATTACK = create(64, "InterceptorAttack");
    /**
     * ScarabAttack (orders.dat entry {@code 65}).
     */
    public static final @NotNull OrderType SCARAB_ATTACK = create(65, "ScarabAttack");
    /**
     * RechargeShieldsUnit (orders.dat entry {@code 66}).
     */
    public static final @NotNull OrderType RECHARGE_SHIELDS_UNIT = create(66, "RechargeShieldsUnit");
    /**
     * RechargeShieldsBattery (orders.dat entry {@code 67}).
     */
    public static final @NotNull OrderType RECHARGE_SHIELDS_BATTERY = create(67, "RechargeShieldsBattery");
    /**
     * ShieldBattery (orders.dat entry {@code 68}).
     */
    public static final @NotNull OrderType SHIELD_BATTERY = create(68, "ShieldBattery");
    /**
     * InterceptorReturn (orders.dat entry {@code 69}).
     */
    public static final @NotNull OrderType INTERCEPTOR_RETURN = create(69, "InterceptorReturn");
    /**
     * DroneLand (orders.dat entry {@code 70}).
     */
    public static final @NotNull OrderType DRONE_LAND = create(70, "DroneLand");
    /**
     * BuildingLand (orders.dat entry {@code 71}).
     */
    public static final @NotNull OrderType BUILDING_LAND = create(71, "BuildingLand");
    /**
     * BuildingLiftoff (orders.dat entry {@code 72}).
     */
    public static final @NotNull OrderType BUILDING_LIFTOFF = create(72, "BuildingLiftoff");
    /**
     * DroneLiftOff (orders.dat entry {@code 73}).
     */
    public static final @NotNull OrderType DRONE_LIFT_OFF = create(73, "DroneLiftOff");
    /**
     * LiftingOff (orders.dat entry {@code 74}).
     */
    public static final @NotNull OrderType LIFTING_OFF = create(74, "LiftingOff");
    /**
     * ResearchTech (orders.dat entry {@code 75}).
     */
    public static final @NotNull OrderType RESEARCH_TECH = create(75, "ResearchTech");
    /**
     * Upgrade (orders.dat entry {@code 76}).
     */
    public static final @NotNull OrderType UPGRADE = create(76, "Upgrade");
    /**
     * Larva (orders.dat entry {@code 77}).
     */
    public static final @NotNull OrderType LARVA = create(77, "Larva");
    /**
     * SpawningLarva (orders.dat entry {@code 78}).
     */
    public static final @NotNull OrderType SPAWNING_LARVA = create(78, "SpawningLarva");
    /**
     * Harvest1 (orders.dat entry {@code 79}).
     */
    public static final @NotNull OrderType HARVEST1 = create(79, "Harvest1");
    /**
     * Harvest2 (orders.dat entry {@code 80}).
     */
    public static final @NotNull OrderType HARVEST2 = create(80, "Harvest2");
    /**
     * MoveToGas (orders.dat entry {@code 81}).
     */
    public static final @NotNull OrderType MOVE_TO_GAS = create(81, "MoveToGas");
    /**
     * WaitForGas (orders.dat entry {@code 82}).
     */
    public static final @NotNull OrderType WAIT_FOR_GAS = create(82, "WaitForGas");
    /**
     * HarvestGas (orders.dat entry {@code 83}).
     */
    public static final @NotNull OrderType HARVEST_GAS = create(83, "HarvestGas");
    /**
     * ReturnGas (orders.dat entry {@code 84}).
     */
    public static final @NotNull OrderType RETURN_GAS = create(84, "ReturnGas");
    /**
     * MoveToMinerals (orders.dat entry {@code 85}).
     */
    public static final @NotNull OrderType MOVE_TO_MINERALS = create(85, "MoveToMinerals");
    /**
     * WaitForMinerals (orders.dat entry {@code 86}).
     */
    public static final @NotNull OrderType WAIT_FOR_MINERALS = create(86, "WaitForMinerals");
    /**
     * MiningMinerals (orders.dat entry {@code 87}).
     */
    public static final @NotNull OrderType MINING_MINERALS = create(87, "MiningMinerals");
    /**
     * GatheringInterrupted (orders.dat entry {@code 88}).
     */
    public static final @NotNull OrderType GATHERING_INTERRUPTED = create(88, "GatheringInterrupted");
    /**
     * GatherWaitInterrupted (orders.dat entry {@code 89}).
     */
    public static final @NotNull OrderType GATHER_WAIT_INTERRUPTED = create(89, "GatherWaitInterrupted");
    /**
     * ReturnMinerals (orders.dat entry {@code 90}).
     */
    public static final @NotNull OrderType RETURN_MINERALS = create(90, "ReturnMinerals");
    /**
     * RechargeShieldsUnitRemoveOverlay (orders.dat entry {@code 91}).
     */
    public static final @NotNull OrderType RECHARGE_SHIELDS_UNIT_REMOVE_OVERLAY = create(91, "RechargeShieldsUnitRemoveOverlay");
    /**
     * EnterTransport (orders.dat entry {@code 92}).
     */
    public static final @NotNull OrderType ENTER_TRANSPORT = create(92, "EnterTransport");
    /**
     * PickupIdle (orders.dat entry {@code 93}).
     */
    public static final @NotNull OrderType PICKUP_IDLE = create(93, "PickupIdle");
    /**
     * PickupTransport (orders.dat entry {@code 94}).
     */
    public static final @NotNull OrderType PICKUP_TRANSPORT = create(94, "PickupTransport");
    /**
     * PickupBunker (orders.dat entry {@code 95}).
     */
    public static final @NotNull OrderType PICKUP_BUNKER = create(95, "PickupBunker");
    /**
     * Pickup4 (orders.dat entry {@code 96}).
     */
    public static final @NotNull OrderType PICKUP4 = create(96, "Pickup4");
    /**
     * PowerupIdle (orders.dat entry {@code 97}).
     */
    public static final @NotNull OrderType POWERUP_IDLE = create(97, "PowerupIdle");
    /**
     * Sieging (orders.dat entry {@code 98}).
     */
    public static final @NotNull OrderType SIEGING = create(98, "Sieging");
    /**
     * Unsieging (orders.dat entry {@code 99}).
     */
    public static final @NotNull OrderType UNSIEGING = create(99, "Unsieging");
    /**
     * WatchTarget (orders.dat entry {@code 100}).
     */
    public static final @NotNull OrderType WATCH_TARGET = create(100, "WatchTarget");
    /**
     * InitCreepGrowth (orders.dat entry {@code 101}).
     */
    public static final @NotNull OrderType INIT_CREEP_GROWTH = create(101, "InitCreepGrowth");
    /**
     * SpreadCreep (orders.dat entry {@code 102}).
     */
    public static final @NotNull OrderType SPREAD_CREEP = create(102, "SpreadCreep");
    /**
     * StoppingCreepGrowth (orders.dat entry {@code 103}).
     */
    public static final @NotNull OrderType STOPPING_CREEP_GROWTH = create(103, "StoppingCreepGrowth");
    /**
     * GuardianAspect (orders.dat entry {@code 104}).
     */
    public static final @NotNull OrderType GUARDIAN_ASPECT = create(104, "GuardianAspect");
    /**
     * ArchonWarp (orders.dat entry {@code 105}).
     */
    public static final @NotNull OrderType ARCHON_WARP = create(105, "ArchonWarp");
    /**
     * CompletingArchonSummon (orders.dat entry {@code 106}).
     */
    public static final @NotNull OrderType COMPLETING_ARCHON_SUMMON = create(106, "CompletingArchonSummon");
    /**
     * HoldPosition (orders.dat entry {@code 107}).
     */
    public static final @NotNull OrderType HOLD_POSITION = create(107, "HoldPosition");
    /**
     * QueenHoldPosition (orders.dat entry {@code 108}).
     */
    public static final @NotNull OrderType QUEEN_HOLD_POSITION = create(108, "QueenHoldPosition");
    /**
     * Cloak (orders.dat entry {@code 109}).
     */
    public static final @NotNull OrderType CLOAK = create(109, "Cloak");
    /**
     * Decloak (orders.dat entry {@code 110}).
     */
    public static final @NotNull OrderType DECLOAK = create(110, "Decloak");
    /**
     * Unload (orders.dat entry {@code 111}).
     */
    public static final @NotNull OrderType UNLOAD = create(111, "Unload");
    /**
     * MoveUnload (orders.dat entry {@code 112}).
     */
    public static final @NotNull OrderType MOVE_UNLOAD = create(112, "MoveUnload");
    /**
     * FireYamatoGun (orders.dat entry {@code 113}).
     */
    public static final @NotNull OrderType FIRE_YAMATO_GUN = create(113, "FireYamatoGun");
    /**
     * MoveToFireYamatoGun (orders.dat entry {@code 114}).
     */
    public static final @NotNull OrderType MOVE_TO_FIRE_YAMATO_GUN = create(114, "MoveToFireYamatoGun");
    /**
     * CastLockdown (orders.dat entry {@code 115}).
     */
    public static final @NotNull OrderType CAST_LOCKDOWN = create(115, "CastLockdown");
    /**
     * Burrowing (orders.dat entry {@code 116}).
     */
    public static final @NotNull OrderType BURROWING = create(116, "Burrowing");
    /**
     * Burrowed (orders.dat entry {@code 117}).
     */
    public static final @NotNull OrderType BURROWED = create(117, "Burrowed");
    /**
     * Unburrowing (orders.dat entry {@code 118}).
     */
    public static final @NotNull OrderType UNBURROWING = create(118, "Unburrowing");
    /**
     * CastDarkSwarm (orders.dat entry {@code 119}).
     */
    public static final @NotNull OrderType CAST_DARK_SWARM = create(119, "CastDarkSwarm");
    /**
     * CastParasite (orders.dat entry {@code 120}).
     */
    public static final @NotNull OrderType CAST_PARASITE = create(120, "CastParasite");
    /**
     * CastSpawnBroodlings (orders.dat entry {@code 121}).
     */
    public static final @NotNull OrderType CAST_SPAWN_BROODLINGS = create(121, "CastSpawnBroodlings");
    /**
     * CastEMPShockwave (orders.dat entry {@code 122}).
     */
    public static final @NotNull OrderType CAST_EMP_SHOCKWAVE = create(122, "CastEMPShockwave");
    /**
     * NukeWait (orders.dat entry {@code 123}).
     */
    public static final @NotNull OrderType NUKE_WAIT = create(123, "NukeWait");
    /**
     * NukeTrain (orders.dat entry {@code 124}).
     */
    public static final @NotNull OrderType NUKE_TRAIN = create(124, "NukeTrain");
    /**
     * NukeLaunch (orders.dat entry {@code 125}).
     */
    public static final @NotNull OrderType NUKE_LAUNCH = create(125, "NukeLaunch");
    /**
     * NukePaint (orders.dat entry {@code 126}).
     */
    public static final @NotNull OrderType NUKE_PAINT = create(126, "NukePaint");
    /**
     * NukeUnit (orders.dat entry {@code 127}).
     */
    public static final @NotNull OrderType NUKE_UNIT = create(127, "NukeUnit");
    /**
     * CastNuclearStrike (orders.dat entry {@code 128}).
     */
    public static final @NotNull OrderType CAST_NUCLEAR_STRIKE = create(128, "CastNuclearStrike");
    /**
     * NukeTrack (orders.dat entry {@code 129}).
     */
    public static final @NotNull OrderType NUKE_TRACK = create(129, "NukeTrack");
    /**
     * InitializeArbiter (orders.dat entry {@code 130}).
     */
    public static final @NotNull OrderType INITIALIZE_ARBITER = create(130, "InitializeArbiter");
    /**
     * CloakNearbyUnits (orders.dat entry {@code 131}).
     */
    public static final @NotNull OrderType CLOAK_NEARBY_UNITS = create(131, "CloakNearbyUnits");
    /**
     * PlaceMine (orders.dat entry {@code 132}).
     */
    public static final @NotNull OrderType PLACE_MINE = create(132, "PlaceMine");
    /**
     * RightClickAction (orders.dat entry {@code 133}).
     */
    public static final @NotNull OrderType RIGHT_CLICK_ACTION = create(133, "RightClickAction");
    /**
     * SuicideUnit (orders.dat entry {@code 134}).
     */
    public static final @NotNull OrderType SUICIDE_UNIT = create(134, "SuicideUnit");
    /**
     * SuicideLocation (orders.dat entry {@code 135}).
     */
    public static final @NotNull OrderType SUICIDE_LOCATION = create(135, "SuicideLocation");
    /**
     * SuicideHoldPosition (orders.dat entry {@code 136}).
     */
    public static final @NotNull OrderType SUICIDE_HOLD_POSITION = create(136, "SuicideHoldPosition");
    /**
     * CastRecall (orders.dat entry {@code 137}).
     */
    public static final @NotNull OrderType CAST_RECALL = create(137, "CastRecall");
    /**
     * Teleport (orders.dat entry {@code 138}).
     */
    public static final @NotNull OrderType TELEPORT = create(138, "Teleport");
    /**
     * CastScannerSweep (orders.dat entry {@code 139}).
     */
    public static final @NotNull OrderType CAST_SCANNER_SWEEP = create(139, "CastScannerSweep");
    /**
     * Scanner (orders.dat entry {@code 140}).
     */
    public static final @NotNull OrderType SCANNER = create(140, "Scanner");
    /**
     * CastDefensiveMatrix (orders.dat entry {@code 141}).
     */
    public static final @NotNull OrderType CAST_DEFENSIVE_MATRIX = create(141, "CastDefensiveMatrix");
    /**
     * CastPsionicStorm (orders.dat entry {@code 142}).
     */
    public static final @NotNull OrderType CAST_PSIONIC_STORM = create(142, "CastPsionicStorm");
    /**
     * CastIrradiate (orders.dat entry {@code 143}).
     */
    public static final @NotNull OrderType CAST_IRRADIATE = create(143, "CastIrradiate");
    /**
     * CastPlague (orders.dat entry {@code 144}).
     */
    public static final @NotNull OrderType CAST_PLAGUE = create(144, "CastPlague");
    /**
     * CastConsume (orders.dat entry {@code 145}).
     */
    public static final @NotNull OrderType CAST_CONSUME = create(145, "CastConsume");
    /**
     * CastEnsnare (orders.dat entry {@code 146}).
     */
    public static final @NotNull OrderType CAST_ENSNARE = create(146, "CastEnsnare");
    /**
     * CastStasisField (orders.dat entry {@code 147}).
     */
    public static final @NotNull OrderType CAST_STASIS_FIELD = create(147, "CastStasisField");
    /**
     * CastHallucination (orders.dat entry {@code 148}).
     */
    public static final @NotNull OrderType CAST_HALLUCINATION = create(148, "CastHallucination");
    /**
     * Hallucination2 (orders.dat entry {@code 149}).
     */
    public static final @NotNull OrderType HALLUCINATION2 = create(149, "Hallucination2");
    /**
     * ResetCollision (orders.dat entry {@code 150}).
     */
    public static final @NotNull OrderType RESET_COLLISION = create(150, "ResetCollision");
    /**
     * ResetHarvestCollision (orders.dat entry {@code 151}).
     */
    public static final @NotNull OrderType RESET_HARVEST_COLLISION = create(151, "ResetHarvestCollision");
    /**
     * Patrol (orders.dat entry {@code 152}).
     */
    public static final @NotNull OrderType PATROL = create(152, "Patrol");
    /**
     * CTFCOPInit (orders.dat entry {@code 153}).
     */
    public static final @NotNull OrderType CTFCOP_INIT = create(153, "CTFCOPInit");
    /**
     * CTFCOPStarted (orders.dat entry {@code 154}).
     */
    public static final @NotNull OrderType CTFCOP_STARTED = create(154, "CTFCOPStarted");
    /**
     * CTFCOP2 (orders.dat entry {@code 155}).
     */
    public static final @NotNull OrderType CTFCOP2 = create(155, "CTFCOP2");
    /**
     * ComputerAI (orders.dat entry {@code 156}).
     */
    public static final @NotNull OrderType COMPUTER_AI = create(156, "ComputerAI");
    /**
     * AtkMoveEP (orders.dat entry {@code 157}).
     */
    public static final @NotNull OrderType ATK_MOVE_EP = create(157, "AtkMoveEP");
    /**
     * HarassMove (orders.dat entry {@code 158}).
     */
    public static final @NotNull OrderType HARASS_MOVE = create(158, "HarassMove");
    /**
     * AIPatrol (orders.dat entry {@code 159}).
     */
    public static final @NotNull OrderType AI_PATROL = create(159, "AIPatrol");
    /**
     * GuardPost (orders.dat entry {@code 160}).
     */
    public static final @NotNull OrderType GUARD_POST = create(160, "GuardPost");
    /**
     * RescuePassive (orders.dat entry {@code 161}).
     */
    public static final @NotNull OrderType RESCUE_PASSIVE = create(161, "RescuePassive");
    /**
     * Neutral (orders.dat entry {@code 162}).
     */
    public static final @NotNull OrderType NEUTRAL = create(162, "Neutral");
    /**
     * ComputerReturn (orders.dat entry {@code 163}).
     */
    public static final @NotNull OrderType COMPUTER_RETURN = create(163, "ComputerReturn");
    /**
     * InitializePsiProvider (orders.dat entry {@code 164}).
     */
    public static final @NotNull OrderType INITIALIZE_PSI_PROVIDER = create(164, "InitializePsiProvider");
    /**
     * SelfDestructing (orders.dat entry {@code 165}).
     */
    public static final @NotNull OrderType SELF_DESTRUCTING = create(165, "SelfDestructing");
    /**
     * Critter (orders.dat entry {@code 166}).
     */
    public static final @NotNull OrderType CRITTER = create(166, "Critter");
    /**
     * HiddenGun (orders.dat entry {@code 167}).
     */
    public static final @NotNull OrderType HIDDEN_GUN = create(167, "HiddenGun");
    /**
     * OpenDoor (orders.dat entry {@code 168}).
     */
    public static final @NotNull OrderType OPEN_DOOR = create(168, "OpenDoor");
    /**
     * CloseDoor (orders.dat entry {@code 169}).
     */
    public static final @NotNull OrderType CLOSE_DOOR = create(169, "CloseDoor");
    /**
     * HideTrap (orders.dat entry {@code 170}).
     */
    public static final @NotNull OrderType HIDE_TRAP = create(170, "HideTrap");
    /**
     * RevealTrap (orders.dat entry {@code 171}).
     */
    public static final @NotNull OrderType REVEAL_TRAP = create(171, "RevealTrap");
    /**
     * EnableDoodad (orders.dat entry {@code 172}).
     */
    public static final @NotNull OrderType ENABLE_DOODAD = create(172, "EnableDoodad");
    /**
     * DisableDoodad (orders.dat entry {@code 173}).
     */
    public static final @NotNull OrderType DISABLE_DOODAD = create(173, "DisableDoodad");
    /**
     * WarpIn (orders.dat entry {@code 174}).
     */
    public static final @NotNull OrderType WARP_IN = create(174, "WarpIn");
    /**
     * MedicIdle (orders.dat entry {@code 175}).
     */
    public static final @NotNull OrderType MEDIC_IDLE = create(175, "MedicIdle");
    /**
     * MedicHeal (orders.dat entry {@code 176}).
     */
    public static final @NotNull OrderType MEDIC_HEAL = create(176, "MedicHeal");
    /**
     * HealMove (orders.dat entry {@code 177}).
     */
    public static final @NotNull OrderType HEAL_MOVE = create(177, "HealMove");
    /**
     * MedicHoldPosition (orders.dat entry {@code 178}).
     */
    public static final @NotNull OrderType MEDIC_HOLD_POSITION = create(178, "MedicHoldPosition");
    /**
     * MedicHealToIdle (orders.dat entry {@code 179}).
     */
    public static final @NotNull OrderType MEDIC_HEAL_TO_IDLE = create(179, "MedicHealToIdle");
    /**
     * CastRestoration (orders.dat entry {@code 180}).
     */
    public static final @NotNull OrderType CAST_RESTORATION = create(180, "CastRestoration");
    /**
     * CastDisruptionWeb (orders.dat entry {@code 181}).
     */
    public static final @NotNull OrderType CAST_DISRUPTION_WEB = create(181, "CastDisruptionWeb");
    /**
     * CastMindControl (orders.dat entry {@code 182}).
     */
    public static final @NotNull OrderType CAST_MIND_CONTROL = create(182, "CastMindControl");
    /**
     * DarkArchonMeld (orders.dat entry {@code 183}).
     */
    public static final @NotNull OrderType DARK_ARCHON_MELD = create(183, "DarkArchonMeld");
    /**
     * CastFeedback (orders.dat entry {@code 184}).
     */
    public static final @NotNull OrderType CAST_FEEDBACK = create(184, "CastFeedback");
    /**
     * CastOpticalFlare (orders.dat entry {@code 185}).
     */
    public static final @NotNull OrderType CAST_OPTICAL_FLARE = create(185, "CastOpticalFlare");
    /**
     * CastMaelstrom (orders.dat entry {@code 186}).
     */
    public static final @NotNull OrderType CAST_MAELSTROM = create(186, "CastMaelstrom");
    /**
     * JunkYardDog (orders.dat entry {@code 187}).
     */
    public static final @NotNull OrderType JUNK_YARD_DOG = create(187, "JunkYardDog");
    /**
     * Fatal (orders.dat entry {@code 188}).
     */
    public static final @NotNull OrderType FATAL = create(188, "Fatal");

    private static final @NotNull List<@NotNull OrderType> TYPE_LIST;

    static {
        TYPE_LIST = List.of(TYPES);
    }

    /**
     * Returns the identity of a orders.dat entry.
     *
     * @param nativeId the orders.dat entry, from {@code 0} to {@code COUNT - 1}
     * @return the identity
     * @throws IllegalArgumentException if the entry is outside the table
     */
    public static @NotNull OrderType get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid orders.dat entry: {0}", nativeId);
        }

        return TYPES[nativeId];
    }

    /**
     * @return every identity in orders.dat order, as the mod registers them
     */
    public static @NotNull List<@NotNull OrderType> getTypes() {
        return TYPE_LIST;
    }

    private static @NotNull OrderType create(int nativeId, @NotNull String debugName) {
        OrderType type = new OrderType(debugName);

        TYPES[nativeId] = type;

        return type;
    }


    private ScrOrders() {
    }
}
