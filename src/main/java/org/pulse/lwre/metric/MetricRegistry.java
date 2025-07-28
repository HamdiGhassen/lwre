package org.pulse.lwre.metric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * The {@code MetricRegistry} class manages a collection of metrics for the Lightweight Rule Engine (LWRE).
 * It provides a thread-safe registry for creating and retrieving timers and meters, which are used to track
 * performance metrics such as rule execution times and error rates. Metrics are stored in a concurrent map,
 * ensuring safe access in multi-threaded environments.
 *
 * @author Hamdi Ghassen
 */
public class MetricRegistry {
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();
    /**
     * Retrieves or creates a timer metric with the specified name.
     *
     * @param name the name of the timer
     * @return the timer instance
     */
    public Timer timer(String name) {
        return (Timer) metrics.computeIfAbsent(name, k -> new Timer());
    }
    /**
     * Retrieves or creates a meter metric with the specified name.
     *
     * @param name the name of the meter
     * @return the meter instance
     */
    public Meter meter(String name) {
        return (Meter) metrics.computeIfAbsent(name, k -> new Meter());
    }
    /**
     * Retrieves the map of all registered metrics.
     *
     * @return the map of metric names to their instances
     */
    public Map<String, Object> getMetrics() {
        return metrics;
    }


}