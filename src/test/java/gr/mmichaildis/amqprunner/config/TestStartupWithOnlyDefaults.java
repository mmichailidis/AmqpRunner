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
package gr.mmichaildis.amqprunner.config;

import gr.mmichaildis.amqprunner.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author MMichailidis
 */
@AmqpCreator({
        @AmqpSetup()})
@RunWith(AmqpRunner.class)
public class TestStartupWithOnlyDefaults {
    @AmqpMock
    private BrokerManager brokerManager;
    @AmqpPort
    private Integer fqPort;

    @Test
    public void doNothing() throws Exception {
        final Field name = BrokerManager.class.getDeclaredField("name");
        name.setAccessible(true);

        final String o = (String) name.get(brokerManager);

        assertEquals("", o);

        final Field ports = BrokerManager.class.getDeclaredField("ports");
        ports.setAccessible(true);

        final Map m = (Map) ports.get(brokerManager);

        final Map<String, Integer> fqExpected = new HashMap<>();

        fqExpected.put("AMQP", fqPort);

        assertEquals(fqExpected, m);

        assertEquals(fqExpected.get("AMQP"), fqPort);

        final Field uuid = BrokerManager.class.getDeclaredField("uuid");
        uuid.setAccessible(true);

        final UUID u = (UUID) uuid.get(brokerManager);

        assertTrue(Files.exists(Paths.get("./build/amqp-" + u.toString())));
    }
}
