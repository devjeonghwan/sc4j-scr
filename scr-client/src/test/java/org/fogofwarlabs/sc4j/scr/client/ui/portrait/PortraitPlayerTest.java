package org.fogofwarlabs.sc4j.scr.client.ui.portrait;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.client.video.VideoClip;
import org.fogofwarlabs.sc4j.engine.client.video.VideoClipDecoder;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.PortdataDat;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModFiles;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModPortrait;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests that the portrait player plays an added portrait from the mod's files: its idle and talking clips of the asset set (the SD clips where
 * the mod gives none), while an installed portrait still plays from the installation and an id past the added portraits shows none.
 */
@RequiresInstallation
final class PortraitPlayerTest {
    private static final @NotNull String SD_IDLE = "portrait/sd/idle";
    private static final @NotNull String SD_TALKING = "portrait/sd/talking";
    private static final @NotNull String HD_IDLE = "portrait/hd/idle";
    private static final @NotNull String HD_TALKING = "portrait/hd/talking";
    private static final @NotNull String SD_PREFIX = "SD/portrait/";
    private static final @NotNull String HD2_PREFIX = "HD2/portrait/";
    private static final @NotNull String HD_PREFIX = "portrait/";
    private static final int CLIP_COUNT = 4;
    private static final long FRAME_MILLISECONDS = 100;
    private static final long TALK_MILLISECONDS = 10_000;
    private static final long LOAD_TIMEOUT_MILLISECONDS = 10_000;
    private static final int MARINE_PORTRAIT = 0;

    @Test
    void added_portrait_plays_its_clips_from_the_mod_files() throws Exception {
        Map<String, byte[]> files = new HashMap<>();

        for (String prefix : new String[]{SD_IDLE, SD_TALKING, HD_IDLE, HD_TALKING}) {
            for (int clip = 0; clip < CLIP_COUNT; clip++) {
                String path = prefix + clip + ScrModPortrait.CLIP_EXTENSION;

                files.put(path, path.getBytes(StandardCharsets.US_ASCII));
            }
        }

        ScrModFiles mod = path -> {
            byte[] contents = files.get(ScrModFiles.checkPath(path));

            if (contents == null) {
                throw Exceptions.newIOException("{0}", path);
            }

            return contents.clone();
        };
        ScrAddedGraphics added = new ScrAddedGraphics(0);
        int portrait = added.addPortrait(new ScrModPortrait.Builder(mod, SD_IDLE, SD_TALKING).setHd(HD_IDLE, HD_TALKING).build());
        // The test decoder keeps the file contents as the only frame.
        VideoClipDecoder decoder = webm -> new VideoClip(1, 1, new byte[][]{webm}, new long[]{FRAME_MILLISECONDS});

        Assertions.assertEquals(PortdataDat.PORTRAIT_COUNT, portrait);

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            PortraitPlayer player = new PortraitPlayer(storage, PortraitTables.load(storage), decoder);
            Object unit = new Object();

            player.setAddedGraphics(added);
            player.show(unit, portrait, 0);

            Assertions.assertEquals(SD_IDLE + 0 + ScrModPortrait.CLIP_EXTENSION, readShownFile(player, portrait));

            player.setPrefix(HD_PREFIX);
            Assertions.assertEquals(HD_IDLE + 0 + ScrModPortrait.CLIP_EXTENSION, readShownFile(player, portrait));

            player.setPrefix(HD2_PREFIX);
            Assertions.assertEquals(SD_IDLE + 0 + ScrModPortrait.CLIP_EXTENSION, readShownFile(player, portrait), "HD2 plays the SD clips");

            player.talk(0, TALK_MILLISECONDS);
            Assertions.assertTrue(readShownFile(player, portrait).startsWith(SD_TALKING));

            player.stopTalking();
            player.setPrefix(SD_PREFIX);
            player.show(unit, MARINE_PORTRAIT, 0);
            Assertions.assertNotNull(waitForFrame(player, MARINE_PORTRAIT).getPath());
            Assertions.assertTrue(waitForFrame(player, MARINE_PORTRAIT).getPath().startsWith(SD_PREFIX), "An installed portrait plays installed clips");

            Assertions.assertNull(player.update(portrait + 1, 0), "An id past the added portraits shows no portrait");
        }
    }

    private static @NotNull String readShownFile(@NotNull PortraitPlayer player, int portrait) throws Exception {
        return new String(waitForFrame(player, portrait).getClip().getFrame(0), StandardCharsets.US_ASCII);
    }

    private static PortraitPlayer.@NotNull Frame waitForFrame(@NotNull PortraitPlayer player, int portrait) throws Exception {
        long end = System.currentTimeMillis() + LOAD_TIMEOUT_MILLISECONDS;

        while (System.currentTimeMillis() < end) {
            PortraitPlayer.Frame frame = player.update(portrait, 0);

            if (frame != null && !player.isLoading()) {
                return frame;
            }

            Thread.sleep(1);
        }

        throw Exceptions.newAssertionError("The portrait clip did not load.");
    }
}
