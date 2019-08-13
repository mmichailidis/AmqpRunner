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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author MMichailidis
 */
public class AssertionTimeTester {

    @Test
    public void testUpdateTotalSecondsNoOverride() {
        AssertionTime at = new AssertionTime(1);

        assertEquals(1000, (long) at.updateCurrentMillis(0L));

        at.updateTotalSeconds(100L, false);

        assertEquals(1100, (long) at.updateCurrentMillis(0L));
    }

    @Test
    public void testUpdateTotalSecondsOverride() {
        AssertionTime at = new AssertionTime(1);

        assertEquals(1000, (long) at.updateCurrentMillis(0L));

        at.updateTotalSeconds(5000L, true);

        assertEquals(5000, (long) at.updateCurrentMillis(0L));
    }

    @Test
    public void updateCurrentMillis() {
        AssertionTime at = new AssertionTime(1);

        assertEquals(950, (long) at.updateCurrentMillis(50L));
    }
}