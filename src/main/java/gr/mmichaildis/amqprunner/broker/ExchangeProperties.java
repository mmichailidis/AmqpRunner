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

import com.rabbitmq.client.BuiltinExchangeType;
import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;

/**
 * @author MMichailidis
 */
@Getter
@Builder(builderMethodName = "exchangeBuilder")
public final class ExchangeProperties {
    private final String name;
    @Default
    private final BuiltinExchangeType type = BuiltinExchangeType.DIRECT;
    @Default
    private final boolean durable = false;
    @Default
    private final boolean autoDelete = false;
    @Default
    private final Map<String, Object> arguments = Collections.emptyMap();
}