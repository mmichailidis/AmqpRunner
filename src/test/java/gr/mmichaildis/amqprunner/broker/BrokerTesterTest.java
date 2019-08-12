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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import lombok.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.rabbitmq.Receiver;
import reactor.test.publisher.TestPublisher;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static java.lang.Thread.sleep;
import static java.util.Objects.isNull;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author MMichailidis
 */
public class BrokerTesterTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void defaultSleep() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);

        brokerTester.defaultSleep(5);

        final Field verification = BrokerTester.class.getDeclaredField("verification");
        verification.setAccessible(true);
        final AssertionVerification ver = (AssertionVerification) verification.get(brokerTester);

        assertEquals(5000L, (long) ver.getBlock().get("default").updateCurrentMillis(0L));
    }

    @Test
    public void declareQueue() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final QueueProperties props = getQueue("something");

        brokerTester.declareQueue(props);

        final Field singleQueues = BrokerTester.class.getDeclaredField("singleQueues");
        singleQueues.setAccessible(true);
        final Map theQueues = (Map) singleQueues.get(brokerTester);

        assertEquals(1, theQueues.size());
        assertTrue(theQueues.get(props.getName()) instanceof QueueProperties);

        final QueueProperties qp = (QueueProperties) theQueues.get(props.getName());

        assertEquals(props, qp);
    }

    @Test
    public void declareQueueSameObjNoError() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final QueueProperties props = getQueue("something");

        brokerTester.declareQueue(props);
        brokerTester.declareQueue(props);

        final Field singleQueues = BrokerTester.class.getDeclaredField("singleQueues");
        singleQueues.setAccessible(true);
        final Map theQueues = (Map) singleQueues.get(brokerTester);

        assertEquals(1, theQueues.size());
        assertTrue(theQueues.get(props.getName()) instanceof QueueProperties);

        final QueueProperties qp = (QueueProperties) theQueues.get(props.getName());

        assertEquals(props, qp);
    }

    @Test(expected = AssertionError.class)
    public void declareQueueFailDoubleName() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);

        brokerTester.declareQueue(getQueue("something"));
        brokerTester.declareQueue(getQueue("something"));
    }

    @Test
    public void declareExchange() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final ExchangeProperties props = getExchange("something");

        brokerTester.declareExchange(props);

        final Field singleQueues = BrokerTester.class.getDeclaredField("singleExchanges");
        singleQueues.setAccessible(true);
        final Map theExchanges = (Map) singleQueues.get(brokerTester);

        assertEquals(1, theExchanges.size());
        assertTrue(theExchanges.get(props.getName()) instanceof ExchangeProperties);

        final ExchangeProperties qp = (ExchangeProperties) theExchanges.get(props.getName());

        assertEquals(props, qp);
    }

    @Test
    public void declareExchangeSameObjNoError() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final ExchangeProperties props = getExchange("something");

        brokerTester.declareExchange(props);
        brokerTester.declareExchange(props);

        final Field singleQueues = BrokerTester.class.getDeclaredField("singleExchanges");
        singleQueues.setAccessible(true);
        final Map theExchanges = (Map) singleQueues.get(brokerTester);

        assertEquals(1, theExchanges.size());
        assertTrue(theExchanges.get(props.getName()) instanceof ExchangeProperties);

        final ExchangeProperties qp = (ExchangeProperties) theExchanges.get(props.getName());

        assertEquals(props, qp);
    }

    @Test(expected = AssertionError.class)
    public void declareExchangeFailDoubleName() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);

        brokerTester.declareExchange(getExchange("something"));
        brokerTester.declareExchange(getExchange("something"));
    }

    @Test
    public void declareBinding() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final QueueProperties someQueue = getQueue("something");
        final ExchangeProperties someExchange = getExchange("someone");
        final String bindingName = brokerTester.bindingNameGenerator(someExchange, someQueue, "");

        brokerTester.declareBinding(someQueue, someExchange);

        final Field singleQueues = BrokerTester.class.getDeclaredField("bindings");
        singleQueues.setAccessible(true);
        final Map theExchanges = (Map) singleQueues.get(brokerTester);

        assertEquals(1, theExchanges.size());
        assertTrue(theExchanges.get(bindingName) instanceof AMQPBinding);

        final AMQPBinding binding = (AMQPBinding) theExchanges.get(bindingName);

        assertEquals(someExchange, binding.getExchangeProperties());
        assertEquals(someQueue, binding.getQueueProperties());
        assertEquals("", binding.getRoutingKey());
    }

    @Test
    public void declareBindingSameObjNoError() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final QueueProperties someQueue = getQueue("something");
        final ExchangeProperties someExchange = getExchange("someone");
        final String bindingName = brokerTester.bindingNameGenerator(someExchange, someQueue, "");

        brokerTester.declareBinding(someQueue, someExchange);
        brokerTester.declareBinding(someQueue, someExchange);

        final Field singleQueues = BrokerTester.class.getDeclaredField("bindings");
        singleQueues.setAccessible(true);
        final Map theExchanges = (Map) singleQueues.get(brokerTester);

        assertEquals(1, theExchanges.size());
        assertTrue(theExchanges.get(bindingName) instanceof AMQPBinding);

        final AMQPBinding binding = (AMQPBinding) theExchanges.get(bindingName);

        assertEquals(someExchange, binding.getExchangeProperties());
        assertEquals(someQueue, binding.getQueueProperties());
        assertEquals("", binding.getRoutingKey());
    }

    @Test(expected = AssertionError.class)
    public void declareBindingFailDoubleNameBothDuplicate() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final QueueProperties someQueue = getQueue("something");
        final QueueProperties someQueue2 = getQueue("something");
        final ExchangeProperties someExchange = getExchange("someone");
        final ExchangeProperties someExchange2 = getExchange("someone");

        brokerTester.declareBinding(someQueue, someExchange);
        brokerTester.declareBinding(someQueue2, someExchange2);
    }

    @Test(expected = AssertionError.class)
    public void declareBindingFailDoubleNameQueueDuplicate() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final QueueProperties someQueue = getQueue("something");
        final QueueProperties someQueue2 = getQueue("something");
        final ExchangeProperties someExchange = getExchange("someone");

        brokerTester.declareBinding(someQueue, someExchange);
        brokerTester.declareBinding(someQueue2, someExchange);
    }

    @Test(expected = AssertionError.class)
    public void declareBindingFailDoubleNameExchangeDuplicate() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final QueueProperties someQueue = getQueue("something");
        final ExchangeProperties someExchange = getExchange("someone");
        final ExchangeProperties someExchange2 = getExchange("someone");

        brokerTester.declareBinding(someQueue, someExchange);
        brokerTester.declareBinding(someQueue, someExchange2);
    }

    @Test
    public void declareBindingExchangeReuse() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final QueueProperties someQueue = getQueue("something");
        final QueueProperties someQueue2 = getQueue("something2");
        final ExchangeProperties someExchange = getExchange("someone");
        final String bindingName = brokerTester.bindingNameGenerator(someExchange, someQueue, "");
        final String bindingName2 = brokerTester.bindingNameGenerator(someExchange, someQueue2, "");

        brokerTester.declareBinding(someQueue, someExchange);
        brokerTester.declareBinding(someQueue2, someExchange);

        final Field singleQueues = BrokerTester.class.getDeclaredField("bindings");
        singleQueues.setAccessible(true);
        final Map theExchanges = (Map) singleQueues.get(brokerTester);

        assertEquals(2, theExchanges.size());
        assertTrue(theExchanges.get(bindingName) instanceof AMQPBinding);
        assertTrue(theExchanges.get(bindingName2) instanceof AMQPBinding);

        final AMQPBinding binding = (AMQPBinding) theExchanges.get(bindingName);
        final AMQPBinding binding2 = (AMQPBinding) theExchanges.get(bindingName2);

        assertEquals(someExchange, binding.getExchangeProperties());
        assertEquals(someQueue, binding.getQueueProperties());
        assertEquals("", binding.getRoutingKey());

        assertEquals(someExchange, binding2.getExchangeProperties());
        assertEquals(someQueue2, binding2.getQueueProperties());
        assertEquals("", binding2.getRoutingKey());
    }

    @Test
    public void declareBindingQueueReuse() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "no", referenceHolder);
        final QueueProperties someQueue = getQueue("something");
        final ExchangeProperties someExchange = getExchange("someone");
        final ExchangeProperties someExchange2 = getExchange("someone2");
        final String bindingName = brokerTester.bindingNameGenerator(someExchange, someQueue, "");
        final String bindingName2 = brokerTester.bindingNameGenerator(someExchange2, someQueue, "1");

        brokerTester.declareBinding(someQueue, someExchange);
        brokerTester.declareBinding(someQueue, someExchange2, "1");

        final Field singleQueues = BrokerTester.class.getDeclaredField("bindings");
        singleQueues.setAccessible(true);
        final Map theExchanges = (Map) singleQueues.get(brokerTester);

        assertEquals(2, theExchanges.size());
        assertTrue(theExchanges.get(bindingName) instanceof AMQPBinding);
        assertTrue(theExchanges.get(bindingName2) instanceof AMQPBinding);

        final AMQPBinding binding = (AMQPBinding) theExchanges.get(bindingName);
        final AMQPBinding binding2 = (AMQPBinding) theExchanges.get(bindingName2);

        assertEquals(someExchange, binding.getExchangeProperties());
        assertEquals(someQueue, binding.getQueueProperties());
        assertEquals("", binding.getRoutingKey());

        assertEquals(someExchange2, binding2.getExchangeProperties());
        assertEquals(someQueue, binding2.getQueueProperties());
        assertEquals("1", binding2.getRoutingKey());
    }

    @Test
    public void expectNextCount() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "TestingTest", referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.expectNextCount("someQueue", 2, 3)
                .verifyNoMoreEmissions(3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(1500);

        assertTrue(verificationAndCounter._1().isValid());
    }

    @Test
    public void expectNextCountIgnoreMoreEmissions() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "TestingTest", referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.expectNextCount("someQueue", 2, 3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(1500);

        assertTrue(verificationAndCounter._1().isValid());
    }

    @Test
    public void expectNextCountLessEmissions() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final String brokerName = "TestingTest";
        final String queueName = "someQueue";
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class),
                brokerName, referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.expectNextCount(queueName, 2, 3)
                .verifyNoMoreEmissions(3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock(Delivery.class));

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(1500);

        assertFalse(verificationAndCounter._1().isValid());

        final AssertionVerification assertionVerification = verificationAndCounter._1();
        final List<AssertionError> errors = assertionVerification.getErrors();

        assertEquals(1, errors.size());

        final AssertionError assertionError = errors.get(0);

        assertTrue((brokerName + " Too little emissions.")
                .equalsIgnoreCase(assertionError.getMessage()));
    }

    @Test
    public void expectNextCountUnexpectedEmissions() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final String brokerName = "TestingTest";
        final String queueName = "someQueue";

        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class),
                brokerName, referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.expectNextCount(queueName, 2, 3)
                .verifyNoMoreEmissions(3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));

        testPublisher.next(mock(Delivery.class));

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(1500);

        assertFalse(verificationAndCounter._1().isValid());

        final AssertionVerification assertionVerification = verificationAndCounter._1();
        final List<AssertionError> errors = assertionVerification.getErrors();

        assertEquals(1, errors.size());

        final AssertionError assertionError = errors.get(0);

        assertTrue((brokerName + " Unexpected emissions (1) on " + queueName + ".")
                .equalsIgnoreCase(assertionError.getMessage()));
    }

    @Test
    public void expectNextCountMultipleUnexpectedEmissions() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final String brokerName = "TestingTest";
        final String queueName = "someQueue";

        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class),
                brokerName, referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.expectNextCount(queueName, 2, 3)
                .verifyNoMoreEmissions(3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));

        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(1500);

        assertFalse(verificationAndCounter._1().isValid());

        final AssertionVerification assertionVerification = verificationAndCounter._1();
        final List<AssertionError> errors = assertionVerification.getErrors();

        assertEquals(1, errors.size());

        final AssertionError assertionError = errors.get(0);

        assertTrue((brokerName + " Unexpected emissions (5) on " + queueName + ".")
                .equalsIgnoreCase(assertionError.getMessage()));
    }

    @Test
    public void expectNoEmissions() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final String brokerName = "TestingTest";
        final String queueName = "someQueue";

        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class),
                brokerName, referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.expectNoEmissions(queueName, 3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(3200);

        assertTrue(verificationAndCounter._1().isValid());
    }

    @Test
    public void expectNoEmissionsHadEmissions() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final String brokerName = "TestingTest";
        final String queueName = "someQueue";

        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class),
                brokerName, referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.expectNoEmissions(queueName, 3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock(Delivery.class));
        testPublisher.next(mock(Delivery.class));

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(3200);

        assertFalse(verificationAndCounter._1().isValid());

        final AssertionVerification assertionVerification = verificationAndCounter._1();
        final List<AssertionError> errors = assertionVerification.getErrors();

        assertEquals(1, errors.size());

        final AssertionError assertionError = errors.get(0);

        assertTrue((brokerName + " Expected no emission.")
                .equalsIgnoreCase(assertionError.getMessage()));
    }

    @Test
    public void assertNextWith() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class), "TestingTest", referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();
        final Delivery mock1 = mock(Delivery.class);
        final Delivery mock2 = mock(Delivery.class);
        final SampleItem s = new SampleItem("123");
        final ObjectMapper om = new ObjectMapper();

        when(mock1.getBody()).thenReturn(om.writeValueAsBytes(s));
        when(mock2.getBody()).thenReturn(om.writeValueAsBytes(s));

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.assertNextWith("someQueue",
                2, 3, SampleItem.class, (subj) -> assertEquals(subj, s))
                .addObjectMapper(om)
                .verifyNoMoreEmissions(3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock1);
        testPublisher.next(mock2);

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(3500);

        assertTrue(verificationAndCounter._1().isValid());
    }

    @Test
    public void assertNextWithUnexpectedEmission() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final String brokerName = "TestingTest";
        final String queueName = "someQueue";
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class),
                brokerName, referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();
        final Delivery mock1 = mock(Delivery.class);
        final Delivery mock2 = mock(Delivery.class);
        final SampleItem s = new SampleItem("123");
        final ObjectMapper om = new ObjectMapper();

        when(mock1.getBody()).thenReturn(om.writeValueAsBytes(s));
        when(mock2.getBody()).thenReturn(om.writeValueAsBytes(s));

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.assertNextWith(queueName,
                2, 3, SampleItem.class, (subj) -> assertEquals(subj, s))
                .addObjectMapper(om)
                .verifyNoMoreEmissions(3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock1);
        testPublisher.next(mock2);

        testPublisher.next(mock2);

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(3500);

        assertFalse(verificationAndCounter._1().isValid());

        final AssertionVerification assertionVerification = verificationAndCounter._1();
        final List<AssertionError> errors = assertionVerification.getErrors();

        assertEquals(1, errors.size());

        final AssertionError assertionError = errors.get(0);

        assertTrue((brokerName + " Unexpected emissions (1) on " + queueName + ".")
                .equalsIgnoreCase(assertionError.getMessage()));
    }

    @Test
    public void assertNextWithTooLittleEmissions() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final String brokerName = "TestingTest";
        final String queueName = "someQueue";
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class),
                brokerName, referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();
        final Delivery mock1 = mock(Delivery.class);
        final Delivery mock2 = mock(Delivery.class);
        final SampleItem s = new SampleItem("123");
        final ObjectMapper om = new ObjectMapper();

        when(mock1.getBody()).thenReturn(om.writeValueAsBytes(s));
        when(mock2.getBody()).thenReturn(om.writeValueAsBytes(s));

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.assertNextWith(queueName,
                2, 3, SampleItem.class, (subj) -> assertEquals(subj, s))
                .addObjectMapper(om)
                .verifyNoMoreEmissions(3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock1);

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(3500);

        assertFalse(verificationAndCounter._1().isValid());

        final AssertionVerification assertionVerification = verificationAndCounter._1();
        final List<AssertionError> errors = assertionVerification.getErrors();

        assertEquals(1, errors.size());

        final AssertionError assertionError = errors.get(0);

        assertTrue((brokerName + " Too little emissions.")
                .equalsIgnoreCase(assertionError.getMessage()));
    }

    @Test
    public void assertNextWithAssertionFailure() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final String brokerName = "TestingTest";
        final String queueName = "someQueue";
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class),
                brokerName, referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();
        final Delivery mock1 = mock(Delivery.class);
        final SampleItem s = new SampleItem("123");
        final SampleItem s2 = new SampleItem("444");
        final ObjectMapper om = new ObjectMapper();

        when(mock1.getBody()).thenReturn(om.writeValueAsBytes(s));

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.assertNextWith(queueName,
                1, 3, SampleItem.class, (subj) -> assertEquals(subj, s2))
                .addObjectMapper(om)
                .verifyNoMoreEmissions(3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock1);

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(3500);

        assertFalse(verificationAndCounter._1().isValid());

        final AssertionVerification assertionVerification = verificationAndCounter._1();
        final List<AssertionError> errors = assertionVerification.getErrors();

        assertEquals(1, errors.size());

        final AssertionError assertionError = errors.get(0);

        assertTrue("expected:<BrokerTesterTest.SampleItem(name=123)> but was:<BrokerTesterTest.SampleItem(name=444)>"
                .equalsIgnoreCase(assertionError.getMessage()));
    }

    @Test
    public void assertNextWithObjectMapperIOException() throws Exception {
        final ReferenceHolder referenceHolder = new ReferenceHolder();
        referenceHolder.setCleanUpList(Collections.synchronizedList(new LinkedList<>()));
        final String brokerName = "TestingTest";
        final String queueName = "someQueue";
        final BrokerTester brokerTester = new BrokerTester(mock(ConnectionFactory.class),
                brokerName, referenceHolder);

        final TestPublisher<Delivery> testPublisher = TestPublisher.create();
        final Delivery mock1 = mock(Delivery.class);
        final SampleItem s2 = new SampleItem("444");
        final ObjectMapper om = mock(ObjectMapper.class);

        doThrow(new IOException("custom io")).when(om).readValue(eq(new byte[]{(byte) 0x15}), eq(SampleItem.class));
        when(mock1.getBody()).thenReturn(new byte[]{(byte) 0x15});

        final Tuple2<AssertionVerification, CountDownLatch> verificationAndCounter = weaveBrokerTesterForVerification(testPublisher, brokerTester);

        brokerTester.assertNextWith(queueName,
                1, 3, SampleItem.class, (subj) -> assertEquals(subj, s2))
                .addObjectMapper(om)
                .verifyNoMoreEmissions(3);

        verificationAndCounter._2().countDown();
        sleep(300);

        testPublisher.next(mock1);

        testPublisher.complete();

        assertTrue(testPublisher.wasSubscribed());

        sleep(3500);

        assertFalse(verificationAndCounter._1().isValid());

        final AssertionVerification assertionVerification = verificationAndCounter._1();
        final List<AssertionError> errors = assertionVerification.getErrors();

        assertEquals(1, errors.size());

        final AssertionError assertionError = errors.get(0);

        assertTrue((brokerName + " IOException")
                .equalsIgnoreCase(assertionError.getMessage()));
    }

    private QueueProperties getQueue(final String name) {
        return QueueProperties.queueBuilder()
                .name(isNull(name) ? "someName" : name)
                .exclusive(true)
                .durable(false)
                .autoDelete(true)
                .build();
    }

    private ExchangeProperties getExchange(String name) {
        return ExchangeProperties.exchangeBuilder()
                .name(isNull(name) ? "someName" : name)
                .type(BuiltinExchangeType.DIRECT)
                .durable(false)
                .autoDelete(true)
                .build();
    }


    public Tuple2<AssertionVerification, CountDownLatch> weaveBrokerTesterForVerification(final TestPublisher<Delivery> mockRabbit,
                                                                                          final BrokerTester brokerTester)
            throws Exception {
        final Receiver mockReceiver = mock(Receiver.class);

        final Flux<Delivery> deliveryFlux = Flux.create(emitter -> mockRabbit.flux()
                .doOnComplete(emitter::complete)
                .doOnNext(emitter::next)
                .subscribe());

        //noinspection UnassignedFluxMonoInstance
        doReturn(deliveryFlux).when(mockReceiver).consumeAutoAck(any());

        final Field receiver = BrokerTester.class.getDeclaredField("receiver");
        receiver.setAccessible(true);
        final Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(receiver, receiver.getModifiers() & ~Modifier.FINAL);
        receiver.set(brokerTester, mockReceiver);

        final Field latch = BrokerTester.class.getDeclaredField("latch");
        latch.setAccessible(true);
        final CountDownLatch o = (CountDownLatch) latch.get(brokerTester);

        final Field verification = BrokerTester.class.getDeclaredField("verification");
        verification.setAccessible(true);
        final AssertionVerification assertionVerification = (AssertionVerification) verification.get(brokerTester);

        return Tuple.of(assertionVerification, o);
    }

    @Getter
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleItem {
        @JsonProperty("name")
        private String name;
    }
}