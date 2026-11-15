package org.fogofwarlabs.sc4j.scr.data.resource.dialog;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogDefinition;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * Imports an SC:R interface file ({@code SD/rez/<name>.ui.json}) as an engine {@link DialogDefinition}: the dialog
 * {@code rect} is its left, top, width and height, the top-level controls keep the width and height of their {@code rect}
 * and the child elements of a control turn their {@code rect} right and bottom edges into a width and height, at least
 * {@code 0}: an animation element ({@code FLC}) gives only its position, with right and bottom {@code 0}. Every control is
 * created by {@link ScrDialogControlUtils#createControl}.
 */
public final class ScrDialogImportUtils {
    private static final int HORIZONTAL_EXTENT_INDEX = 2;
    private static final int VERTICAL_EXTENT_INDEX = 3;

    private static final int RECTANGLE_SIZE = 4;

    /**
     * The folder of the interface files.
     */
    private static final @NotNull String DIALOG_FOLDER = "SD/rez/";
    /**
     * The file name suffix of an interface file.
     */
    private static final @NotNull String DIALOG_SUFFIX = ".ui.json";

    /**
     * Reads an interface file of a storage and creates its dialog definition.
     *
     * @param storage the open storage
     * @param name    the file name without {@code .ui.json}, for example {@code gluMain}
     * @return the dialog definition
     * @throws IOException if the file is missing or malformed
     */
    public static @NotNull DialogDefinition load(@NotNull CascStorage storage, @NotNull String name) throws IOException {
        return parse(storage.readFile(getPath(name)), name);
    }

    /**
     * Returns the CASC path of an interface file.
     *
     * @param name the file name without {@code .ui.json}
     * @return the path below {@code SD/rez/}
     */
    public static @NotNull String getPath(@NotNull String name) {
        return DIALOG_FOLDER + name + DIALOG_SUFFIX;
    }

    /**
     * Creates the dialog definition of the contents of an interface file.
     *
     * @param contents the file contents
     * @param name     the name used when the file has none
     * @return the dialog definition
     * @throws IOException if the contents are not a dialog definition
     */
    public static @NotNull DialogDefinition parse(byte @NotNull [] contents, @NotNull String name) throws IOException {
        if (!(JsonReader.parse(contents) instanceof Map<?, ?> dialog)) {
            throw Exceptions.newIOException("{0}: not a dialog object.", name);
        }

        int[] rectangle = readRectangle(dialog.get("rect"));
        List<DialogControl> controls = new ArrayList<>();

        readControls(dialog.get("children"), false, controls);

        Object dialogName = dialog.get("name");

        try {
            return new DialogDefinition(dialogName instanceof String text ? text : name,
                                        rectangle[0], rectangle[1], rectangle[HORIZONTAL_EXTENT_INDEX], rectangle[VERTICAL_EXTENT_INDEX],
                                        controls);
        } catch (IllegalArgumentException exception) {
            throw Exceptions.newIOException(exception, "{0}: {1}", name, exception.getMessage());
        }
    }

    private static void readControls(@Nullable Object list, boolean children, @NotNull List<DialogControl> output) throws IOException {
        if (!(list instanceof List<?> entries)) {
            return;
        }

        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> control)) {
                throw Exceptions.newIOException("A control is not an object.");
            }

            int[] rectangle = readRectangle(control.get("rect"));
            int left = rectangle[0];
            int top = rectangle[1];
            int width = children ? Math.max(0, rectangle[HORIZONTAL_EXTENT_INDEX] - rectangle[0]) : rectangle[HORIZONTAL_EXTENT_INDEX];
            int height = children ? Math.max(0, rectangle[VERTICAL_EXTENT_INDEX] - rectangle[1]) : rectangle[VERTICAL_EXTENT_INDEX];
            Set<String> flags = new LinkedHashSet<>();

            if (control.get("flags") instanceof List<?> names) {
                for (Object flag : names) {
                    if (flag instanceof String text) {
                        flags.add(text);
                    }
                }
            }

            List<DialogControl> parts = new ArrayList<>();

            readControls(control.get("children"), true, parts);

            String type = control.get("type") instanceof String text ? text : "";
            String content = control.get("content") instanceof String text ? text : "";
            int identifier = control.get("id") instanceof Number number ? number.intValue() : 0;

            try {
                output.add(ScrDialogControlUtils.createControl(ScrControlType.of(type), identifier, left, top, width, height, flags, content, parts));
            } catch (IllegalArgumentException exception) {
                throw Exceptions.newIOException(exception, "Invalid control {0}: {1}", identifier, exception.getMessage());
            }
        }
    }

    private static int @NotNull [] readRectangle(@Nullable Object value) throws IOException {
        if (!(value instanceof List<?> numbers) || numbers.size() != RECTANGLE_SIZE) {
            throw Exceptions.newIOException("A rectangle is not four numbers.");
        }

        int[] rectangle = new int[RECTANGLE_SIZE];

        for (int index = 0; index < RECTANGLE_SIZE; index++) {
            rectangle[index] = numbers.get(index) instanceof Number number ? number.intValue() : 0;
        }

        return rectangle;
    }

    private ScrDialogImportUtils() {
    }
}
