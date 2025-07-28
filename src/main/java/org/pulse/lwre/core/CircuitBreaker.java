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
 * The {@code CircuitBreaker} class implements a circuit breaker pattern to prevent
 * system overload by limiting execution when a threshold of failures is reached.
 * It tracks the number of failures and enforces a reset timeout period during which
 * no executions are allowed if the maximum failure count is exceeded. The class is
 * thread-safe, using synchronized methods to manage state.
 *
 * @author Hamdi Ghassen
 */
public class CircuitBreaker {
    private final int maxFailures;
    private final long resetTimeout;
    private int failureCount;
    private long lastFailureTime;

    /**
     * Constructs a new {@code CircuitBreaker} with the specified maximum failures and reset timeout.
     *
     * @param maxFailures  the maximum number of failures before the circuit breaker trips
     * @param resetTimeout the time in milliseconds to wait before resetting the failure count
     */
    public CircuitBreaker(int maxFailures, long resetTimeout) {
        this.maxFailures = maxFailures;
        this.resetTimeout = resetTimeout;
    }

    /**
     * Checks if execution is allowed based on the current failure count and reset timeout.
     * If the maximum failure count is reached and the reset timeout has not elapsed, execution is blocked.
     * Resets the failure count if the timeout has elapsed.
     *
     * @return true if execution is allowed, false otherwise
     */
    public synchronized boolean allowExecution() {
        if (failureCount >= maxFailures) {
            long timeSinceLast = System.currentTimeMillis() - lastFailureTime;
            if (timeSinceLast < resetTimeout) {
                return false;
            }
            failureCount = 0;
        }
        return true;
    }

    /**
     * Records a failure and updates the failure count and last failure timestamp.
     */
    public synchronized void recordFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
    }
}