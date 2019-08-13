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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * With this annotation each field of {@link BrokerManager} can be configured
 * to what it should use upon launch. Also the existence of this annotation in
 * a {@link AmqpCreator} defines that a {@link BrokerManager} will be launched
 *
 * @author MMichailidis
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface AmqpSetup {
    /**
     * The name of the {@link BrokerManager}.
     *
     * @return The name that will identify the {@link BrokerManager}
     */
    String name() default "";

    /**
     * The port on which the {@link BrokerManager} will listen for the amqp
     *
     * @return The port requested for amqp
     */
    int amqpPort() default 0;

    /**
     * The username of the {@link BrokerManager} that will be used.
     *
     * @return The requested username
     */
    String username() default "guest";

    /**
     * The password of the {@link BrokerManager} that will be used.
     *
     * @return The requested password
     */
    String password() default "guest";

    /**
     * The folder in which the {@link BrokerManager} will write its db. This folder will try to be deleted
     * after the execution but it may fail due to locks. Its safe to remove it after the test is complete
     * and should be somewhere that can be deleted easily.
     *
     * @return The folder for the {@link BrokerManager} db.
     */
    String workPath() default "./build/amqp-";

    /**
     * The folder in which the {@link BrokerManager} will write its logs. This folder will try to be deleted
     * after the execution but it may fail due to locks. Its safe to remove it after the test is complete
     * and should be somewhere that can be deleted easily. Normally no logs will be written so an empty file
     * will be created.
     *
     * @return The folder for the {@link BrokerManager} logs.
     */
    String logPath() default "./build/amqpLog-";
}
