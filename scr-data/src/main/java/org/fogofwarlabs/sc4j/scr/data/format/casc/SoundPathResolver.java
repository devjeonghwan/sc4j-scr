package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Resolves sound paths using the requested locale, English and unlocalized assets.
 * <p>
 * Resolution tries the selected audio set before the other set. Remastered audio uses the storage root;
 * classic audio uses the SD prefix.
 */
public final class SoundPathResolver {
    private static final @NotNull String REMASTERED_EXTENSION = ".ogg";

    /**
     * The installed audio sets: the shared resolver's classic set and its storage-root set ({@link AssetResolver.AssetSet}).
     */
    public enum AudioSet {
        /**
         * Remastered audio at the storage root.
         */
        REMASTERED(AssetResolver.AssetSet.HD),
        /**
         * Classic audio below {@code SD/}.
         */
        CLASSIC(AssetResolver.AssetSet.SD);

        private final @NotNull String prefix;

        AudioSet(AssetResolver.@NotNull AssetSet assetSet) {
            this.prefix = assetSet.getPrefix();
        }

        /**
         * @return the path prefix of this set, empty for the storage root
         */
        public @NotNull String getPrefix() {
            return this.prefix;
        }
    }


    private final @NotNull CascStorage storage;
    private final @NotNull String locale;
    private final @NotNull AudioSet audioSet;

    /**
     * Creates a {@link SoundPathResolver} instance.
     *
     * @param storage  the installed storage
     * @param locale   the game locale ({@link GameLocaleUtils#select}), for example {@code koKR}
     * @param audioSet the preferred audio set
     */
    public SoundPathResolver(@NotNull CascStorage storage, @NotNull String locale, @NotNull AudioSet audioSet) {
        this.storage  = storage;
        this.locale   = locale;
        this.audioSet = audioSet;
    }

    /**
     * @return the speech locale
     */
    public @NotNull String getLocale() {
        return this.locale;
    }

    /**
     * @return the preferred audio set
     */
    public @NotNull AudioSet getAudioSet() {
        return this.audioSet;
    }


    /**
     * Returns the storage path of a sound.
     *
     * @param tableName the sfxdata.tbl name, for example {@code Protoss\DARCHON\PDaRdy00.WAV}
     * @return the first existing storage path, or {@code null} when no candidate exists
     */
    public @Nullable String resolve(@NotNull String tableName) {
        String name = tableName.replace('\\', '/');
        String path = this.resolveLocalized(this.audioSet.getPrefix() + "sound/" + name);

        if (path != null) {
            return path;
        }

        return this.resolveLocalized((this.audioSet == AudioSet.REMASTERED ? AudioSet.CLASSIC : AudioSet.REMASTERED).getPrefix() + "sound/" + name);
    }

    /**
     * Reads a sound through {@link #resolve(String)}.
     *
     * @param tableName the sfxdata.tbl name
     * @return the WAV file
     * @throws IOException if no candidate exists or the file cannot be read
     */
    public byte @NotNull [] read(@NotNull String tableName) throws IOException {
        String path = this.resolve(tableName);

        if (path == null) {
            throw Exceptions.newIOException("The sound does not exist in the storage for locale {0}: {1}", this.locale, tableName);
        }

        return this.storage.readFile(path);
    }

    /**
     * Resolves raw map path bytes when they form a valid UTF-8 asset name.
     * Other encodings cannot match storage names; {@link #readAsset(byte[], MpqArchive)} can use the map archive.
     *
     * @param rawPath the raw asset path bytes below the audio-set root, without a terminator
     * @return the first existing storage path, or {@code null} when no candidate exists
     */
    public @Nullable String resolveAsset(byte @NotNull [] rawPath) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                                       .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            return this.resolveAsset(decoder.decode(ByteBuffer.wrap(rawPath)).toString());
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    /**
     * Resolves a sound asset below the audio-set root using locale and audio-set fallbacks.
     * The remastered set tries the OGG extension before the supplied name.
     *
     * @param assetPath the asset path below the audio-set root
     * @return the first existing storage path, or {@code null} when no candidate exists
     */
    public @Nullable String resolveAsset(@NotNull String assetPath) {
        String name = assetPath.replace('\\', '/');
        String path = this.resolveAssetInSet(this.audioSet, name);

        if (path != null) {
            return path;
        }

        return this.resolveAssetInSet(this.audioSet == AudioSet.REMASTERED ? AudioSet.CLASSIC : AudioSet.REMASTERED, name);
    }

    /**
     * Reads a raw map path from storage first, then from the open map archive.
     * The map archive hashes the supplied raw bytes.
     *
     * @param rawPath    the raw asset path bytes, without a terminator
     * @param mapArchive the open map archive, or {@code null} without one
     * @return the file contents, or {@code null} when neither holds the file
     * @throws IOException if a found file cannot be read
     */
    public byte @Nullable [] readAsset(byte @NotNull [] rawPath, @Nullable MpqArchive mapArchive) throws IOException {
        String path = this.resolveAsset(rawPath);

        if (path != null) {
            return this.storage.readFile(path);
        }

        if (mapArchive == null || mapArchive.findEntry(rawPath) == null) {
            return null;
        }

        return mapArchive.readFile(rawPath);
    }


    private @Nullable String resolveAssetInSet(@NotNull AudioSet set, @NotNull String name) {
        int dotIndex = name.lastIndexOf('.');

        if (set == AudioSet.REMASTERED && dotIndex > name.lastIndexOf('/') && !name.substring(dotIndex).equalsIgnoreCase(REMASTERED_EXTENSION)) {
            String path = this.resolveLocalized(set.getPrefix() + name.substring(0, dotIndex) + REMASTERED_EXTENSION);

            if (path != null) {
                return path;
            }
        }

        return this.resolveLocalized(set.getPrefix() + name);
    }

    private @Nullable String resolveLocalized(@NotNull String asset) {
        // The audio-set prefix is part of the asset path, so the resolver adds no quality prefix (the root set).
        return new AssetResolver(this.storage, AssetResolver.AssetSet.HD, "", this.locale).resolveLocalized(asset);
    }
}
