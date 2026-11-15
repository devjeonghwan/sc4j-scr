package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;

/**
 * Holds the native order identifiers (orders.dat entries) of the SC:R mod as {@code int} constants: the values the SC:R
 * simulation stores and compares, and the values EUD, CHK sections, replays and saved games hold. {@link ScrOrders} holds the
 * engine identities; {@link ScrNativeIds} maps between the two.
 */
public final class ScrOrderIds {
    /**
     * Die (orders.dat entry {@code 0}).
     */
    public static final int DIE = 0;
    /**
     * Stop (orders.dat entry {@code 1}).
     */
    public static final int STOP = 1;
    /**
     * Guard (orders.dat entry {@code 2}).
     */
    public static final int GUARD = 2;
    /**
     * PlayerGuard (orders.dat entry {@code 3}).
     */
    public static final int PLAYER_GUARD = 3;
    /**
     * TurretGuard (orders.dat entry {@code 4}).
     */
    public static final int TURRET_GUARD = 4;
    /**
     * BunkerGuard (orders.dat entry {@code 5}).
     */
    public static final int BUNKER_GUARD = 5;
    /**
     * Move (orders.dat entry {@code 6}).
     */
    public static final int MOVE = 6;
    /**
     * ReaverStop (orders.dat entry {@code 7}).
     */
    public static final int REAVER_STOP = 7;
    /**
     * AttackDefault (orders.dat entry {@code 8}).
     */
    public static final int ATTACK_DEFAULT = 8;
    /**
     * MoveToAttack (orders.dat entry {@code 9}).
     */
    public static final int MOVE_TO_ATTACK = 9;
    /**
     * AttackUnit (orders.dat entry {@code 10}).
     */
    public static final int ATTACK_UNIT = 10;
    /**
     * AttackFixedRange (orders.dat entry {@code 11}).
     */
    public static final int ATTACK_FIXED_RANGE = 11;
    /**
     * AttackTile (orders.dat entry {@code 12}).
     */
    public static final int ATTACK_TILE = 12;
    /**
     * Hover (orders.dat entry {@code 13}).
     */
    public static final int HOVER = 13;
    /**
     * AttackMove (orders.dat entry {@code 14}).
     */
    public static final int ATTACK_MOVE = 14;
    /**
     * InfestedCommandCenter (orders.dat entry {@code 15}).
     */
    public static final int INFESTED_COMMAND_CENTER = 15;
    /**
     * UnusedNothing (orders.dat entry {@code 16}).
     */
    public static final int UNUSED_NOTHING = 16;
    /**
     * UnusedPowerup (orders.dat entry {@code 17}).
     */
    public static final int UNUSED_POWERUP = 17;
    /**
     * TowerGuard (orders.dat entry {@code 18}).
     */
    public static final int TOWER_GUARD = 18;
    /**
     * TowerAttack (orders.dat entry {@code 19}).
     */
    public static final int TOWER_ATTACK = 19;
    /**
     * SpiderMine (orders.dat entry {@code 20}).
     */
    public static final int SPIDER_MINE = 20;
    /**
     * StayInRange (orders.dat entry {@code 21}).
     */
    public static final int STAY_IN_RANGE = 21;
    /**
     * TurretAttack (orders.dat entry {@code 22}).
     */
    public static final int TURRET_ATTACK = 22;
    /**
     * Nothing (orders.dat entry {@code 23}).
     */
    public static final int NOTHING = 23;
    /**
     * NothingWait (orders.dat entry {@code 24}).
     */
    public static final int NOTHING_WAIT = 24;
    /**
     * DroneStartBuild (orders.dat entry {@code 25}).
     */
    public static final int DRONE_START_BUILD = 25;
    /**
     * DroneBuild (orders.dat entry {@code 26}).
     */
    public static final int DRONE_BUILD = 26;
    /**
     * CastInfestation (orders.dat entry {@code 27}).
     */
    public static final int CAST_INFESTATION = 27;
    /**
     * MoveToInfest (orders.dat entry {@code 28}).
     */
    public static final int MOVE_TO_INFEST = 28;
    /**
     * InfestingCommandCenter (orders.dat entry {@code 29}).
     */
    public static final int INFESTING_COMMAND_CENTER = 29;
    /**
     * PlaceBuilding (orders.dat entry {@code 30}).
     */
    public static final int PLACE_BUILDING = 30;
    /**
     * PlaceProtossBuilding (orders.dat entry {@code 31}).
     */
    public static final int PLACE_PROTOSS_BUILDING = 31;
    /**
     * CreateProtossBuilding (orders.dat entry {@code 32}).
     */
    public static final int CREATE_PROTOSS_BUILDING = 32;
    /**
     * ConstructingBuilding (orders.dat entry {@code 33}).
     */
    public static final int CONSTRUCTING_BUILDING = 33;
    /**
     * Repair (orders.dat entry {@code 34}).
     */
    public static final int REPAIR = 34;
    /**
     * MoveToRepair (orders.dat entry {@code 35}).
     */
    public static final int MOVE_TO_REPAIR = 35;
    /**
     * PlaceAddon (orders.dat entry {@code 36}).
     */
    public static final int PLACE_ADDON = 36;
    /**
     * BuildAddon (orders.dat entry {@code 37}).
     */
    public static final int BUILD_ADDON = 37;
    /**
     * Train (orders.dat entry {@code 38}).
     */
    public static final int TRAIN = 38;
    /**
     * RallyPointUnit (orders.dat entry {@code 39}).
     */
    public static final int RALLY_POINT_UNIT = 39;
    /**
     * RallyPointTile (orders.dat entry {@code 40}).
     */
    public static final int RALLY_POINT_TILE = 40;
    /**
     * ZergBirth (orders.dat entry {@code 41}).
     */
    public static final int ZERG_BIRTH = 41;
    /**
     * ZergUnitMorph (orders.dat entry {@code 42}).
     */
    public static final int ZERG_UNIT_MORPH = 42;
    /**
     * ZergBuildingMorph (orders.dat entry {@code 43}).
     */
    public static final int ZERG_BUILDING_MORPH = 43;
    /**
     * IncompleteBuilding (orders.dat entry {@code 44}).
     */
    public static final int INCOMPLETE_BUILDING = 44;
    /**
     * IncompleteMorphing (orders.dat entry {@code 45}).
     */
    public static final int INCOMPLETE_MORPHING = 45;
    /**
     * BuildNydusExit (orders.dat entry {@code 46}).
     */
    public static final int BUILD_NYDUS_EXIT = 46;
    /**
     * EnterNydusCanal (orders.dat entry {@code 47}).
     */
    public static final int ENTER_NYDUS_CANAL = 47;
    /**
     * IncompleteWarping (orders.dat entry {@code 48}).
     */
    public static final int INCOMPLETE_WARPING = 48;
    /**
     * Follow (orders.dat entry {@code 49}).
     */
    public static final int FOLLOW = 49;
    /**
     * Carrier (orders.dat entry {@code 50}).
     */
    public static final int CARRIER = 50;
    /**
     * ReaverCarrierMove (orders.dat entry {@code 51}).
     */
    public static final int REAVER_CARRIER_MOVE = 51;
    /**
     * CarrierStop (orders.dat entry {@code 52}).
     */
    public static final int CARRIER_STOP = 52;
    /**
     * CarrierAttack (orders.dat entry {@code 53}).
     */
    public static final int CARRIER_ATTACK = 53;
    /**
     * CarrierMoveToAttack (orders.dat entry {@code 54}).
     */
    public static final int CARRIER_MOVE_TO_ATTACK = 54;
    /**
     * CarrierIgnore2 (orders.dat entry {@code 55}).
     */
    public static final int CARRIER_IGNORE2 = 55;
    /**
     * CarrierFight (orders.dat entry {@code 56}).
     */
    public static final int CARRIER_FIGHT = 56;
    /**
     * CarrierHoldPosition (orders.dat entry {@code 57}).
     */
    public static final int CARRIER_HOLD_POSITION = 57;
    /**
     * Reaver (orders.dat entry {@code 58}).
     */
    public static final int REAVER = 58;
    /**
     * ReaverAttack (orders.dat entry {@code 59}).
     */
    public static final int REAVER_ATTACK = 59;
    /**
     * ReaverMoveToAttack (orders.dat entry {@code 60}).
     */
    public static final int REAVER_MOVE_TO_ATTACK = 60;
    /**
     * ReaverFight (orders.dat entry {@code 61}).
     */
    public static final int REAVER_FIGHT = 61;
    /**
     * ReaverHoldPosition (orders.dat entry {@code 62}).
     */
    public static final int REAVER_HOLD_POSITION = 62;
    /**
     * TrainFighter (orders.dat entry {@code 63}).
     */
    public static final int TRAIN_FIGHTER = 63;
    /**
     * InterceptorAttack (orders.dat entry {@code 64}).
     */
    public static final int INTERCEPTOR_ATTACK = 64;
    /**
     * ScarabAttack (orders.dat entry {@code 65}).
     */
    public static final int SCARAB_ATTACK = 65;
    /**
     * RechargeShieldsUnit (orders.dat entry {@code 66}).
     */
    public static final int RECHARGE_SHIELDS_UNIT = 66;
    /**
     * RechargeShieldsBattery (orders.dat entry {@code 67}).
     */
    public static final int RECHARGE_SHIELDS_BATTERY = 67;
    /**
     * ShieldBattery (orders.dat entry {@code 68}).
     */
    public static final int SHIELD_BATTERY = 68;
    /**
     * InterceptorReturn (orders.dat entry {@code 69}).
     */
    public static final int INTERCEPTOR_RETURN = 69;
    /**
     * DroneLand (orders.dat entry {@code 70}).
     */
    public static final int DRONE_LAND = 70;
    /**
     * BuildingLand (orders.dat entry {@code 71}).
     */
    public static final int BUILDING_LAND = 71;
    /**
     * BuildingLiftoff (orders.dat entry {@code 72}).
     */
    public static final int BUILDING_LIFTOFF = 72;
    /**
     * DroneLiftOff (orders.dat entry {@code 73}).
     */
    public static final int DRONE_LIFT_OFF = 73;
    /**
     * LiftingOff (orders.dat entry {@code 74}).
     */
    public static final int LIFTING_OFF = 74;
    /**
     * ResearchTech (orders.dat entry {@code 75}).
     */
    public static final int RESEARCH_TECH = 75;
    /**
     * Upgrade (orders.dat entry {@code 76}).
     */
    public static final int UPGRADE = 76;
    /**
     * Larva (orders.dat entry {@code 77}).
     */
    public static final int LARVA = 77;
    /**
     * SpawningLarva (orders.dat entry {@code 78}).
     */
    public static final int SPAWNING_LARVA = 78;
    /**
     * Harvest1 (orders.dat entry {@code 79}).
     */
    public static final int HARVEST1 = 79;
    /**
     * Harvest2 (orders.dat entry {@code 80}).
     */
    public static final int HARVEST2 = 80;
    /**
     * MoveToGas (orders.dat entry {@code 81}).
     */
    public static final int MOVE_TO_GAS = 81;
    /**
     * WaitForGas (orders.dat entry {@code 82}).
     */
    public static final int WAIT_FOR_GAS = 82;
    /**
     * HarvestGas (orders.dat entry {@code 83}).
     */
    public static final int HARVEST_GAS = 83;
    /**
     * ReturnGas (orders.dat entry {@code 84}).
     */
    public static final int RETURN_GAS = 84;
    /**
     * MoveToMinerals (orders.dat entry {@code 85}).
     */
    public static final int MOVE_TO_MINERALS = 85;
    /**
     * WaitForMinerals (orders.dat entry {@code 86}).
     */
    public static final int WAIT_FOR_MINERALS = 86;
    /**
     * MiningMinerals (orders.dat entry {@code 87}).
     */
    public static final int MINING_MINERALS = 87;
    /**
     * GatheringInterrupted (orders.dat entry {@code 88}).
     */
    public static final int GATHERING_INTERRUPTED = 88;
    /**
     * GatherWaitInterrupted (orders.dat entry {@code 89}).
     */
    public static final int GATHER_WAIT_INTERRUPTED = 89;
    /**
     * ReturnMinerals (orders.dat entry {@code 90}).
     */
    public static final int RETURN_MINERALS = 90;
    /**
     * RechargeShieldsUnitRemoveOverlay (orders.dat entry {@code 91}).
     */
    public static final int RECHARGE_SHIELDS_UNIT_REMOVE_OVERLAY = 91;
    /**
     * EnterTransport (orders.dat entry {@code 92}).
     */
    public static final int ENTER_TRANSPORT = 92;
    /**
     * PickupIdle (orders.dat entry {@code 93}).
     */
    public static final int PICKUP_IDLE = 93;
    /**
     * PickupTransport (orders.dat entry {@code 94}).
     */
    public static final int PICKUP_TRANSPORT = 94;
    /**
     * PickupBunker (orders.dat entry {@code 95}).
     */
    public static final int PICKUP_BUNKER = 95;
    /**
     * Pickup4 (orders.dat entry {@code 96}).
     */
    public static final int PICKUP4 = 96;
    /**
     * PowerupIdle (orders.dat entry {@code 97}).
     */
    public static final int POWERUP_IDLE = 97;
    /**
     * Sieging (orders.dat entry {@code 98}).
     */
    public static final int SIEGING = 98;
    /**
     * Unsieging (orders.dat entry {@code 99}).
     */
    public static final int UNSIEGING = 99;
    /**
     * WatchTarget (orders.dat entry {@code 100}).
     */
    public static final int WATCH_TARGET = 100;
    /**
     * InitCreepGrowth (orders.dat entry {@code 101}).
     */
    public static final int INIT_CREEP_GROWTH = 101;
    /**
     * SpreadCreep (orders.dat entry {@code 102}).
     */
    public static final int SPREAD_CREEP = 102;
    /**
     * StoppingCreepGrowth (orders.dat entry {@code 103}).
     */
    public static final int STOPPING_CREEP_GROWTH = 103;
    /**
     * GuardianAspect (orders.dat entry {@code 104}).
     */
    public static final int GUARDIAN_ASPECT = 104;
    /**
     * ArchonWarp (orders.dat entry {@code 105}).
     */
    public static final int ARCHON_WARP = 105;
    /**
     * CompletingArchonSummon (orders.dat entry {@code 106}).
     */
    public static final int COMPLETING_ARCHON_SUMMON = 106;
    /**
     * HoldPosition (orders.dat entry {@code 107}).
     */
    public static final int HOLD_POSITION = 107;
    /**
     * QueenHoldPosition (orders.dat entry {@code 108}).
     */
    public static final int QUEEN_HOLD_POSITION = 108;
    /**
     * Cloak (orders.dat entry {@code 109}).
     */
    public static final int CLOAK = 109;
    /**
     * Decloak (orders.dat entry {@code 110}).
     */
    public static final int DECLOAK = 110;
    /**
     * Unload (orders.dat entry {@code 111}).
     */
    public static final int UNLOAD = 111;
    /**
     * MoveUnload (orders.dat entry {@code 112}).
     */
    public static final int MOVE_UNLOAD = 112;
    /**
     * FireYamatoGun (orders.dat entry {@code 113}).
     */
    public static final int FIRE_YAMATO_GUN = 113;
    /**
     * MoveToFireYamatoGun (orders.dat entry {@code 114}).
     */
    public static final int MOVE_TO_FIRE_YAMATO_GUN = 114;
    /**
     * CastLockdown (orders.dat entry {@code 115}).
     */
    public static final int CAST_LOCKDOWN = 115;
    /**
     * Burrowing (orders.dat entry {@code 116}).
     */
    public static final int BURROWING = 116;
    /**
     * Burrowed (orders.dat entry {@code 117}).
     */
    public static final int BURROWED = 117;
    /**
     * Unburrowing (orders.dat entry {@code 118}).
     */
    public static final int UNBURROWING = 118;
    /**
     * CastDarkSwarm (orders.dat entry {@code 119}).
     */
    public static final int CAST_DARK_SWARM = 119;
    /**
     * CastParasite (orders.dat entry {@code 120}).
     */
    public static final int CAST_PARASITE = 120;
    /**
     * CastSpawnBroodlings (orders.dat entry {@code 121}).
     */
    public static final int CAST_SPAWN_BROODLINGS = 121;
    /**
     * CastEMPShockwave (orders.dat entry {@code 122}).
     */
    public static final int CAST_EMP_SHOCKWAVE = 122;
    /**
     * NukeWait (orders.dat entry {@code 123}).
     */
    public static final int NUKE_WAIT = 123;
    /**
     * NukeTrain (orders.dat entry {@code 124}).
     */
    public static final int NUKE_TRAIN = 124;
    /**
     * NukeLaunch (orders.dat entry {@code 125}).
     */
    public static final int NUKE_LAUNCH = 125;
    /**
     * NukePaint (orders.dat entry {@code 126}).
     */
    public static final int NUKE_PAINT = 126;
    /**
     * NukeUnit (orders.dat entry {@code 127}).
     */
    public static final int NUKE_UNIT = 127;
    /**
     * CastNuclearStrike (orders.dat entry {@code 128}).
     */
    public static final int CAST_NUCLEAR_STRIKE = 128;
    /**
     * NukeTrack (orders.dat entry {@code 129}).
     */
    public static final int NUKE_TRACK = 129;
    /**
     * InitializeArbiter (orders.dat entry {@code 130}).
     */
    public static final int INITIALIZE_ARBITER = 130;
    /**
     * CloakNearbyUnits (orders.dat entry {@code 131}).
     */
    public static final int CLOAK_NEARBY_UNITS = 131;
    /**
     * PlaceMine (orders.dat entry {@code 132}).
     */
    public static final int PLACE_MINE = 132;
    /**
     * RightClickAction (orders.dat entry {@code 133}).
     */
    public static final int RIGHT_CLICK_ACTION = 133;
    /**
     * SuicideUnit (orders.dat entry {@code 134}).
     */
    public static final int SUICIDE_UNIT = 134;
    /**
     * SuicideLocation (orders.dat entry {@code 135}).
     */
    public static final int SUICIDE_LOCATION = 135;
    /**
     * SuicideHoldPosition (orders.dat entry {@code 136}).
     */
    public static final int SUICIDE_HOLD_POSITION = 136;
    /**
     * CastRecall (orders.dat entry {@code 137}).
     */
    public static final int CAST_RECALL = 137;
    /**
     * Teleport (orders.dat entry {@code 138}).
     */
    public static final int TELEPORT = 138;
    /**
     * CastScannerSweep (orders.dat entry {@code 139}).
     */
    public static final int CAST_SCANNER_SWEEP = 139;
    /**
     * Scanner (orders.dat entry {@code 140}).
     */
    public static final int SCANNER = 140;
    /**
     * CastDefensiveMatrix (orders.dat entry {@code 141}).
     */
    public static final int CAST_DEFENSIVE_MATRIX = 141;
    /**
     * CastPsionicStorm (orders.dat entry {@code 142}).
     */
    public static final int CAST_PSIONIC_STORM = 142;
    /**
     * CastIrradiate (orders.dat entry {@code 143}).
     */
    public static final int CAST_IRRADIATE = 143;
    /**
     * CastPlague (orders.dat entry {@code 144}).
     */
    public static final int CAST_PLAGUE = 144;
    /**
     * CastConsume (orders.dat entry {@code 145}).
     */
    public static final int CAST_CONSUME = 145;
    /**
     * CastEnsnare (orders.dat entry {@code 146}).
     */
    public static final int CAST_ENSNARE = 146;
    /**
     * CastStasisField (orders.dat entry {@code 147}).
     */
    public static final int CAST_STASIS_FIELD = 147;
    /**
     * CastHallucination (orders.dat entry {@code 148}).
     */
    public static final int CAST_HALLUCINATION = 148;
    /**
     * Hallucination2 (orders.dat entry {@code 149}).
     */
    public static final int HALLUCINATION2 = 149;
    /**
     * ResetCollision (orders.dat entry {@code 150}).
     */
    public static final int RESET_COLLISION = 150;
    /**
     * ResetHarvestCollision (orders.dat entry {@code 151}).
     */
    public static final int RESET_HARVEST_COLLISION = 151;
    /**
     * Patrol (orders.dat entry {@code 152}).
     */
    public static final int PATROL = 152;
    /**
     * CTFCOPInit (orders.dat entry {@code 153}).
     */
    public static final int CTFCOP_INIT = 153;
    /**
     * CTFCOPStarted (orders.dat entry {@code 154}).
     */
    public static final int CTFCOP_STARTED = 154;
    /**
     * CTFCOP2 (orders.dat entry {@code 155}).
     */
    public static final int CTFCOP2 = 155;
    /**
     * ComputerAI (orders.dat entry {@code 156}).
     */
    public static final int COMPUTER_AI = 156;
    /**
     * AtkMoveEP (orders.dat entry {@code 157}).
     */
    public static final int ATK_MOVE_EP = 157;
    /**
     * HarassMove (orders.dat entry {@code 158}).
     */
    public static final int HARASS_MOVE = 158;
    /**
     * AIPatrol (orders.dat entry {@code 159}).
     */
    public static final int AI_PATROL = 159;
    /**
     * GuardPost (orders.dat entry {@code 160}).
     */
    public static final int GUARD_POST = 160;
    /**
     * RescuePassive (orders.dat entry {@code 161}).
     */
    public static final int RESCUE_PASSIVE = 161;
    /**
     * Neutral (orders.dat entry {@code 162}).
     */
    public static final int NEUTRAL = 162;
    /**
     * ComputerReturn (orders.dat entry {@code 163}).
     */
    public static final int COMPUTER_RETURN = 163;
    /**
     * InitializePsiProvider (orders.dat entry {@code 164}).
     */
    public static final int INITIALIZE_PSI_PROVIDER = 164;
    /**
     * SelfDestructing (orders.dat entry {@code 165}).
     */
    public static final int SELF_DESTRUCTING = 165;
    /**
     * Critter (orders.dat entry {@code 166}).
     */
    public static final int CRITTER = 166;
    /**
     * HiddenGun (orders.dat entry {@code 167}).
     */
    public static final int HIDDEN_GUN = 167;
    /**
     * OpenDoor (orders.dat entry {@code 168}).
     */
    public static final int OPEN_DOOR = 168;
    /**
     * CloseDoor (orders.dat entry {@code 169}).
     */
    public static final int CLOSE_DOOR = 169;
    /**
     * HideTrap (orders.dat entry {@code 170}).
     */
    public static final int HIDE_TRAP = 170;
    /**
     * RevealTrap (orders.dat entry {@code 171}).
     */
    public static final int REVEAL_TRAP = 171;
    /**
     * EnableDoodad (orders.dat entry {@code 172}).
     */
    public static final int ENABLE_DOODAD = 172;
    /**
     * DisableDoodad (orders.dat entry {@code 173}).
     */
    public static final int DISABLE_DOODAD = 173;
    /**
     * WarpIn (orders.dat entry {@code 174}).
     */
    public static final int WARP_IN = 174;
    /**
     * MedicIdle (orders.dat entry {@code 175}).
     */
    public static final int MEDIC_IDLE = 175;
    /**
     * MedicHeal (orders.dat entry {@code 176}).
     */
    public static final int MEDIC_HEAL = 176;
    /**
     * HealMove (orders.dat entry {@code 177}).
     */
    public static final int HEAL_MOVE = 177;
    /**
     * MedicHoldPosition (orders.dat entry {@code 178}).
     */
    public static final int MEDIC_HOLD_POSITION = 178;
    /**
     * MedicHealToIdle (orders.dat entry {@code 179}).
     */
    public static final int MEDIC_HEAL_TO_IDLE = 179;
    /**
     * CastRestoration (orders.dat entry {@code 180}).
     */
    public static final int CAST_RESTORATION = 180;
    /**
     * CastDisruptionWeb (orders.dat entry {@code 181}).
     */
    public static final int CAST_DISRUPTION_WEB = 181;
    /**
     * CastMindControl (orders.dat entry {@code 182}).
     */
    public static final int CAST_MIND_CONTROL = 182;
    /**
     * DarkArchonMeld (orders.dat entry {@code 183}).
     */
    public static final int DARK_ARCHON_MELD = 183;
    /**
     * CastFeedback (orders.dat entry {@code 184}).
     */
    public static final int CAST_FEEDBACK = 184;
    /**
     * CastOpticalFlare (orders.dat entry {@code 185}).
     */
    public static final int CAST_OPTICAL_FLARE = 185;
    /**
     * CastMaelstrom (orders.dat entry {@code 186}).
     */
    public static final int CAST_MAELSTROM = 186;
    /**
     * JunkYardDog (orders.dat entry {@code 187}).
     */
    public static final int JUNK_YARD_DOG = 187;
    /**
     * Fatal (orders.dat entry {@code 188}).
     */
    public static final int FATAL = 188;
    /**
     * The "none" sentinel index ({@code 189}) that follows the orders.dat entries.
     */
    public static final int NONE = 189;

    /**
     * The number of values, from {@code 0} through {@link #NONE}.
     */
    private static final int VALUE_COUNT = 190;

    /**
     * @param identifier the value to check
     * @return {@code true} if the value is a order identifier, {@code false} otherwise
     */
    public static boolean isValid(int identifier) {
        return identifier >= 0 && identifier < VALUE_COUNT;
    }

    /**
     * Checks that a value is a order identifier.
     *
     * @param identifier the value to check
     * @return the value
     * @throws IllegalArgumentException if the value is not a order identifier
     */
    public static int requireValid(int identifier) {
        if (!isValid(identifier)) {
            throw Exceptions.newIllegalArgumentException("Invalid order type index: {0}", identifier);
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


    private ScrOrderIds() {
    }
}
