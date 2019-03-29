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
package uk.ac.lancs.networks.apps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.ServiceCreationException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.config.ConfigurationContext;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.jsoncmd.ContinuousJsonReader;
import uk.ac.lancs.networks.jsoncmd.ContinuousJsonWriter;
import uk.ac.lancs.networks.jsoncmd.JsonAggregatorServer;
import uk.ac.lancs.networks.jsoncmd.JsonChannel;
import uk.ac.lancs.networks.jsoncmd.JsonChannelManager;
import uk.ac.lancs.networks.jsoncmd.JsonNetworkServer;
import uk.ac.lancs.networks.jsoncmd.JsonSwitchServer;
import uk.ac.lancs.networks.jsoncmd.MultiplexingJsonServer;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkResourceException;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.scc.usmux.Session;
import uk.ac.lancs.scc.usmux.SessionException;
import uk.ac.lancs.scc.usmux.SessionServer;
import uk.ac.lancs.scc.usmux.SessionServerFactory;

/**
 * Instantiates network agents from configuration, and serves
 * JSON-formatted requests to them via Usmux.
 * 
 * @author simpsons
 */
public final class NetworkServer {
    private final Map<String, Network> networks = new HashMap<>();
    private final Executor executor;

    NetworkServer(Executor executor, SessionServer usmuxServer,
                  Configuration config)
        throws AgentCreationException,
            ServiceCreationException {
        this.executor = executor;

        /* Create a collection of named agents. */
        Map<String, Agent> agents = new LinkedHashMap<>();

        /* Provide an executor to other agents, and a way to find
         * networks by name. */
        agents.put("system", new Agent() {
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
        agent_instantiation:
        for (Configuration agentConf : config.references("agents")) {
            String name = agentConf.get("name");
            if (name == null)
                throw new IllegalArgumentException("agent config ["
                    + agentConf.prefix() + " has no name");
            for (AgentFactory factory : ServiceLoader
                .load(AgentFactory.class)) {
                if (!factory.recognize(agentConf)) continue;
                Agent agent = factory.makeAgent(agents::get, agentConf);
                agents.put(name, agent);
                System.out.printf("  Created agent %s%n  (%s)%n", name,
                                  factory);
                continue agent_instantiation;
            }
            throw new IllegalArgumentException("agent " + name
                + " not recognized");
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

    /**
     * Read an LF-terminated line from a byte stream, and interpret as a
     * string in the platform's character encoding.
     * 
     * @param in the byte source
     * 
     * @return the line including the terminator, or {@code null} if EOF
     * was reached without reading any bytes
     * 
     * @throws IOException if an I/O error occurred
     */
    private static String readLine(InputStream in) throws IOException {
        byte[] buf = new byte[1024];
        int got = 0;
        int c;
        while ((c = in.read()) >= 0) {
            if (got == buf.length)
                buf = Arrays.copyOf(buf, buf.length + buf.length / 2);
            buf[got++] = (byte) c;
            if (c == 10) return new String(buf);
        }
        if (got == 0) return null;
        return new String(buf);
    }

    private class Interaction implements Runnable {
        private final Session sess;
        private Collection<String> managables = new HashSet<>();
        private Collection<String> controllables = new HashSet<>();

        JsonNetworkServer server = null;

        public Interaction(Session sess) {
            this.sess = sess;
        }

        @Override
        public void run() {
            List<Runnable> actions = new ArrayList<>();
            try (Session sess = this.sess) {
                InputStream in = sess.getInputStream();

                /* Read lines from the local caller (in the local
                 * charset), until 'drop' is given. A command 'manage
                 * <name>' permits the network with that name to be
                 * managed and controlled. 'control <name>' permits it
                 * to be controlled only. 'auth :<token>' sets the
                 * authorization token on new services. 'auth-match
                 * :<regex>' sets the authorization pattern for
                 * modifying existing services. */
                String line;
                while ((line = readLine(in)) != null
                    && !(line = line.trim()).equals("drop")) {
                    String[] words = line.trim().split("\\s+", 2);
                    if (words.length < 2) continue;
                    switch (words[0]) {
                    case "auth":
                        NetworkControl.SERVICE_AUTH_TOKEN
                            .set(words[1].substring(1));
                        break;
                    case "auth-match":
                        Service.AUTH_TOKEN
                            .set(Pattern.compile(words[1].substring(1)));
                        break;
                    case "manage":
                        managables.add(words[1]);
                        System.err.printf("%s is managable%n", words[1]);
                        // Fall through.
                    case "control":
                        controllables.add(words[1]);
                        System.err.printf("%s is controllable%n", words[1]);
                        break;
                    }
                }
                System.err.printf("dropped privileges%ncontrol: %s%n",
                                  controllables);

                /* Read one more line, which is the name of the
                 * network. */
                System.err.printf("awaiting network name%n");
                String networkName = readLine(in);
                System.err.printf("network name = %s%n", networkName);
                networkName = networkName.trim();

                /* Remaining communication is from the remote caller,
                 * and is in UTF-8. Read in JSON objects as requests,
                 * and respond to them. */
                do {
                    try (JsonReader jin = new ContinuousJsonReader(in);
                        JsonWriter jout = new ContinuousJsonWriter(sess
                            .getOutputStream())) {
                        /* Fail if the selected network is not
                         * accessible. */
                        if (!controllables.contains(networkName)) {
                            jout.writeObject(Json.createObjectBuilder()
                                .add("error", "unauthorized")
                                .add("network", networkName).build());
                            break;
                        }

                        /* Access the network, and fail if it doesn't
                         * exist. */
                        Network network = networks.get(networkName);
                        if (network == null) {
                            jout.writeObject(Json.createObjectBuilder()
                                .add("error", "no-network")
                                .add("network-name", networkName).build());
                            break;
                        }

                        /* Wrap a JSON interface around the network.
                         * Create a positive response, indicating what
                         * functions are available. */
                        JsonObjectBuilder builder = Json.createObjectBuilder()
                            .add("network-name", networkName);
                        if (controllables.contains(networkName)) {
                            if (network instanceof Aggregator) {
                                builder.add("aggregator", true);
                                this.server =
                                    new JsonAggregatorServer((Aggregator) network);
                            } else if (network instanceof Switch) {
                                builder.add("switch", true);
                                this.server =
                                    new JsonSwitchServer((Switch) network);
                            } else {
                                builder.add("network", true);
                                this.server =
                                    new JsonNetworkServer(network, true);
                            }
                        } else {
                            this.server =
                                new JsonNetworkServer(network, false);
                        }
                        jout.writeObject(builder.build());

                        JsonChannel base = new JsonChannel() {
                            @Override
                            public void write(JsonObject msg) {
                                jout.writeObject(msg);
                            }

                            @Override
                            public JsonObject read() {
                                return jin.readObject();
                            }

                            @Override
                            public void close() {
                                try {
                                    sess.close();
                                } catch (IOException e) {
                                    throw new JsonException("closing", e);
                                }
                            }
                        };
                        JsonChannelManager mgr =
                            new MultiplexingJsonServer(base);
                        do {
                            JsonChannel session = mgr.getChannel();
                            if (session == null) {
                                System.err.printf("Base closed%n");
                                break;
                            }
                            executor.execute(() -> {
                                JsonObject req;
                                while ((req = session.read()) != null) {
                                    for (JsonObject rsp : process(req,
                                                                  actions::add)) {
                                        System.err.printf("%s -> %s%n", req,
                                                          rsp);
                                        session.write(rsp);
                                    }
                                    System.err
                                        .printf("Request complete: %s%n",
                                                req);
                                }
                            });
                        } while (true);
                    }
                } while (false);
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            /* Perform clean-up actions requested by the network
             * delegate. */
            for (Runnable action : actions)
                action.run();
        }

        private Iterable<JsonObject> process(JsonObject req,
                                             Executor onClose) {
            try {
                if (server != null) return server.interact(req, onClose);
                return one(Json.createObjectBuilder()
                    .add("error", "unknown-command")
                    .add("type", req.getString("type")).build());
            } catch (NetworkResourceException ex) {
                return one(Json.createObjectBuilder()
                    .add("error", "network-resource")
                    .add("network-name", ex.getNetworkName())
                    .add("msg", ex.getMessage()).build());
            } catch (IllegalArgumentException ex) {
                return one(Json.createObjectBuilder()
                    .add("error", "bad-argument").add("msg", ex.getMessage())
                    .build());
            }
        }
    }

    void interact(Session sess) {
        Interaction ixn = new Interaction(sess);
        executor.execute(ixn);
    }

    /**
     * Runs a server containing agents that operate switches.
     * 
     * <p>
     * The following system properties are recognized:
     * 
     * <dl>
     * 
     * <dt><samp>program.name</samp>
     * 
     * <dd>Identifies the program to appear in error messages.
     * 
     * <dt><samp>usmux.config</samp>
     * 
     * <dd>Specifies the Usmux configuration, normally provided by the
     * Usmux daemon invoking this process.
     * 
     * <dt><samp>network.config.server</samp>
     * 
     * <dd>Specifies the path to the agent configuration file.
     * 
     * </dl>
     * 
     * @param args Arguments are ignored.
     */
    public static void main(String[] args) {
        String usmuxConf = System.getProperty("usmux.config");
        Path dataplaneConf =
            Paths.get(System.getProperty("network.config.server"));

        try {
            /* Create the Usmux session server. We don't start it until
             * we have all our configured components in place. */
            SessionServer usmuxServer =
                SessionServerFactory.makeServer(usmuxConf);

            Executor executor = Executors.newCachedThreadPool();

            ConfigurationContext configCtxt =
                new ConfigurationContext(System.getProperties());
            Configuration config;
            config = configCtxt.get(dataplaneConf.toFile());
            NetworkServer us =
                new NetworkServer(executor, usmuxServer, config);

            /* Start to accept calls. */
            usmuxServer.start();
            Session sess;
            while ((sess = usmuxServer.accept()) != null) {
                us.interact(sess);
            }
        } catch (SessionException | IOException | AgentCreationException
            | ServiceCreationException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static <E> List<E> one(E elem) {
        return Collections.singletonList(elem);
    }
}
