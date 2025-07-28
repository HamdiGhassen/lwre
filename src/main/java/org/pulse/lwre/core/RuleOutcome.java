package org.pulse.lwre.core;
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * The {@code RuleOutcome} class represents the result of a rule execution in the Lightweight Rule Engine (LWRE).
 * It encapsulates the final result of the rule's execution and a boolean indicating whether the execution was successful.
 * This class is used to convey the outcome of rule processing, including any computed result or status.
 *
 * @author Hamdi Ghassen
 */
public class RuleOutcome {
    final Object finalResult;
    final boolean success;

    /**
     * Constructs a new {@code RuleOutcome} with the specified result and success status.
     *
     * @param finalResult the result of the rule execution, or null if none
     * @param success     true if the rule executed successfully, false otherwise
     */
    RuleOutcome(Object finalResult, boolean success) {
        this.finalResult = finalResult;
        this.success = success;
    }
}