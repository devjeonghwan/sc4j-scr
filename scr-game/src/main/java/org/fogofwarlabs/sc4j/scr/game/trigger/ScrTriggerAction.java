package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.engine.trigger.TriggerAction;

/**
 * Executes one SC:R trigger action type for a running trigger ({@link RunningTrigger}, with its execution flags and action cursor) and one
 * action record ({@link TriggerDefinition.Action}). An action is registered by its type in the action registry of a {@link TriggerContext};
 * the action loop calls it for every enabled action of a running trigger and keeps the action cursor when it fails, so that the trigger
 * resumes at that action. One instance may serve several types that share their behavior.
 */
@FunctionalInterface
public interface ScrTriggerAction extends TriggerAction<TriggerContext, RunningTrigger, TriggerDefinition.Action> {
}
