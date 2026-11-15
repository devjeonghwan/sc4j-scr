package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.save.SaveFileReader;
import org.fogofwarlabs.sc4j.scr.game.save.SaveFileWriter;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameContext;
import org.fogofwarlabs.sc4j.scr.game.save.SaveSection;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores two size-prefixed terrain overlay and decal sections.
 * <p>
 * A plain DWORD size and a section of one WORD per map tile: the terrain retained below an overlay
 * ({@link GameMap#getOverlayTileIdentifier}); then a plain DWORD size and a section of one byte per map tile, the
 * renderer's tile decal index, which the simulation does not keep: a game started anew writes zero, a loaded game the
 * bytes its reader filled ({@link GameMap#retainSavedDecals}).
 */
public final class TerrainOverlaySection implements SaveSection {
    /**
     * Creates a {@link TerrainOverlaySection} instance.
     */
    public TerrainOverlaySection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        GameMap map = context.getGame().getMap();
        int tileCount = map.getWidth() * map.getHeight();
        ByteBuffer overlays = ByteBuffer.allocate(tileCount * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        if (map.hasOverlayTiles()) {
            for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
                overlays.putShort(tileIndex * Short.BYTES, (short) map.getOverlayTileIdentifier(tileIndex));
            }
        }

        output.writeDword(overlays.capacity());
        output.writeSection(overlays.array());

        byte[] decals = map.getSavedDecals();

        output.writeDword(tileCount);
        output.writeSection(decals != null && decals.length == tileCount ? decals : new byte[tileCount]);
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws UncheckedIOException if a saved array size does not match the map's tile count
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        GameMap map = context.getGame().getMap();
        int tileCount = map.getWidth() * map.getHeight();
        int overlaySize = input.readDword();

        if (overlaySize != tileCount * Short.BYTES) {
            throw SaveFileReader.createFailure("The saved overlay array has {0} bytes for {1} tiles.", overlaySize, tileCount);
        }

        ByteBuffer overlays = ByteBuffer.wrap(input.readSection(overlaySize)).order(ByteOrder.LITTLE_ENDIAN);

        if (!map.hasOverlayTiles()) {
            map.initializeOverlayTiles();
        }

        for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
            map.restoreOverlayTileIdentifier(tileIndex, overlays.getShort(tileIndex * Short.BYTES));
        }

        int decalSize = input.readDword();

        if (decalSize != tileCount) {
            throw SaveFileReader.createFailure("The saved decal array has {0} bytes for {1} tiles.", decalSize, tileCount);
        }

        map.retainSavedDecals(input.readSection(decalSize));
    }
}
