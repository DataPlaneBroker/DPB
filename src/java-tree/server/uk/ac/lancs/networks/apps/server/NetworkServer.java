/*
 * Copyright 2018,2019, Regents of the University of Lancaster
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
package uk.ac.lancs.networks.apps.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import org.apache.http.ConnectionClosedException;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
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
import uk.ac.lancs.networks.jsoncmd.JsonNetworkServer;
import uk.ac.lancs.networks.jsoncmd.JsonSwitchServer;
import uk.ac.lancs.networks.jsoncmd.MultiplexingJsonServer;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkResourceException;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.networks.rest.FiveGExchangeNetworkControlServer;
import uk.ac.lancs.networks.rest.RESTNetworkControlServer;
import uk.ac.lancs.rest.server.RESTDispatcher;

/**
 * Instantiates network agents from configuration, and serves
 * JSON-formatted requests to them via Usmux, and via REST.
 * 
 * <p>
 * REST functions defined by {@link RESTNetworkControlServer} are bound
 * to the prefix
 * <samp>http://{@value #DEFAULT_HOST_TEXT}:{@value #DEFAULT_PORT}/network/<var>network-name</var>.</samp>
 * 
 * <p>
 * REST functions defined by {@link FiveGExchangeNetworkControlServer}
 * are bound to the prefix
 * <samp>http://{@value #DEFAULT_HOST_TEXT}:{@value #DEFAULT_PORT}/5gnetwork/<var>network-name</var>.</samp>
 * 
 * @author simpsons
 */
public final class NetworkServer {
    private final Map<String, Network> networks = new HashMap<>();

    private final Executor executor;

    NetworkServer(Executor executor, RESTDispatcher restMapping,
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
                if (type == Executor.class) return Collections.singleton(null);
                return Collections.emptySet();
            }
        });

        /* Instantiate all agents. */
        System.out.printf("Creating agents...%n");
        agent_instantiation: for (Configuration agentConf : config
            .references("agents")) {
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
                Network network = agent.findService(Network.class, serviceKey);
                if (network == null) continue;
                String networkName = network.getControl().name();
                networks.put(networkName, network);
                System.out.printf("  Created network %s from agent %s%s%n",
                                  networkName, agentId,
                                  serviceKey != null ? ":" + serviceKey : "");
            }
        }

        /* Add network controller to the REST API. */
        for (Map.Entry<String, Network> entry : networks.entrySet()) {
            String prefix = "/network/" + entry.getKey();
            new RESTNetworkControlServer(entry.getValue().getControl())
                .bind(restMapping, prefix);
        }

        /* Add network controller to the 5GExchange REST API. */
        for (Map.Entry<String, Network> entry : networks.entrySet()) {
            String prefix = "/5gnetwork/" + entry.getKey();
            new FiveGExchangeNetworkControlServer(entry.getValue().getControl())
                .bind(restMapping, prefix);
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
        private final ByteChannel sess;

        private Collection<String> managables = new HashSet<>();

        private Collection<String> controllables = new HashSet<>();

        JsonNetworkServer server = null;

        public Interaction(ByteChannel sess) {
            this.sess = sess;
        }

        @Override
        public void run() {
            List<Runnable> actions = new ArrayList<>();
            try (var sess = this.sess) {
                InputStream in = new ByteChannelInputStream(sess);

                /* Read lines from the local caller (in the local
                 * charset), until 'drop' is given. A command 'manage
                 * <name>' permits the network with that name to be
                 * managed and controlled. 'control <name>' permits it
                 * to be controlled only. 'auth :<token>' sets the
                 * authorization token on new services. 'auth-match
                 * :<regex>' sets the authorization pattern for
                 * modifying existing services. */
                String line;
                while ((line = readLine(in)) != null &&
                    !(line = line.trim()).equals("drop")) {
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
                        // System.err.printf("%s is managable%n",
                        // words[1]);
                        /* Fall through. */
                    case "control":
                        controllables.add(words[1]);
                        // System.err.printf("%s is controllable%n",
                        // words[1]);
                        break;
                    }
                }
                logger.info(() -> "managed: " + managables);
                logger.info(() -> "controlled: " + controllables);

                /* Read one more line, which is the name of the
                 * network. */
                String networkName = readLine(in);
                networkName = networkName.trim();

                /* Remaining communication is from the remote caller,
                 * and is in UTF-8. Read in JSON objects as requests,
                 * and respond to them. */
                do {
                    try (JsonReader jin = new ContinuousJsonReader(in);
                         JsonWriter jout =
                             new ContinuousJsonWriter(new ByteChannelOutputStream(sess))) {
                        /* Fail if the selected network is not
                         * accessible. */
                        if (!controllables.contains(networkName)) {
                            final String nn = networkName;
                            logger.warning(() -> "unauthorized: [" + nn
                                + "] not in " + controllables);
                            jout.writeObject(Json.createObjectBuilder()
                                .add("error", "unauthorized")
                                .add("network", networkName).build());
                            break;
                        }

                        /* Access the network, and fail if it doesn't
                         * exist. */
                        Network network = networks.get(networkName);
                        if (network == null) {
                            final String nn = networkName;
                            logger.warning(() -> "no network: [" + nn
                                + "] not in " + networks.keySet());
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
                            this.server = new JsonNetworkServer(network, false);
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
                        MultiplexingJsonServer mgr =
                            new MultiplexingJsonServer(base);
                        mgr.initiate();
                        do {
                            JsonChannel session = mgr.getChannel();
                            if (session == null) {
                                System.err.printf("Base closed%n");
                                break;
                            }
                            executor.execute(() -> {
                                JsonObject req;
                                while ((req = session.read()) != null) {
                                    final JsonObject rr = req;
                                    for (JsonObject rsp : process(req,
                                                                  actions::add)) {
                                        logger.finer(() -> rr.toString()
                                            + " -> " + rsp);
                                        session.write(rsp);
                                    }
                                    logger
                                        .fine(() -> "Request complete: " + rr);
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

        private Iterable<JsonObject> process(JsonObject req, Executor onClose) {
            try {
                if (server != null) return server.interact(req, onClose);
                return one(Json.createObjectBuilder()
                    .add("error", "unknown-command")
                    .add("type", req.getString("type")).build());
            } catch (NetworkResourceException ex) {
                logger.log(Level.SEVERE, "unreachable code", ex);
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

    void interact(ByteChannel sess) {
        Interaction ixn = new Interaction(sess);
        executor.execute(ixn);
    }

    private static final String DEFAULT_HOST_TEXT = "0.0.0.0";

    private static final int DEFAULT_PORT = 4753;

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
     * <dt><samp>mgmt.bindaddr</samp>
     * 
     * <dd>Specifies the Unix-domain socket.
     * 
     * <dt><samp>network.config.server</samp>
     * 
     * <dd>Specifies the path to the agent configuration file.
     * 
     * </dl>
     * 
     * <p>
     * The configuration file may contain the following properties:
     * 
     * <dl>
     * 
     * <dt><samp>agents</samp></dt>
     * 
     * <dd>A comma-separated list of keys of agents to instantiate
     * 
     * <dt><samp><var>agent-key</var>.name=<var>agent-key</var></samp></dt>
     * 
     * <dd>The name to register the agent under
     * 
     * <dt><samp><var>agent-key</var>.type</samp></dt>
     * 
     * <dd>The agent's type, to be recognized by
     * {@link AgentFactory#recognize(Configuration)}
     * 
     * <dt><samp><var>agent-key</var>.<var>other-properties</var></samp></dt>
     * 
     * <dd>Other agent-specific properties
     * 
     * <dt><samp>rest.host={@value #DEFAULT_HOST_TEXT}</samp>
     * 
     * <dd>Specifies the host to bind the HTTP server to.
     * 
     * <dt><samp>rest.port={@value #DEFAULT_PORT}</samp>
     * 
     * <dd>Specifies the port to bind the HTTP server to.
     * 
     * </dl>
     * 
     * @param args Arguments are ignored.
     */
    public static void main(String[] args) {
        Path mgmtAddrTxt = Paths.get(System.getProperty("mgmt.bindaddr"));
        Path dataplaneConf =
            Paths.get(System.getProperty("network.config.server"));

        SocketAddress mgmtAddr = UnixDomainSocketAddress.of(mgmtAddrTxt);

        try {
            /* Create the Unix-domain management socket. Ensure the
             * rendezvous point is deleted on exit, provided binding is
             * successful. */
            ServerSocketChannel mgmtServer =
                ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            try {
                mgmtServer.bind(mgmtAddr);
            } catch (BindException ex) {
                System.err.printf("Can't bind management to %s%n", mgmtAddrTxt);
                System.exit(1);
            }
            mgmtAddrTxt.toFile().deleteOnExit();

            Executor executor = Executors.newCachedThreadPool();

            ConfigurationContext configCtxt =
                new ConfigurationContext(System.getProperties());
            Configuration config;
            config = configCtxt.get(dataplaneConf.toFile());
            RESTDispatcher restMapping = new RESTDispatcher();
            NetworkServer us = new NetworkServer(executor, restMapping, config);

            /* Create HTTP server. */
            InetAddress host = InetAddress
                .getByName(config.get("rest.host", DEFAULT_HOST_TEXT));
            int port = config.getInt("rest.port", DEFAULT_PORT);
            try {
                HttpServer webServer = ServerBootstrap.bootstrap()
                    .setLocalAddress(host).setListenerPort(port)
                    .setServerInfo("DPB/1.0").setSocketConfig(SocketConfig
                        .custom().setTcpNoDelay(true).build())
                    .setExceptionLogger((ex) -> {
                        try {
                            throw ex;
                        } catch (ConnectionClosedException e) {
                            // Ignore.
                        } catch (Throwable t) {
                            t.printStackTrace(System.err);
                        }
                    }).setHandlerMapper(restMapping).create();
                webServer.start();
            } catch (BindException ex) {
                System.err.printf("Can't bind REST to %s:%d%n", host, port);
                System.exit(1);
            }

            /* Start to accept calls. */
            while (true) {
                SocketChannel ch = mgmtServer.accept();
                us.interact(ch);
            }
        } catch (IOException | AgentCreationException |
                 ServiceCreationException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static <E> List<E> one(E elem) {
        return Collections.singletonList(elem);
    }

    private static Logger logger =
        Logger.getLogger(NetworkServer.class.getName());
}
