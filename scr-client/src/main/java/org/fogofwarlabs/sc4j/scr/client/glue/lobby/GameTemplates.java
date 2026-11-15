package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.fogofwarlabs.sc4j.scr.net.lan.LanGameOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Loads localized game templates and supplies the create screens with ordered type and subtype choices.
 */
public final class GameTemplates {
    /**
     * The template directory.
     */
    private static final @NotNull String DIRECTORY = "Templates/";
    /**
     * The template list file of the template directory.
     */
    private static final @NotNull String LIST_FILE = "templates.lst";
    /**
     * The skin folder and the locale the templates are read in.
     */
    private static final @NotNull String SKIN_FOLDER = "";
    private static final @NotNull String LOCALE = "enUS";
    private static final @NotNull String LINE_BREAK_PATTERN = "\r?\n";
    /**
     * The offsets of the type name, the subtype label and the game options in a template file.
     */
    private static final int NAME_OFFSET = 1;
    private static final int LABEL_OFFSET = 0x21;
    private static final int OPTIONS_OFFSET = 0x41;
    /**
     * The maximum length of a template file's text.
     */
    private static final int TEXT_SIZE = 32;
    /**
     * The {@code gluAll} key prefix of the game type names; the SDK type of the game type follows it.
     */
    private static final @NotNull String TYPE_NAME_KEY_PREFIX = "scr.common.terms.gameType.";

    /**
     * Stores the localized labels and options of one game template.
     */
    public static final class Template {
        private final @NotNull String name;
        private final @NotNull String subtypeLabel;
        private final @NotNull LanGameOptions options;

        /**
         * Creates a {@link Template} instance with localized labels and parsed options.
         *
         * @param name         the displayed game type name
         * @param subtypeLabel the displayed subtype label
         * @param options      the parsed game options
         */
        Template(@NotNull String name, @NotNull String subtypeLabel, @NotNull LanGameOptions options) {
            this.name         = name;
            this.subtypeLabel = subtypeLabel;
            this.options      = options;
        }

        /**
         * @return the template's type name
         */
        public @NotNull String getName() {
            return this.name;
        }

        /**
         * @return the displayed subtype label
         */
        public @NotNull String getSubtypeLabel() {
            return this.subtypeLabel;
        }

        /**
         * @return the game options
         */
        public @NotNull LanGameOptions getOptions() {
            return this.options;
        }

        /**
         * @return the game options as the game header takes them
         */
        public @NotNull GameTemplate getTemplate() {
            return this.options.createTemplate();
        }
    }

    /**
     * Loads every listed template in list order.
     *
     * @param storage the installed game storage
     * @return the templates
     * @throws IOException if the list or a listed file is missing
     */
    public static @NotNull List<Template> load(@NotNull CascStorage storage) throws IOException {
        AssetResolver resolver = new AssetResolver(storage, AssetResolver.AssetSet.HD, SKIN_FOLDER, LOCALE);
        String list = new String(resolver.readLocalized(DIRECTORY + LIST_FILE), StandardCharsets.ISO_8859_1);
        List<Template> templates = new ArrayList<>();

        for (String line : list.split(LINE_BREAK_PATTERN)) {
            String file = line.strip().replace("\"", "");

            if (file.isEmpty()) {
                continue;
            }

            templates.add(parse(resolver.readLocalized(DIRECTORY + file)));
        }

        return templates;
    }

    /**
     * Loads every listed template in list order, or creates the fallback templates ({@link #createDefaults}) when the list
     * or a listed file cannot be read.
     *
     * @param storage the installed game storage
     * @param logger  the logger of the screen, which reports an unreadable template
     * @return the templates
     */
    static @NotNull List<Template> loadOrCreateDefaults(@NotNull CascStorage storage, @NotNull Logger logger) {
        try {
            return load(storage);
        } catch (IOException exception) {
            logger.warning("GAME TEMPLATES UNAVAILABLE: " + exception.getMessage());

            return createDefaults();
        }
    }

    /**
     * Creates fallback Melee, Free For All and Use Map Settings templates when the installed list cannot be read.
     *
     * @return the three templates
     */
    public static @NotNull List<Template> createDefaults() {
        List<Template> templates = new ArrayList<>();

        for (GameTemplate template : new GameTemplate[]{GameTemplate.MELEE, GameTemplate.FREE_FOR_ALL, GameTemplate.USE_MAP_SETTINGS}) {
            templates.add(new Template("", "", LanGameOptions.parse(template.encode())));
        }

        return templates;
    }

    /**
     * Collects the game types a create screen offers: each type id of the list once, ascending, which is the order of the
     * type names {@code scr.common.terms.gameType.1..13}.
     *
     * @param templates the templates
     * @param offered   the screen's filter
     * @return the type ids
     */
    public static int @NotNull [] getTypeIds(@NotNull List<Template> templates, @NotNull Predicate<GameTemplate> offered) {
        return templates.stream().map(Template::getTemplate).filter(offered).mapToInt(GameTemplate::getTypeId).distinct().sorted().toArray();
    }

    /**
     * Reads the localized name of a game type, the name entry of its SDK type ({@link GameTemplate#toSdkType}).
     *
     * @param strings the {@code gluAll} strings
     * @param typeId  the game type id
     * @return the name, or an empty string if the game type has no SDK type
     */
    public static @NotNull String getTypeName(@NotNull GluText strings, int typeId) {
        int sdkType = GameTemplate.toSdkType(typeId);

        return sdkType == GameTemplate.NO_SDK_TYPE ? "" : strings.get(TYPE_NAME_KEY_PREFIX + sdkType);
    }

    /**
     * Collects the templates of one game type in list order: the subtypes the screen offers for it.
     *
     * @param templates the templates
     * @param typeId    the game type id
     * @return the type's templates
     */
    public static @NotNull List<Template> getSubtypes(@NotNull List<Template> templates, int typeId) {
        List<Template> subtypes = new ArrayList<>();

        for (Template template : templates) {
            if (template.getOptions().getTypeId() == typeId) {
                subtypes.add(template);
            }
        }

        return subtypes;
    }

    /**
     * Finds a template by its type and subtype ids.
     *
     * @param templates the templates
     * @param typeId    the game type id
     * @param subTypeId the subtype id
     * @return the template, or {@code null} if the list has none
     */
    public static @Nullable Template find(@NotNull List<Template> templates, int typeId, int subTypeId) {
        for (Template template : templates) {
            if (template.getOptions().getTypeId() == typeId && template.getOptions().getSubTypeId() == subTypeId) {
                return template;
            }
        }

        return null;
    }

    /**
     * Parses a template name, subtype label and game options from a template file.
     *
     * @param data a {@code .got} file
     * @return its template
     */
    static @NotNull Template parse(byte @NotNull [] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] options = new byte[LanGameOptions.SIZE];

        buffer.position(OPTIONS_OFFSET);
        buffer.get(options, 0, Math.min(options.length, data.length - OPTIONS_OFFSET));

        return new Template(readText(data, NAME_OFFSET), readText(data, LABEL_OFFSET), LanGameOptions.parse(options));
    }

    private static @NotNull String readText(byte @NotNull [] data, int offset) {
        int end = offset;

        while (end < offset + TEXT_SIZE && end < data.length && data[end] != 0) {
            end++;
        }

        return new String(data, offset, end - offset, StandardCharsets.ISO_8859_1);
    }

    /**
     * Creates a {@link GameTemplates} instance.
     */
    private GameTemplates() {
    }
}
