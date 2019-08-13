# AmqpRunner
[![Build Status](https://travis-ci.org/mmichailidis/AmqpRunner.svg?branch=master)](https://travis-ci.org/mmichailidis/AmqpRunner)

[![codecov](https://codecov.io/gh/mmichailidis/AmqpRunner/branch/master/graph/badge.svg)](https://codecov.io/gh/mmichailidis/AmqpRunner)

[![Maven Central](https://img.shields.io/maven-central/v/gr.mmichailidis/amqprunner.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22gr.mmichailidis%22%20AND%20a:%22amqprunner%22)

AmqpRunner is a JUnit runner that provides a fluent api for creating in 
memory amqp and validating the packets that reached it. It extends 
Spring JUnit runner in order to create the environment for the in-memory amqp
to be up and running just before Spring boots and tries to connect to it.

## How to use

Setting up the environment is as simple as putting a couple of annotations. 
```java
import gr.mmichaildis.amqprunner.AmqpCreator;
import gr.mmichaildis.amqprunner.AmqpRunner;
import gr.mmichaildis.amqprunner.AmqpSetup;
import org.junit.runner.RunWith;

@AmqpCreator({
    @AmqpSetup
})
@RunWith(AmqpRunner.class)
public class MyTest{
}
```

With only those annotations one `AMQP` server will be instantiated with everything set to default.

To retrieve the instantiated class just put
```java
import gr.mmichaildis.amqprunner.AmqpMock;
import gr.mmichaildis.amqprunner.AmqpPort;
import gr.mmichaildis.amqprunner.BrokerManager;

public class MyTest {
    @AmqpMock
    public BrokerManager brokerManager;
    @AmqpPort
    public Integer port;
}
```
and the objects will be injected in your test.

`@AmqpSetup` provides a bunch of customizations for the created server.
The customizations are : 

| attribute      | default          | type    | usage                                                                |
|:---------------|:-----------------|:--------|:---------------------------------------------------------------------|
| name           | ""               | String  | sets the name of the server for identification and injection         |
| amqpPort       | 0                | Integer | the port on which amqp will listen                                   |
| username       | guest            | String  | the username for the server                                          |
| password       | guest            | String  | the password for the server                                          |
| workPath       | ./build/amqp-    | String  | the broker will create a folder to store data. Unavoidable           |
| logPath        | ./build/amqpLog- | String  | the broker will create a derby.log file. Unavoidable                 |

<sub>A small note here. Amqp `workPath` and `logPath` are two unavoidable files which serves nothing after the broker 
is closed. So if you change the default path keep in mind to delete possible leftovers ( that java may not delete 
due to locks). To ensure no collisions between the brokers will appear a uuid will be added as suffix to the given 
path (thats why the - in the end )</sub> 

`@AmqpMock` and `@AmqpPort` accept only a single attribute which is the name of the broker which will
 provide the injections `@AmqpMock("myCoolBroker")` will get the instance created by `@AmqpSetup(name = "myCoolBroker)`

---

After you are all setup and ready to test you will propably need a way to validate the data that reached your amqp.

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BuiltinExchangeType;
import gr.mmichaildis.amqprunner.AmqpMock;
import gr.mmichaildis.amqprunner.BrokerManager;
import gr.mmichaildis.amqprunner.broker.ExchangeProperties;
import gr.mmichaildis.amqprunner.broker.QueueProperties;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@AmqpCreator({
    @AmqpSetup
})
@RunWith(AmqpRunner.class)
public class MyTest {
    @AmqpMock
    public BrokerManager brokerManager;
    
    @Test
    public void someTest(){
        Sender sender = brokerManager.tester()
            //Declaring a binding will create the exchange and queue and will bind those two together. 
            .declareBinding(
                //Queues and exchanges can be reused as long as you use the same object
                QueueProperties
                    .queueBuilder()
                    .durable(true)
                    .exclusive(true)
                    .autoDelete(true)
                    .name("demoQueue")
                    .arguments(new HashMap<>())
                    .build(),
                //The only mandatory field is the name. The rest have default values
                ExchangeProperties
                    .exchangeBuilder()
                    .autoDelete(true)
                    .durable(true)
                    .name("demoExchange")
                    .type(BuiltinExchangeType.DIRECT)
                    .arguments(new HashMap<>())
                    .build(),
                "routingKey")
            //Declaring a queue will create a hanging queue that is not bind to any exchange
            .declareQueue(
                QueueProperties
                    .queueBuilder()
                    .durable(true)
                    .exclusive(true)
                    .autoDelete(true)
                    .name("singleQueue")
                    .arguments(new HashMap<>())
                    .build())
            //Declaring an exchange will create an exchange without any binded queues
            .declareExchange(
                ExchangeProperties
                    .exchangeBuilder()
                    .autoDelete(true)
                    .durable(true)
                    .name("singleExchange")
                    .type(BuiltinExchangeType.DIRECT)
                    .arguments(new HashMap<>())
                    .build())
            // ObjectMapper is required of deserialization of the Delivery body for the "assertNextWith" assertion
            .addObjectMapper(new ObjectMapper())
            .expectNextCount("singleQueue", 4, 3)
            .expectNoEmissions("someOtherQueue", 3)
            .assertNextWith("demoQueue", 2, 3, YourClass.class, subjectForTest -> {
                assertNotNull(subjectForTest);
                assertTrue(subjectForTest.someMethod());
            })
            .verifyNoMoreEmissions(10)
            //Initialize will create a sender on the managers connectionFactory
            .initialize();
        
        // At this point your test setup is ready. Declared queues and exchanges that your application
        // may not configure. Setup the assertions for each queue ( it doesn't require to be declared here. 
        // it will use the name to bind ). Now add the rest of your test code.
 
        // moving to the verification
        brokerManager.verify();    
        // this is all you need. It will block the thread until all the timeouts in the multiple worker threads 
        // are complete and will collect the results of the assertions and inform JUnit if it failed or not.
    }
}
```

A common test scenario is pushing a message in the queue A in which your application may listen and expect 
a result on some other queue.

### Import to your project
```xml
<dependency>
  <groupId>gr.mmichailidis</groupId>
  <artifactId>amqprunner</artifactId>
  <version>1.1.0</version>
</dependency>
```