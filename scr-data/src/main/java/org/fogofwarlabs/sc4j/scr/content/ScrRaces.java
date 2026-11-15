package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.faction.Faction;
import org.fogofwarlabs.sc4j.engine.content.faction.FactionDefinition;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the races of the SC:R mod as engine factions, in native race order: Zerg, Terran and Protoss. The mod registers them
 * in this order, so the index of each race is its native race number. The other native race values (independent, neutral,
 * user selectable, random, inactive) are slot settings, not races.
 */
public final class ScrRaces {
    /**
     * The number of races.
     */
    public static final int COUNT = 3;

    /**
     * The Zerg (native race {@code 0}).
     */
    public static final @NotNull Faction ZERG = new Faction("Zerg");
    /**
     * The Terran (native race {@code 1}).
     */
    public static final @NotNull Faction TERRAN = new Faction("Terran");
    /**
     * The Protoss (native race {@code 2}).
     */
    public static final @NotNull Faction PROTOSS = new Faction("Protoss");

    /**
     * The number of workers in the start units of a race.
     */
    private static final int START_WORKER_COUNT = 4;
    /**
     * The refinery type of each race, in race order: SC:R race data the engine faction has no field for.
     */
    private static final int @NotNull [] REFINERY_IDS = {ScrUnitIds.ZERG_EXTRACTOR, ScrUnitIds.TERRAN_REFINERY, ScrUnitIds.PROTOSS_ASSIMILATOR};

    private static final @NotNull List<@NotNull Faction> TYPE_LIST = List.of(ZERG, TERRAN, PROTOSS);
    private static final @NotNull List<@NotNull FactionDefinition> DEFINITIONS;

    static {
        UnitType[] workers = {ScrUnits.ZERG_DRONE, ScrUnits.TERRAN_SCV, ScrUnits.PROTOSS_PROBE};
        UnitType[] townHalls = {ScrUnits.ZERG_HATCHERY, ScrUnits.TERRAN_COMMAND_CENTER, ScrUnits.PROTOSS_NEXUS};
        UnitType[] supplyProviders = {ScrUnits.ZERG_OVERLORD, ScrUnits.TERRAN_SUPPLY_DEPOT, ScrUnits.PROTOSS_PYLON};
        List<FactionDefinition> definitions = new ArrayList<>(COUNT);

        for (int nativeId = 0; nativeId < COUNT; nativeId++) {
            FactionDefinition.Builder definition = new FactionDefinition.Builder().setWorker(workers[nativeId])
                                                                                  .setTownHall(townHalls[nativeId])
                                                                                  .setSupplyProvider(supplyProviders[nativeId])
                                                                                  .addResourceKind(ScrResources.MINERALS)
                                                                                  .addResourceKind(ScrResources.GAS)
                                                                                  .addStartUnit(townHalls[nativeId]);

            for (int worker = 0; worker < START_WORKER_COUNT; worker++) {
                definition.addStartUnit(workers[nativeId]);
            }

            if (nativeId == ScrRaceIds.ZERG) {
                definition.addStartUnit(ScrUnits.ZERG_OVERLORD);
            }

            definitions.add(definition.build());
        }

        DEFINITIONS = List.copyOf(definitions);
    }

    /**
     * Returns the race of a native race number.
     *
     * @param nativeId the native race number, from {@code 0} to {@code COUNT - 1}
     * @return the race
     * @throws IllegalArgumentException if the number is not a race
     */
    public static @NotNull Faction get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid race: {0}", nativeId);
        }

        return TYPE_LIST.get(nativeId);
    }

    /**
     * Returns the definition of a race: worker, town hall, supply provider, minerals and gas, and the start units (the town hall, four
     * workers and, for Zerg, an Overlord). The importer registers these definitions; they are game-invariant.
     *
     * @param nativeId the race value, from {@code 0} to {@code COUNT - 1}
     * @return the definition
     * @throws IllegalArgumentException if the value is not a race
     */
    public static @NotNull FactionDefinition getDefinition(int nativeId) {
        get(nativeId);

        return DEFINITIONS.get(nativeId);
    }

    /**
     * @param nativeId the race value, from {@code 0} to {@code COUNT - 1}
     * @return the units.dat entry of the race's worker
     * @throws IllegalArgumentException if the value is not a race
     */
    public static int getWorkerId(int nativeId) {
        return ScrUnits.getNativeId(getDefinition(nativeId).getWorker());
    }

    /**
     * @param nativeId the race value, from {@code 0} to {@code COUNT - 1}
     * @return the units.dat entry of the race's town hall, its first resource depot
     * @throws IllegalArgumentException if the value is not a race
     */
    public static int getTownHallId(int nativeId) {
        return ScrUnits.getNativeId(getDefinition(nativeId).getTownHall());
    }

    /**
     * @param nativeId the race value, from {@code 0} to {@code COUNT - 1}
     * @return the units.dat entry of the race's refinery
     * @throws IllegalArgumentException if the value is not a race
     */
    public static int getRefineryId(int nativeId) {
        get(nativeId);

        return REFINERY_IDS[nativeId];
    }

    /**
     * @return every race in native race order, as the mod registers them
     */
    public static @NotNull List<@NotNull Faction> getTypes() {
        return TYPE_LIST;
    }


    private ScrRaces() {
    }
}
