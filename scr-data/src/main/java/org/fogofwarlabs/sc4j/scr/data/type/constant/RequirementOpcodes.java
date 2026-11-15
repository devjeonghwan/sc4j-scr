package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names the opcode words of the SC:R requirement scripts: the order, technology-use, research, upgrade and unit production scripts, and the
 * production script of an added unit type. An entry is a sequence of groups that the word {@link #END} closes; a word below {@link #OR} names a
 * unit type, an opcode tests the unit or the player, and {@link #OR} joins the alternatives of a group. Not every opcode is valid in every script.
 */
public final class RequirementOpcodes {
    /**
     * The opcode that joins the alternatives of a requirement group.
     */
    public static final int OR = 0xFF01;
    /**
     * The opcode followed by a unit type the unit (or producer) must be; the owner having one completed also counts.
     */
    public static final int CURRENT_UNIT = 0xFF02;
    /**
     * The opcode followed by a unit type the owner must have, completed or not.
     */
    public static final int MUST_HAVE = 0xFF03;
    /**
     * The production opcode followed by the add-on type the producer must have attached.
     */
    public static final int ADDON = 0xFF04;
    /**
     * The opcode that requires a landed building.
     */
    public static final int LANDED = 0xFF05;
    /**
     * The opcode that requires a lifted building.
     */
    public static final int LIFTED = 0xFF06;
    /**
     * The opcode that requires the unit not to be training or morphing, researching or upgrading.
     */
    public static final int NOT_BUSY = 0xFF07;
    /**
     * The opcode that requires the unit not to be building an add-on or placing a Protoss building.
     */
    public static final int NOT_BUILDING_ADDON = 0xFF08;
    /**
     * The opcode that requires the unit not to be researching.
     */
    public static final int NOT_RESEARCHING = 0xFF09;
    /**
     * The opcode that requires the unit not to be upgrading.
     */
    public static final int NOT_UPGRADING = 0xFF0A;
    /**
     * The production opcode that requires an SCV not constructing.
     */
    public static final int NOT_CONSTRUCTING = 0xFF0B;
    /**
     * The production opcode that requires a producer without an add-on.
     */
    public static final int NO_ADDON = 0xFF0C;
    /**
     * The opcode that requires the unit field shared by the Nuclear Silo missile and the Nydus Canal exit to be empty.
     */
    public static final int NO_LINKED_UNIT = 0xFF0D;
    /**
     * The production opcode that requires room in the producer's hangar.
     */
    public static final int HANGAR_ROOM = 0xFF0E;
    /**
     * The opcode that counts a researched technology (the tested one).
     */
    public static final int RESEARCHED = 0xFF0F;
    /**
     * The production opcode that requires an empty Nuclear Silo.
     */
    public static final int SILO_EMPTY = 0xFF10;
    /**
     * The opcode that requires the unit not to be burrowed.
     */
    public static final int NOT_BURROWED = 0xFF11;
    /**
     * The opcode that requires the unit not to be a landed building.
     */
    public static final int NOT_LANDED_BUILDING = 0xFF12;
    /**
     * The opcode that requires the unit to be a landed building.
     */
    public static final int LANDED_BUILDING = 0xFF13;
    /**
     * The opcode that requires a unit that can move.
     */
    public static final int CAN_MOVE = 0xFF14;
    /**
     * The opcode that requires a unit that can attack.
     */
    public static final int CAN_ATTACK = 0xFF15;
    /**
     * The opcode that requires a worker.
     */
    public static final int WORKER = 0xFF16;
    /**
     * The opcode that requires a flying building.
     */
    public static final int FLYING_BUILDING = 0xFF17;
    /**
     * The opcode that requires a transport.
     */
    public static final int TRANSPORT = 0xFF18;
    /**
     * The opcode that requires a powerup.
     */
    public static final int POWERUP = 0xFF19;
    /**
     * The opcode that requires a subunit.
     */
    public static final int SUBUNIT = 0xFF1A;
    /**
     * The opcode that requires Spider Mines.
     */
    public static final int SPIDER_MINES = 0xFF1B;
    /**
     * The opcode that counts a hero.
     */
    public static final int HERO = 0xFF1C;
    /**
     * The opcode that requires a right-click action.
     */
    public static final int RIGHT_CLICK = 0xFF1D;
    /**
     * The opcode that allows the test for a hallucination.
     */
    public static final int HALLUCINATION_ALLOWED = 0xFF1E;
    /**
     * The opcode that jumps to the requirement section of the current upgrade level.
     */
    public static final int UPGRADE_LEVEL = 0xFF1F;
    /**
     * The opcode that starts the requirement section of upgrade level one.
     */
    public static final int LEVEL_ONE_SECTION = 0xFF20;
    /**
     * The opcode that starts the requirement section of upgrade level two.
     */
    public static final int LEVEL_TWO_SECTION = 0xFF21;
    /**
     * The opcode that always fails with a disabled button.
     */
    public static final int DISABLED = 0xFF22;
    /**
     * The opcode that always fails as if no script existed.
     */
    public static final int UNAVAILABLE = 0xFF23;
    /**
     * The opcode that requires the expansion.
     */
    public static final int EXPANSION = 0xFF24;
    /**
     * The production opcode followed by a technology the owner must have researched.
     */
    public static final int TECHNOLOGY_RESEARCHED = 0xFF25;
    /**
     * The opcode that requires the unit to be burrowed.
     */
    public static final int BURROWED = 0xFF26;
    /**
     * The word that ends an entry, a group sequence and a script.
     */
    public static final int END = 0xFFFF;

    private RequirementOpcodes() {
    }
}
