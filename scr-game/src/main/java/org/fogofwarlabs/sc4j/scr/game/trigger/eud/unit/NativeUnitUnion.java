package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the unit-kind-dependent members of the classic DWORD range {@link #FIRST_OFFSET} through
 * {@link #LAST_OFFSET}. Each offset selects the first matching kind in its defined order. An unmatched offset reads
 * zero and ignores writes. Pointer members identify their shadow slots; scalar members pack and unpack their covered
 * bytes, leaving uncovered bits zero on reads and discarding them on writes.
 */
public final class NativeUnitUnion {
    /**
     * The first classic DWORD of the union.
     */
    public static final int FIRST_OFFSET = 0xC0;
    /**
     * The last classic DWORD of the union.
     */
    public static final int LAST_OFFSET = 0xD8;

    /**
     * The second classic DWORD of the union.
     */
    private static final int SECOND_OFFSET = 0xC4;
    /**
     * The third classic DWORD of the union.
     */
    private static final int THIRD_OFFSET = 0xC8;
    /**
     * The fourth classic DWORD of the union.
     */
    private static final int FOURTH_OFFSET = 0xCC;
    /**
     * The fifth classic DWORD of the union.
     */
    private static final int FIFTH_OFFSET = 0xD0;
    /**
     * The sixth classic DWORD of the union.
     */
    private static final int SIXTH_OFFSET = 0xD4;

    private static final int INTERCEPTOR = ScrUnitIds.PROTOSS_INTERCEPTOR;
    private static final int SCARAB = ScrUnitIds.PROTOSS_SCARAB;
    private static final int NUCLEAR_SILO = ScrUnitIds.TERRAN_NUCLEAR_SILO;
    private static final int NYDUS_CANAL = ScrUnitIds.ZERG_NYDUS_CANAL;
    private static final int PYLON = ScrUnitIds.PROTOSS_PYLON;
    private static final int HATCHERY = ScrUnitIds.ZERG_HATCHERY;
    private static final int HIVE = ScrUnitIds.ZERG_HIVE;

    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;
    private static final int SECOND_BYTE = 8;
    private static final int THIRD_BYTE = 16;
    private static final int FOURTH_BYTE = 24;


    /**
     * A member of the union, with the native shadow slot of a pointer.
     */
    public enum Member {
        /**
         * Building: the attached add-on.
         */
        BUILDING_ADDON(0x98),
        /**
         * Carrier or Reaver: the first unit inside the hangar.
         */
        HANGAR_FIRST_INSIDE(0xB0),
        /**
         * Vulture BYTE: the Spider Mine count.
         */
        VULTURE_MINES(-1),
        /**
         * Interceptor or Scarab: the hangar parent.
         */
        FIGHTER_PARENT(0xC0),
        /**
         * Worker: the carried powerup.
         */
        WORKER_POWERUP(0xA0),
        /**
         * Building WORDs: the add-on build type and the research or upgrade time.
         */
        BUILDING_ADDON_TYPE_AND_TIME(-1),
        /**
         * Carrier or Reaver: the first unit outside the hangar.
         */
        HANGAR_FIRST_OUTSIDE(0xB8),
        /**
         * Interceptor or Scarab: the previous hangar unit.
         */
        FIGHTER_PREVIOUS(0xC8),
        /**
         * Worker WORDs: the target resource position.
         */
        WORKER_TARGET_POSITION(-1),
        /**
         * Building BYTEs: technology, upgrade, larva timer and landing byte.
         */
        BUILDING_RESEARCH(-1),
        /**
         * Carrier or Reaver BYTEs: the inside and outside counts and a stored byte.
         */
        HANGAR_COUNTS(-1),
        /**
         * Interceptor or Scarab: the next hangar unit.
         */
        FIGHTER_NEXT(0xD0),
        /**
         * Worker: the target resource unit.
         */
        WORKER_TARGET_RESOURCE(0xA8),
        /**
         * Building BYTEs: the creep timer and the upgrade level.
         */
        BUILDING_CREEP_AND_LEVEL(-1),
        /**
         * Interceptor or Scarab BYTE: outside the hangar.
         */
        FIGHTER_OUTSIDE(-1),
        /**
         * Worker WORD and BYTEs: repair timer, gathering, carried amount.
         */
        WORKER_REPAIR_AND_CARRY(-1),
        /**
         * Nuclear Silo: the stored missile.
         */
        SILO_NUKE(0xD8),
        /**
         * Nydus Canal: the exit.
         */
        NYDUS_EXIT(0xE0),
        /**
         * Pylon: the psi field sprite.
         */
        PYLON_PSI_SPRITE(0xE8),
        /**
         * Resource WORD and BYTEs: amount, animation, being gathered.
         */
        RESOURCE_AMOUNT(-1),
        /**
         * Powerup DWORD: the origin position.
         */
        POWERUP_ORIGIN(-1),
        /**
         * Worker: the gather target.
         */
        WORKER_GATHER_TARGET(0x100),
        /**
         * Hatchery WORDs: the first two larva side counts.
         */
        HATCHERY_FIRST_SIDES(-1),
        /**
         * Nuclear Silo DWORD: the ready state.
         */
        SILO_READY(-1),
        /**
         * Powerup: the carrying worker.
         */
        POWERUP_CARRIER(0xF0),
        /**
         * Resource: the first gathering worker.
         */
        RESOURCE_FIRST_GATHERER(0xF8),
        /**
         * Worker: the previous worker gathering the same resource.
         */
        WORKER_PREVIOUS_GATHERER(0x108),
        /**
         * Hatchery WORDs: the last two larva side counts.
         */
        HATCHERY_LAST_SIDES(-1),
        /**
         * Resource BYTEs: the area index and the computer claim.
         */
        RESOURCE_AREA(-1),
        /**
         * Worker: the next worker gathering the same resource.
         */
        WORKER_NEXT_GATHERER(0x110);

        private final int shadowSlot;

        Member(int shadowSlot) {
            this.shadowSlot = shadowSlot;
        }

        /**
         * @return {@code true} if the member is a pointer, {@code false} otherwise
         */
        public boolean isPointer() {
            return this.shadowSlot >= 0;
        }

        /**
         * @return {@code true} if the member points to a sprite, {@code false} otherwise
         */
        public boolean isSpritePointer() {
            return this == PYLON_PSI_SPRITE;
        }
    }


    /**
     * Resolves the member a classic DWORD designates for the unit's current kind.
     *
     * @param unitTypes     the unit type table
     * @param unit          the unit
     * @param classicOffset the classic DWORD offset from {@link #FIRST_OFFSET} through {@link #LAST_OFFSET}
     * @return the member, or {@code null} when no kind of the DWORD matches (reads {@code 0}, writes are ignored)
     * @throws IllegalArgumentException if the offset is no union DWORD
     */
    public static @Nullable Member getMember(@NotNull ScrUnitTypeView unitTypes, @NotNull Unit unit, int classicOffset) {
        int type = unit.getUnitId();
        boolean building = UnitQueries.isBuilding(unitTypes, type);
        boolean hangar = unitTypes.hasHangar(type);
        boolean vulture = unitTypes.isVulture(type);
        boolean fighter = type == INTERCEPTOR || type == SCARAB;
        boolean worker = isWorker(unitTypes, type);
        boolean resource = (unitTypes.getFlags(type) & UnitTypeFlags.RESOURCE_CONTAINER) != 0;
        boolean powerup = (unitTypes.getFlags(type) & UnitTypeFlags.POWERUP) != 0;
        boolean hatchery = type >= HATCHERY && type <= HIVE;

        return switch (classicOffset) {
            case FIRST_OFFSET -> building ? Member.BUILDING_ADDON
                                          : hangar ? Member.HANGAR_FIRST_INSIDE
                                                   : vulture ? Member.VULTURE_MINES
                                                             : fighter ? Member.FIGHTER_PARENT
                                                                       : worker ? Member.WORKER_POWERUP
                                                                                : null;
            case SECOND_OFFSET -> building ? Member.BUILDING_ADDON_TYPE_AND_TIME
                                           : hangar ? Member.HANGAR_FIRST_OUTSIDE
                                                    : vulture ? null
                                                              : fighter ? Member.FIGHTER_PREVIOUS
                                                                        : worker ? Member.WORKER_TARGET_POSITION
                                                                                 : null;
            case THIRD_OFFSET -> building ? Member.BUILDING_RESEARCH
                                          : hangar ? Member.HANGAR_COUNTS
                                                   : vulture ? null
                                                             : fighter ? Member.FIGHTER_NEXT
                                                                       : worker ? Member.WORKER_TARGET_RESOURCE
                                                                                : null;
            case FOURTH_OFFSET -> building ? Member.BUILDING_CREEP_AND_LEVEL
                                           : hangar || vulture ? null
                                                               : fighter ? Member.FIGHTER_OUTSIDE
                                                                         : worker ? Member.WORKER_REPAIR_AND_CARRY
                                                                                  : null;
            case FIFTH_OFFSET -> type == NUCLEAR_SILO ? Member.SILO_NUKE
                                                      : type == NYDUS_CANAL ? Member.NYDUS_EXIT
                                                                            : type == PYLON ? Member.PYLON_PSI_SPRITE
                                                                                            : resource ? Member.RESOURCE_AMOUNT
                                                                                                       : powerup ? Member.POWERUP_ORIGIN
                                                                                                                 : worker ? Member.WORKER_GATHER_TARGET
                                                                                                                          : hatchery ? Member.HATCHERY_FIRST_SIDES
                                                                                                                                     : null;
            case SIXTH_OFFSET -> type == NUCLEAR_SILO ? Member.SILO_READY
                                                      : powerup ? Member.POWERUP_CARRIER
                                                                : resource ? Member.RESOURCE_FIRST_GATHERER
                                                                           : worker ? Member.WORKER_PREVIOUS_GATHERER
                                                                                    : hatchery ? Member.HATCHERY_LAST_SIDES
                                                                                               : null;
            case LAST_OFFSET -> resource ? Member.RESOURCE_AREA
                                         : worker ? Member.WORKER_NEXT_GATHERER
                                                  : null;
            default -> throw Exceptions.newIllegalArgumentException("Classic unit offset 0x{0} is not a union DWORD.",
                                                                    Integer.toHexString(classicOffset));
        };
    }

    /**
     * Reads a unit pointer member.
     *
     * @param unit   the unit
     * @param member a pointer member other than {@link Member#PYLON_PSI_SPRITE}
     * @return the one-based pool index of the target, or {@code 0} for none
     * @throws IllegalArgumentException if the member is no unit pointer
     */
    public static int readUnit(@NotNull Unit unit, @NotNull Member member) {
        Unit target = switch (member) {
            case BUILDING_ADDON -> unit.getAddon();
            case HANGAR_FIRST_INSIDE -> unit.getFirstHangarUnit(false);
            case FIGHTER_PARENT -> unit.getHangarParent();
            case WORKER_POWERUP -> unit.getCarriedPowerup();
            case HANGAR_FIRST_OUTSIDE -> unit.getFirstHangarUnit(true);
            case FIGHTER_PREVIOUS -> unit.getPreviousHangarUnit();
            case FIGHTER_NEXT -> unit.getNextHangarUnit();
            case WORKER_TARGET_RESOURCE -> unit.getTargetResourceUnit();
            case SILO_NUKE -> unit.getSiloNuke();
            case NYDUS_EXIT -> unit.getNydusExit();
            case WORKER_GATHER_TARGET -> unit.getGatherTarget();
            case POWERUP_CARRIER -> unit.getPowerupCarrier();
            case RESOURCE_FIRST_GATHERER -> unit.getFirstGatheringWorker();
            case WORKER_PREVIOUS_GATHERER -> unit.getPreviousGatheringWorker();
            case WORKER_NEXT_GATHERER -> unit.getNextGatheringWorker();
            default -> throw Exceptions.newIllegalArgumentException("{0} is no unit pointer.", member);
        };

        return target == null ? 0 : target.getIndex();
    }

    /**
     * Stores a raw unit pointer member without relinking the surrounding lists.
     *
     * @param pool      the unit pool
     * @param unit      the unit
     * @param member    a pointer member other than {@link Member#PYLON_PSI_SPRITE}
     * @param poolIndex the one-based pool index of the new target, or {@code 0} for none
     * @throws IllegalArgumentException if the member is no unit pointer
     */
    public static void writeUnit(@NotNull UnitPool pool, @NotNull Unit unit, @NotNull Member member, int poolIndex) {
        Unit target = poolIndex == 0 ? null : pool.getUnit(poolIndex);

        switch (member) {
            case BUILDING_ADDON -> unit.setAddon(target);
            case HANGAR_FIRST_INSIDE -> unit.setFirstHangarUnit(false, target);
            case FIGHTER_PARENT -> unit.setHangarParent(target);
            case WORKER_POWERUP -> unit.setCarriedPowerup(target);
            case HANGAR_FIRST_OUTSIDE -> unit.setFirstHangarUnit(true, target);
            case FIGHTER_PREVIOUS -> unit.setPreviousHangarUnit(target);
            case FIGHTER_NEXT -> unit.setNextHangarUnit(target);
            case WORKER_TARGET_RESOURCE -> unit.setTargetResource(target, unit.getTargetResourceX(), unit.getTargetResourceY());
            case SILO_NUKE -> unit.setSiloNuke(target);
            case NYDUS_EXIT -> unit.setNydusExit(target);
            case WORKER_GATHER_TARGET -> unit.setGatherTarget(target);
            case POWERUP_CARRIER -> unit.setPowerupCarrier(target);
            case RESOURCE_FIRST_GATHERER -> unit.setFirstGatheringWorker(target);
            case WORKER_PREVIOUS_GATHERER -> unit.setPreviousGatheringWorker(target);
            case WORKER_NEXT_GATHERER -> unit.setNextGatheringWorker(target);
            default -> throw Exceptions.newIllegalArgumentException("{0} is no unit pointer.", member);
        }
    }

    /**
     * Reads the psi field sprite pointer of a Pylon.
     *
     * @param unit the Pylon
     * @return the sprite pool index of the psi field sprite, or {@code -1} for none
     */
    public static int readSprite(@NotNull Unit unit) {
        Sprite sprite = unit.getPsiFieldSprite();

        return sprite == null ? -1 : sprite.getPoolIndex();
    }

    /**
     * Stores the raw psi field sprite pointer of a Pylon.
     *
     * @param sprites     the sprite pool
     * @param unit        the Pylon
     * @param spriteIndex the sprite pool index, or {@code -1} for none
     */
    public static void writeSprite(@NotNull SpritePool sprites, @NotNull Unit unit, int spriteIndex) {
        unit.setPsiFieldSprite(spriteIndex < 0 ? null : sprites.getSprite(spriteIndex));
    }

    /**
     * Reads a scalar member as the classic DWORD.
     *
     * @param unit   the unit
     * @param member a scalar member
     * @return the DWORD
     * @throws IllegalArgumentException if the member is a pointer
     */
    public static int readValue(@NotNull Unit unit, @NotNull Member member) {
        return switch (member) {
            case VULTURE_MINES -> unit.getSpiderMineCount() & BYTE_MASK;
            case BUILDING_ADDON_TYPE_AND_TIME -> packWords(unit.getAddonBuildType(), unit.getUpgradeResearchTime());
            case WORKER_TARGET_POSITION -> packWords(unit.getTargetResourceX(), unit.getTargetResourceY());
            case BUILDING_RESEARCH -> packBytes(unit.getResearchType(), unit.getUpgradeType(), unit.getLarvaSpawnCountdown(), unit.getLandingByte());
            case HANGAR_COUNTS -> packBytes(unit.getHangarCount(false), unit.getHangarCount(true), unit.getHangarSpareByte(), 0);
            case BUILDING_CREEP_AND_LEVEL -> packBytes(unit.getCreepTimer(), unit.getUpgradeLevel(), 0, 0);
            case FIGHTER_OUTSIDE -> unit.getHangarOutsideByte();
            case WORKER_REPAIR_AND_CARRY -> unit.getRepairTimer() & WORD_MASK | (unit.getGatheringResourceByte() & BYTE_MASK) << THIRD_BYTE |
                                            (unit.getCarriedResourceAmount() & BYTE_MASK) << FOURTH_BYTE;
            case RESOURCE_AMOUNT -> unit.getResources() & WORD_MASK | (unit.getResourceAnimation() & BYTE_MASK) << THIRD_BYTE |
                                    (unit.getResourceBeingGatheredByte() & BYTE_MASK) << FOURTH_BYTE;
            case POWERUP_ORIGIN, HATCHERY_FIRST_SIDES -> packWords(unit.getResources(), unit.getSecondLarvaSideCount());
            case SILO_READY -> unit.getSiloReadyDword();
            case HATCHERY_LAST_SIDES -> packWords(unit.getThirdLarvaSideCount(), unit.getFourthLarvaSideCount());
            case RESOURCE_AREA -> packBytes(unit.getResourceAreaIndex(), unit.getResourceClaimByte(), 0, 0);
            default -> throw Exceptions.newIllegalArgumentException("{0} is a pointer.", member);
        };
    }

    /**
     * Stores a scalar member from the classic DWORD; bytes the member does not cover are dropped.
     *
     * @param unit   the unit
     * @param member a scalar member
     * @param value  the DWORD
     * @throws IllegalArgumentException if the member is a pointer
     */
    public static void writeValue(@NotNull Unit unit, @NotNull Member member, int value) {
        int low = value & WORD_MASK;
        int high = value >>> THIRD_BYTE;
        int second = value >>> SECOND_BYTE & BYTE_MASK;
        int third = value >>> THIRD_BYTE & BYTE_MASK;
        int fourth = value >>> FOURTH_BYTE;

        switch (member) {
            case VULTURE_MINES -> unit.setSpiderMineCount(value);
            case BUILDING_ADDON_TYPE_AND_TIME -> {
                unit.setAddonBuildType(low);
                unit.setUpgradeResearchTime(high);
            }
            case WORKER_TARGET_POSITION -> unit.setTargetResource(unit.getTargetResourceUnit(), low, high);
            case BUILDING_RESEARCH -> {
                unit.setResearchType(value);
                unit.setUpgradeType(second);
                unit.setLarvaSpawnCountdown(third);
                unit.setLandingByte(fourth);
            }
            case HANGAR_COUNTS -> {
                unit.setHangarCount(false, value & BYTE_MASK);
                unit.setHangarCount(true, second);
                unit.setHangarSpareByte(third);
            }
            case BUILDING_CREEP_AND_LEVEL -> {
                unit.setCreepTimer(value);
                unit.setUpgradeLevel(second);
            }
            case FIGHTER_OUTSIDE -> unit.setHangarOutsideByte(value);
            case WORKER_REPAIR_AND_CARRY -> {
                unit.setRepairTimer(low);
                unit.setGatheringResourceByte(third);
                unit.setCarriedResourceAmount(fourth);
            }
            case RESOURCE_AMOUNT -> {
                unit.setResources(low);
                unit.setResourceAnimation(third);
                unit.setResourceBeingGatheredByte(fourth);
            }
            case POWERUP_ORIGIN, HATCHERY_FIRST_SIDES -> {
                unit.setResources(low);
                unit.setSecondLarvaSideCount(high);
            }
            case SILO_READY -> unit.setSiloReadyDword(value);
            case HATCHERY_LAST_SIDES -> {
                unit.setThirdLarvaSideCount(low);
                unit.setFourthLarvaSideCount(high);
            }
            case RESOURCE_AREA -> {
                unit.setResourceAreaIndex(value);
                unit.setResourceClaimByte(second);
            }
            default -> throw Exceptions.newIllegalArgumentException("{0} is a pointer.", member);
        }
    }

    private static boolean isWorker(@NotNull ScrUnitTypeView unitTypes, int type) {
        return type < ScrUnitIds.NONE && (unitTypes.getFlags(type) & UnitTypeFlags.WORKER) != 0;
    }

    private static int packWords(int low, int high) {
        return low & WORD_MASK | (high & WORD_MASK) << THIRD_BYTE;
    }

    private static int packBytes(int first, int second, int third, int fourth) {
        return first & BYTE_MASK | (second & BYTE_MASK) << SECOND_BYTE | (third & BYTE_MASK) << THIRD_BYTE | (fourth & BYTE_MASK) << FOURTH_BYTE;
    }


    private NativeUnitUnion() {
    }
}
