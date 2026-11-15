package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.ai.ComputerPlayerPhase;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.resource.ResourceAreas;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores twenty computer-player sections in the following order:
 * <ul>
 *     <li>the region count DWORD, the military pool ({@link ComputerPlayerPhase#SAVED_MILITARY_SIZE}) and one section of
 *     region records per owner, owner {@code 7} first ({@link ComputerPlayerPhase#writeSaveRegions});</li>
 *     <li>the guard pool and the owners' guard lists ({@link ComputerPlayerPhase#writeSaveGuards});</li>
 *     <li>the worker, building and town pools and the owners' town lists ({@link ComputerPlayerPhase#writeSaveTowns});</li>
 *     <li>the eight player records ({@link ComputerPlayerPhase#writeSavePlayers});</li>
 *     <li>the script pool and its active list ({@link ComputerPlayerPhase#writeSaveScripts});</li>
 *     <li>the resource area table ({@link ResourceAreas#writeSaveRecords}).</li>
 * </ul>
 * Writing clears the free pool records. Reading recreates region records for the saved region count.
 */
public final class ComputerSection implements SaveSection {
    private static @NotNull ByteBuffer allocate(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static @NotNull ByteBuffer wrap(byte @NotNull [] payload) {
        return ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
    }


    /**
     * Creates a {@link ComputerSection} instance.
     */
    public ComputerSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Game game = context.getGame();
        ComputerPlayerPhase phase = SaveGameUtils.require(game.getComputerPlayerPhase(), "computer-player phase");
        ResourceAreas areas = SaveGameUtils.require(game.getResourceAreas(), "resource areas");
        SaveReferences references = new SaveReferences(game);
        int regionCount = phase.getRegionCount();
        ByteBuffer count = allocate(ComputerPlayerPhase.SAVED_REGION_COUNT_SIZE);
        ByteBuffer military = allocate(ComputerPlayerPhase.SAVED_MILITARY_SIZE);
        ByteBuffer[] regions = new ByteBuffer[PlayerTable.PLAYABLE_PLAYER_SLOTS];
        ByteBuffer guards = allocate(ComputerPlayerPhase.SAVED_GUARDS_SIZE);
        ByteBuffer guardHeads = allocate(ComputerPlayerPhase.SAVED_GUARD_HEADS_SIZE);
        ByteBuffer workers = allocate(ComputerPlayerPhase.SAVED_WORKERS_SIZE);
        ByteBuffer buildings = allocate(ComputerPlayerPhase.SAVED_BUILDINGS_SIZE);
        ByteBuffer towns = allocate(ComputerPlayerPhase.SAVED_TOWNS_SIZE);
        ByteBuffer townHeads = allocate(ComputerPlayerPhase.SAVED_TOWN_HEADS_SIZE);
        ByteBuffer players = allocate(ComputerPlayerPhase.SAVED_PLAYERS_SIZE);
        ByteBuffer scripts = allocate(ComputerPlayerPhase.SAVED_SCRIPTS_SIZE);
        ByteBuffer scriptHead = allocate(ComputerPlayerPhase.SAVED_SCRIPT_HEAD_SIZE);
        ByteBuffer resourceAreas = allocate(ResourceAreas.SAVED_SIZE);

        for (int owner = 0; owner < regions.length; owner++) {
            regions[owner] = allocate(regionCount * ComputerPlayerPhase.SAVED_REGION_RECORD_SIZE);
        }

        count.putInt(0, regionCount);
        phase.writeSaveRegions(military, regions, references);
        output.writeSection(count.array());
        output.writeSection(military.array());

        for (int owner = regions.length - 1; owner >= 0; owner--) {
            output.writeSection(regions[owner].array());
        }

        phase.writeSaveGuards(guards, guardHeads, references);
        output.writeSection(guards.array());
        output.writeSection(guardHeads.array());

        phase.writeSaveTowns(workers, buildings, towns, townHeads, references);
        output.writeSection(workers.array());
        output.writeSection(buildings.array());
        output.writeSection(towns.array());
        output.writeSection(townHeads.array());

        phase.writeSavePlayers(players, references);
        output.writeSection(players.array());

        phase.writeSaveScripts(scripts, scriptHead);
        output.writeSection(scripts.array());
        output.writeSection(scriptHead.array());

        areas.writeSaveRecords(resourceAreas);
        output.writeSection(resourceAreas.array());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        ComputerPlayerPhase phase = SaveGameUtils.require(game.getComputerPlayerPhase(), "computer-player phase");
        ResourceAreas areas = SaveGameUtils.require(game.getResourceAreas(), "resource areas");
        SaveReferences references = new SaveReferences(game);
        int regionCount = wrap(input.readSection(ComputerPlayerPhase.SAVED_REGION_COUNT_SIZE)).getInt(0);
        ByteBuffer[] regions = new ByteBuffer[PlayerTable.PLAYABLE_PLAYER_SLOTS];
        ByteBuffer military = wrap(input.readSection(ComputerPlayerPhase.SAVED_MILITARY_SIZE));

        for (int owner = regions.length - 1; owner >= 0; owner--) {
            regions[owner] = wrap(input.readSection(regionCount * ComputerPlayerPhase.SAVED_REGION_RECORD_SIZE));
        }

        phase.readSaveRegions(regionCount, military, regions, references);

        ByteBuffer guards = wrap(input.readSection(ComputerPlayerPhase.SAVED_GUARDS_SIZE));
        ByteBuffer guardHeads = wrap(input.readSection(ComputerPlayerPhase.SAVED_GUARD_HEADS_SIZE));

        phase.readSaveGuards(guards, guardHeads, references);

        ByteBuffer workers = wrap(input.readSection(ComputerPlayerPhase.SAVED_WORKERS_SIZE));
        ByteBuffer buildings = wrap(input.readSection(ComputerPlayerPhase.SAVED_BUILDINGS_SIZE));
        ByteBuffer towns = wrap(input.readSection(ComputerPlayerPhase.SAVED_TOWNS_SIZE));
        ByteBuffer townHeads = wrap(input.readSection(ComputerPlayerPhase.SAVED_TOWN_HEADS_SIZE));

        phase.readSaveTowns(workers, buildings, towns, townHeads, references);

        ByteBuffer players = wrap(input.readSection(ComputerPlayerPhase.SAVED_PLAYERS_SIZE));

        phase.readSavePlayers(players, references);

        ByteBuffer scripts = wrap(input.readSection(ComputerPlayerPhase.SAVED_SCRIPTS_SIZE));
        ByteBuffer scriptHead = wrap(input.readSection(ComputerPlayerPhase.SAVED_SCRIPT_HEAD_SIZE));

        phase.readSaveScripts(scripts, scriptHead);

        ByteBuffer resourceAreas = wrap(input.readSection(ResourceAreas.SAVED_SIZE));

        areas.readSaveRecords(resourceAreas);
    }
}
