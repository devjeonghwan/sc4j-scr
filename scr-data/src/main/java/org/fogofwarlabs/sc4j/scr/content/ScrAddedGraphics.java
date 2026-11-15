package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.dat.FlingyDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.PortdataDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.SpritesDat;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptCode;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptFile;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModPortrait;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModSheetFrame;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModWireframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registers the graphics entries a mod adds after the native tables, in registration order: images ({@link ScrAddedImage}), sprites
 * ({@link ScrAddedSprite}), flingy entries ({@link ScrAddedFlingy}) and iscript scripts ({@link ScrAddedScript}); and the interface art of added
 * unit types: portraits ({@link ScrModPortrait}), command icons and wireframes ({@link ScrModSheetFrame}, {@link ScrModWireframe}).
 * <p>
 * The {@code n}-th added image has the id {@link #FIRST_IMAGE_ID}{@code  + n}, right after the native {@code images.dat} entries, and likewise
 * the sprites from {@link #FIRST_SPRITE_ID} and the flingy entries from {@link #FIRST_FLINGY_ID}; the engine content registers them right after
 * the native entries, so the engine index of an added entry is its id. The {@code n}-th added script has the id {@link #getFirstScriptId()}
 * {@code  + n}, after the highest native script id. The ids stay within the WORD fields that hold them (sprite image, flingy sprite, image and
 * script fields of the game state); no native id changes meaning, and the DAT tables, the EUD memory and the native iscript bytes keep their
 * native layout. An added unit type ({@link ScrAddedUnitType}) names an added flingy entry through its flingy value.
 * <p>
 * The {@code n}-th added portrait has the id {@link #FIRST_PORTRAIT_ID}{@code  + n}, right after the native {@code portdata.dat} entries; an
 * added unit type names it through its portrait value. The {@code n}-th added command icon and the {@code n}-th added wireframe have the frame
 * {@link #FIRST_INTERFACE_FRAME}{@code  + n} of their sheets ({@link #COMMAND_ICON_SHEET}, {@link #WIREFRAME_SHEET},
 * {@link #GROUP_WIREFRAME_SHEET}, {@link #TRANSPORT_WIREFRAME_SHEET}), above every frame of the installed sheets; an added unit type names them
 * ({@link ScrAddedUnitType#getCommandIcon()}, {@link ScrAddedUnitType#getWireframe()}). The game state holds none of them.
 * <p>
 * The registration is open until the content of the games is built from it ({@link #freeze()}); with no added entry every table, script, frame
 * and hash is the native one. A replay holds the ids only: it plays back with the same registration.
 */
public final class ScrAddedGraphics {
    /**
     * The id of the first added image, the first id after the native {@code images.dat} entries.
     */
    public static final int FIRST_IMAGE_ID = ImagesDat.IMAGE_COUNT;
    /**
     * The id of the first added sprite, the first id after the native {@code sprites.dat} entries.
     */
    public static final int FIRST_SPRITE_ID = SpritesDat.SPRITE_COUNT;
    /**
     * The id of the first added flingy entry, the first id after the native {@code flingy.dat} entries.
     */
    public static final int FIRST_FLINGY_ID = FlingyDat.FLINGY_COUNT;
    /**
     * The id of the first added portrait, the first id after the native {@code portdata.dat} entries.
     */
    public static final int FIRST_PORTRAIT_ID = PortdataDat.PORTRAIT_COUNT;
    /**
     * The frame of the first added command icon and of the first added wireframe in their sheets, above every frame of the installed sheets.
     */
    public static final int FIRST_INTERFACE_FRAME = 0x1000;
    /**
     * The sheet of the command icons, without the quality prefix.
     */
    public static final @NotNull String COMMAND_ICON_SHEET = "unit/cmdicons/cmdicons.dds.grp";
    /**
     * The sheet of the single-selection wireframes, without the quality prefix.
     */
    public static final @NotNull String WIREFRAME_SHEET = "unit/wirefram/wirefram.dds.grp";
    /**
     * The sheet of the group-selection wireframes, without the quality prefix.
     */
    public static final @NotNull String GROUP_WIREFRAME_SHEET = "unit/wirefram/grpwire.dds.grp";
    /**
     * The sheet of the cargo wireframes, without the quality prefix.
     */
    public static final @NotNull String TRANSPORT_WIREFRAME_SHEET = "unit/wirefram/tranwire.dds.grp";
    /**
     * The highest id an added image, sprite, flingy entry or script may get: the largest WORD value below the iscript table terminator.
     */
    public static final int LAST_ID = 0xFFFE;
    /**
     * The size of the WORD offset space the scripts, with the added code, must fit in.
     */
    public static final int ISCRIPT_OFFSET_SPACE = 0x10000;

    private static final int SCRIPT_MAGIC = 0x45504353;
    private static final int TABLE_TERMINATOR = 0xFFFF;
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    private static void writeWord(@NotNull ByteArrayOutputStream output, int value) {
        output.write(value & BYTE_MASK);
        output.write(value >>> Byte.SIZE & BYTE_MASK);
    }

    /**
     * Creates a {@link ScrAddedGraphics} instance without added entries whose scripts follow the highest script id of an installed iscript.
     *
     * @param iscript the installed iscript
     * @return the created {@link ScrAddedGraphics} instance
     */
    public static @NotNull ScrAddedGraphics create(@NotNull IscriptFile iscript) {
        int highestScript = -1;

        for (IscriptHeader header : iscript.getHeaders()) {
            highestScript = Math.max(highestScript, header.getId());
        }

        return new ScrAddedGraphics(highestScript + 1);
    }

    private static int checkId(int id, @NotNull String kind) {
        if (id > LAST_ID) {
            throw Exceptions.newIllegalArgumentException("No {0} id is left.", kind);
        }

        return id;
    }


    private final int firstScriptId;

    private final @NotNull List<@NotNull ScrAddedImage> images;
    private final @NotNull List<@NotNull ScrAddedSprite> sprites;
    private final @NotNull List<@NotNull ScrAddedFlingy> flingies;
    private final @NotNull List<@NotNull ScrAddedScript> scripts;

    private final @NotNull List<@NotNull ScrModPortrait> portraits;
    private final @NotNull List<@NotNull ScrModSheetFrame> commandIcons;
    private final @NotNull List<@NotNull ScrModWireframe> wireframes;

    private boolean frozen;

    /**
     * Creates a {@link ScrAddedGraphics} instance without added entries, open for registration.
     *
     * @param firstScriptId the id of the first added script, the number of script ids of the installed iscript (its highest id plus one)
     */
    public ScrAddedGraphics(int firstScriptId) {
        this.firstScriptId = firstScriptId;

        this.images   = new ArrayList<>();
        this.sprites  = new ArrayList<>();
        this.flingies = new ArrayList<>();
        this.scripts  = new ArrayList<>();

        this.portraits    = new ArrayList<>();
        this.commandIcons = new ArrayList<>();
        this.wireframes   = new ArrayList<>();

        this.frozen = false;
    }

    /**
     * @return the id of the first added script, the first id after the highest native script id
     */
    public int getFirstScriptId() {
        return this.firstScriptId;
    }

    /**
     * @return the added images in registration order, the order of their ids
     */
    public @NotNull List<@NotNull ScrAddedImage> getImages() {
        return Collections.unmodifiableList(this.images);
    }

    /**
     * @return the added sprites in registration order, the order of their ids
     */
    public @NotNull List<@NotNull ScrAddedSprite> getSprites() {
        return Collections.unmodifiableList(this.sprites);
    }

    /**
     * @return the added flingy entries in registration order, the order of their ids
     */
    public @NotNull List<@NotNull ScrAddedFlingy> getFlingies() {
        return Collections.unmodifiableList(this.flingies);
    }

    /**
     * @return the added scripts in registration order, the order of their ids
     */
    public @NotNull List<@NotNull ScrAddedScript> getScripts() {
        return Collections.unmodifiableList(this.scripts);
    }

    /**
     * @return the added portraits in registration order, the order of their ids
     */
    public @NotNull List<@NotNull ScrModPortrait> getPortraits() {
        return Collections.unmodifiableList(this.portraits);
    }

    /**
     * @return the added command icons in registration order, the order of their frames
     */
    public @NotNull List<@NotNull ScrModSheetFrame> getCommandIcons() {
        return Collections.unmodifiableList(this.commandIcons);
    }

    /**
     * @return the added wireframes in registration order, the order of their frames
     */
    public @NotNull List<@NotNull ScrModWireframe> getWireframes() {
        return Collections.unmodifiableList(this.wireframes);
    }

    /**
     * @return {@code true} if no entry is added, {@code false} otherwise
     */
    public boolean isEmpty() {
        return this.images.isEmpty() && this.sprites.isEmpty() && this.flingies.isEmpty() && this.scripts.isEmpty() && this.portraits.isEmpty() &&
               this.commandIcons.isEmpty() && this.wireframes.isEmpty();
    }

    /**
     * @return {@code true} if the registration is closed, {@code false} otherwise
     */
    public boolean isFrozen() {
        return this.frozen;
    }


    /**
     * Adds an image after the registered ones.
     *
     * @param image the added image
     * @return the id of the image
     * @throws IllegalStateException    if the registration is closed
     * @throws IllegalArgumentException if no id is left
     */
    public int addImage(@NotNull ScrAddedImage image) {
        this.checkOpen();

        int id = checkId(FIRST_IMAGE_ID + this.images.size(), "image");

        this.images.add(image);

        return id;
    }

    /**
     * Adds a sprite after the registered ones.
     *
     * @param sprite the added sprite
     * @return the id of the sprite
     * @throws IllegalStateException    if the registration is closed
     * @throws IllegalArgumentException if no id is left
     */
    public int addSprite(@NotNull ScrAddedSprite sprite) {
        this.checkOpen();

        int id = checkId(FIRST_SPRITE_ID + this.sprites.size(), "sprite");

        this.sprites.add(sprite);

        return id;
    }

    /**
     * Adds a flingy entry after the registered ones.
     *
     * @param flingy the added flingy entry
     * @return the id of the flingy entry
     * @throws IllegalStateException    if the registration is closed
     * @throws IllegalArgumentException if no id is left
     */
    public int addFlingy(@NotNull ScrAddedFlingy flingy) {
        this.checkOpen();

        int id = checkId(FIRST_FLINGY_ID + this.flingies.size(), "flingy");

        this.flingies.add(flingy);

        return id;
    }

    /**
     * Adds a script after the registered ones.
     *
     * @param script the added script
     * @return the id of the script
     * @throws IllegalStateException    if the registration is closed
     * @throws IllegalArgumentException if no id is left
     */
    public int addScript(@NotNull ScrAddedScript script) {
        this.checkOpen();

        int id = checkId(this.firstScriptId + this.scripts.size(), "script");

        this.scripts.add(script);

        return id;
    }

    /**
     * Adds a portrait after the registered ones.
     *
     * @param portrait the clips of the portrait
     * @return the id of the portrait
     * @throws IllegalStateException    if the registration is closed
     * @throws IllegalArgumentException if no id is left
     */
    public int addPortrait(@NotNull ScrModPortrait portrait) {
        this.checkOpen();

        int id = checkId(FIRST_PORTRAIT_ID + this.portraits.size(), "portrait");

        this.portraits.add(portrait);

        return id;
    }

    /**
     * Adds a command icon after the registered ones.
     *
     * @param icon the icon's frame files
     * @return the frame of the icon in {@link #COMMAND_ICON_SHEET}
     * @throws IllegalStateException    if the registration is closed
     * @throws IllegalArgumentException if no frame is left
     */
    public int addCommandIcon(@NotNull ScrModSheetFrame icon) {
        this.checkOpen();

        int frame = checkId(FIRST_INTERFACE_FRAME + this.commandIcons.size(), "command icon");

        this.commandIcons.add(icon);

        return frame;
    }

    /**
     * Adds a wireframe after the registered ones.
     *
     * @param wireframe the wireframe's frame files
     * @return the frame of the wireframe in the wireframe sheets
     * @throws IllegalStateException    if the registration is closed
     * @throws IllegalArgumentException if no frame is left
     */
    public int addWireframe(@NotNull ScrModWireframe wireframe) {
        this.checkOpen();

        int frame = checkId(FIRST_INTERFACE_FRAME + this.wireframes.size(), "wireframe");

        this.wireframes.add(wireframe);

        return frame;
    }

    /**
     * Closes the registration; the content and the scripts of the games are built from the closed registration.
     */
    public void freeze() {
        this.frozen = true;
    }

    /**
     * Tests whether an id names an added portrait.
     *
     * @param portraitId a portrait id or any other value
     * @return {@code true} if the id names an added portrait, {@code false} otherwise
     */
    public boolean containsPortrait(int portraitId) {
        int index = portraitId - FIRST_PORTRAIT_ID;

        return index >= 0 && index < this.portraits.size();
    }

    /**
     * Returns the added portrait of an id.
     *
     * @param portraitId the portrait id
     * @return the added portrait
     * @throws IllegalArgumentException if the id names no added portrait
     */
    public @NotNull ScrModPortrait getPortrait(int portraitId) {
        if (!this.containsPortrait(portraitId)) {
            throw Exceptions.newIllegalArgumentException("Not an added portrait id: {0}", portraitId);
        }

        return this.portraits.get(portraitId - FIRST_PORTRAIT_ID);
    }

    /**
     * Finds the added frame of an interface sheet: an added command icon in {@link #COMMAND_ICON_SHEET}, or the view of an added wireframe in
     * {@link #WIREFRAME_SHEET}, {@link #GROUP_WIREFRAME_SHEET} or {@link #TRANSPORT_WIREFRAME_SHEET}.
     *
     * @param sheet the sheet path without the quality prefix
     * @param frame the frame
     * @return the frame files, or {@code null} if the frame of the sheet is no added one
     */
    public @Nullable ScrModSheetFrame findSheetFrame(@NotNull String sheet, int frame) {
        int index = frame - FIRST_INTERFACE_FRAME;

        if (index < 0) {
            return null;
        }

        if (sheet.equals(COMMAND_ICON_SHEET)) {
            return index < this.commandIcons.size() ? this.commandIcons.get(index) : null;
        }

        if (index >= this.wireframes.size()) {
            return null;
        }

        return switch (sheet) {
            case WIREFRAME_SHEET -> this.wireframes.get(index).getSingle();
            case GROUP_WIREFRAME_SHEET -> this.wireframes.get(index).getGroup();
            case TRANSPORT_WIREFRAME_SHEET -> this.wireframes.get(index).getTransport();
            default -> null;
        };
    }

    /**
     * Tests whether an id names an added image.
     *
     * @param imageId an image id or any other value
     * @return {@code true} if the id names an added image, {@code false} otherwise
     */
    public boolean containsImage(int imageId) {
        int index = imageId - FIRST_IMAGE_ID;

        return index >= 0 && index < this.images.size();
    }

    /**
     * Returns the added image of an id.
     *
     * @param imageId the image id
     * @return the added image
     * @throws IllegalArgumentException if the id names no added image
     */
    public @NotNull ScrAddedImage getImage(int imageId) {
        if (!this.containsImage(imageId)) {
            throw Exceptions.newIllegalArgumentException("Not an added image id: {0}", imageId);
        }

        return this.images.get(imageId - FIRST_IMAGE_ID);
    }

    /**
     * Returns the native image whose installed overlay files an image uses: the image itself for a native image, the base image of an added one.
     *
     * @param imageId the image id
     * @return the native image id, or {@code imageId} if it names no added image
     */
    public int getOverlayImage(int imageId) {
        return this.containsImage(imageId) ? this.images.get(imageId - FIRST_IMAGE_ID).getBaseImage() : imageId;
    }

    /**
     * Creates the iscript of the games: the installed file with the added scripts after it. The own code blocks and the headers of the added
     * scripts follow the installed bytes, then a new id table with the installed entries and the added ones; the installed code keeps its offsets.
     * Without added scripts the installed file itself is returned.
     *
     * @param installed the installed iscript
     * @return the iscript with the added scripts
     * @throws IOException              if the assembled file does not parse
     * @throws IllegalArgumentException if the added code does not fit the WORD offset space
     */
    public @NotNull IscriptFile createIscript(@NotNull IscriptFile installed) throws IOException {
        if (this.scripts.isEmpty()) {
            return installed;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int[] headerOffsets = new int[this.scripts.size()];

        output.writeBytes(installed.copyData());

        for (int index = 0; index < this.scripts.size(); index++) {
            ScrAddedScript script = this.scripts.get(index);
            int[] offsets = new int[script.getAnimationCount()];

            for (int animation = 0; animation < offsets.length; animation++) {
                IscriptCode code = script.getAnimationCode(animation);

                offsets[animation] = code == null ? script.getAnimationOffset(animation) : output.size();

                if (code != null) {
                    output.writeBytes(code.encode(output.size()));
                }
            }

            headerOffsets[index] = output.size();

            writeWord(output, SCRIPT_MAGIC & WORD_MASK);
            writeWord(output, SCRIPT_MAGIC >>> Short.SIZE);
            writeWord(output, script.getType());
            writeWord(output, 0);

            for (int offset : offsets) {
                writeWord(output, offset);
            }
        }

        int tableOffset = output.size();

        for (IscriptHeader header : installed.getHeaders()) {
            writeWord(output, header.getId());
            writeWord(output, header.getOffset());
        }

        for (int index = 0; index < headerOffsets.length; index++) {
            writeWord(output, this.firstScriptId + index);
            writeWord(output, headerOffsets[index]);
        }

        writeWord(output, TABLE_TERMINATOR);
        writeWord(output, 0);

        if (output.size() > ISCRIPT_OFFSET_SPACE) {
            throw Exceptions.newIllegalArgumentException("The added scripts take {0} bytes; the iscript may hold {1}.",
                                                         output.size(), ISCRIPT_OFFSET_SPACE);
        }

        byte[] data = output.toByteArray();

        data[0] = (byte) (tableOffset & BYTE_MASK);
        data[1] = (byte) (tableOffset >>> Byte.SIZE & BYTE_MASK);
        data[2] = 0;
        data[3] = 0;

        return IscriptFile.parse(data);
    }

    private void checkOpen() {
        if (this.frozen) {
            throw Exceptions.newIllegalStateException("The added graphics are closed.");
        }
    }
}
