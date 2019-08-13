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

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author MMichailidis
 */
public class BrokerManagerTest {

    @Test
    public void checkProperCleanUp() throws Exception {
        final String workPath = "./build/amqp-";
        final String logPath = "./build/amqpLog-";

        final BrokerManager bm = new BrokerManager("hello", "world", "",
                0, workPath,
                logPath);

        bm.startBroker();
        Thread.sleep(2000);

        final Field uuid = bm.getClass().getDeclaredField("uuid");
        uuid.setAccessible(true);
        final UUID o = (UUID) uuid.get(bm);

        final Path amqp = Paths.get(workPath + o.toString());
        final Path amqpLog = Paths.get(logPath + o.toString());

        assertTrue("Amqp work was not created", Files.exists(amqp));
        final boolean wasLogCreated = Files.exists(amqpLog);

        bm.stopBroker();

        assertFalse("Amqp work was not deleted", Files.exists(amqp));
        if (wasLogCreated)
            assertFalse("AmqpLog was not deleted", Files.exists(amqpLog));
    }
}