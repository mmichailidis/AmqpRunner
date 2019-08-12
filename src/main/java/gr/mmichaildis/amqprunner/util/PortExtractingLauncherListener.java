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
package gr.mmichaildis.amqprunner.util;

import org.apache.qpid.server.SystemLauncherListener;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.SystemConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author MMichailidis
 */
public class PortExtractingLauncherListener implements SystemLauncherListener {
    private SystemConfig<?> _systemConfig;
    private Map<String, Integer> _ports = new HashMap<>();

    @Override
    public void beforeStartup() {

    }

    @Override
    public void errorOnStartup(final RuntimeException e) {

    }

    @Override
    public void afterStartup() {
        if (_systemConfig == null) {
            throw new IllegalStateException("System config is required");
        }

        Broker<?> _broker = (Broker<?>) _systemConfig.getContainer();
        Collection<Port> ports = _broker.getChildren(Port.class);
        for (Port port : ports) {
            _ports.put(port.getName(), port.getBoundPort());
        }
    }

    @Override
    public void onContainerResolve(final SystemConfig<?> systemConfig) {
        _systemConfig = systemConfig;
    }

    @Override
    public void onContainerClose(final SystemConfig<?> systemConfig) {

    }

    @Override
    public void onShutdown(final int exitCode) {

    }

    @Override
    public void exceptionOnShutdown(final Exception e) {

    }

    public Map<String, Integer> getPorts() {
        return _ports;
    }
}