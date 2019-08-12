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

/**
 * A helper {@link AssertionError} to initialize the assertions on the main {@link Thread}
 * in-case their status is never updated ( which means their threads never woke up ).
 *
 * The threads will wake up as soon as {@link BrokerTester#initialize()} is complete.
 *
 * @author MMichailidis
 */
final class NeverTriggered extends AssertionError {
    NeverTriggered(String identifier) {
        super(identifier.trim() + " Validation never triggered");
    }
}
