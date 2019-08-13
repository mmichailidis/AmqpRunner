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

import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * @author MMichailidis
 */
@Slf4j
public class AmqpRunner extends SpringJUnit4ClassRunner {

    private final Map<String, BrokerManager> brokerManager;

    /**
     * Construct a new {@code SpringJUnit4ClassRunner} and initialize a
     * {@link TestContextManager} to provide Spring testing functionality to
     * standard JUnit tests.
     *
     * @param clazz the test class to be run
     * @throws InitializationError in case the initialization fails.
     * @see #createTestContextManager(Class)
     */
    public AmqpRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
        brokerManager = new HashMap<>();
    }

    @Override
    protected Object createTest() throws Exception {
        Object testClazz = super.createTest();

        try {
            testClazz = getTestContextManager().getTestContext().getTestInstance();

            final Field[] fields = testClazz.getClass().getDeclaredFields();
            for (Field f : fields) {
                if (f.isAnnotationPresent(AmqpMock.class)) {
                    final String rabbitMockValue = f.getAnnotation(AmqpMock.class).value();
                    f.setAccessible(true);
                    f.set(testClazz, brokerManager.get(rabbitMockValue));
                }
                if (f.isAnnotationPresent(AmqpPort.class)) {
                    final String rabbitMockValue = f.getAnnotation(AmqpPort.class).value();
                    f.setAccessible(true);
                    f.set(testClazz, brokerManager.get(rabbitMockValue).getAmqpPort());
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return testClazz;
    }

    @Override
    public void run(RunNotifier notifier) {
        Array.of(getTestContextManager()
                .getTestContext()
                .getTestClass()
                .getAnnotation(AmqpCreator.class)
                .value())
                .map(amqpSetup -> new BrokerManager(
                        amqpSetup.username(),
                        amqpSetup.password(),
                        amqpSetup.name(),
                        amqpSetup.amqpPort(),
                        amqpSetup.workPath(),
                        amqpSetup.logPath()))
                .forEach(manager -> brokerManager.put(manager.getName(), manager));

        List.ofAll(brokerManager.values())
                .forEach(manager -> Try.run(manager::startBroker)
                        .onFailure(Throwable::printStackTrace));

        super.run(notifier);

        brokerManager.values()
                .forEach(BrokerManager::stopBroker);

        getTestContextManager().getTestContext().markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
    }
}
