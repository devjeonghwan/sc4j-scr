package org.fogofwarlabs.sc4j.scr.client.audio;

/**
 * Stores music, sound effect, voice and subtitle settings and derives linear playback gains.
 */
public final class SoundSettings {
    /**
     * The default music volume.
     */
    public static final int DEFAULT_MUSIC_VOLUME = 25;

    /**
     * The default sound effect volume.
     */
    public static final int DEFAULT_SOUND_EFFECTS_VOLUME = 50;

    /**
     * The highest volume; the setters clamp to it.
     */
    public static final int MAXIMUM_VOLUME = 100;

    /**
     * Clamps an unsigned volume to the maximum.
     *
     * @param volume the volume
     * @return the clamped volume
     */
    private static int clampVolume(int volume) {
        return Integer.compareUnsigned(volume, MAXIMUM_VOLUME) > 0 ? MAXIMUM_VOLUME : volume;
    }


    private int musicVolume;
    private boolean musicEnabled;

    private int soundEffectsVolume;
    private boolean soundEffectsEnabled;

    private boolean unitSpeech;
    private boolean unitAcknowledgments;
    private boolean buildingSounds;
    private boolean soundInBackground;

    private boolean gameSubtitles;

    private boolean cinematicSubtitles;
    private boolean originalUnitVoices;
    private boolean originalCampaign;

    /**
     * Creates a {@link SoundSettings} instance with the default playback and subtitle settings.
     */
    public SoundSettings() {
        this.musicVolume  = DEFAULT_MUSIC_VOLUME;
        this.musicEnabled = true;

        this.soundEffectsVolume  = DEFAULT_SOUND_EFFECTS_VOLUME;
        this.soundEffectsEnabled = true;

        this.unitSpeech          = true;
        this.unitAcknowledgments = true;
        this.buildingSounds      = true;
        this.soundInBackground   = true;

        this.gameSubtitles = true;

        this.cinematicSubtitles = true;
        this.originalUnitVoices = false;
        this.originalCampaign   = false;
    }

    /**
     * @return {@code music_volume}, from {@code 0} to {@code 100}
     */
    public int getMusicVolume() {
        return this.musicVolume;
    }

    /**
     * Sets the music volume with an unsigned clamp, so negative values become the maximum.
     *
     * @param musicVolume the volume
     */
    public void setMusicVolume(int musicVolume) {
        this.musicVolume = clampVolume(musicVolume);
    }

    /**
     * @return {@code true} if {@code music_enabled} is on, {@code false} otherwise
     */
    public boolean isMusicEnabled() {
        return this.musicEnabled;
    }

    /**
     * Sets {@code music_enabled}.
     *
     * @param musicEnabled whether music plays
     */
    public void setMusicEnabled(boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
    }

    /**
     * Returns the music volume divided by the maximum while music is enabled, otherwise {@code 0}.
     *
     * @return the linear gain from {@code 0} to {@code 1}
     */
    public float getMusicGain() {
        return this.musicEnabled ? this.musicVolume / (float) MAXIMUM_VOLUME : 0.0f;
    }

    /**
     * @return {@code true} if music is enabled with nonzero volume, {@code false} otherwise
     */
    public boolean isMusicAudible() {
        return this.musicEnabled && this.musicVolume != 0;
    }


    /**
     * @return {@code sfx_volume}, from {@code 0} to {@code 100}
     */
    public int getSoundEffectsVolume() {
        return this.soundEffectsVolume;
    }

    /**
     * Sets the sound effect volume with an unsigned clamp, so negative values become the maximum.
     *
     * @param soundEffectsVolume the volume
     */
    public void setSoundEffectsVolume(int soundEffectsVolume) {
        this.soundEffectsVolume = clampVolume(soundEffectsVolume);
    }

    /**
     * @return {@code true} if {@code sfx_enabled} is on, {@code false} otherwise
     */
    public boolean isSoundEffectsEnabled() {
        return this.soundEffectsEnabled;
    }

    /**
     * Sets {@code sfx_enabled}.
     *
     * @param soundEffectsEnabled whether sound effects play
     */
    public void setSoundEffectsEnabled(boolean soundEffectsEnabled) {
        this.soundEffectsEnabled = soundEffectsEnabled;
    }

    /**
     * Returns the sound effect volume divided by the maximum while effects are enabled, otherwise {@code 0}.
     *
     * @return the linear gain from {@code 0} to {@code 1}
     */
    public float getSoundEffectsGain() {
        return this.soundEffectsEnabled ? this.soundEffectsVolume / (float) MAXIMUM_VOLUME : 0.0f;
    }

    /**
     * @return {@code true} if effects are enabled with nonzero volume, {@code false} otherwise
     */
    public boolean isSoundEffectsAudible() {
        return this.soundEffectsEnabled && this.soundEffectsVolume != 0;
    }


    /**
     * @return {@code true} if {@code unitspeech} is on (the advisor lines: unit ready, work done, under attack), {@code false} otherwise
     */
    public boolean isUnitSpeech() {
        return this.unitSpeech;
    }

    /**
     * Sets {@code unitspeech}.
     *
     * @param unitSpeech whether the advisor lines play
     */
    public void setUnitSpeech(boolean unitSpeech) {
        this.unitSpeech = unitSpeech;
    }

    /**
     * @return {@code true} if {@code unitnoise} is on (the selection and order responses of units), {@code false} otherwise
     */
    public boolean isUnitAcknowledgments() {
        return this.unitAcknowledgments;
    }

    /**
     * Sets {@code unitnoise}.
     *
     * @param unitAcknowledgments whether the unit responses play
     */
    public void setUnitAcknowledgments(boolean unitAcknowledgments) {
        this.unitAcknowledgments = unitAcknowledgments;
    }

    /**
     * @return {@code true} if {@code bldgnoise} is on (the selection sounds of buildings and the add-on detached line), {@code false} otherwise
     */
    public boolean isBuildingSounds() {
        return this.buildingSounds;
    }

    /**
     * Sets {@code bldgnoise}.
     *
     * @param buildingSounds whether the building sounds play
     */
    public void setBuildingSounds(boolean buildingSounds) {
        this.buildingSounds = buildingSounds;
    }

    /**
     * @return {@code true} if {@code sound_in_background} is on, {@code false} otherwise
     */
    public boolean isSoundInBackground() {
        return this.soundInBackground;
    }

    /**
     * Sets {@code sound_in_background}.
     *
     * @param soundInBackground whether sound plays while the window is in the background
     */
    public void setSoundInBackground(boolean soundInBackground) {
        this.soundInBackground = soundInBackground;
    }


    /**
     * @return {@code true} if {@code trigtext} is on, {@code false} otherwise
     */
    public boolean isGameSubtitles() {
        return this.gameSubtitles;
    }

    /**
     * Sets {@code trigtext}.
     *
     * @param gameSubtitles whether trigger text shows without its always-display flag
     */
    public void setGameSubtitles(boolean gameSubtitles) {
        this.gameSubtitles = gameSubtitles;
    }


    /**
     * @return {@code true} if cinematics start with subtitles shown, {@code false} otherwise
     */
    public boolean isCinematicSubtitles() {
        return this.cinematicSubtitles;
    }

    /**
     * Sets Cinematic Subtitles ({@code cinematicSubtitlesEnabled}).
     *
     * @param cinematicSubtitles whether a cinematic starts with its subtitles shown
     */
    public void setCinematicSubtitles(boolean cinematicSubtitles) {
        this.cinematicSubtitles = cinematicSubtitles;
    }

    /**
     * @return {@code true} if original English unit voices are selected, {@code false} otherwise
     */
    public boolean isOriginalUnitVoices() {
        return this.originalUnitVoices;
    }

    /**
     * Sets Original Unit Voice Overs ({@code originalUnitVO}).
     *
     * @param originalUnitVoices whether the unit voices are the original English ones
     */
    public void setOriginalUnitVoices(boolean originalUnitVoices) {
        this.originalUnitVoices = originalUnitVoices;
    }

    /**
     * @return {@code true} if original campaign scripts are selected, {@code false} otherwise
     */
    public boolean isOriginalCampaign() {
        return this.originalCampaign;
    }

    /**
     * Sets Original 1998 Campaign ({@code originalCampaign}).
     *
     * @param originalCampaign whether the campaign uses its original 1998 scripts
     */
    public void setOriginalCampaign(boolean originalCampaign) {
        this.originalCampaign = originalCampaign;
    }
}
