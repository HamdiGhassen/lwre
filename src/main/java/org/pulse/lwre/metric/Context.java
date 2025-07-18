package org.pulse.lwre.metric;
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
 * The {@code Context} class is a utility for the Lightweight Rule Engine (LWRE) to measure the duration of
 * an event within a {@code Timer}. It implements {@code AutoCloseable} to support try-with-resources usage,
 * automatically updating the associated timer with the elapsed time when closed. This class is used to track
 * execution times of rule components in a thread-safe manner.
 *
 * @author Hamdi Ghassen
 */
public class Context implements AutoCloseable {
    private final Timer timer;
    private final long start;
    /**
     * Constructs a new {@code Context} for timing an event with the specified timer and start time.
     *
     * @param timer the timer to update
     * @param start the start time in nanoseconds
     */
    public Context(Timer timer, long start) {
        this.timer = timer;
        this.start = start;
    }
    /**
     * Closes the context, updating the associated timer with the elapsed time since the context was created.
     */
    @Override
    public void close() {
        timer.update(System.nanoTime() - start);
    }
}