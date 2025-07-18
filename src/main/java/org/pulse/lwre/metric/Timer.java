package org.pulse.lwre.metric;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
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
 * The {@code Timer} class is a metric utility for the Lightweight Rule Engine (LWRE) to measure the duration
 * and frequency of events, such as rule executions. It tracks the number of events and their total duration
 * using thread-safe atomic variables, providing methods to calculate mean rates and approximate percentiles.
 * The class supports creating timing contexts for measuring elapsed time and updating metrics accordingly.
 *
 * @author Hamdi Ghassen
 */
public class Timer {
    private final AtomicLong count = new AtomicLong();
    private final LongAdder totalTime = new LongAdder();
    private final long startTime = System.nanoTime();
    /**
     * Creates a new timing context to measure the duration of an event.
     *
     * @return a new {@code Context} instance
     */
    public Context time() {
        return new Context(this, System.nanoTime());
    }
    /**
     * Retrieves the total number of timed events.
     *
     * @return the event count
     */
    public long getCount() {
        return count.get();
    }
    /**
     * Calculates the mean rate of events per second since the timer was created.
     *
     * @return the mean rate in events per second
     */
    public double getMeanRate() {
        long countVal = count.get();
        if (countVal == 0) return 0;
        double elapsedSec = (System.nanoTime() - startTime) / 1e9;
        return countVal / elapsedSec;
    }
    /**
     * Calculates an approximate 99th percentile duration in milliseconds.
     *
     * @return the approximate 99th percentile duration
     */
    public double get99thPercentile() {
        return totalTime.doubleValue() / Math.max(1, count.get()) / 1e6;
    }
    /**
     * Updates the timer with the duration of an event.
     *
     * @param duration the duration in nanoseconds
     */
    public void update(long duration) {
        count.incrementAndGet();
        totalTime.add(duration);
    }


}