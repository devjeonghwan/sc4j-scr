package org.fogofwarlabs.sc4j.scr.client.ui.portrait;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.client.video.VideoClip;
import org.fogofwarlabs.sc4j.engine.client.video.VideoClipDecoder;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.PortdataDat;
import org.fogofwarlabs.sc4j.scr.data.format.text.TblFile;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrModPortrait;
import org.fogofwarlabs.sc4j.scr.data.type.PortraitTypeTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chooses and times portrait clips with the private portrait generator shared by static noise.
 * Clip reads and decoding run on a single background executor and are cached by path.
 * Changing the shown unit or portrait restarts its clip and static; talking clips continue until their deadline.
 * <p>
 * A portrait id after the native {@code portdata.dat} entries names an added portrait ({@link ScrAddedGraphics#addPortrait}) of the graphics set
 * by {@link #setAddedGraphics}: its clips, idle chances and clip numbering take the place of the table entries, and its clips are read from the
 * mod's files.
 */
public final class PortraitPlayer {
    private static final int REPEATED_CHOICE_OFFSET = 2;
    /**
     * The clip path prefix of the classic asset set, the prefix until {@link #setPrefix} selects another set.
     */
    private static final @NotNull String CLASSIC_PREFIX = "SD/portrait/";
    /**
     * The portrait folder in a clip path, replaced by the skin's portrait folder for a skinned clip.
     */
    private static final @NotNull String PORTRAIT_FOLDER = "portrait/";
    private static final @NotNull String CLIP_EXTENSION = ".webm";
    private static final @NotNull String LOADER_THREAD_NAME = "portrait-loader";


    private static final class Loaded {
        private final @NotNull VideoClip clip;
        private final @NotNull String path;

        /**
         * Creates a {@link Loaded} instance with a decoded clip and the path it was read from.
         *
         * @param clip the decoded clip
         * @param path the clip path
         */
        Loaded(@NotNull VideoClip clip, @NotNull String path) {
            this.clip = clip;
            this.path = path;
        }
    }

    /**
     * A portrait frame to show.
     */
    public static final class Frame {
        private final @NotNull VideoClip clip;
        private final int index;
        private final @Nullable String path;

        /**
         * Creates a {@link Frame} instance with the shown clip, its frame index and the clip path.
         *
         * @param clip  the decoded clip
         * @param index the frame index in the clip
         * @param path  the clip path, or {@code null} when unavailable
         */
        Frame(@NotNull VideoClip clip, int index, @Nullable String path) {
            this.clip  = clip;
            this.index = index;
            this.path  = path;
        }

        /**
         * @return the clip
         */
        public @NotNull VideoClip getClip() {
            return this.clip;
        }

        /**
         * @return the frame index in the clip
         */
        public int getIndex() {
            return this.index;
        }

        /**
         * @return the stable texture-cache path, or {@code null} when unavailable
         */
        public @Nullable String getPath() {
            return this.path;
        }
    }

    private final @NotNull CascStorage storage;
    private final @NotNull PortraitTypeTable portraits;
    private final @NotNull TblFile paths;
    private final @NotNull VideoClipDecoder decoder;

    private final @NotNull Map<String, CompletableFuture<Loaded>> clips;
    private final @NotNull ExecutorService loader;
    private final @NotNull PortraitGenerator generator;
    private final @NotNull PortraitStatic noise;

    private @NotNull String prefix;
    private AssetResolver.@NotNull AssetSet quality;
    private @Nullable String skin;
    private @Nullable ScrAddedGraphics addedGraphics;

    private @Nullable Object shownUnit;
    private int shownPortrait;
    private int portrait;
    private @Nullable CompletableFuture<Loaded> pending;
    private @Nullable VideoClip clip;
    private @Nullable String clipPath;
    private long clipStart;
    private @Nullable VideoClip lastClip;
    private int lastIndex;

    private boolean talking;
    private long talkEnd;
    private int lastTalkClip;

    /**
     * Creates a {@link PortraitPlayer} instance.
     *
     * @param storage the installed game storage, for the clips
     * @param tables  the client's portrait tables
     * @param decoder the clip decoder
     */
    public PortraitPlayer(@NotNull CascStorage storage, @NotNull PortraitTables tables, @NotNull VideoClipDecoder decoder) {
        this.storage   = storage;
        this.portraits = tables.getPortraits();
        this.paths     = tables.getPaths();
        this.decoder   = decoder;

        this.clips     = new HashMap<>();
        this.loader    = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, LOADER_THREAD_NAME);

            thread.setDaemon(true);

            return thread;
        });
        this.generator = new PortraitGenerator();
        this.noise     = new PortraitStatic(this.generator);

        this.prefix        = CLASSIC_PREFIX;
        this.quality       = AssetResolver.AssetSet.SD;
        this.skin          = null;
        this.addedGraphics = null;

        this.shownUnit     = null;
        this.shownPortrait = -1;
        this.portrait      = -1;
        this.pending       = null;
        this.clip          = null;
        this.clipPath      = null;
        this.clipStart     = 0;
        this.lastClip      = null;
        this.lastIndex     = -1;

        this.talking      = false;
        this.talkEnd      = 0;
        this.lastTalkClip = -1;
    }


    /**
     * Sets the HD skin folder of the portrait clips (for example {@code Carbot}); the SD set has no skins.
     *
     * @param skin the skin folder, or {@code null}
     */
    public void setSkin(@Nullable String skin) {
        this.skin = skin;
    }

    /**
     * Sets the added graphics whose portraits the ids after the native entries name.
     *
     * @param addedGraphics the added graphics of the game, or {@code null} for none
     */
    public void setAddedGraphics(@Nullable ScrAddedGraphics addedGraphics) {
        this.addedGraphics = addedGraphics;
    }

    /**
     * Sets the path prefix of the asset set: {@code SD/portrait/} for classic graphics, {@code HD2/portrait/} or {@code portrait/} for HD.
     * The asset set of the prefix chooses the clips of an added portrait.
     *
     * @param prefix the path prefix
     */
    public void setPrefix(@NotNull String prefix) {
        if (!prefix.equals(this.prefix)) {
            // The decoded clips of the previous set are dropped; the shown portrait restarts from the new set.
            this.prefix   = prefix;
            this.quality  = AssetResolver.AssetSet.SD;
            this.portrait = -1;

            for (AssetResolver.AssetSet set : AssetResolver.AssetSet.values()) {
                if (prefix.equals(set.getPrefix() + PORTRAIT_FOLDER)) {
                    this.quality = set;
                }
            }

            this.pending = null;
            this.clip    = null;

            this.clips.clear();
        }
    }


    /**
     * @return the static of the shown portrait
     */
    public @NotNull PortraitStatic getStatic() {
        return this.noise;
    }

    /**
     * @return {@code true} if the chosen clip is still loading, {@code false} otherwise
     */
    public boolean isLoading() {
        return this.pending != null && !this.pending.isDone();
    }

    /**
     * @return {@code true} if a talking animation is playing, {@code false} otherwise
     */
    public boolean isTalking() {
        return this.talking;
    }


    /**
     * Changes the shown unit or portrait and restarts its clip and static.
     * An invalid portrait hides it immediately; showing the same unit and portrait retains playback.
     *
     * @param unit     the unit object, or {@code null} for a transmission
     * @param portrait the portdata.dat entry, or a negative value for none
     * @param now      a monotonic clock in milliseconds
     * @throws IOException if the clip cannot be read
     */
    public void show(@Nullable Object unit, int portrait, long now) throws IOException {
        if (!this.isPortrait(portrait)) {
            this.shownUnit     = null;
            this.shownPortrait = -1;

            return;
        }

        if (unit == this.shownUnit && portrait == this.shownPortrait) {
            return;
        }

        this.shownUnit     = unit;
        this.shownPortrait = portrait;
        this.talking       = this.talking && unit == null;
        this.portrait      = portrait;
        this.clip          = null;

        this.startNextClip(now);
        this.noise.restart();
    }

    /**
     * Returns the frame to show for a portrait at a time, starting or changing clips as needed.
     *
     * @param portrait the portdata.dat entry, or a negative value for none
     * @param now      a monotonic clock in milliseconds
     * @return the frame to show, or {@code null} for no portrait or while no clip is loaded
     * @throws IOException if the clip cannot be read or decoded
     */
    public @Nullable Frame update(int portrait, long now) throws IOException {
        if (!this.isPortrait(portrait)) {
            this.portrait = -1;
            this.clip     = null;

            return null;
        }

        if (portrait != this.portrait || this.clip == null && this.pending == null) {
            if (portrait != this.portrait) {
                this.talking = false;
            }

            this.portrait = portrait;
            this.clip     = null;

            this.startNextClip(now);
        }

        if (this.pending != null) {
            if (!this.pending.isDone()) {
                return this.clip == null ? null : this.createFrameAt(this.clip, now);
            }

            CompletableFuture<Loaded> done = this.pending;

            this.pending = null;

            Loaded loaded;

            try {
                loaded = done.join();
            } catch (CompletionException exception) {
                this.clips.values().remove(done);

                this.clip = null;

                throw Exceptions.newIOException(exception.getCause(), "Portrait clip: {0}", exception.getCause());
            }

            this.clip      = loaded != null ? loaded.clip : null;
            this.clipPath  = loaded != null ? loaded.path : null;
            this.clipStart = now;
        }

        VideoClip current = this.clip;

        if (current == null || current.getFrameCount() == 0) {
            return null;
        }

        long elapsed = now - this.clipStart;

        if (elapsed >= current.getTotalDuration() && current.getTotalDuration() > 0) {
            // Timer 2 at the clip's end: another talking clip while talking, else an idle clip. The next clip loads in the
            // background; the finished clip's last frame stays meanwhile.
            this.startNextClip(now);

            if (this.pending != null && !this.pending.isDone()) {
                return new Frame(current, current.getFrameCount() - 1, this.clipPath);
            }

            return this.update(portrait, now);
        }

        return this.createFrameAt(current, now);
    }

    /**
     * Selects the timed clip frame and ends restart noise when the shown frame changes.
     *
     * @param current the loaded clip
     * @param now     the monotonic clock in milliseconds
     * @return the frame to show
     */
    private @NotNull Frame createFrameAt(@NotNull VideoClip current, long now) {
        long elapsed = Math.min(now - this.clipStart, Math.max(0, current.getTotalDuration() - 1));
        int index = 0;
        long time = 0;

        while (index < current.getFrameCount() - 1 && time + current.getDuration(index) <= elapsed) {
            time += current.getDuration(index++);
        }

        Frame frame = new Frame(current, index, this.clipPath);

        // Timer 1: a new video frame ends the full static of a restart.
        if (frame.getClip() != this.lastClip || frame.getIndex() != this.lastIndex) {
            this.lastClip  = frame.getClip();
            this.lastIndex = frame.getIndex();

            this.noise.onFrameShown();
        }

        return frame;
    }

    /**
     * Starts talking playback for the supplied duration when a portrait is selected.
     * At the deadline the current clip finishes before idle playback resumes.
     *
     * @param now                  a monotonic clock in milliseconds
     * @param durationMilliseconds the talking duration
     * @throws IOException if the clip cannot be read
     */
    public void talk(long now, long durationMilliseconds) throws IOException {
        if (this.portrait < 0) {
            return;
        }

        this.talking = true;
        this.talkEnd = now + durationMilliseconds;
        this.clip    = null;

        this.startNextClip(now);
    }

    /**
     * Ends the talking animation; the next clip is an idle clip.
     */
    public void stopTalking() {
        this.talking = false;
        this.clip    = null;
    }

    /**
     * Tests whether an id names a portrait: a native {@code portdata.dat} entry or an added portrait.
     */
    private boolean isPortrait(int portrait) {
        ScrAddedGraphics added = this.addedGraphics;

        return portrait >= 0 && (portrait < PortdataDat.PORTRAIT_COUNT || added != null && added.containsPortrait(portrait));
    }

    /**
     * Schedules a talking or weighted idle clip after checking the talking deadline.
     *
     * @param now the monotonic clock in milliseconds
     */
    private void startNextClip(long now) {
        ScrAddedGraphics graphics = this.addedGraphics;
        ScrModPortrait added = graphics != null && graphics.containsPortrait(this.portrait) ? graphics.getPortrait(this.portrait) : null;

        if (this.talking && now >= this.talkEnd) {
            this.talking = false;
        }

        if (this.talking) {
            int choice = this.generator.nextTalkClip();

            if (choice == this.lastTalkClip) {
                choice = (choice + REPEATED_CHOICE_OFFSET) % PortraitGenerator.TALK_CLIP_COUNT;
            }

            this.lastTalkClip = choice;

            this.startClip(added != null ? added.getTalkingPrefix(this.quality) : this.getClipPrefix(this.portraits.getTalkingDirectory(this.portrait)),
                           choice, added);
        } else {
            int pick = this.generator.nextPercent();
            int choice = 0;
            int sum = 0;

            for (int index = 0; index < PortraitTypeTable.IDLE_CLIP_COUNT; index++) {
                sum += added != null ? added.getIdleChance(index) : this.portraits.getIdleChange(this.portrait, index);

                if (pick <= sum) {
                    choice = index;

                    break;
                }
            }

            this.startClip(added != null ? added.getIdlePrefix(this.quality) : this.getClipPrefix(this.portraits.getIdleDirectory(this.portrait)),
                           choice, added);
        }
    }

    /**
     * Returns the clip path prefix of an installed portrait directory in the current asset set.
     */
    private @NotNull String getClipPrefix(int directory) {
        return this.prefix + (directory > 0 ? this.paths.getString(directory - 1).replace('\\', '/') : "");
    }

    /**
     * Schedules the chosen path when no successful cached load exists.
     *
     * @param base   the clip path prefix
     * @param choice the chosen clip number
     * @param added  the added portrait whose files hold the clips, or {@code null} for an installed portrait
     */
    private void startClip(@NotNull String base, int choice, @Nullable ScrModPortrait added) {
        String key = base + choice;
        CompletableFuture<Loaded> future = this.clips.get(key);

        if (future == null || future.isCompletedExceptionally()) {
            future = CompletableFuture.supplyAsync(() -> this.loadClip(base, choice, added), this.loader);

            this.clips.put(key, future);
        }

        this.pending = future;
    }

    /**
     * Reads and decodes a clip on the loader thread, trying lower numbers when a file is missing.
     *
     * @param base   the clip path prefix
     * @param choice the first clip number to try
     * @param added  the added portrait whose files hold the clips, or {@code null} for an installed portrait
     * @return the decoded clip and path, or {@code null} if every candidate is missing
     */
    private @Nullable Loaded loadClip(@NotNull String base, int choice, @Nullable ScrModPortrait added) {
        for (int number = choice; number >= 0; number--) {
            String path = base + number + CLIP_EXTENSION;
            // The resolver: the skinned file of the set when installed, else the unskinned one; a mod's clips have no skins.
            String skinned = this.skin == null || added != null
                             ? null
                             : base.replaceFirst(PORTRAIT_FOLDER, this.skin + "/" + PORTRAIT_FOLDER) + number + CLIP_EXTENSION;

            if (skinned != null && this.storage.hasFile(skinned)) {
                path = skinned;
            }

            byte[] webm;

            try {
                webm = added != null ? added.readClip(path) : this.storage.readFile(path);
            } catch (IOException exception) {
                continue;
            }

            try {
                return new Loaded(this.decoder.decode(webm), path);
            } catch (IOException exception) {
                throw Exceptions.newUncheckedIOException(exception);
            }
        }

        return null;
    }
}
