package org.fogofwarlabs.sc4j.scr.data.resource;

import org.fogofwarlabs.sc4j.engine.resource.ResourceKey;
import org.fogofwarlabs.sc4j.engine.resource.ResourceNotFoundException;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogDefinition;
import org.fogofwarlabs.sc4j.engine.resource.graphics.*;
import org.fogofwarlabs.sc4j.engine.resource.text.FontFace;
import org.fogofwarlabs.sc4j.engine.resource.text.FontGlyph;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.format.image.AnimFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Tests the installation's resources as engine model objects: every unit GRP sheet against its decoded frames, the tileset
 * megatiles, the game fonts and every interface file.
 */
@RequiresInstallation
final class ScrResourceProviderTest {
    private static final @NotNull String @NotNull [] TILESETS = {
            "badlands", "platform", "install", "ashworld", "jungle", "desert", "ice", "twilight"
    };
    private static final @NotNull String @NotNull [] FONTS = {
            "font8", "font10", "font14", "font16", "font16x"
    };
    /**
     * The Marine image and the number of its HD anim file.
     */
    private static final int MARINE_IMAGE = 239;
    private static final @NotNull String MARINE_IMAGE_FILE = "239";
    private static final @NotNull String CARBOT_SKIN = "Carbot";

    @Test
    void grp_sheets_match_the_decoded_frames_of_every_unit_grp_and_every_image_has_one() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            ScrResourceProvider provider = ScrResourceProvider.borrow(storage);
            int grpFiles = 0;
            int images = 0;

            for (String path : storage.getFileNames()) {
                if (path.startsWith("unit/") && path.endsWith(".grp") && !path.endsWith(".dds.grp") && storage.isLocallyAvailable(path)) {
                    assertSheet(GrpFile.parse(storage.readFile(path)), provider.loadSpriteSheet(ScrResourceKeys.ofGrp(path)), path);
                    grpFiles++;
                }
            }

            for (int image = 0; image < ImagesDat.IMAGE_COUNT; image++) {
                Assertions.assertTrue(provider.loadSpriteSheet(ScrResourceKeys.ofImage(image)).getFrameCount() > 0, "image " + image);
                images++;
            }

            Assertions.assertTrue(grpFiles > 500, "GRP files " + grpFiles);
            Assertions.assertEquals(ImagesDat.IMAGE_COUNT, images);
            Assertions.assertThrows(ResourceNotFoundException.class, () -> provider.loadSpriteSheet(ScrResourceKeys.ofImage(ImagesDat.IMAGE_COUNT)));
        }
    }

    @Test
    void tilesets_fonts_and_dialogs_import() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            ScrResourceProvider provider = ScrResourceProvider.borrow(storage);
            int dialogs = 0;

            for (String tileset : TILESETS) {
                TileGraphics graphics = provider.loadTileGraphics(ScrResourceKeys.ofTileset(tileset));

                Assertions.assertEquals(storage.readFile("tileset/" + tileset + ".vx4ex").length / 64, graphics.getTileCount(), tileset);
                Assertions.assertEquals(256, provider.loadPalette(ScrResourceKeys.ofPalette("tileset/" + tileset + ".wpe")).getSize(), tileset);
            }

            for (String font : FONTS) {
                FontFace face = provider.loadFont(ScrResourceKeys.ofFont(font));
                FontGlyph letter = face.findGlyph('A');

                Assertions.assertNotNull(letter, font);
                Assertions.assertTrue(letter.getWidth() > 0 && face.getLineHeight() >= letter.getHeight(), font);
                Assertions.assertNotNull(face.findGlyph('\t'), font);
            }

            for (String path : storage.getFileNames()) {
                if (path.startsWith("SD/rez/") && path.endsWith(".ui.json") && storage.isLocallyAvailable(path)) {
                    String name = path.substring("SD/rez/".length(), path.length() - ".ui.json".length());
                    DialogDefinition definition = provider.loadDialog(ResourceKey.of(ScrResourceKeys.DIALOG_PREFIX + name));

                    Assertions.assertNotNull(definition.getName(), path);
                    dialogs++;
                }
            }

            DialogDefinition main = provider.loadDialog(ScrResourceKeys.ofDialog("gluMain"));

            Assertions.assertTrue(dialogs > 100, "dialogs " + dialogs);
            Assertions.assertFalse(main.getControls().isEmpty());
            Assertions.assertTrue(main.getControls().stream().anyMatch(control -> ScrDialogControlUtils.hasFlag(control, "WS_VISIBLE")));
            Assertions.assertThrows(ResourceNotFoundException.class, () -> provider.loadDialog(ScrResourceKeys.ofDialog("noSuchDialog")));
        }
    }

    @Test
    void texture_sheets_hold_the_compressed_layers_of_the_hd_anims_of_each_quality_and_skin() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            ScrResourceProvider provider = ScrResourceProvider.borrow(storage);
            TextureSheet hd = provider.loadTextureSheet(ScrResourceKeys.ofTexture(AssetResolver.AssetSet.HD, null, MARINE_IMAGE));
            TextureSheet half = provider.loadTextureSheet(ScrResourceKeys.ofTexture(AssetResolver.AssetSet.HD2, null, MARINE_IMAGE));
            TextureSheet skinned = provider.loadTextureSheet(ScrResourceKeys.ofTexture(AssetResolver.AssetSet.HD, CARBOT_SKIN, MARINE_IMAGE));
            CompressedTexture diffuse = hd.findLayer("diffuse");
            AnimFile file = AnimFile.parse(storage.readFile("anim/main_" + MARINE_IMAGE_FILE + ".anim"));
            DdsFile expected = file.getEntry(0).getLayerTexture(file.findLayer("diffuse"));

            Assertions.assertNotNull(diffuse);
            Assertions.assertNotNull(expected);
            Assertions.assertEquals(1, hd.getUnitsPerTexel());
            Assertions.assertEquals(2, half.getUnitsPerTexel());
            Assertions.assertEquals(file.getEntry(0).getFrameCount(), hd.getFrameCount());
            Assertions.assertEquals(file.getEntry(0).getFrameX(1), hd.getFrame(1).getTextureX());
            Assertions.assertEquals(CompressionFormat.BC3, diffuse.getFormat());
            Assertions.assertTrue(hd.getLayerNames().contains("teamcolor"));
            Assertions.assertNotNull(skinned.findLayer("diffuse"));

            ByteBuffer blocks = ByteBuffer.allocate(diffuse.getLevelSize(0));

            diffuse.copyLevel(0, blocks);
            Assertions.assertArrayEquals(expected.decodeRgba(),
                                         DdsFile.decodeBlocks(DdsFile.Format.DXT5, diffuse.getWidth(), diffuse.getHeight(), blocks.array(), 0));
            Assertions.assertSame(provider.loadSkin(AssetResolver.AssetSet.HD, null), provider.loadSkin(AssetResolver.AssetSet.HD, null));
            Assertions.assertThrows(ResourceNotFoundException.class,
                                    () -> provider.loadTextureSheet(ScrResourceKeys.ofTexture(AssetResolver.AssetSet.HD, null, ImagesDat.IMAGE_COUNT + 1)));
            Assertions.assertThrows(ResourceNotFoundException.class, () -> provider.loadTextureSheet(ScrResourceKeys.ofImage(MARINE_IMAGE)));
        }
    }

    private static void assertSheet(@NotNull GrpFile file, @NotNull SpriteSheet sheet, @NotNull String path) throws IOException {
        Assertions.assertEquals(file.getFrameCount(), sheet.getFrameCount(), path);
        Assertions.assertEquals(file.getWidth(), sheet.getWidth(), path);
        Assertions.assertEquals(file.getHeight(), sheet.getHeight(), path);

        for (int frameIndex = 0; frameIndex < file.getFrameCount(); frameIndex++) {
            GrpFile.Frame frame = file.decodeFrame(frameIndex);
            SpriteFrame stored = sheet.getFrame(frameIndex);

            Assertions.assertEquals(frame.getXOffset(), stored.getX(), path);
            Assertions.assertEquals(frame.getYOffset(), stored.getY(), path);
            Assertions.assertEquals(frame.getFrameWidth(), stored.getWidth(), path);
            Assertions.assertEquals(frame.getFrameHeight(), stored.getHeight(), path);

            for (int pixel = 0; pixel < frame.getPixels().length; pixel++) {
                int column = pixel % frame.getWidth() - stored.getX();
                int row = pixel / frame.getWidth() - stored.getY();
                boolean inside = column >= 0 && row >= 0 && column < stored.getWidth() && row < stored.getHeight();
                int value = inside ? sheet.getColor().getValue(stored.getPixelOffset() + row * stored.getWidth() + column) : 0;

                Assertions.assertEquals(frame.getPixels()[pixel] & 0xFF, value, path);
                Assertions.assertEquals(frame.getCoverage()[pixel] != 0, value != 0, path);
            }
        }
    }
}
