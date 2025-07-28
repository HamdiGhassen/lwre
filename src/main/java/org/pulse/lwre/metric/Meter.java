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
 * The {@code Meter} class is a metric utility for the Lightweight Rule Engine (LWRE) to track the frequency
 * of events, such as errors or rule executions. It maintains a count of events using thread-safe atomic variables
 * and provides methods to mark occurrences and calculate the mean rate of events over time.
 *
 * @author Hamdi Ghassen
 */
public class Meter {
    private final AtomicLong count = new AtomicLong();
    private final LongAdder total = new LongAdder();
    private final long startTime = System.nanoTime();
    /**
     * Marks a single occurrence of an event.
     */
    public void mark() {
        mark(1);
    }
    /**
     * Marks multiple occurrences of an event.
     *
     * @param n the number of occurrences to mark
     */
    public void mark(long n) {
        count.addAndGet(n);
        total.add(n);
    }
    /**
     * Retrieves the total number of marked events.
     *
     * @return the event count
     */
    public long getCount() {
        return count.get();
    }
    /**
     * Calculates the mean rate of events per second since the meter was created.
     *
     * @return the mean rate in events per second
     */
    public double getMeanRate() {
        long countVal = count.get();
        if (countVal == 0) return 0;
        double elapsedSec = (System.nanoTime() - startTime) / 1e9;
        return countVal / elapsedSec;
    }
}