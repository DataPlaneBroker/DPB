/*
 * Copyright 2017, Regents of the University of Lancaster
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the
 *    distribution.
 * 
 *  * Neither the name of the University of Lancaster nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */
package uk.ac.lancs.networks.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentContext;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.ServiceCreationException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.config.ConfigurationContext;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.mgmt.Network;

/**
 * This program is intended to be invoked from an SSH call.
 * 
 * @author simpsons
 */
public final class SSHDaemon {
    private static final JsonReaderFactory readerFactory =
        Json.createReaderFactory(Collections.emptyMap());
    private static final JsonWriterFactory writerFactory =
        Json.createWriterFactory(Collections.emptyMap());

    private final InputStream in;
    private final OutputStream out;

    private SSHDaemon(Path configPath, InputStream in, OutputStream out)
        throws IOException,
            AgentCreationException,
            ServiceCreationException {
        this.in = in;
        this.out = out;
        final ConfigurationContext configCtxt = new ConfigurationContext();
        final Configuration root = configCtxt.get(configPath.toFile());
        final Map<String, Agent> agents = new LinkedHashMap<>();
        final Map<String, Network> networks = new HashMap<>();

        /* Provide an executor to other agents, and a way to find
         * networks by name. */
        agents.put("system", new Agent() {
            final Executor executor = IdleExecutor.INSTANCE;

            @Override
            public <T> T findService(Class<T> type, String key) {
                if (type == NetworkControl.class) {
                    Network nw = networks.get(key);
                    if (nw == null) return null;
                    return type.cast(nw.getControl());
                }
                if (type == Executor.class && key == null)
                    return type.cast(executor);
                return null;
            }

            @Override
            public Collection<String> getKeys(Class<?> type) {
                if (type == NetworkControl.class) return Collections
                    .unmodifiableCollection(networks.keySet());
                if (type == Executor.class)
                    return Collections.singleton(null);
                return Collections.emptySet();
            }
        });

        /* Instantiate all agents. */
        System.out.printf("Creating agents...%n");
        final AgentContext agentContext = agents::get;
        for (Configuration agentConf : root.references("agents")) {
            String name = agentConf.get("name");
            if (name == null) {
                System.err.printf("agent config [%s] has no name%n",
                                  agentConf.prefix());
                throw new IllegalArgumentException();
            }
            String type = agentConf.get("type");
            if (type == null) {
                System.err.printf("agent config [%s] has no type%n",
                                  agentConf.prefix());
                throw new IllegalArgumentException();
            }
            for (AgentFactory factory : ServiceLoader
                .load(AgentFactory.class)) {
                if (!factory.recognize(agentConf)) continue;
                Agent agent = factory.makeAgent(agentContext, agentConf);
                agents.put(name, agent);
                System.out.printf("  Created agent %s as %s%n", name, type);
            }
        }

        /* Obtain networks from agents. */
        System.out.printf("Obtaining networks...%n");
        for (Map.Entry<String, Agent> entry : agents.entrySet()) {
            String agentId = entry.getKey();
            Agent agent = entry.getValue();
            for (String serviceKey : agent.getKeys(Network.class)) {
                Network network =
                    agent.findService(Network.class, serviceKey);
                if (network == null) continue;
                String networkName = network.getControl().name();
                networks.put(networkName, network);
                System.out.printf("  Created network %s from agent %s%s%n",
                                  networkName, agentId,
                                  serviceKey != null ? ":" + serviceKey : "");
            }
        }
    }

    private void start() {
        do {
            try {
                JsonReader reader =
                    readerFactory.createReader(in, StandardCharsets.UTF_8);
                JsonObject cmd = reader.readObject();
                if (cmd.getString("cmd").equals("watch")) {
                    watch(cmd);
                    break;
                } else {
                    JsonObject res = command(cmd);
                    String txn = cmd.getString("txn");
                    if (txn != null) {
                        JsonObjectBuilder builder =
                            Json.createObjectBuilder();
                        builder.add("txn", txn);
                        res.entrySet().forEach(e -> builder
                            .add(e.getKey(), e.getValue()));
                        res = builder.build();
                    }
                    JsonWriter writer = writerFactory.createWriter(out);
                    writer.write(res);
                }
            } catch (JsonException ex) {
                try {
                    throw ex.getCause();
                } catch (EOFException exIn) {
                    break;
                } catch (Throwable exIn) {

                }
            }
        } while (true);
    }

    private void watch(JsonObject cmd) {
        // TODO
        throw new UnsupportedOperationException("unimplemented");
    }

    private JsonObject command(JsonObject cmd) {
        // TODO
        throw new UnsupportedOperationException("unimplemented");
    }

    /**
     * The program reads successive JSON maps from standard input, and
     * treats them as commands to execute. In most cases, each command
     * produces a single JSON object as a response. The fields of each
     * command and response are command-dependent, except for the field
     * <samp>cmd</samp> which identifies the command, and
     * <samp>txn</samp> which optionally specifies a caller-defined
     * transaction reference which must appear in the response under the
     * same name.
     * 
     * @param args The first argument is the configuration file path.
     * @throws IOException
     * @throws ServiceCreationException
     * @throws AgentCreationException
     */
    public static void main(String[] args)
        throws IOException,
            AgentCreationException,
            ServiceCreationException {
        Path configFile = Paths.get(args[0]);
        SSHDaemon server = new SSHDaemon(configFile, System.in, System.out);
        server.start();
    }
}
