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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A helper class that provide verification results and handle for all the assertion methods.
 * Without this class assertions and time manipulation would be impossible.
 *
 * @author MMichailidis
 */
@ToString
public final class AssertionVerification {
    private static final Long SECOND_TO_MILLIS = 1_000L;

    private Map<String, AssertionPair> response;
    private Map<String, Integer> nameProvider;
    private List<AssertionError> assertionErrors;
    @Getter(value = AccessLevel.PROTECTED)
    private Map<String, AssertionTime> block;

    AssertionVerification(String defaultIdentifier, Integer defaultSleepSeconds) {
        nameProvider = new HashMap<>();
        block = new HashMap<>();
        response = new HashMap<>();
        assertionErrors = new ArrayList<>();
        this.block.put(defaultIdentifier, new AssertionTime(defaultSleepSeconds));
    }

    /**
     * Iterates all the assertions registered if their assertion response is {@code true} or {@code false}.
     *
     * @return {@code true} if all the registered assertions responded with {@code true} else {@code false}.
     */
    public boolean isValid() {
        response.values().stream().filter(not(AssertionPair::isValid))
                .forEach(failedVal -> {
                    if ((failedVal.getAssertionError() != null)) {
                        assertionErrors.add(failedVal.getAssertionError());
                    }
                });
        return assertionErrors.isEmpty();
    }

    /**
     * After {@link AssertionVerification#isValid()} is called a {@link List} is populated with all
     * the {@link AssertionError} found in all the registered assertions.
     *
     * @return the {@link List} which contains all the {@link AssertionError} found.
     * If the {@link AssertionVerification#isValid()} was not called before
     * this then the {@link List} is empty.
     */
    public List<AssertionError> getErrors() {
        return assertionErrors;
    }

    /**
     * Sets the valid status for the identified assertion.
     *
     * @param identifier The assertions identifier.
     * @param valid      The valid status of the identified assertion.
     */
    protected void setValid(String identifier, boolean valid) {
        final AssertionPair pair = this.response.get(identifier);
        pair.setValid(valid);
        this.response.put(identifier, pair);
    }

    /**
     * Sets an {@link AssertionError} for the identified assertion.
     *
     * @param identifier The assertions identifier.
     * @param e          The {@link AssertionError} that will be registered for the identified assertion.
     */
    protected void setAssertionError(String identifier, AssertionError e) {
        final AssertionPair pair = this.response.get(identifier);
        pair.setAssertionError(e);
        this.response.put(identifier, pair);
    }

    /**
     * Registers the assertion using the given identifier. This identifier should be used by the assertion to
     * reference on itself when update on main {@link Thread} is required.
     *
     * @param identifier The identifier that will identify each assertion.
     */
    protected void register(String identifier, String userProvidedIdentifier) {
        response.put(identifier, AssertionPair.initial(userProvidedIdentifier));
        block.put(identifier, new AssertionTime(0));
    }

    /**
     * Generates an identifier which is unique for each assertion and can be used to register
     * and identify themselves on the main {@link Thread}.
     *
     * @param nextName The assertion name which will be used to create the unique identifier.
     * @return The unique identifier which should be used for registration and identification on
     * the main {@link Thread}.
     */
    protected String getNextName(String nextName) {
        Integer identifier = nameProvider.getOrDefault(nextName, 0);
        identifier += 1;
        nameProvider.put(nextName, identifier);
        return nextName + "_" + identifier;
    }

    /**
     * Clears the assertion errors for the given assertion identified by its identifier.
     *
     * @param identifier The assertions identifier.
     */
    protected void clearAssertionError(String identifier) {
        final AssertionPair pair = response.get(identifier);
        pair.setAssertionError(null);
        response.put(identifier, pair);
    }

    /**
     * Increase the sleep by N <b>seconds</b>.
     *
     * @param identifier The identifier that will have it sleep increased.
     * @param sleepTime  The N <b>seconds</b> that will be increase.
     */
    protected void updateBlock(String identifier, Integer sleepTime) {
        this.block.get(identifier)
                .updateTotalSeconds(sleepTime * SECOND_TO_MILLIS, false);
    }

    /**
     * Sets the sleep at N <b>seconds</b>.
     * This will override any previous values.
     *
     * @param identifier The identifier that will have it sleep increased.
     * @param sleepTime  The N <b>seconds</b> that will be set.
     */
    protected void setBlock(String identifier, Integer sleepTime) {
        this.block.get(identifier).updateTotalSeconds(sleepTime * SECOND_TO_MILLIS,
                true);
    }

    /**
     * Iterates all the registered assertions informing them for the step in time and checking
     * if they require more time to complete.
     *
     * @param sleepStep the step in time
     * @return {@code true} if at least one assertion requires more time. {@code false} if and only if all
     * the assertions are completed and responds they require no more time.
     */
    public boolean shouldBlock(Long sleepStep) {
        return block.values().stream()
                .map(assertionTime -> assertionTime.updateCurrentMillis(sleepStep))
                .anyMatch(this::notNegative);
    }

    /**
     * Validated the value is not negative or 0.
     *
     * @param value the value to check.
     * @return {@code true} if the value is > 0 else {@code false}.
     */
    private boolean notNegative(Long value) {
        return value > 0;
    }

    /**
     * Negates the given {@link Predicate} inverting its response.
     *
     * @param t   the {@link Predicate} to invert.
     * @param <T> The type of the response.
     * @return The inverted response.
     */
    private <T> Predicate<T> not(Predicate<T> t) {
        return t.negate();
    }

    /**
     * Removes the block ( for time ) request for the given assertion identified by its identifier.
     *
     * @param identifier The assertions identifier.
     */
    protected void removeBlock(String identifier) {
        block.get(identifier).updateTotalSeconds(150L, true);
    }
}
