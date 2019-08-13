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

import com.rabbitmq.client.ConnectionFactory;
import gr.mmichaildis.amqprunner.broker.BrokerTester;
import gr.mmichaildis.amqprunner.broker.ReferenceHolder;
import gr.mmichaildis.amqprunner.util.PortExtractingLauncherListener;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.server.SystemLauncher;
import org.apache.qpid.server.SystemLauncherListener.DefaultSystemLauncherListener;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.*;

import static gr.mmichaildis.amqprunner.util.StreamHelpers.not;
import static gr.mmichaildis.amqprunner.util.StreamHelpers.replaceWith;
import static io.vavr.API.*;
import static java.lang.Thread.sleep;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * A wrapper class for Qpid Broker ({@link SystemLauncher} for some-reason).
 * Provides the basic functionally that is required for the tests. When Qpid
 * starts it creates two items that are obsolete after the tests and should
 * not be committed. Those items are derby.log and work directory.
 *
 * @author MMichailidis
 */
@Slf4j
public class BrokerManager {

    private static final Long SLEEP_STEP = 100L;
    private final String introduction;

    /**
     * The path that contains the configuration file for qpid initialization.
     */
    private static final String INITIAL_CONFIG_PATH = "amqp.json";
    private static final ReferenceHolder refHolder = new ReferenceHolder();

    private final String username;
    private final String password;
    private final UUID uuid;
    private final String name;
    private final Map<String, Integer> ports;

    private final Integer requestedAmqpPort;
    private final String requestedWorkPath;
    private final String requestedLogPath;

    private SystemLauncher systemLauncher;

    /**
     * Creates a {@link BrokerManager} with a username / password
     * but doesn't initialize qpid.
     *
     * @param username          The username of the broker.
     * @param password          The password of the broker.
     * @param name              The name of this {@link BrokerManager} instance.
     * @param requestedAmqpPort The amqp port in which was requested to start.
     * @param requestedWorkPath The dir for the work folder to be written.
     * @param requestedLogPath  The dir for the log folder to be written.
     */
    public BrokerManager(final String username,
                         final String password,
                         final String name,
                         final Integer requestedAmqpPort,
                         final String requestedWorkPath,
                         final String requestedLogPath) {
        this.name = name;
        this.requestedAmqpPort = requestedAmqpPort;
        this.requestedWorkPath = requestedWorkPath;
        this.requestedLogPath = requestedLogPath;

        refHolder.setQueueCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        refHolder.setExchangeCleanUpList(Collections.synchronizedList(new LinkedList<>()));

        introduction = "[BrokerManager" + (name.isEmpty() ? "" : "-" + name) + "] ";
        // this.systemLauncher = new SystemLauncher();
        final PortExtractingLauncherListener portExtractingLauncherListener = new PortExtractingLauncherListener();

        this.systemLauncher = new SystemLauncher(new DefaultSystemLauncherListener(), portExtractingLauncherListener);
        ports = portExtractingLauncherListener.getPorts();

        this.username = username;
        this.password = password;
        this.uuid = UUID.randomUUID();
    }


    /**
     * Start the broker with the properties that was initialized with.
     *
     * @throws Exception in case of exception.
     */
    public void startBroker() throws Exception {
        Map<String, String> conf = new HashMap<>();

        conf.put("qpid.port", String.valueOf(requestedAmqpPort));
        conf.put("qpid.user", username);
        conf.put("qpid.pass", password);

        log.info(introduction + "The uuid used for this instance is : " + uuid);

        conf.put("QPID_WORK", requestedWorkPath + uuid);
        conf.put("derby.system.home", requestedLogPath + uuid);

        conf.forEach(System::setProperty);

        systemLauncher.startup(createSystemConfig());
    }

    /**
     * Get the assertion results that were registered on the building mode.
     * This will sleep the main {@link Thread} so no further {@link Thread#sleep(long)} should be used.
     * Sleep time varies from the default as minimum up to
     * max(assertionTimeout) + max(assertionTimeout.verifyNoMoreEmission.timeout).
     * The sleep will <b>NOT</b> be the sum(all[assertionTimeouts]) as those timeouts are executed in parallel
     * on different {@link Thread}
     *
     * @throws InterruptedException in case of interrupted exception during sleep.
     */
    public void verify() throws InterruptedException {
        boolean flag = true;
        while (flag) {
            sleep(SLEEP_STEP);
            flag = refHolder.getAssertionVerification().shouldBlock(SLEEP_STEP);
        }

        assertNotNull(introduction + "Verification is empty. Either initialize wasn't called "
                        + "or there were no assertions to be made",
                refHolder.getAssertionVerification());

        if (!refHolder.getAssertionVerification().isValid()) {
            refHolder.getAssertionVerification().getErrors().forEach(Throwable::printStackTrace);
            fail(introduction + "Verification failed");
        }
    }

    /**
     * Shutdown the broker.
     */
    public void stopBroker() {
        log.info("Initializing shutdown sequence.");
        systemLauncher.shutdown();
        log.info("SystemLauncher shutdown complete. Cleaning up.");

        final File db = new File(requestedWorkPath + uuid);
        final File log = new File(requestedLogPath + uuid);

        Stream.of(db, log)
                .filter(File::exists)
                .forEach(BrokerManager::deleteFolder);

        try {
            sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void deleteFolder(File folder) {
        Option.of(folder.listFiles())
                .map(Arrays::asList)
                .map(list -> {
                    list.forEach(file -> {
                        if (file.isDirectory()) {
                            deleteFolder(file);
                        } else {
                            if (!file.delete()) {
                                log.error("{} failed to be deleted. Sending for retries", file.getAbsolutePath());
                                deleteFile(file, 0);
                            } else {
                                log.debug("File {} was deleted successfully", file.getAbsolutePath());

                            }
                        }
                    });
                    return list;
                })
                .map(ignore -> !folder.delete())
                .filter(Boolean::booleanValue)
                .forEach(ignore -> log.warn("Folder {} was not deleted", folder.getName()));
    }

    private static void deleteFile(File file, Integer retryStep) {
        try {
            sleep(2500 * retryStep);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Try.run(() -> sleep(2500 * retryStep))
                .filter(ignore -> retryStep < 3)
                .map(ignore -> file.delete())
                .filter(not(Boolean::booleanValue))
                .map(replaceWith(retryStep < 3))
                .forEach(hasMoreSteps -> Match(hasMoreSteps).of(
                        Case($(true), run(() -> deleteFile(file, retryStep + 1))),
                        Case($(false), run(() -> log.error("File {} failed to be deleted after {} retries", file, retryStep)))
                ));
    }

    /**
     * Cleans up all the queues and exchanges in the broker.
     */
    public void cleanUp() {
        refHolder.getQueueCleanUpList().forEach(r -> r.apply(null));
        refHolder.getExchangeCleanUpList().forEach(r -> r.apply(null));
    }

    private Map<String, Object> createSystemConfig() {
        Map<String, Object> attributes = new HashMap<>();
        final URL initialConfig = BrokerManager.class.getClassLoader().getResource(INITIAL_CONFIG_PATH);

        if (Objects.isNull(initialConfig)) {
            throw new UnsupportedOperationException("Unexpected null object on initial config");
        }

        attributes.put("type", "Memory");
        attributes.put("initialConfigurationLocation", initialConfig.toExternalForm());

        attributes.put("startupLoggedToSystemOut", true);

        return attributes;
    }

    public BrokerTester tester() {
        return new BrokerTester(createConnectionFactory(), introduction, refHolder);
    }

    public BrokerTester tester(Integer threads) {
        return new BrokerTester(createConnectionFactory(), threads, introduction, refHolder);
    }

    private ConnectionFactory createConnectionFactory() {
        ConnectionFactory connectionFactory = new ConnectionFactory();

        connectionFactory.setHost("localhost");
        connectionFactory.setPort(getAmqpPort());
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);

        return connectionFactory;
    }

    protected Integer getAmqpPort() {
        return ports.get("AMQP");
    }

    protected String getName() {
        return name;
    }
}
