package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verifies that DDS frame containers expose their frames, reject a wrong declared size and match the installed tileset assets.
 */
final class DdsGrpFileTest {
    /**
     * The size of the container header: the declared file size DWORD, the frame count WORD and the version WORD.
     */
    private static final int CONTAINER_HEADER_SIZE = 8;
    /**
     * The size of a frame header: the unknown DWORD, the width and height WORDs and the texture size DWORD.
     */
    private static final int FRAME_HEADER_SIZE = 12;

    private static final int VX4EX_MEGATILE_SIZE = 64;
    private static final int HD_TILE_SIZE = 128;
    private static final int HD2_TILE_SIZE = 64;
    private static final int HD_SCALE = 4;

    @Test
    void dds_grp_containers_expose_frames_and_reject_wrong_declared_size() throws IOException {
        ByteBuffer first = DdsFileScenario.createCompressedHeader(4, 4, DdsFileScenario.FOUR_CHARACTER_DXT1, DdsFileScenario.DXT1_BLOCK_SIZE);

        first.putShort((short) 0xF800).putShort((short) 0).putInt(0);

        byte[] textureContents = first.array();
        int containerSize = CONTAINER_HEADER_SIZE + 2 * (FRAME_HEADER_SIZE + textureContents.length);
        ByteBuffer container = ByteBuffer.allocate(containerSize).order(ByteOrder.LITTLE_ENDIAN);

        container.putInt(container.capacity()).putShort((short) 2).putShort((short) 0x1004);

        for (int frameIndex = 0; frameIndex < 2; frameIndex++) {
            container.putInt(0).putShort((short) 4).putShort((short) 4).putInt(textureContents.length).put(textureContents);
        }

        DdsGrpFile file = DdsGrpFile.parse(container.array());

        Assertions.assertEquals(2, file.getFrameCount());
        Assertions.assertEquals(0x1004, file.getVersion());
        Assertions.assertEquals(4, file.getFrameWidth(1));
        DdsPixelAssert.assertPixel(file.getFrame(1).decodeRgba(), 0, 255, 0, 0, 255);
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> file.getFrame(2));

        container.putInt(0, container.capacity() + 1);

        Assertions.assertThrows(IOException.class, () -> DdsGrpFile.parse(container.array()));
    }

    @RequiresInstallation
    @Test
    void installed_tileset_containers_match_megatiles_and_classic_creep_edges() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            int megatileCount = storage.readFile("tileset/jungle.vx4ex").length / VX4EX_MEGATILE_SIZE;
            DdsGrpFile highResolutionTiles = DdsGrpFile.parse(storage.readFile("TileSet/Jungle.dds.vr4"));
            DdsGrpFile reducedResolutionTiles = DdsGrpFile.parse(storage.readFile("HD2/TileSet/Jungle.dds.vr4"));

            Assertions.assertEquals(megatileCount, highResolutionTiles.getFrameCount(), "One HD texture per VX4 megatile.");
            Assertions.assertEquals(megatileCount, reducedResolutionTiles.getFrameCount());
            Assertions.assertEquals(HD_TILE_SIZE, highResolutionTiles.getFrame(0).getWidth());
            Assertions.assertEquals(HD2_TILE_SIZE, reducedResolutionTiles.getFrame(megatileCount - 1).getHeight());
            Assertions.assertEquals(DdsFile.Format.DXT1, reducedResolutionTiles.getFrame(1).getFormat());

            GrpFile classicEdges = GrpFile.parse(storage.readFile("tileset/jungle.grp"));
            DdsGrpFile highResolutionEdges = DdsGrpFile.parse(storage.readFile("TileSet/Jungle.dds.grp"));

            Assertions.assertEquals(classicEdges.getFrameCount(), highResolutionEdges.getFrameCount());

            // HD creep-edge frames cover the classic frame rectangle at four times the size, padded to block sizes and kept
            // inside the 128-pixel tile.
            for (int frameIndex = 0; frameIndex < highResolutionEdges.getFrameCount(); frameIndex++) {
                Assertions.assertTrue(highResolutionEdges.getFrameWidth(frameIndex) <= HD_TILE_SIZE &&
                                      highResolutionEdges.getFrameHeight(frameIndex) <= HD_TILE_SIZE,
                                      "Creep edge frame " + frameIndex);
                Assertions.assertTrue(highResolutionEdges.getFrameWidth(frameIndex) >= classicEdges.getFrameWidth(frameIndex) * HD_SCALE,
                                      "Creep edge frame " + frameIndex);
                Assertions.assertTrue(highResolutionEdges.getFrameHeight(frameIndex) + 4 >= classicEdges.getFrameHeight(frameIndex) * HD_SCALE,
                                      "Creep edge frame " + frameIndex);
            }
        }
    }
}
