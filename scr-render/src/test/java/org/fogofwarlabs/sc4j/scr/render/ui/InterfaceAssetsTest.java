package org.fogofwarlabs.sc4j.scr.render.ui;

import org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.image.ImageFileWriterUtils;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModFiles;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModSheetFrame;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModWireframe;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tests the interface sheet frames of the installation and the added ones of a mod: an added command icon or wireframe frame decodes from the
 * mod's file of the asset set (the SD file where the mod gives none), at its interface size, while the installed frames stay the installed ones.
 */
@RequiresInstallation
final class InterfaceAssetsTest {
    private static final @NotNull String SD_ICON = "icon-sd.dds.grp";
    private static final @NotNull String HD2_ICON = "icon-hd2.dds.grp";
    private static final @NotNull String HD_ICON = "icon-hd.dds.grp";
    private static final @NotNull String SD_WIREFRAME = "wirefram-sd.dds.grp";
    private static final @NotNull String GROUP_WIREFRAME = "grpwire-sd.dds.grp";
    private static final @NotNull String TRANSPORT_WIREFRAME = "tranwire-sd.dds.grp";
    private static final int ICON_SIZE = 32;
    private static final int ICON_VISIBLE_SIZE = 28;
    private static final int WIREFRAME_HEIGHT = 64;
    private static final int GROUP_WIREFRAME_HEIGHT = 32;
    private static final int CELL_COUNT = 6;
    private static final int HD2_SCALE = 2;
    private static final int HD_SCALE = 4;
    private static final int TILESET = 0;

    private static byte @NotNull [] createSheet(int width, int height, int scale) {
        byte[] rgba = new byte[width * height * ImageFileWriterUtils.RGBA_BYTES];

        Arrays.fill(rgba, (byte) -1);

        return ImageFileWriterUtils.encodeDdsGrp(scale, List.of(ImageFileWriterUtils.encodeDxt5(width, height, rgba, true)));
    }

    @Test
    void added_sheet_frames_decode_from_the_mod_files_of_the_asset_set() throws IOException {
        Map<String, byte[]> files = Map.of(SD_ICON, createSheet(ICON_VISIBLE_SIZE, ICON_VISIBLE_SIZE, 1),
                                           HD2_ICON, createSheet(ICON_SIZE * HD2_SCALE, ICON_SIZE * HD2_SCALE, HD2_SCALE),
                                           HD_ICON, createSheet(ICON_SIZE * HD_SCALE, ICON_SIZE * HD_SCALE, HD_SCALE),
                                           SD_WIREFRAME, createSheet(WIREFRAME_HEIGHT * CELL_COUNT, WIREFRAME_HEIGHT, 1),
                                           GROUP_WIREFRAME, createSheet(GROUP_WIREFRAME_HEIGHT * CELL_COUNT, GROUP_WIREFRAME_HEIGHT, 1),
                                           TRANSPORT_WIREFRAME, createSheet(GROUP_WIREFRAME_HEIGHT * CELL_COUNT, GROUP_WIREFRAME_HEIGHT, 1));
        ScrModFiles mod = path -> files.get(ScrModFiles.checkPath(path)).clone();
        ScrAddedGraphics added = new ScrAddedGraphics(0);
        int icon = added.addCommandIcon(new ScrModSheetFrame.Builder(mod, SD_ICON).setHd2(HD2_ICON).setHd(HD_ICON).build());
        int wireframe = added.addWireframe(new ScrModWireframe(new ScrModSheetFrame.Builder(mod, SD_WIREFRAME).build(),
                                                               new ScrModSheetFrame.Builder(mod, GROUP_WIREFRAME).build(),
                                                               new ScrModSheetFrame.Builder(mod, TRANSPORT_WIREFRAME).build()));

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            InterfaceAssets assets = InterfaceAssets.load(ScrResourceProvider.borrow(storage, added), TILESET);
            int nativeIcons = assets.getSheetFrameCount(ScrAddedGraphics.COMMAND_ICON_SHEET);
            int nativeWidth = assets.getClassicCommandIconWidth(0);

            Assertions.assertEquals(ScrAddedGraphics.FIRST_INTERFACE_FRAME, icon);
            Assertions.assertEquals(ScrAddedGraphics.FIRST_INTERFACE_FRAME, wireframe);
            Assertions.assertTrue(nativeIcons <= ScrAddedGraphics.FIRST_INTERFACE_FRAME, "The installed icons stay below the added frames.");
            Assertions.assertTrue(assets.isAddedSheetFrame(ScrAddedGraphics.COMMAND_ICON_SHEET, icon));
            Assertions.assertTrue(assets.isAddedSheetFrame(ScrAddedGraphics.GROUP_WIREFRAME_SHEET, wireframe));
            Assertions.assertFalse(assets.isAddedSheetFrame(ScrAddedGraphics.COMMAND_ICON_SHEET, icon + 1));
            Assertions.assertFalse(assets.isAddedSheetFrame(ScrAddedGraphics.COMMAND_ICON_SHEET, 0));
            Assertions.assertEquals(ICON_VISIBLE_SIZE, assets.getClassicCommandIconWidth(icon));
            Assertions.assertEquals(ICON_VISIBLE_SIZE, assets.getClassicCommandIconHeight(icon));
            Assertions.assertEquals(nativeWidth, assets.getClassicCommandIconWidth(0));

            for (AssetResolver.AssetSet set : AssetResolver.AssetSet.values()) {
                assets.setAssetSet(set);

                InterfaceAssets.ScaledImage image = assets.decodeSheetFrame(ScrAddedGraphics.COMMAND_ICON_SHEET, icon);
                InterfaceAssets.ScaledImage strip = assets.decodeSheetFrame(ScrAddedGraphics.WIREFRAME_SHEET, wireframe);
                InterfaceAssets.ScaledImage group = assets.decodeSheetFrame(ScrAddedGraphics.GROUP_WIREFRAME_SHEET, wireframe);
                InterfaceAssets.ScaledImage installed = assets.decodeSheetFrame(ScrAddedGraphics.COMMAND_ICON_SHEET, 0);

                Assertions.assertEquals(set == AssetResolver.AssetSet.SD ? ICON_VISIBLE_SIZE : ICON_SIZE, image.getInterfaceWidth(), set.name());
                Assertions.assertEquals(1, strip.getScale(), set + ": the wireframe takes the SD strip where the mod gives none");
                Assertions.assertEquals(WIREFRAME_HEIGHT, strip.getInterfaceHeight());
                Assertions.assertEquals(GROUP_WIREFRAME_HEIGHT, group.getInterfaceHeight());
                Assertions.assertEquals(nativeIcons, assets.getSheetFrameCount(ScrAddedGraphics.COMMAND_ICON_SHEET));
                Assertions.assertEquals(ICON_SIZE, installed.getInterfaceWidth(), set + ": the installed icon stays the installed one");
            }
        }
    }
}
