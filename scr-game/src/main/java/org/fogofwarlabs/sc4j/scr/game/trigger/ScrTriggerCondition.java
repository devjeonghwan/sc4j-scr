package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.engine.trigger.TriggerCondition;

/**
 * Tests one SC:R trigger condition type over the operands of one condition record ({@link TriggerConditionOperands}). A condition is
 * registered by its type in the condition registry of a {@link TriggerContext}; the condition loop tests every enabled condition of a
 * trigger in record order and stops at the first that does not hold. One instance may serve several types that share their test.
 */
@FunctionalInterface
public interface ScrTriggerCondition extends TriggerCondition<TriggerContext, TriggerConditionOperands> {
}
