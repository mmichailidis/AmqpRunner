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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;
import gr.mmichaildis.amqprunner.BrokerManager;
import gr.mmichaildis.amqprunner.TestFunction;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Try;
import org.reactivestreams.Publisher;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.*;
import reactor.util.function.Tuple2;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static gr.mmichaildis.amqprunner.util.StreamHelpers.not;
import static gr.mmichaildis.amqprunner.util.StreamHelpers.replaceWith;
import static java.lang.Thread.sleep;

/**
 * @author MMichailidis
 */
@SuppressWarnings("UnusedReturnValue")
public final class BrokerTester {
    //Incoming from BrokerManager.
    private final String introduction;
    private final ReferenceHolder referenceHolder;

    //Self created final items.
    private final CountDownLatch latch;
    /**
     * DEFAULT_VALUE = {@value}
     */
    private final static String DEFAULT_VALUE = "default";
    /**
     * DEFAULT_IDENTIFIER = {@value}
     */
    private final static String DEFAULT_IDENTIFIER = "";
    /**
     * DEFAULT_COUNT = {@value}
     */
    private final static int DEFAULT_COUNT = 1;
    /**
     * DEFAULT_TIMEOUT = {@value}
     */
    private final static int DEFAULT_TIMEOUT = 5;
    private final ExecutorService es;

    //Mutable items for testing purposes.
    private Channel channel;
    private final Map<String, ExchangeProperties> exchangeNames;
    private final Map<String, QueueProperties> queueNames;
    private final Map<String, AMQPBinding> bindings;
    private final Map<String, QueueProperties> singleQueues;
    private final Map<String, ExchangeProperties> singleExchanges;
    private final Map<String, ObjectMapper> theMappers;
    private AssertionVerification verification;
    private Boolean verifyNoMoreEmissions;
    private Integer verificationTimeout;

    private final ReceiverOptions receiverOptions;
    private final Receiver receiver;

    public BrokerTester(final ConnectionFactory connectionFactory,
                        final Integer threads,
                        final String introduction,
                        final ReferenceHolder referenceHolder) {
        latch = new CountDownLatch(1);
        this.introduction = introduction.trim();
        this.referenceHolder = referenceHolder;
        exchangeNames = new HashMap<>();
        queueNames = new HashMap<>();
        theMappers = new HashMap<>();
        bindings = new HashMap<>();
        singleQueues = new HashMap<>();
        singleExchanges = new HashMap<>();
        verification = new AssertionVerification(DEFAULT_VALUE, 2);
        final ThreadFactoryBuilder tfb = new ThreadFactoryBuilder();
        es = Executors.newFixedThreadPool(threads, tfb.setDaemon(true)
                .setNameFormat(introduction.replace("]", "-%d]")).build());
        verifyNoMoreEmissions = false;

        receiverOptions = new ReceiverOptions().connectionFactory(connectionFactory);
        receiver = RabbitFlux.createReceiver(receiverOptions);
    }

    public BrokerTester(final ConnectionFactory connectionFactory,
                        final String introduction,
                        final ReferenceHolder referenceHolder) {
        this(connectionFactory, 5, introduction, referenceHolder);
    }

    /**
     * Updates the minimum sleep which will be applied on {@link BrokerManager#verify()}.
     *
     * @param seconds The minimum seconds to sleep.
     * @return self for continuation
     */
    public BrokerTester defaultSleep(final Integer seconds) {
        verification.setBlock(DEFAULT_VALUE, seconds);
        return this;
    }

    public BrokerTester declareQueue(final QueueProperties queueProperties) {
        isQueueUnique(queueProperties);

        singleQueues.put(queueProperties.getName(), queueProperties);

        return this;
    }

    public BrokerTester declareExchange(final ExchangeProperties exchangeProperties) {
        isExchangeUnique(exchangeProperties);

        singleExchanges.put(exchangeProperties.getName(), exchangeProperties);

        return this;
    }

    /**
     * Declares a {@link AMQPBinding} with empty routing key.
     * The binding will not be initialized until {@link BrokerTester#initialize()} is called.
     *
     * @param queueProperties    The {@link QueueProperties} that will be used to create the queue.
     * @param exchangeProperties The {@link ExchangeProperties} that will be used to create the exchange.
     * @return self for continuation
     */
    public BrokerTester declareBinding(final QueueProperties queueProperties,
                                       final ExchangeProperties exchangeProperties) {
        return declareBinding(queueProperties, exchangeProperties, "");
    }


    /**
     * Declares a {@link AMQPBinding}.
     * The binding will not be initialized until {@link BrokerTester#initialize()} is called.
     *
     * @param queueProperties    The {@link QueueProperties} that will be used to create the queue.
     * @param exchangeProperties The {@link ExchangeProperties} that will be used to create the exchange.
     * @param routingKey         The routingKey that will be used.
     * @return self for continuation
     */
    public BrokerTester declareBinding(final QueueProperties queueProperties,
                                       final ExchangeProperties exchangeProperties,
                                       final String routingKey) {
        isQueueUnique(queueProperties);
        isExchangeUnique(exchangeProperties);

        exchangeNames.put(exchangeProperties.getName(), exchangeProperties);
        queueNames.put(queueProperties.getName(), queueProperties);

        bindings.put(bindingNameGenerator(exchangeProperties, queueProperties, routingKey),
                AMQPBinding.from(routingKey, exchangeProperties, queueProperties));

        return this;
    }

    /**
     * Assert that the desired queue will have the desired emissions in the desired time window.
     * No identifier will be provided for this assertion.
     *
     * @param queueName The queue that will be monitors for emitted messages.
     * @param count     The count of the desired emitted messages.
     * @param timeout   The maximum time in seconds that will wait for the desired emitted messages.
     * @return self for continuation
     */
    public BrokerTester expectNextCount(final String queueName,
                                        final Integer count,
                                        final Integer timeout) {
        return expectNextCount(queueName, count, timeout, DEFAULT_IDENTIFIER);
    }

    /**
     * Assert that the desired queue will have the desired emissions in the desired time window.
     *
     * @param queueName  The queue that will be monitors for emitted messages.
     * @param count      The count of the desired emitted messages.
     * @param timeout    The maximum time in seconds that will wait for the desired emitted messages.
     * @param identifier The assertion identifier
     * @return self for continuation
     */
    public BrokerTester expectNextCount(final String queueName,
                                        final Integer count,
                                        final Integer timeout,
                                        final String identifier) {
        es.submit(() -> {
            Mono.just(verification.getNextName("nextCount"))
                    .flatMap(myName ->
                            getDeliveries(myName, queueName, timeout, count, identifier)
                                    .filter(Tuple2::getT2)
                                    .map(Tuple2::getT1)
                                    .doOnNext(item -> {
                                        if (item.size() == count) {
                                            verification.setValid(myName, true);
                                            verification.clearAssertionError(myName);
                                        } else {
                                            verification.setValid(myName, false);
                                            verification.setAssertionError(myName,
                                                    new AssertionError(createMessage(identifier,
                                                            "Too little emissions.")));
                                            verification.removeBlock(myName);
                                        }
                                    })
                                    .doFinally(ignore -> verification.removeBlock(myName)))
                    .subscribe();
        });

        return this;
    }

    /**
     * Assert that the desired queue will have no emissions in the desired time window.
     * The maximum monitoring time for messages will be the default ( {@value #DEFAULT_TIMEOUT} seconds )
     * No identifier will be provided for this assertion.
     *
     * @param queueName The queue that will be monitored for emitted messages.
     * @return self for continuation
     */
    public BrokerTester expectNoEmissions(final String queueName) {
        return expectNoEmissions(queueName, DEFAULT_IDENTIFIER);
    }

    /**
     * Assert that the desired queue will have no emissions in the desired time window.
     * No identifier will be provided for this assertion.
     *
     * @param queueName The queue that will be monitored for emitted messages.
     * @param timeout   The maximum time in seconds that will wait for the monitoring of messages.
     * @return self for continuation
     */
    public BrokerTester expectNoEmissions(final String queueName,
                                          final Integer timeout) {
        return expectNoEmissions(queueName, timeout, DEFAULT_IDENTIFIER);
    }

    /**
     * Assert that the desired queue will have no emissions in the desired time window.
     * The maximum monitoring time for messages will be the default ( {@value #DEFAULT_TIMEOUT} seconds )
     *
     * @param queueName  The queue that will be monitored for emitted messages.
     * @param identifier The assertion identifier
     * @return self for continuation
     */
    public BrokerTester expectNoEmissions(final String queueName,
                                          final String identifier) {
        return expectNoEmissions(queueName, DEFAULT_TIMEOUT, identifier);
    }

    /**
     * Assert that the desired queue will have no emissions in the desired time window.
     *
     * @param queueName  The queue that will be monitored for emitted messages.
     * @param timeout    The maximum time in seconds that will wait for the monitoring of messages.
     * @param identifier The assertion identifier
     * @return self for continuation
     */
    public BrokerTester expectNoEmissions(final String queueName,
                                          final Integer timeout,
                                          final String identifier) {
        es.submit(() -> {
            Mono.just(verification.getNextName("expectNoEmissions"))
                    .doOnNext(myName -> initiateLock(myName, timeout, identifier))
                    .flatMap(myName -> verifyNoMoreEmissions(queueName, myName, Mono.empty(), timeout)
                            .switchIfEmpty(Mono.just(true))
                            .zipWith(Mono.just(myName)))
                    .doOnNext(item -> {
                        if (!item.getT1()) {
                            verification.setValid(item.getT2(), false);
                            verification.setAssertionError(item.getT2(),
                                    new AssertionError(createMessage(identifier,
                                            "Expected no emission.")));
                            verification.removeBlock(item.getT2());
                        } else {
                            verification.setValid(item.getT2(), true);
                            verification.clearAssertionError(item.getT2());
                        }
                    })
                    .doOnNext(ignore -> verification.removeBlock(ignore.getT2()))
                    .subscribe();
        });

        return this;
    }

    /**
     * Assert that the desired queue will have the desired emissions in the desired time window.
     * Then apply an assertion function on the deserialized message for custom assertions.
     * <p>
     * The desired emitted message count will be the default ( {@value #DEFAULT_VALUE} )
     * The maximum wait time for emitted messages will be the default ( {@value #DEFAULT_TIMEOUT} seconds )
     * The objectMapper that will be used will be the default.
     *
     * @param queueName  The queue that will be monitored for emitted messages.
     * @param clazz      The Class that the emitted messages will be deserialized into.
     * @param assertions The assertions that will be applied on the emissions.
     * @param <T>        The emitted message payload {@link Class}
     * @param identifier The assertion identifier
     * @return self for continuation
     */
    public <T> BrokerTester assertNextWith(final String queueName,
                                           final Class<T> clazz,
                                           final TestFunction<T> assertions,
                                           final String identifier) {
        return assertNextWith(queueName, DEFAULT_COUNT, DEFAULT_TIMEOUT,
                clazz, assertions, identifier);
    }

    /**
     * Assert that the desired queue will have the desired emissions in the desired time window.
     * Then apply an assertion function on the deserialized message for custom assertions.
     * <p>
     * The desired emitted message count will be the default ( {@value #DEFAULT_COUNT} )
     * The maximum wait time for emitted messages will be the default ( {@value #DEFAULT_TIMEOUT} second )
     * The objectMapper that will be used will be the default.
     * No identifier will be provided for this assertion.
     *
     * @param queueName  The queue that will be monitored for emitted messages.
     * @param clazz      The Class that the emitted messages will be deserialized into.
     * @param assertions The assertions that will be applied on the emissions.
     * @param <T>        The emitted message payload {@link Class}
     * @return self for continuation
     */
    public <T> BrokerTester assertNextWith(final String queueName,
                                           final Class<T> clazz,
                                           final TestFunction<T> assertions) {
        return assertNextWith(queueName, DEFAULT_COUNT, DEFAULT_TIMEOUT,
                clazz, assertions, DEFAULT_IDENTIFIER);
    }

    /**
     * Assert that the desired queue will have the desired emissions in the desired time window.
     * Then apply an assertion function on the deserialized message for custom assertions.
     * <p>
     * The objectMapper that will be used will be the default.
     *
     * @param queueName  The queue that will be monitored for emitted messages.
     * @param count      The count of the desired emitted messages.
     * @param timeout    The maximum time in seconds that this function will wait for
     *                   the desired emitted messages.
     * @param clazz      The Class that the emitted messages will be deserialized into.
     * @param assertions The assertions that will be applied on the emissions.
     * @param <T>        The emitted message payload {@link Class}
     * @param identifier The assertion identifier
     * @return self for continuation
     */
    public <T> BrokerTester assertNextWith(final String queueName,
                                           final Integer count,
                                           final Integer timeout,
                                           final Class<T> clazz,
                                           final TestFunction<T> assertions,
                                           final String identifier) {
        return assertNextWith(queueName, count, timeout, clazz, assertions,
                DEFAULT_VALUE, identifier);
    }

    /**
     * Assert that the desired queue will have the desired emissions in the desired time window.
     * Then apply an assertion function on the deserialized message for custom assertions.
     * <p>
     * The objectMapper that will be used will be the default.
     * No identifier will be provided for this assertion.
     *
     * @param queueName  The queue that will be monitored for emitted messages.
     * @param count      The count of the desired emitted messages.
     * @param timeout    The maximum time in seconds that this function will wait for
     *                   the desired emitted messages.
     * @param clazz      The Class that the emitted messages will be deserialized into.
     * @param assertions The assertions that will be applied on the emissions.
     * @param <T>        The emitted message payload {@link Class}
     * @return self for continuation
     */
    public <T> BrokerTester assertNextWith(final String queueName,
                                           final Integer count,
                                           final Integer timeout,
                                           final Class<T> clazz,
                                           final TestFunction<T> assertions) {
        return assertNextWith(queueName, count, timeout, clazz, assertions,
                DEFAULT_VALUE, DEFAULT_IDENTIFIER);
    }

    /**
     * Assert that the desired queue will have the desired emissions in the desired time window.
     * Then apply an assertion function on the deserialized message for custom assertions.
     *
     * @param queueName       The queue that will be monitored for emitted messages.
     * @param count           The count of the desired emitted messages.
     * @param timeout         The maximum time in seconds that this function will wait for the
     *                        desired emitted messages.
     * @param clazz           The Class that the emitted messages will be deserialized into.
     * @param assertions      The assertions that will be applied on the emissions.
     * @param mapperQualifier The objectMapper qualifier name that will be used to deserialize
     *                        the emitted messages
     *                        payloads.
     * @param <T>             The emitted message payload {@link Class}
     * @param identifier      The assertion identifier
     * @return self for continuation
     */
    public <T> BrokerTester assertNextWith(final String queueName,
                                           final Integer count,
                                           final Integer timeout,
                                           final Class<T> clazz,
                                           final TestFunction<T> assertions,
                                           final String mapperQualifier,
                                           final String identifier) {
        es.submit(() -> {
            Mono.just(verification.getNextName("assertNextWith"))
                    .flatMap(myName -> getDeliveries(myName, queueName,
                            timeout, count, identifier)
                            .filter(Tuple2::getT2)
                            .map(Tuple2::getT1)
                            .flatMap(uncountedList -> {
                                if (uncountedList.size() != count) {
                                    verification.setValid(myName, false);
                                    verification.setAssertionError(myName,
                                            new AssertionError(createMessage(identifier,
                                                    "Too little emissions.")));
                                    verification.removeBlock(myName);
                                    return Mono.empty();
                                }
                                return Mono.just(uncountedList);
                            })
                            .doOnNext(ignore -> verification.setValid(myName, true))
                            .doOnNext(ignore -> verification.clearAssertionError(myName))
                            .flatMap(countedList -> {
                                countedList.flatMap(delivery ->
                                        Try.of(() -> theMappers.get(mapperQualifier).readValue(delivery.getBody(), clazz))
                                                .onFailure(throwable -> {
                                                    verification.setValid(myName, false);
                                                    verification.setAssertionError(myName,
                                                            new AssertionError(createMessage(
                                                                    identifier, "IOException")));
                                                    throwable.printStackTrace();
                                                }))
                                        .flatMap(obj -> Try.run(() -> assertions.test(obj))
                                                .onFailure(throwable -> {
                                                    verification.setValid(myName, false);
                                                    //TODO deepCopy the error to add custom name?
                                                    verification.setAssertionError(myName, (AssertionError) throwable);
                                                }));
                                return Mono.just(countedList);
                            })
                            .doFinally(ignore -> verification.removeBlock(myName))
                    )
                    .subscribe();
        });

        return this;
    }

    /**
     * Because {@link BrokerTester} has assertions it will require a {@link ObjectMapper} to deserialize
     * the incoming payloads. If this is the first mapper added to the tester then it will be setup
     * as the default mapper ( it can always be overridden by calling {@link #addObjectMapper(ObjectMapper)}).
     *
     * @param qualifier    The  {@link ObjectMapper} qualifier name.
     * @param objectMapper the {@link ObjectMapper}.
     * @return self for continuation.
     * @see #addObjectMapper(ObjectMapper)
     */
    public BrokerTester addObjectMapper(final String qualifier,
                                        final ObjectMapper objectMapper) {
        this.theMappers.put(qualifier, objectMapper);
        if (this.theMappers.size() == 1) {
            return addObjectMapper(objectMapper);
        }
        return this;
    }

    /**
     * Because {@link BrokerTester} has assertions it will require a {@link ObjectMapper} to deserialize
     * the incoming payloads.
     *
     * @param objectMapper The {@link ObjectMapper} that will be registered as default.
     * @return self for continuation
     */
    public BrokerTester addObjectMapper(final ObjectMapper objectMapper) {
        this.theMappers.put(DEFAULT_VALUE, objectMapper);
        return this;
    }

    /**
     * Initializes the connection to {@link BrokerManager} and setup the {@link AMQPBinding}.
     *
     * @param senderOptions Override the default {@link SenderOptions} with custom. {@link ConnectionFactory} is not
     *                      required and will be added if missing
     * @return Returns the {@link Sender} with the given {@link SenderOptions}.
     * @throws IOException      in-case there are IO problems with the connection or the creation of the
     *                          {@link AMQPBinding}
     * @throws TimeoutException in-case the connection has timeout.
     */
    public Sender initialize(final SenderOptions senderOptions) throws IOException, TimeoutException {
        final Connection connection = receiverOptions.getConnectionFactory().newConnection();
        channel = connection.createChannel();

        List.ofAll(bindings.values())
                .map(binding -> Tuple.of(
                        declareQueue(channel, binding.getQueueProperties()).toOption(),
                        declareExchange(channel, binding.getExchangeProperties()).toOption(),
                        binding.getRoutingKey()
                ))
                .filter(tuple -> tuple._1().isDefined())
                .filter(tuple -> tuple._2().isDefined())
                .forEach(tuple -> Try.of(() -> channel.queueBind(tuple._1.get(), tuple._2.get(), tuple._3))
                        .onFailure(Throwable::printStackTrace));

        Stream.ofAll(singleQueues.values())
                .forEach(queue -> declareQueue(channel, queue));

        Stream.ofAll(singleExchanges.values())
                .forEach(exchange -> declareExchange(channel, exchange));

        latch.countDown();

        try {
            sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        referenceHolder.setAssertionVerification(verification);

        return RabbitFlux.createSender(
                senderOptions.getConnectionFactory() != null
                        ? senderOptions
                        : senderOptions.connectionFactory(receiverOptions.getConnectionFactory())
        );
    }

    /**
     * Declares a queue in the given {@link Channel} with the given {@link QueueProperties} defining a
     * cleanUp function in the process.
     *
     * @param channel         The {@link Channel} in which the queue will be declared.
     * @param queueProperties The queue definition.
     * @return A {@link Try} containing the queue name.
     */
    private Try<String> declareQueue(final Channel channel,
                                     final QueueProperties queueProperties) {
        return Try.run(() -> channel
                .queueDeclare(
                        queueProperties.getName(),
                        queueProperties.isDurable(),
                        queueProperties.isExclusive(),
                        queueProperties.isAutoDelete(),
                        queueProperties.getArguments()))
                .onFailure(Throwable::printStackTrace)
                .map(ignore -> referenceHolder.getQueueCleanUpList()
                        .add(ignore2 ->
                                Try.run(() -> channel.queueDeleteNoWait(queueProperties.getName(),
                                        false, false))
                                        .onFailure(Throwable::printStackTrace)
                                        .getOrElse(() -> null)
                        ))
                .map(replaceWith(queueProperties.getName()));
    }

    /**
     * Declares an exchange in the given {@link Channel} with the given {@link ExchangeProperties} defining a
     * cleanUp function in the process.
     *
     * @param channel            The {@link Channel} in which the exchange will be declared.
     * @param exchangeProperties The exchange definition.
     * @return A {@link Try} containing the queue name.
     */
    private Try<String> declareExchange(final Channel channel,
                                        final ExchangeProperties exchangeProperties) {
        return Try.run(() -> channel
                .exchangeDeclare(
                        exchangeProperties.getName(),
                        exchangeProperties.getType().getType(),
                        exchangeProperties.isDurable(),
                        exchangeProperties.isAutoDelete(),
                        exchangeProperties.getArguments()
                ))
                .onFailure(Throwable::printStackTrace)
                .map(ignore -> referenceHolder.getExchangeCleanUpList()
                        .add(ignore2 ->
                                Try.run(() -> channel.exchangeDeleteNoWait(exchangeProperties.getName(), false))
                                        .onFailure(Throwable::printStackTrace)
                                        .getOrElse(() -> null)
                        ))
                .map(replaceWith(exchangeProperties.getName()));
    }

    /**
     * Initializes the connection to {@link BrokerManager} and setup the {@link AMQPBinding}. The {@link Sender} has
     * the default {@link SenderOptions} which contain the given {@link ConnectionFactory}.
     *
     * @return Returns the {@link Sender} with the given {@link SenderOptions}.
     * @throws IOException      in-case there are IO problems with the connection or the creation of the
     *                          {@link AMQPBinding}
     * @throws TimeoutException in-case the connection has timeout.
     */
    public Sender initialize() throws IOException, TimeoutException {
        return initialize(new SenderOptions().connectionFactory(receiverOptions.getConnectionFactory()));
    }

    /**
     * Require a verification that the queues asserted by the {@link BrokerTester} will have no more emission after the
     * asserted ones. The default timeout is {@value #DEFAULT_TIMEOUT} seconds.
     *
     * @return self for continuation.
     */
    public BrokerTester verifyNoMoreEmissions() {
        return verifyNoMoreEmissions(DEFAULT_TIMEOUT);
    }

    /**
     * Require a verification that the queues asserted by the {@link BrokerTester} will have no more emission after the
     * asserted ones.
     *
     * @param timeout the time in seconds that will wait to see if more emissions are made.
     * @return self for continuation.
     */
    public BrokerTester verifyNoMoreEmissions(final Integer timeout) {
        verificationTimeout = timeout;
        verifyNoMoreEmissions = true;
        return this;
    }

    /**
     * Registers a consumer on the queue listening for a specific amount of time for emissions.
     * if any emission is found then it marks the assertion that call it as failed. As we do
     * not want this to take effect right away in the stream we provice a signal {@link Publisher}
     * that will initiate the listen process when the first signal passes ( even a onComplete signal ).
     * The sleep time will be the default timeout that was given when requested no more emissions with
     * {@link #verifyNoMoreEmissions()} and {@link #verifyNoMoreEmissions(Integer)} }
     *
     * @param queueName     The queue on which the listener will subscribe.
     * @param assertionName The assertion that called for verification.
     * @param signal        The signal {@link Publisher} to initiate the listening process.
     * @return {@link Boolean#TRUE} if no emissions were found. Else {@link Boolean#FALSE}
     * @see #verifyNoMoreEmissions()
     * @see #verifyNoMoreEmissions(Integer)
     */
    private Mono<Boolean> verifyNoMoreEmissions(final String queueName,
                                                final String assertionName,
                                                final Publisher<?> signal) {
        return verifyNoMoreEmissions(queueName, assertionName, signal, verificationTimeout);
    }

    /**
     * Registers a consumer on the queue listening for a specific amount of time for emissions.
     * if any emission is found then it marks the assertion that call it as failed. As we do
     * not want this to take effect right away in the stream we provice a signal {@link Publisher}
     * that will initiate the listen process when the first signal passes ( even a onComplete signal )
     *
     * @param queueName     The queue on which the listener will subscribe.
     * @param assertionName The assertion that called for verification.
     * @param signal        The signal {@link Publisher} to initiate the listening process.
     * @param sleepTime     The amount of time that it will monitor for no emissions.
     * @return {@link Boolean#TRUE} if no emissions were found. Else {@link Boolean#FALSE}
     */
    private Mono<Boolean> verifyNoMoreEmissions(final String queueName,
                                                final String assertionName,
                                                final Publisher<?> signal,
                                                final Integer sleepTime) {
        return Mono.just(assertionName)
                .doOnNext(assertName -> verification.setBlock(assertName, sleepTime))
                .flatMap(ignore -> receiver.consumeAutoAck(queueName)
                        .delaySubscription(signal)
                        .take(Duration.ofSeconds(sleepTime))
                        .collect(List.collector()))
                .filter(not(List::isEmpty))
                .doOnNext(ignore -> this.verification.setValid(assertionName, false))
                .doOnNext(emissions -> this.verification.setAssertionError(assertionName,
                        new AssertionError(createMessage(DEFAULT_IDENTIFIER,
                                "Unexpected emissions (" + emissions.size() + ") on " + queueName + "."))))
                .map(ignore -> false);
    }

    /**
     * Returns a {@link List} of {@link Delivery} for the given specifications. The function will return as soon as the
     * {@link Delivery} count consumed matches the requested count or the timeout runs out. To validate no more
     * emissions will be passed in the queue {@link #verifyNoMoreEmissions()} should be used.
     *
     * @param identification         the identification of the assertion requesting the {@link Delivery}
     * @param queueName              the queue name in which {@link Delivery} should be found
     * @param timeout                the max time in seconds it should monitor the queue for
     *                               the requested {@link Delivery}
     * @param count                  the max count of {@link Delivery} that should be consumed.
     * @param userProvidedIdentifier the identification that the user requested for the assertion.
     * @return a {@link Mono} which contains a {@link Tuple2}. The {@link Tuple2} contains the {@link List}
     * with 0 to {@code count} deliveries consumed with the given specifications. The second part of the
     * {@link Tuple2} has a {@link Boolean} with the result of the {@link #verifyNoMoreEmissions(String, String, Publisher)})}
     * @see #verifyNoMoreEmissions(String, String, Publisher)
     */
    private Mono<Tuple2<List<Delivery>, Boolean>> getDeliveries(final String identification,
                                                                final String queueName,
                                                                final Integer timeout,
                                                                final Integer count,
                                                                final String userProvidedIdentifier) {
        final DirectProcessor<String> signal = DirectProcessor.create();

        final Flux<Integer> counterFlux = Flux.fromStream(IntStream.range(0, count).boxed());

        final Flux<Long> longFlux = Flux.interval(Duration.ofSeconds(timeout));

        return Mono.fromCallable(() -> initiateLock(identification, timeout, userProvidedIdentifier))
                .thenMany(receiver.consumeAutoAck(queueName))
                .doOnSubscribe(ignore -> longFlux.subscribe())
                .zipWith(counterFlux)
                .takeUntilOther(longFlux)
                .map(Tuple2::getT1)
                .collect(List.collector())
                .doFinally(ignore -> signal.onComplete())
                .switchIfEmpty(Mono.just(List.empty()))
                .zipWith(Mono.just(verifyNoMoreEmissions)
                        .filter(Boolean::booleanValue)
                        .flatMap(ignore -> verifyNoMoreEmissions(queueName, identification, signal))
                        .switchIfEmpty(Mono.just(true)));
    }

    private boolean initiateLock(final String identification,
                                 final Integer block,
                                 final String userProvidedIdentifier) {
        verification.register(identification, userProvidedIdentifier);
        verification.updateBlock(identification, block);

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return true;
    }

    private String createMessage(final String identifier,
                                 final String message) {
        return introduction
                + " "
                + (identifier.isEmpty() ? "" : identifier + " ")
                + message;
    }

    private void isQueueUnique(final QueueProperties queueProperties) {
        assert (!singleQueues.containsKey(queueProperties.getName())
                || singleQueues.get(queueProperties.getName()).equals(queueProperties))
                && (!queueNames.containsKey(queueProperties.getName())
                || queueNames.get(queueProperties.getName()).equals(queueProperties))
                : createMessage(DEFAULT_IDENTIFIER,
                "Queue name must be unique ( " + queueProperties.getName() + " )");
    }

    private void isExchangeUnique(final ExchangeProperties exchangeProperties) {
        assert (!singleExchanges.containsKey(exchangeProperties.getName())
                || singleExchanges.get(exchangeProperties.getName()).equals(exchangeProperties))
                && (!exchangeNames.containsKey(exchangeProperties.getName())
                || exchangeNames.get(exchangeProperties.getName()).equals(exchangeProperties))
                : createMessage(DEFAULT_IDENTIFIER,
                "Exchange name must be unique ( " + exchangeProperties.getName() + " )");
    }

    protected String bindingNameGenerator(final ExchangeProperties exchangeProperties,
                                          final QueueProperties queueProperties,
                                          final String routingKey) {
        return exchangeProperties.getName() + "_" + queueProperties.getName()
                + ("".equals(routingKey) ? "" : "_" + routingKey);
    }
}
