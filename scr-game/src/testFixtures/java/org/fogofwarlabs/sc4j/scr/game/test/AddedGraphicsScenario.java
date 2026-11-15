package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.ImageType;
import org.fogofwarlabs.sc4j.engine.content.type.MotionProfile;
import org.fogofwarlabs.sc4j.engine.content.type.SpriteType;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.data.format.dat.FlingyDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.SpritesDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.image.ImageFileWriterUtils;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptCode;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptOpcodes;
import org.fogofwarlabs.sc4j.scr.data.format.replay.*;
import org.fogofwarlabs.sc4j.scr.data.resource.*;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registers a test unit with added graphics generated here, not read from any art file: a copy of the Terran Marine whose flingy entry, sprite,
 * image, shadow image and script are added entries ({@link ScrAddedGraphics}), with synthetic SD frames (a GRP of {@link #SET_COUNT} frame sets of
 * {@link #DIRECTIONS} directions, team color indices in every frame) and synthetic HD and HD2 frames (ANIM files of DXT5 layers) in in-memory mod
 * files. Its script plays the idle, walk, attack and death frame sets with the native iscript instructions, and the Command Center shows its train
 * button. The scenario also writes a replay that trains the unit and plays it back.
 */
public final class AddedGraphicsScenario {
    /**
     * The directions of a frame set, north through south clockwise; the renderer mirrors the other fifteen.
     */
    public static final int DIRECTIONS = 17;
    /**
     * The first frame set of the main image, which holds the idle frames; the frame sets follow in frame order.
     */
    public static final int IDLE_SET = 0;
    /**
     * The frame set of the main image that holds the first walking frames.
     */
    public static final int WALK_SET = 1;
    /**
     * The frame set of the main image that holds the second walking frames.
     */
    public static final int SECOND_WALK_SET = 2;
    /**
     * The frame set of the main image that holds the attack frames.
     */
    public static final int ATTACK_SET = 3;
    /**
     * The frame set of the main image that holds the death frames.
     */
    public static final int DEATH_SET = 4;
    /**
     * The number of frame sets of the main image.
     */
    public static final int SET_COUNT = 5;
    /**
     * The frames of the main image and of its shadow, which follows the main image's frame.
     */
    public static final int FRAME_COUNT = SET_COUNT * DIRECTIONS;
    /**
     * The SD canvas side of the frames.
     */
    public static final int CANVAS_SIZE = 32;
    /**
     * The palette index of the body.
     */
    public static final int BODY_INDEX = 0x2A;
    /**
     * The palette index of the direction marker.
     */
    public static final int MARKER_INDEX = 0xA5;
    /**
     * The first palette index of the team color stripe, which uses the indices {@code 8..15}.
     */
    public static final int FIRST_TEAM_INDEX = 8;
    /**
     * The number of palette indices of the team color stripe.
     */
    public static final int TEAM_INDEX_COUNT = 8;
    /**
     * The palette index of the shadow.
     */
    public static final int SHADOW_INDEX = 0x01;
    /**
     * The name of the test unit and of its added entries.
     */
    public static final @NotNull String NAME = "Test Walker";
    /**
     * The Command Center position of the test unit's train button, where the Command Center shows Stop only while it flies.
     */
    public static final int TRAIN_BUTTON_POSITION = 2;
    /**
     * The hotkey of the test unit's train button.
     */
    public static final char TRAIN_BUTTON_HOTKEY = 'w';
    /**
     * The tooltip text of the test unit's train button.
     */
    public static final @NotNull String TRAIN_BUTTON_TEXT = "Train " + NAME;
    /**
     * The SD side of the generated command icon ({@link #registerWithInterfaceArt}); the HD2 icon is twice and the HD icon four times as large.
     */
    public static final int COMMAND_ICON_SIZE = 32;
    /**
     * The SD cell side of the generated single-selection wireframe.
     */
    public static final int WIREFRAME_CELL_SIZE = 64;
    /**
     * The SD cell side of the generated group and cargo wireframes.
     */
    public static final int GROUP_WIREFRAME_CELL_SIZE = 32;
    /**
     * The number of cells of each generated SD wireframe strip.
     */
    public static final int WIREFRAME_CELL_COUNT = 6;
    /**
     * The HD cell side of the generated single-selection wireframe.
     */
    public static final int HD_WIREFRAME_CELL_SIZE = 256;
    /**
     * The number of cells of the generated HD wireframe strip.
     */
    public static final int HD_WIREFRAME_CELL_COUNT = 8;
    /**
     * The path prefix of the generated SD idle portrait clips, which HD2 also plays. Each clip file holds its own path in ASCII.
     */
    public static final @NotNull String SD_IDLE_CLIPS = "portrait/test-walker/sd/idle";
    /**
     * The path prefix of the generated SD talking portrait clips, which HD2 also plays.
     */
    public static final @NotNull String SD_TALKING_CLIPS = "portrait/test-walker/sd/talking";
    /**
     * The path prefix of the generated HD idle portrait clips.
     */
    public static final @NotNull String HD_IDLE_CLIPS = "portrait/test-walker/hd/idle";
    /**
     * The path prefix of the generated HD talking portrait clips.
     */
    public static final @NotNull String HD_TALKING_CLIPS = "portrait/test-walker/hd/talking";
    /**
     * The number of generated idle portrait clips of each resolution.
     */
    public static final int IDLE_CLIP_COUNT = 4;
    /**
     * The number of generated talking portrait clips of each resolution.
     */
    public static final int TALKING_CLIP_COUNT = 3;

    private static final @NotNull String MAIN_FRAMES = "graphics/test-walker/main.grp";
    private static final @NotNull String SHADOW_FRAMES = "graphics/test-walker/shadow.grp";
    private static final @NotNull String MAIN_HD = "graphics/test-walker/main-hd.anim";
    private static final @NotNull String MAIN_HD2 = "graphics/test-walker/main-hd2.anim";
    private static final @NotNull String SHADOW_HD = "graphics/test-walker/shadow-hd.anim";
    private static final @NotNull String SHADOW_HD2 = "graphics/test-walker/shadow-hd2.anim";
    private static final int HD_SCALE = 4;
    private static final int HD2_SCALE = 2;
    private static final int HD_FRAMES_PER_ROW = DIRECTIONS;
    private static final @NotNull String SD_COMMAND_ICON = "interface/test-walker/cmdicon-sd.dds.grp";
    private static final @NotNull String HD2_COMMAND_ICON = "interface/test-walker/cmdicon-hd2.dds.grp";
    private static final @NotNull String HD_COMMAND_ICON = "interface/test-walker/cmdicon-hd.dds.grp";
    private static final @NotNull String SD_WIREFRAME = "interface/test-walker/wirefram-sd.dds.grp";
    private static final @NotNull String HD_WIREFRAME = "interface/test-walker/wirefram-hd.dds.grp";
    private static final @NotNull String GROUP_WIREFRAME = "interface/test-walker/grpwire-sd.dds.grp";
    private static final @NotNull String TRANSPORT_WIREFRAME = "interface/test-walker/tranwire-sd.dds.grp";
    private static final int ICON_GRAY = 0x80;
    private static final int OPAQUE = 0xFF;

    private static final int ANIMATION_INIT = 0;
    private static final int ANIMATION_DEATH = 1;
    private static final int ANIMATION_GROUND_ATTACK_INIT = 2;
    private static final int ANIMATION_GROUND_ATTACK_REPEAT = 5;
    private static final int ANIMATION_GROUND_ATTACK_TO_IDLE = 8;
    private static final int ANIMATION_WALKING = 11;
    private static final int ANIMATION_WALKING_TO_IDLE = 12;
    private static final int IDLE_WAIT = 125;
    private static final int WALK_STEP = 4;
    private static final int DEATH_WAIT = 3;

    private static final int BUILD_TIME = 30;
    private static final int HIT_POINTS = 40 << 8;
    private static final int TRAIN_FRAME = 400;
    private static final int UNIT_OFFSET_Y = 96;
    private static final int @NotNull [] HD_BODY_COLOR = {0x40, 0x60, 0xA0, 0xFF};
    private static final int @NotNull [] HD_MARKER_COLOR = {0xF0, 0xE0, 0x20, 0xFF};
    private static final int @NotNull [] HD_TEAM_COLOR = {0xFF, 0xFF, 0xFF, 0xFF};
    private static final int @NotNull [] HD_SHADOW_COLOR = {0, 0, 0, 0xFF};

    /**
     * Registers the test unit and its graphics into an installed data, before its game definition is built. The unit shows the interface art of
     * its wireframe type and the portrait of its base type.
     *
     * @param gameData the installed data
     * @return the created {@link AddedGraphicsScenario} instance
     * @throws IOException if the installed script of the base image cannot be read
     */
    public static @NotNull AddedGraphicsScenario register(@NotNull GameData gameData) throws IOException {
        return register(gameData, false);
    }

    private static @NotNull AddedGraphicsScenario register(@NotNull GameData gameData, boolean interfaceArt) {
        int baseFlingy = gameData.getUnitTypes().getFlingyType(ScrUnitIds.TERRAN_MARINE);
        int baseSprite = gameData.getFlingyTypes().getSprite(baseFlingy);
        int baseImage = gameData.getSpriteTypes().getImage(baseSprite);
        int baseScript = gameData.getImageTypes().getIscript(baseImage);
        int baseShadow = findShadowImage(gameData, baseScript);
        Map<String, byte[]> files = new HashMap<>();
        List<byte[]> mainFrames = new ArrayList<>();
        List<byte[]> shadowFrames = new ArrayList<>();

        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            mainFrames.add(createMainFrame(frame / DIRECTIONS, frame % DIRECTIONS));
            shadowFrames.add(createShadowFrame(frame / DIRECTIONS));
        }

        files.put(MAIN_FRAMES, ImageFileWriterUtils.encodeGrp(CANVAS_SIZE, CANVAS_SIZE, mainFrames));
        files.put(SHADOW_FRAMES, ImageFileWriterUtils.encodeGrp(CANVAS_SIZE, CANVAS_SIZE, shadowFrames));
        files.put(MAIN_HD, createAnimation(mainFrames, HD_SCALE, false));
        files.put(MAIN_HD2, createAnimation(mainFrames, HD2_SCALE, false));
        files.put(SHADOW_HD, createAnimation(shadowFrames, HD_SCALE, true));
        files.put(SHADOW_HD2, createAnimation(shadowFrames, HD2_SCALE, true));
        files.put(SD_COMMAND_ICON, createSheetFrame(COMMAND_ICON_SIZE, COMMAND_ICON_SIZE, 1, false));
        files.put(HD2_COMMAND_ICON, createSheetFrame(COMMAND_ICON_SIZE * HD2_SCALE, COMMAND_ICON_SIZE * HD2_SCALE, HD2_SCALE, false));
        files.put(HD_COMMAND_ICON, createSheetFrame(COMMAND_ICON_SIZE * HD_SCALE, COMMAND_ICON_SIZE * HD_SCALE, HD_SCALE, false));
        files.put(SD_WIREFRAME, createSheetFrame(WIREFRAME_CELL_SIZE * WIREFRAME_CELL_COUNT, WIREFRAME_CELL_SIZE, 1, true));
        files.put(HD_WIREFRAME, createSheetFrame(HD_WIREFRAME_CELL_SIZE * HD_WIREFRAME_CELL_COUNT, HD_WIREFRAME_CELL_SIZE, HD_SCALE, true));
        files.put(GROUP_WIREFRAME, createSheetFrame(GROUP_WIREFRAME_CELL_SIZE * WIREFRAME_CELL_COUNT, GROUP_WIREFRAME_CELL_SIZE, 1, true));
        files.put(TRANSPORT_WIREFRAME, createSheetFrame(GROUP_WIREFRAME_CELL_SIZE * WIREFRAME_CELL_COUNT, GROUP_WIREFRAME_CELL_SIZE, 1, true));

        for (String prefix : List.of(SD_IDLE_CLIPS, HD_IDLE_CLIPS, SD_TALKING_CLIPS, HD_TALKING_CLIPS)) {
            int count = prefix.endsWith("idle") ? IDLE_CLIP_COUNT : TALKING_CLIP_COUNT;

            for (int clip = 0; clip < count; clip++) {
                String path = prefix + clip + ScrModPortrait.CLIP_EXTENSION;

                files.put(path, path.getBytes(StandardCharsets.US_ASCII));
            }
        }

        AtomicInteger fileReads = new AtomicInteger();
        ScrModFiles modFiles = path -> {
            byte[] contents = files.get(ScrModFiles.checkPath(path));

            if (contents == null) {
                throw Exceptions.newIOException("The test mod has no file {0}.", path);
            }

            fileReads.incrementAndGet();

            return contents.clone();
        };
        ScrAddedGraphics graphics = gameData.getAddedGraphics();
        int shadowImage = graphics.addImage(new ScrAddedImage.Builder(new ImageType(NAME + " shadow"), baseShadow, gameData.getImages())
                                                    .setGraphics(new ScrModGraphics.Builder(modFiles, SHADOW_FRAMES).setHdAnimation(SHADOW_HD)
                                                                                                                    .setHd2Animation(SHADOW_HD2)
                                                                                                                    .build())
                                                    .build());
        int script = graphics.addScript(createScript(gameData, baseScript, shadowImage));
        int image = graphics.addImage(new ScrAddedImage.Builder(new ImageType(NAME), baseImage, gameData.getImages())
                                              .setValue(ImagesDat.ISCRIPT, script)
                                              .setGraphics(new ScrModGraphics.Builder(modFiles, MAIN_FRAMES).setHdAnimation(MAIN_HD)
                                                                                                            .setHd2Animation(MAIN_HD2)
                                                                                                            .build())
                                              .build());
        int sprite = graphics.addSprite(new ScrAddedSprite.Builder(new SpriteType(NAME), baseSprite, gameData.getSprites())
                                                .setValue(SpritesDat.IMAGE, image)
                                                .build());
        int flingy = graphics.addFlingy(new ScrAddedFlingy.Builder(new MotionProfile(NAME), baseFlingy, gameData.getFlingy())
                                                .setValue(FlingyDat.SPRITE, sprite)
                                                .build());
        ScrAddedUnitType.Builder unit = new ScrAddedUnitType.Builder(new UnitType(NAME), NAME, ScrUnits.TERRAN_MARINE, gameData.getUnits())
                .setRequirementType(ScrUnits.TERRAN_SCV)
                .setValue(UnitsDat.FLINGY_TYPE, flingy)
                .setValue(UnitsDat.MINERAL_COST, 0)
                .setValue(UnitsDat.SUPPLY_REQUIRED, 0)
                .setValue(UnitsDat.BUILD_TIME, BUILD_TIME)
                .setValue(UnitsDat.HIT_POINTS, HIT_POINTS)
                .setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.TERRAN_COMMAND_CENTER, TRAIN_BUTTON_POSITION,
                                                                 TRAIN_BUTTON_HOTKEY, TRAIN_BUTTON_TEXT));

        if (interfaceArt) {
            int portrait = graphics.addPortrait(new ScrModPortrait.Builder(modFiles, SD_IDLE_CLIPS, SD_TALKING_CLIPS)
                                                        .setHd(HD_IDLE_CLIPS, HD_TALKING_CLIPS)
                                                        .build());
            int commandIcon = graphics.addCommandIcon(new ScrModSheetFrame.Builder(modFiles, SD_COMMAND_ICON).setHd2(HD2_COMMAND_ICON)
                                                                                                             .setHd(HD_COMMAND_ICON)
                                                                                                             .build());
            int wireframe = graphics.addWireframe(new ScrModWireframe(new ScrModSheetFrame.Builder(modFiles, SD_WIREFRAME).setHd(HD_WIREFRAME).build(),
                                                                      new ScrModSheetFrame.Builder(modFiles, GROUP_WIREFRAME).build(),
                                                                      new ScrModSheetFrame.Builder(modFiles, TRANSPORT_WIREFRAME).build()));

            unit.setValue(UnitsDat.PORTRAIT, portrait)
                .setCommandIcon(commandIcon)
                .setWireframe(wireframe);
        }

        int unitType = gameData.getAddedUnitTypes().add(unit.build());

        return new AddedGraphicsScenario(gameData, modFiles, fileReads, image, shadowImage, sprite, unitType);
    }

    /**
     * Registers the test unit and its graphics into an installed data as {@link #register} does, and gives the unit generated interface art of
     * its own: an added portrait (SD and HD clips), command icon (SD, HD2 and HD) and wireframe (SD views and HD; HD2 takes the SD strip).
     *
     * @param gameData the installed data
     * @return the created {@link AddedGraphicsScenario} instance
     * @throws IOException if the installed script of the base image cannot be read
     */
    public static @NotNull AddedGraphicsScenario registerWithInterfaceArt(@NotNull GameData gameData) throws IOException {
        return register(gameData, true);
    }

    /**
     * Finds a living unit of a type and owner.
     *
     * @param game     the game
     * @param owner    the owner
     * @param unitType the unit type
     * @return the unit, or {@code null} if the owner has none
     */
    public static @Nullable Unit findUnit(@NotNull Game game, int owner, int unitType) {
        UnitPool pool = game.getUnitSystem().requireUnitPool();

        for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (unit.getUnitId() == unitType && !unit.isDestroyed()) {
                return unit;
            }
        }

        return null;
    }

    /**
     * Creates a single-frame interface sheet: an opaque gray picture for a command icon, or white masks in a box inset by a quarter of each cell
     * for a wireframe strip.
     */
    private static byte @NotNull [] createSheetFrame(int width, int height, int scale, boolean wireframe) {
        byte[] rgba = new byte[width * height * ImageFileWriterUtils.RGBA_BYTES];
        int inset = height / 4;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cellX = x % height;
                boolean shown = !wireframe || cellX >= inset && cellX < height - inset && y >= inset && y < height - inset;
                int offset = (y * width + x) * ImageFileWriterUtils.RGBA_BYTES;

                if (shown) {
                    rgba[offset]     = (byte) (wireframe ? OPAQUE : ICON_GRAY);
                    rgba[offset + 1] = (byte) (wireframe ? OPAQUE : ICON_GRAY);
                    rgba[offset + 2] = (byte) (wireframe ? OPAQUE : ICON_GRAY);
                    rgba[offset + 3] = (byte) OPAQUE;
                }
            }
        }

        return ImageFileWriterUtils.encodeDdsGrp(scale, List.of(ImageFileWriterUtils.encodeDxt5(width, height, rgba, true)));
    }

    /**
     * Finds the shadow image the installed script of the base image creates in its {@code Init} animation ({@code imgul}).
     */
    private static int findShadowImage(@NotNull GameData gameData, int baseScript) {
        IscriptHeader header = gameData.getIscript().findHeader(baseScript);

        Assertions.assertNotNull(header);

        for (IscriptHeader.Animation animation : header.getAnimations()) {
            if (animation.getTypeIndex() == ANIMATION_INIT) {
                for (IscriptInstruction instruction : animation.getInstructions()) {
                    if (instruction.getOpcode() == IscriptOpcodes.IMGUL) {
                        return instruction.getOperands()[0];
                    }
                }
            }
        }

        throw Exceptions.newIllegalStateException("The base script {0} creates no shadow image.", baseScript);
    }

    private static @NotNull ScrAddedScript createScript(@NotNull GameData gameData, int baseScript, int shadowImage) {
        IscriptCode idle = new IscriptCode.Builder().instruction(IscriptOpcodes.IMGUL, shadowImage, 0, 0)
                                                    .instruction(IscriptOpcodes.PLAYFRAM, IDLE_SET * DIRECTIONS)
                                                    .label("idle")
                                                    .instruction(IscriptOpcodes.WAIT, IDLE_WAIT)
                                                    .jump("idle")
                                                    .build();
        IscriptCode toIdle = new IscriptCode.Builder().instruction(IscriptOpcodes.PLAYFRAM, IDLE_SET * DIRECTIONS)
                                                      .label("idle")
                                                      .instruction(IscriptOpcodes.WAIT, IDLE_WAIT)
                                                      .jump("idle")
                                                      .build();
        IscriptCode walk = new IscriptCode.Builder().label("walk")
                                                    .instruction(IscriptOpcodes.MOVE, WALK_STEP)
                                                    .instruction(IscriptOpcodes.PLAYFRAM, WALK_SET * DIRECTIONS)
                                                    .instruction(IscriptOpcodes.WAIT, 1)
                                                    .instruction(IscriptOpcodes.MOVE, WALK_STEP)
                                                    .instruction(IscriptOpcodes.PLAYFRAM, SECOND_WALK_SET * DIRECTIONS)
                                                    .instruction(IscriptOpcodes.WAIT, 1)
                                                    .jump("walk")
                                                    .build();
        IscriptCode attack = new IscriptCode.Builder().instruction(IscriptOpcodes.PLAYFRAM, ATTACK_SET * DIRECTIONS)
                                                      .instruction(IscriptOpcodes.WAIT, 1)
                                                      .instruction(IscriptOpcodes.ATTACK)
                                                      .instruction(IscriptOpcodes.WAIT, 1)
                                                      .instruction(IscriptOpcodes.PLAYFRAM, IDLE_SET * DIRECTIONS)
                                                      .instruction(IscriptOpcodes.WAIT, 2)
                                                      .instruction(IscriptOpcodes.GOTOREPEATATTK)
                                                      .instruction(IscriptOpcodes.IGNOREREST)
                                                      .instruction(IscriptOpcodes.PLAYFRAM, IDLE_SET * DIRECTIONS)
                                                      .label("idle")
                                                      .instruction(IscriptOpcodes.WAIT, IDLE_WAIT)
                                                      .jump("idle")
                                                      .build();
        IscriptCode death = new IscriptCode.Builder().instruction(IscriptOpcodes.PLAYFRAM, DEATH_SET * DIRECTIONS)
                                                     .instruction(IscriptOpcodes.WAIT, DEATH_WAIT)
                                                     .instruction(IscriptOpcodes.WAIT, DEATH_WAIT)
                                                     .instruction(IscriptOpcodes.END)
                                                     .build();

        return new ScrAddedScript.Builder(baseScript, gameData.getIscript()).setAnimation(ANIMATION_INIT, idle)
                                                                            .setAnimation(ANIMATION_DEATH, death)
                                                                            .setAnimation(ANIMATION_GROUND_ATTACK_INIT, attack)
                                                                            .setAnimation(ANIMATION_GROUND_ATTACK_REPEAT, attack)
                                                                            .setAnimation(ANIMATION_GROUND_ATTACK_TO_IDLE, toIdle)
                                                                            .setAnimation(ANIMATION_WALKING, walk)
                                                                            .setAnimation(ANIMATION_WALKING_TO_IDLE, toIdle)
                                                                            .build();
    }

    /**
     * Creates the SD canvas of one main frame: a body whose height shrinks in the death set and that leans by the frame set, a team color stripe
     * and a marker at the edge the direction faces.
     */
    private static byte @NotNull [] createMainFrame(int set, int direction) {
        byte[] canvas = new byte[CANVAS_SIZE * CANVAS_SIZE];
        int top = set == DEATH_SET ? CANVAS_SIZE / 2 + 4 : 8;
        int left = 10 + (set == WALK_SET ? 1 : set == SECOND_WALK_SET ? -1 : 0);

        for (int y = top; y < CANVAS_SIZE - 4; y++) {
            for (int x = left; x < left + 12; x++) {
                int stripe = y - top;

                canvas[y * CANVAS_SIZE + x] = (byte) (stripe >= 4 && stripe < 8 ? FIRST_TEAM_INDEX + (x - left) % TEAM_INDEX_COUNT : BODY_INDEX);
            }
        }

        double angle = Math.PI * direction / (DIRECTIONS - 1);
        int markerX = (int) Math.round(CANVAS_SIZE / 2.0 + Math.sin(angle) * 9) - 1;
        int markerY = (int) Math.round(CANVAS_SIZE / 2.0 - Math.cos(angle) * 9) - 1;
        int reach = set == ATTACK_SET ? 4 : 3;

        for (int y = markerY; y < markerY + reach; y++) {
            for (int x = markerX; x < markerX + reach; x++) {
                canvas[Math.clamp(y, 0, CANVAS_SIZE - 1) * CANVAS_SIZE + Math.clamp(x, 0, CANVAS_SIZE - 1)] = (byte) MARKER_INDEX;
            }
        }

        return canvas;
    }

    private static byte @NotNull [] createShadowFrame(int set) {
        byte[] canvas = new byte[CANVAS_SIZE * CANVAS_SIZE];
        int halfWidth = set == DEATH_SET ? 9 : 7;

        for (int y = CANVAS_SIZE - 7; y < CANVAS_SIZE - 2; y++) {
            for (int x = CANVAS_SIZE / 2 - halfWidth; x < CANVAS_SIZE / 2 + halfWidth; x++) {
                canvas[y * CANVAS_SIZE + x + 2] = (byte) SHADOW_INDEX;
            }
        }

        return canvas;
    }

    /**
     * Creates an ANIM file of the SD frames scaled up: one atlas row per frame set, a {@code diffuse} layer with fixed colors per palette index
     * group and a {@code teamcolor} layer over the team color indices; each frame covers the whole canvas, placed by its canvas offset.
     */
    private static byte @NotNull [] createAnimation(@NotNull List<byte @NotNull []> canvases, int scale, boolean shadow) {
        int cell = CANVAS_SIZE * scale;
        int unitsPerTexel = ImageFileWriterUtils.ANIM_UNIT_SCALE / scale;
        int canvasUnits = CANVAS_SIZE * ImageFileWriterUtils.ANIM_UNIT_SCALE;
        int rows = (canvases.size() + HD_FRAMES_PER_ROW - 1) / HD_FRAMES_PER_ROW;
        int atlasWidth = HD_FRAMES_PER_ROW * cell;
        int atlasHeight = rows * cell;
        byte[] diffuse = new byte[atlasWidth * atlasHeight * ImageFileWriterUtils.RGBA_BYTES];
        byte[] team = new byte[diffuse.length];
        List<int[]> frames = new ArrayList<>();

        for (int frame = 0; frame < canvases.size(); frame++) {
            byte[] canvas = canvases.get(frame);
            int atlasX = frame % HD_FRAMES_PER_ROW * cell;
            int atlasY = frame / HD_FRAMES_PER_ROW * cell;

            for (int y = 0; y < cell; y++) {
                for (int x = 0; x < cell; x++) {
                    int index = canvas[y / scale * CANVAS_SIZE + x / scale] & 0xFF;
                    int target = ((atlasY + y) * atlasWidth + atlasX + x) * ImageFileWriterUtils.RGBA_BYTES;
                    boolean teamIndex = index >= FIRST_TEAM_INDEX && index < FIRST_TEAM_INDEX + TEAM_INDEX_COUNT;
                    int[] color = index == 0 ? null : shadow ? HD_SHADOW_COLOR : teamIndex ? HD_TEAM_COLOR : index == MARKER_INDEX ? HD_MARKER_COLOR
                                                                                                                                   : HD_BODY_COLOR;

                    for (int channel = 0; channel < ImageFileWriterUtils.RGBA_BYTES && color != null; channel++) {
                        diffuse[target + channel] = (byte) color[channel];
                        team[target + channel]    = (byte) (teamIndex ? 0xFF : 0);
                    }
                }
            }

            frames.add(new int[]{atlasX * unitsPerTexel, atlasY * unitsPerTexel, 0, 0, canvasUnits, canvasUnits,
                                 ImageFileWriterUtils.ANIM_FRAME_CANVAS_PLACED});
        }

        Map<String, byte[]> layers = new LinkedHashMap<>();

        layers.put("diffuse", ImageFileWriterUtils.encodeDxt5(atlasWidth, atlasHeight, diffuse));
        layers.put("teamcolor", ImageFileWriterUtils.encodeDxt5(atlasWidth, atlasHeight, team));

        return ImageFileWriterUtils.encodeAnim(scale, canvasUnits, canvasUnits, frames, layers);
    }


    private final @NotNull GameData gameData;
    private final @NotNull ScrModFiles files;
    private final @NotNull AtomicInteger fileReads;

    private final int imageId;
    private final int shadowImageId;
    private final int spriteId;
    private final int unitTypeId;

    private AddedGraphicsScenario(@NotNull GameData gameData, @NotNull ScrModFiles files, @NotNull AtomicInteger fileReads,
                                  int imageId, int shadowImageId, int spriteId, int unitTypeId) {
        this.gameData  = gameData;
        this.files     = files;
        this.fileReads = fileReads;

        this.imageId       = imageId;
        this.shadowImageId = shadowImageId;
        this.spriteId      = spriteId;
        this.unitTypeId    = unitTypeId;
    }

    /**
     * @return the in-memory mod files that hold the generated frames
     */
    public @NotNull ScrModFiles getFiles() {
        return this.files;
    }

    /**
     * @return the number of files read from the in-memory mod files so far
     */
    public int getFileReadCount() {
        return this.fileReads.get();
    }

    /**
     * @return the id of the added main image
     */
    public int getImageId() {
        return this.imageId;
    }

    /**
     * @return the id of the added shadow image
     */
    public int getShadowImageId() {
        return this.shadowImageId;
    }

    /**
     * @return the id of the added sprite
     */
    public int getSpriteId() {
        return this.spriteId;
    }

    /**
     * @return the SC:R id of the added unit type
     */
    public int getUnitTypeId() {
        return this.unitTypeId;
    }


    /**
     * Creates a completed test unit below the human player's Command Center.
     *
     * @param game    the running game
     * @param offsetX the horizontal offset from the Command Center
     * @param offsetY the vertical offset from {@code 96} pixels below the Command Center
     * @param owner   the owner
     * @return the created unit
     */
    public @NotNull Unit createUnit(@NotNull Game game, int offsetX, int offsetY, int owner) {
        Sprite base = findCommandCenterSprite(game);

        return RunningGameScenario.createUnit(game, this.unitTypeId, base.getX() + offsetX, base.getY() + UNIT_OFFSET_Y + offsetY, owner);
    }

    /**
     * Creates a completed native unit to the right of a unit.
     *
     * @param game     the running game
     * @param unitType the native unit type
     * @param near     the unit it is placed beside
     * @param distance the horizontal distance
     * @param owner    the owner
     * @return the created unit
     */
    public @NotNull Unit createNativeUnit(@NotNull Game game, int unitType, @NotNull Unit near, int distance, int owner) {
        return RunningGameScenario.createUnit(game, unitType, near.getPositionX() + distance, near.getPositionY(), owner);
    }

    /**
     * Writes the replay of {@link GameTestResources#REPLAY_TERRAN_VS_COMPUTER} with two commands of its player at frame {@code 400} (select the
     * Command Center, train the test unit), parses it and plays it to a frame.
     *
     * @param lastFrame the last frame
     * @param runtimes  the list that receives the runtime, stopped at the last frame
     * @return the state hash of every frame
     * @throws IOException if the replay cannot be read, written or played
     */
    public @NotNull List<Long> playTrainingReplay(int lastFrame, @NotNull List<ReplayRuntime> runtimes) throws IOException {
        return this.play(this.createTrainingReplay(), lastFrame, runtimes);
    }

    private static @NotNull Sprite findCommandCenterSprite(@NotNull Game game) {
        Unit commandCenter = findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);

        Assertions.assertNotNull(commandCenter);

        Sprite sprite = commandCenter.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        return sprite;
    }

    private @NotNull List<Long> play(@NotNull ReplayFile replay, int lastFrame, @NotNull List<ReplayRuntime> runtimes) throws IOException {
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, this.gameData);
        List<Long> hashes = new ArrayList<>();

        runtime.start(0, 0);

        Game game = runtime.getGame();

        while (game.getCurrentFrame() < lastFrame) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));

            hashes.add(game.computeStateHash());
        }

        runtimes.add(runtime);

        return hashes;
    }

    private @NotNull ReplayFile createTrainingReplay() throws IOException {
        ReplayFile original = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TERRAN_VS_COMPUTER)));
        List<ReplayRuntime> runtimes = new ArrayList<>();

        this.play(original, TRAIN_FRAME, runtimes);

        Game game = runtimes.getFirst().getGame();
        Unit commandCenter = findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);

        Assertions.assertNotNull(commandCenter);

        ReplayCommand firstSelection = original.getCommands().stream().filter(command -> ReplaySelectionCommand.supports(command.getTypeId()))
                                               .findFirst().orElseThrow();
        int selectType = firstSelection.getTypeId() >= ReplayCommandIds.SELECT_121 ? ReplayCommandIds.SELECT_121 : ReplayCommandIds.SELECT;
        int handleWidth = selectType == ReplayCommandIds.SELECT_121 ? Integer.BYTES : Short.BYTES;
        long handle = game.getUnitSystem().requireUnitPool().getHandle(commandCenter.getIndex());
        ReplaySelectionCommand select = ReplaySelectionCommand.create(ReplaySelectionCommand.Operation.REPLACE, handleWidth, new long[]{handle});
        List<ReplayCommand> commands = new ArrayList<>();
        boolean injected = false;

        for (ReplayCommand command : original.getCommands()) {
            if (!injected && command.getFrame() > TRAIN_FRAME) {
                commands.add(new ReplayCommand(TRAIN_FRAME, firstSelection.getPlayerId(), selectType,
                                               ReplayCommandCodecs.encode(ReplaySelectionCommand.createCodec(selectType), select)));
                commands.add(new ReplayCommand(TRAIN_FRAME, firstSelection.getPlayerId(), ReplayCommandIds.TRAIN,
                                               ReplayCommandCodecs.encodeValue(ReplayCommandIds.TRAIN, this.unitTypeId)));
                injected = true;
            }

            commands.add(command);
        }

        Assertions.assertTrue(injected);

        byte[] header = original.getHeaderSection();
        byte[] mapData = original.getMapData();

        Assertions.assertNotNull(header);
        Assertions.assertNotNull(mapData);

        byte[] written = new ReplayWriter(header, ReplayWriter.encodeCommands(commands), mapData,
                                          ReplayWriter.encodeNames(original.getPlayerNameBytes()), new byte[ReplayWriter.SKIN_SIZE],
                                          original.getLimits().getRequestedValues(), original.getGameVersion().getFlags(),
                                          new byte[ReplayWriter.CUSTOM_COLORS_SIZE],
                                          ReplayWriter.encodeVersionBlock(original.getGameVersion().getBuild(), 0)).write();

        return ReplayFile.parse(written);
    }
}
