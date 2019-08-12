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

import lombok.Getter;

/**
 * A helper class that will hold the bindings until the initialize.
 *
 * @author MMichailidis
 */
@Getter
final class AMQPBinding {
    private final String routingKey;
    private final ExchangeProperties exchangeProperties;
    private final QueueProperties queueProperties;

    private AMQPBinding(String routingKey, ExchangeProperties exchangeProperties, QueueProperties queueProperties) {
        this.routingKey = routingKey;
        this.exchangeProperties = exchangeProperties;
        this.queueProperties = queueProperties;
    }

    protected static AMQPBinding from(
            String routingKey, ExchangeProperties exchangeProperties, QueueProperties queueProperties) {
        return new AMQPBinding(routingKey, exchangeProperties, queueProperties);
    }
}
