package org.fogofwarlabs.sc4j.scr.render.hd;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that {@link HdFoliage} places the same foliage for the same seed, keeps its placement invariants and removes it under blocked tiles.
 */
@RequiresInstallation
final class HdFoliageTest {
    private static final int JUNGLE_TILESET = 4;
    /**
     * A Jungle dirt tile (CV5 group {@code 2}, megatile {@code 5}) whose foliage table entry has a spawn chance.
     */
    private static final int DIRT_TILE = 0x24;
    /**
     * The width and height of the placed map in tiles.
     */
    private static final int MAP_SIZE = 64;
    private static final int GAME_SEED = 12345;
    /**
     * The record flags of a visible foliage, a placed anchor and a blocked tile.
     */
    private static final int FLAG_VISIBLE = 1;
    private static final int FLAG_PLACED = 2;
    private static final int FLAG_BLOCKED = 4;

    @Test
    void foliage_is_the_same_for_the_same_seed_and_keeps_its_invariants() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            HdRenderAssets assets = HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, HdRenderAssets.Quality.HD);
            HdFoliage first = assets.loadFoliage();
            HdFoliage second = assets.loadFoliage();

            Assertions.assertNotNull(first);
            Assertions.assertNotNull(second);

            int megatile = assets.getCv5Megatile(DIRT_TILE);
            int visible = 0;

            first.place(GAME_SEED, MAP_SIZE, MAP_SIZE, tile -> megatile);
            second.place(GAME_SEED, MAP_SIZE, MAP_SIZE, tile -> megatile);

            for (int tile = 0; tile < MAP_SIZE * MAP_SIZE; tile++) {
                Assertions.assertEquals(first.getFlags(tile), second.getFlags(tile), "tile " + tile);

                int flags = first.getFlags(tile);

                if ((flags & FLAG_VISIBLE) == 0) {
                    continue;
                }

                visible++;

                // Visible foliage is an anchor that is not blocked, and its offsets lie in its tile footprint.
                Assertions.assertEquals(FLAG_PLACED, flags & (FLAG_PLACED | FLAG_BLOCKED), "tile " + tile);
                Assertions.assertTrue(first.getOffsetX(tile) >= 0 && first.getOffsetY(tile) >= 0, "tile " + tile);
            }

            Assertions.assertTrue(visible > 0, "the dirt tiles carry foliage");

            // Resources remove the foliage under them.
            first.block(0, 0, MAP_SIZE, MAP_SIZE);

            for (int tile = 0; tile < MAP_SIZE * MAP_SIZE; tile++) {
                Assertions.assertEquals(0, first.getFlags(tile) & FLAG_VISIBLE);
            }
        }
    }
}
