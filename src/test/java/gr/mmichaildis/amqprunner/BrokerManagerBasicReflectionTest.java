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
package gr.mmichaildis.amqprunner;

import org.junit.Test;
import org.junit.runner.notification.RunNotifier;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * @author MMichailidis
 */
public class BrokerManagerBasicReflectionTest {

    @AmqpCreator({
            @AmqpSetup(
                    name = "fq",
                    amqpPort = 8095,
                    managementPort = 8097,
                    management = true),
            @AmqpSetup(name = "sq",
                    amqpPort = 8094,
                    managementPort = 8096,
                    management = true)})
    public static class SomeTest {

        @AmqpMock("fq")
        private BrokerManager brokerManager;
        @AmqpMock("sq")
        private BrokerManager brokerManagerSq;

        @AmqpPort("fq")
        private Integer fqPort;
        @AmqpPort("sq")
        private Integer sqPort;

        @Test
        public void demoTest() {
        }
    }

    @Test
    public void demo() throws Exception {
        final Map<String, BrokerManager> brokerManager = new HashMap<>();

        final AmqpRunner ar = new AmqpRunner(SomeTest.class);

        final Field vL = AmqpRunner.class.getDeclaredField("brokerManager");
        vL.setAccessible(true);

        final Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(vL, vL.getModifiers() & ~Modifier.FINAL);

        vL.set(ar, brokerManager);

        final RunNotifier runNotifier = mock(RunNotifier.class);
        ar.run(runNotifier);
        final SomeTest test = (SomeTest) ar.createTest();

        assertNotNull(test.brokerManager);
        assertNotNull(test.brokerManagerSq);

        assertEquals(8095, (int) test.fqPort);
        assertEquals(8094, (int) test.sqPort);

        final Map<String, Integer> fqMap = new HashMap<>();
        fqMap.put("AMQP", 8095);
        fqMap.put("HTTP", 8097);

        final Map<String, Integer> sqMap = new HashMap<>();
        sqMap.put("AMQP", 8094);
        sqMap.put("HTTP", 8096);

        final Field ports = BrokerManager.class.getDeclaredField("ports");
        ports.setAccessible(true);

        assertEquals(fqMap, ports.get(test.brokerManager));
        assertEquals(sqMap, ports.get(test.brokerManagerSq));
    }
}
