package org.fogofwarlabs.sc4j.scr.game.ai;

/**
 * Names the opcodes of computer AI scripts ({@code aiscript.bin} and {@code bwscript.bin}).
 */
public final class ComputerScriptOpcodes {
    /**
     * {@code goto}.
     */
    public static final int GOTO = 0x00;
    /**
     * {@code notowns_jump}.
     */
    public static final int NOTOWNS_JUMP = 0x01;
    /**
     * {@code wait}.
     */
    public static final int WAIT = 0x02;
    /**
     * {@code start_town}.
     */
    public static final int START_TOWN = 0x03;
    /**
     * {@code start_areatown}.
     */
    public static final int START_AREATOWN = 0x04;
    /**
     * {@code expand}.
     */
    public static final int EXPAND = 0x05;
    /**
     * {@code build}.
     */
    public static final int BUILD = 0x06;
    /**
     * {@code upgrade}.
     */
    public static final int UPGRADE = 0x07;
    /**
     * {@code tech}.
     */
    public static final int TECH = 0x08;
    /**
     * {@code wait_build}.
     */
    public static final int WAIT_BUILD = 0x09;
    /**
     * {@code wait_buildstart}.
     */
    public static final int WAIT_BUILDSTART = 0x0A;
    /**
     * {@code attack_clear}.
     */
    public static final int ATTACK_CLEAR = 0x0B;
    /**
     * {@code attack_add}.
     */
    public static final int ATTACK_ADD = 0x0C;
    /**
     * {@code attack_prepare}.
     */
    public static final int ATTACK_PREPARE = 0x0D;
    /**
     * {@code attack_do}.
     */
    public static final int ATTACK_DO = 0x0E;
    /**
     * {@code wait_secure}.
     */
    public static final int WAIT_SECURE = 0x0F;
    /**
     * {@code capt_expand}.
     */
    public static final int CAPT_EXPAND = 0x10;
    /**
     * {@code build_bunkers}.
     */
    public static final int BUILD_BUNKERS = 0x11;
    /**
     * {@code wait_bunkers}.
     */
    public static final int WAIT_BUNKERS = 0x12;
    /**
     * {@code defensebuild_gg}.
     */
    public static final int DEFENSEBUILD_GG = 0x13;
    /**
     * {@code defensebuild_ag}.
     */
    public static final int DEFENSEBUILD_AG = 0x14;
    /**
     * {@code defensebuild_ga}.
     */
    public static final int DEFENSEBUILD_GA = 0x15;
    /**
     * {@code defensebuild_aa}.
     */
    public static final int DEFENSEBUILD_AA = 0x16;
    /**
     * {@code defenseuse_gg}.
     */
    public static final int DEFENSEUSE_GG = 0x17;
    /**
     * {@code defenseuse_ag}.
     */
    public static final int DEFENSEUSE_AG = 0x18;
    /**
     * {@code defenseuse_ga}.
     */
    public static final int DEFENSEUSE_GA = 0x19;
    /**
     * {@code defenseuse_aa}.
     */
    public static final int DEFENSEUSE_AA = 0x1A;
    /**
     * {@code defenseclear_gg}.
     */
    public static final int DEFENSECLEAR_GG = 0x1B;
    /**
     * {@code defenseclear_ag}.
     */
    public static final int DEFENSECLEAR_AG = 0x1C;
    /**
     * {@code defenseclear_ga}.
     */
    public static final int DEFENSECLEAR_GA = 0x1D;
    /**
     * {@code defenseclear_aa}.
     */
    public static final int DEFENSECLEAR_AA = 0x1E;
    /**
     * {@code send_suicide}.
     */
    public static final int SEND_SUICIDE = 0x1F;
    /**
     * {@code player_enemy}.
     */
    public static final int PLAYER_ENEMY = 0x20;
    /**
     * {@code player_ally}.
     */
    public static final int PLAYER_ALLY = 0x21;
    /**
     * {@code default_min}.
     */
    public static final int DEFAULT_MIN = 0x22;
    /**
     * {@code defaultbuild_off}.
     */
    public static final int DEFAULTBUILD_OFF = 0x23;
    /**
     * {@code stop}.
     */
    public static final int STOP = 0x24;
    /**
     * {@code switch_rescue}.
     */
    public static final int SWITCH_RESCUE = 0x25;
    /**
     * {@code move_dt}.
     */
    public static final int MOVE_DT = 0x26;
    /**
     * {@code debug}.
     */
    public static final int DEBUG = 0x27;
    /**
     * {@code fatal_error}.
     */
    public static final int FATAL_ERROR = 0x28;
    /**
     * {@code enter_bunker}.
     */
    public static final int ENTER_BUNKER = 0x29;
    /**
     * {@code value_area}.
     */
    public static final int VALUE_AREA = 0x2A;
    /**
     * {@code transports_off}.
     */
    public static final int TRANSPORTS_OFF = 0x2B;
    /**
     * {@code check_transports}.
     */
    public static final int CHECK_TRANSPORTS = 0x2C;
    /**
     * {@code nuke_rate}.
     */
    public static final int NUKE_RATE = 0x2D;
    /**
     * {@code max_force}.
     */
    public static final int MAX_FORCE = 0x2E;
    /**
     * {@code clear_combatdata}.
     */
    public static final int CLEAR_COMBATDATA = 0x2F;
    /**
     * {@code random_jump}.
     */
    public static final int RANDOM_JUMP = 0x30;
    /**
     * {@code time_jump}.
     */
    public static final int TIME_JUMP = 0x31;
    /**
     * {@code farms_notiming}.
     */
    public static final int FARMS_NOTIMING = 0x32;
    /**
     * {@code farms_timing}.
     */
    public static final int FARMS_TIMING = 0x33;
    /**
     * {@code build_turrets}.
     */
    public static final int BUILD_TURRETS = 0x34;
    /**
     * {@code wait_turrets}.
     */
    public static final int WAIT_TURRETS = 0x35;
    /**
     * {@code default_build}.
     */
    public static final int DEFAULT_BUILD = 0x36;
    /**
     * {@code harass_factor}.
     */
    public static final int HARASS_FACTOR = 0x37;
    /**
     * {@code start_campaign}.
     */
    public static final int START_CAMPAIGN = 0x38;
    /**
     * {@code race_jump}.
     */
    public static final int RACE_JUMP = 0x39;
    /**
     * {@code region_size}.
     */
    public static final int REGION_SIZE = 0x3A;
    /**
     * {@code get_oldpeons}.
     */
    public static final int GET_OLDPEONS = 0x3B;
    /**
     * {@code groundmap_jump}.
     */
    public static final int GROUNDMAP_JUMP = 0x3C;
    /**
     * {@code place_guard}.
     */
    public static final int PLACE_GUARD = 0x3D;
    /**
     * {@code wait_force}.
     */
    public static final int WAIT_FORCE = 0x3E;
    /**
     * {@code guard_resources}.
     */
    public static final int GUARD_RESOURCES = 0x3F;
    /**
     * {@code call}.
     */
    public static final int CALL = 0x40;
    /**
     * {@code return}.
     */
    public static final int RETURN = 0x41;
    /**
     * {@code eval_harass}.
     */
    public static final int EVAL_HARASS = 0x42;
    /**
     * {@code creep}.
     */
    public static final int CREEP = 0x43;
    /**
     * {@code panic}.
     */
    public static final int PANIC = 0x44;
    /**
     * {@code player_need}.
     */
    public static final int PLAYER_NEED = 0x45;
    /**
     * {@code do_morph}.
     */
    public static final int DO_MORPH = 0x46;
    /**
     * {@code wait_upgrades}.
     */
    public static final int WAIT_UPGRADES = 0x47;
    /**
     * {@code multirun}.
     */
    public static final int MULTIRUN = 0x48;
    /**
     * {@code rush}.
     */
    public static final int RUSH = 0x49;
    /**
     * {@code scout_with}.
     */
    public static final int SCOUT_WITH = 0x4A;
    /**
     * {@code define_max}.
     */
    public static final int DEFINE_MAX = 0x4B;
    /**
     * {@code train}.
     */
    public static final int TRAIN = 0x4C;
    /**
     * {@code target_expansion}.
     */
    public static final int TARGET_EXPANSION = 0x4D;
    /**
     * {@code wait_train}.
     */
    public static final int WAIT_TRAIN = 0x4E;
    /**
     * {@code set_attacks}.
     */
    public static final int SET_ATTACKS = 0x4F;
    /**
     * {@code set_gencmd}.
     */
    public static final int SET_GENCMD = 0x50;
    /**
     * {@code make_patrol}.
     */
    public static final int MAKE_PATROL = 0x51;
    /**
     * {@code give_money}.
     */
    public static final int GIVE_MONEY = 0x52;
    /**
     * {@code prep_down}.
     */
    public static final int PREP_DOWN = 0x53;
    /**
     * {@code resources_jump}.
     */
    public static final int RESOURCES_JUMP = 0x54;
    /**
     * {@code enter_transport}.
     */
    public static final int ENTER_TRANSPORT = 0x55;
    /**
     * {@code exit_transport}.
     */
    public static final int EXIT_TRANSPORT = 0x56;
    /**
     * {@code sharedvision_on}.
     */
    public static final int SHAREDVISION_ON = 0x57;
    /**
     * {@code sharedvision_off}.
     */
    public static final int SHAREDVISION_OFF = 0x58;
    /**
     * {@code nuke_location}.
     */
    public static final int NUKE_LOCATION = 0x59;
    /**
     * {@code harass_location}.
     */
    public static final int HARASS_LOCATION = 0x5A;
    /**
     * {@code implode}.
     */
    public static final int IMPLODE = 0x5B;
    /**
     * {@code guard_all}.
     */
    public static final int GUARD_ALL = 0x5C;
    /**
     * {@code enemyowns_jump}.
     */
    public static final int ENEMYOWNS_JUMP = 0x5D;
    /**
     * {@code enemyresources_jump}.
     */
    public static final int ENEMYRESOURCES_JUMP = 0x5E;
    /**
     * {@code if_dif}.
     */
    public static final int IF_DIF = 0x5F;
    /**
     * {@code easy_attack}.
     */
    public static final int EASY_ATTACK = 0x60;
    /**
     * {@code kill_thread}.
     */
    public static final int KILL_THREAD = 0x61;
    /**
     * {@code killable}.
     */
    public static final int KILLABLE = 0x62;
    /**
     * {@code wait_finishattack}.
     */
    public static final int WAIT_FINISHATTACK = 0x63;
    /**
     * {@code quick_attack}.
     */
    public static final int QUICK_ATTACK = 0x64;
    /**
     * {@code junkyard_dog}.
     */
    public static final int JUNKYARD_DOG = 0x65;
    /**
     * {@code fake_nuke}.
     */
    public static final int FAKE_NUKE = 0x66;
    /**
     * {@code disruption_web}.
     */
    public static final int DISRUPTION_WEB = 0x67;
    /**
     * {@code recall_location}.
     */
    public static final int RECALL_LOCATION = 0x68;
    /**
     * {@code set_randomseed}.
     */
    public static final int SET_RANDOMSEED = 0x69;
    /**
     * {@code if_owned}.
     */
    public static final int IF_OWNED = 0x6A;
    /**
     * {@code create_nuke}.
     */
    public static final int CREATE_NUKE = 0x6B;
    /**
     * {@code create_unit}.
     */
    public static final int CREATE_UNIT = 0x6C;
    /**
     * {@code nuke_pos}.
     */
    public static final int NUKE_POS = 0x6D;
    /**
     * {@code help_iftrouble}.
     */
    public static final int HELP_IFTROUBLE = 0x6E;
    /**
     * {@code allies_watch}.
     */
    public static final int ALLIES_WATCH = 0x6F;
    /**
     * {@code try_townpoint}.
     */
    public static final int TRY_TOWNPOINT = 0x70;
    /**
     * The number of supported opcodes; higher opcode values are rejected.
     */
    public static final int COUNT = 0x71;

    private ComputerScriptOpcodes() {
    }
}
