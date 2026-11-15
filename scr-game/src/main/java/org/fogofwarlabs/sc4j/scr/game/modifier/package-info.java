/**
 * The SC:R upgrade effects as stat modifiers of a game: each modifier reads the game's upgrade storage, so a level outside the upgrade
 * tables reads the game-context byte the native layout addresses. The SC:R game adds them to its content when it attaches and applies
 * them on the native values ({@link org.fogofwarlabs.sc4j.scr.game.modifier.ScrStatModifier#applyNative}).
 */
package org.fogofwarlabs.sc4j.scr.game.modifier;
