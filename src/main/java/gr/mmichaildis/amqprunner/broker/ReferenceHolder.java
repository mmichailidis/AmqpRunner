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

import gr.mmichaildis.amqprunner.BrokerManager;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.function.Function;

/**
 * A helper class containing references on {@link AssertionVerification} and a {@link List} which are meant
 * to be used by {@link BrokerManager} but updated by {@link BrokerTester}.
 *
 * @author MMichailidis
 */
@Getter
@Setter
public final class ReferenceHolder {
    protected AssertionVerification assertionVerification;
    protected List<Function<Void, Void>> queueCleanUpList;
    protected List<Function<Void, Void>> exchangeCleanUpList;
}
