package org.fogofwarlabs.sc4j.scr.client.glue;

import org.fogofwarlabs.sc4j.engine.client.video.VideoStream;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrControlType;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogImportUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Verifies that the glue {@code FLC} elements of the installed ready rooms start over ({@code FLC_LOOP}) except the Terran
 * {@code proj} pieces under the globe, which play once and hold their last frame, and that the installed glue clips decode through the engine
 * {@link VideoStream} across repeated and concurrent lifetimes.
 */
@RequiresInstallation
final class GlueVideoTest {
    /**
     * The Terran projector pieces that play once: the SD and the HD piece.
     */
    private static final int PROJECTOR_PIECE_COUNT = 2;
    /**
     * The video file name ending of a projector piece.
     */
    private static final @NotNull String PROJECTOR_VIDEO_SUFFIX = "\\proj.webm";
    /**
     * The ready-room dialogs of the Terran, Zerg and Protoss campaigns.
     */
    private static final @NotNull String TERRAN_READY_ROOM = "gluRdyT";
    private static final @NotNull String ZERG_READY_ROOM = "GluRdyZ";
    private static final @NotNull String PROTOSS_READY_ROOM = "gluRdyP";
    /**
     * The decoded glue clips.
     */
    private static final @NotNull String @NotNull [] CLIPS = {
            "SD/glue/mainmenu/Exit.webm", "SD/glue/mainmenu/ExitOn.webm", "SD/glue/mainmenu/Multi.webm", "SD/glue/mainmenu/MultiOn.webm",
            "SD/glue/mainmenu/Single.webm", "SD/glue/mainmenu/SingleOn.webm",
            "SD/glue/campaign/Prot.webm", "SD/glue/campaign/Terr.webm", "SD/glue/campaign/Zerg.webm"
    };
    /**
     * The repeated decoding rounds, the number of streams open at once and the bytes of one RGBA pixel.
     */
    private static final int ROUNDS = 12;
    private static final int OPEN_AT_ONCE = 24;
    private static final int RGBA_BYTES_PER_PIXEL = 4;

    /**
     * Collects video controls recursively in their dialog-tree order.
     *
     * @param controls the controls at the current tree level
     * @return the collected video controls
     */
    private static @NotNull List<DialogControl> collectVideoElements(@NotNull List<DialogControl> controls) {
        List<DialogControl> found = new ArrayList<>();

        for (DialogControl control : controls) {
            if (ScrDialogControlUtils.getType(control) == ScrControlType.FLC) {
                found.add(control);
            }

            found.addAll(collectVideoElements(control.getChildren()));
        }

        return found;
    }

    private static byte @NotNull [] @NotNull [] readClipContents() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            byte[][] clips = new byte[CLIPS.length][];

            for (int index = 0; index < CLIPS.length; index++) {
                clips[index] = storage.readFile(CLIPS[index]);
            }

            return clips;
        }
    }

    @Test
    void ready_room_videos_follow_their_loop_flag() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            List<DialogControl> terran = collectVideoElements(ScrDialogImportUtils.load(storage, TERRAN_READY_ROOM).getControls());
            int playOnceCount = 0;

            for (DialogControl element : terran) {
                try (GlueVideo video = new GlueVideo(element, null)) {
                    boolean projector = ScrDialogControlUtils.getContent(element).toLowerCase(Locale.ROOT).endsWith(PROJECTOR_VIDEO_SUFFIX);

                    Assertions.assertEquals(!projector, video.isLooping(), ScrDialogControlUtils.getContent(element));
                    playOnceCount += projector ? 1 : 0;
                }
            }

            Assertions.assertEquals(PROJECTOR_PIECE_COUNT, playOnceCount, "the SD and HD projector pieces play once");

            for (String name : List.of(ZERG_READY_ROOM, PROTOSS_READY_ROOM)) {
                for (DialogControl element : collectVideoElements(ScrDialogImportUtils.load(storage, name).getControls())) {
                    try (GlueVideo video = new GlueVideo(element, null)) {
                        Assertions.assertTrue(video.isLooping(), name + " " + ScrDialogControlUtils.getContent(element));
                    }
                }
            }

            Assertions.assertFalse(terran.isEmpty());
        }
    }

    @Test
    void glue_clips_open_decode_and_close_repeatedly() throws IOException {
        byte[][] clips = readClipContents();

        for (int round = 0; round < ROUNDS; round++) {
            for (byte[] clip : clips) {
                try (VideoStream stream = VideoStream.open(clip, false)) {
                    byte[] frame = stream.readNextLoopedFrame();

                    Assertions.assertNotNull(frame);
                    Assertions.assertEquals(stream.getWidth() * stream.getHeight() * RGBA_BYTES_PER_PIXEL, frame.length);
                }
            }
        }
    }

    @Test
    void many_glue_clips_stay_open_and_decode_at_once() throws IOException {
        byte[][] clips = readClipContents();
        List<VideoStream> streams = new ArrayList<>();

        try {
            for (int index = 0; index < OPEN_AT_ONCE; index++) {
                VideoStream stream = VideoStream.open(clips[index % clips.length], false);

                streams.add(stream);

                Assertions.assertNotNull(stream.readNextLoopedFrame());
            }

            for (VideoStream stream : streams) {
                Assertions.assertNotNull(stream.readNextLoopedFrame());
            }
        } finally {
            for (VideoStream stream : streams) {
                stream.close();
            }
        }
    }
}
