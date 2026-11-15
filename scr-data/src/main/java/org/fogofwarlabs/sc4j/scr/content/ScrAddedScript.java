package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptCode;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptFile;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes an iscript script a mod adds after the native scripts ({@link ScrAddedGraphics}): a header with one entry per animation
 * ({@link IscriptHeader#ANIMATION_TYPE_NAMES}), each the offset of installed code or a block of the mod's own code ({@link IscriptCode}).
 * <p>
 * The entries start as a copy of a native base script's header (its type and every animation offset), so an added image that names the script
 * animates as the base image does; the mod then replaces the animations it wants with its own code, for example idle, walking, attack and death
 * frames of its own frame sets. The own code is placed after the installed file inside the WORD offset space when the game data is built, so
 * images, saved positions and state hashes keep native offsets.
 */
public final class ScrAddedScript {
    /**
     * Builds {@link ScrAddedScript} instances.
     */
    public static final class Builder {
        private final int type;

        private final int @NotNull [] offsets;
        private final @Nullable IscriptCode @NotNull [] codes;

        /**
         * Creates a {@link Builder} instance with the header of a native base script.
         *
         * @param baseScript the native script id whose header is copied
         * @param iscript    the installed iscript the header is read from
         * @throws IllegalArgumentException if the iscript has no header with the id
         */
        public Builder(int baseScript, @NotNull IscriptFile iscript) {
            IscriptHeader header = iscript.findHeader(baseScript);

            if (header == null) {
                throw Exceptions.newIllegalArgumentException("The iscript has no script {0}.", baseScript);
            }

            this.type = header.getType();

            this.offsets = new int[header.getType() + 1];
            this.codes   = new IscriptCode[this.offsets.length];

            for (int animation = 0; animation < this.offsets.length; animation++) {
                this.offsets[animation] = header.getAnimationOffset(animation);
            }
        }

        /**
         * Sets an animation to a block of own code.
         * <p>
         * The blocks of a script follow each other in the iscript, so a block that runs past its last instruction runs the next one: end every
         * block in a loop or {@code end}. As in the native unit scripts, an attack that ends with {@code ignorerest} is followed by its way to
         * idle: without a target unit {@code ignorerest} starts that animation and goes on with the next instruction.
         *
         * @param animation the animation index ({@link IscriptHeader#ANIMATION_TYPE_NAMES}), below the header's animation count
         * @param code      the code
         * @return this instance for chaining
         * @throws IllegalArgumentException if the header has no entry for the animation
         */
        public @NotNull Builder setAnimation(int animation, @NotNull IscriptCode code) {
            this.checkAnimation(animation);

            this.offsets[animation] = 0;
            this.codes[animation]   = code;

            return this;
        }

        /**
         * Sets an animation to installed code.
         *
         * @param animation the animation index ({@link IscriptHeader#ANIMATION_TYPE_NAMES}), below the header's animation count
         * @param offset    the offset of the installed code, or {@code 0} for no animation
         * @return this instance for chaining
         * @throws IllegalArgumentException if the header has no entry for the animation
         */
        public @NotNull Builder setAnimationOffset(int animation, int offset) {
            this.checkAnimation(animation);

            this.offsets[animation] = offset;
            this.codes[animation]   = null;

            return this;
        }

        /**
         * Builds a {@link ScrAddedScript} instance with the current entries.
         *
         * @return the built {@link ScrAddedScript} instance
         */
        public @NotNull ScrAddedScript build() {
            return new ScrAddedScript(this);
        }

        private void checkAnimation(int animation) {
            if (animation < 0 || animation >= this.offsets.length) {
                throw Exceptions.newIllegalArgumentException("The script header of type {0} has no animation {1}.", this.type, animation);
            }
        }
    }

    private final int type;

    private final int @NotNull [] offsets;
    private final @Nullable IscriptCode @NotNull [] codes;

    private ScrAddedScript(@NotNull Builder builder) {
        this.type = builder.type;

        this.offsets = builder.offsets.clone();
        this.codes   = builder.codes.clone();
    }

    /**
     * @return the header type, the base script's
     */
    public int getType() {
        return this.type;
    }

    /**
     * @return the number of animation entries of the header
     */
    public int getAnimationCount() {
        return this.offsets.length;
    }

    /**
     * @param animation the animation index, below {@link #getAnimationCount()}
     * @return the offset of the installed code of the animation, or {@code 0} if the animation is own code or absent
     */
    public int getAnimationOffset(int animation) {
        return this.offsets[animation];
    }

    /**
     * @param animation the animation index, below {@link #getAnimationCount()}
     * @return the own code of the animation, or {@code null} if the animation is installed code or absent
     */
    public @Nullable IscriptCode getAnimationCode(int animation) {
        return this.codes[animation];
    }
}
