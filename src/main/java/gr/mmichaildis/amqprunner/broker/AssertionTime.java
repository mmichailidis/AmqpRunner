/*
 * Copyright 2019 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gr.mmichaildis.amqprunner.broker;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A helper class to manipulate time.
 * It has a target time and a counter. Each tick (which is setup manually)
 * moves the counter forward and <b>THEN</b> calculates the difference of
 * the target time and the current step.
 *
 * @author MMichailidis
 */
final class AssertionTime {
    private static final Integer SECOND_TO_MILLIS = 1000;
    private static final Integer INITIAL_MILLIS = 0;

    private final AtomicLong totalSeconds;
    private final AtomicLong currentMillis;

    AssertionTime(Integer totalSeconds) {
        this.totalSeconds = new AtomicLong(totalSeconds * SECOND_TO_MILLIS);
        this.currentMillis = new AtomicLong(INITIAL_MILLIS);
    }

    /**
     * Depending on the override either push the target time ahead by {@code totalMillis} or
     * set the target to {@code totalMillis} and reset the counter.
     *
     * @param totalMillis The millis that will be either setup as the new target or will put the current target ahead.
     * @param override {@code true} to override the target, {@code false} to push ahead.
     */
    void updateTotalSeconds(Long totalMillis, Boolean override) {
        if (override) {
            this.totalSeconds.set(totalMillis);
            currentMillis.set(0L);
        } else {
            this.totalSeconds.addAndGet(totalMillis);
        }
    }

    /**
     * Update the counter and get the current difference between the counter and the target.
     * The difference is calculated <b>AFTER</b> the update.
     *
     * @param sleepStep the amount of time to update the counter.
     * @return The difference between the counter and the target.
     */
    Long updateCurrentMillis(Long sleepStep) {
        return totalSeconds.get() - currentMillis.updateAndGet(curr -> curr + sleepStep);
    }
}
