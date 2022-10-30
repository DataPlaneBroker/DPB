// -*- c-basic-offset: 4; indent-tabs-mode: nil -*-

import java.nio.charset.StandardCharsets;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;

import uk.ac.lancs.rest.server.RESTContext;
import uk.ac.lancs.rest.server.RESTDispatcher;
import uk.ac.lancs.rest.server.RESTField;
import uk.ac.lancs.rest.server.RESTRegistration;
import uk.ac.lancs.rest.service.Route;
import uk.ac.lancs.rest.service.Subpath;

public class TestRestServer {
    private static class Foo {
        private static final RESTField<Integer> FIELD =
            RESTField.ofInt().from("code").done();

        @Route("/bar/(?<code>[0-9]+)")
        @Subpath
        private void bar(HttpRequest req, HttpResponse rsp,
                         HttpContext ctxt) {
            RESTContext rest = RESTContext.get(ctxt);
            System.out.printf("Path info: %s%n",
                              rest.get(RESTRegistration.PATH_INFO));
            System.out.printf("Code: %s%n", rest.get(FIELD));
            rsp.setEntity(new StringEntity("yes\n", StandardCharsets.UTF_8));
        }

        @Route("/foo/(?<code>[0-9]+)")
        @Subpath
        private void foo(HttpRequest req, HttpResponse rsp, HttpContext ctxt,
                         RESTContext rest) {
            System.out.printf("Path info: %s%n",
                              rest.get(RESTRegistration.PATH_INFO));
            System.out.printf("Code: %s%n", rest.get(FIELD));
            rsp.setEntity(new StringEntity("yes\n", StandardCharsets.UTF_8));
        }
    }

    public static void main(String[] args) throws Exception {
        RESTDispatcher disp = new RESTDispatcher();
        Foo foo = new Foo();
        RESTRegistration reg = new RESTRegistration();
        reg.record(foo, "");
        reg.register(disp);
        HttpServer webServer =
            ServerBootstrap.bootstrap().setListenerPort(4753)
                .setServerInfo("LURest/1.0").setSocketConfig(SocketConfig
                    .custom().setTcpNoDelay(true).build())
                .setExceptionLogger((ex) -> {
                    try {
                        throw ex;
                    } catch (ConnectionClosedException e) {
                        // Ignore.
                    } catch (Throwable t) {
                        t.printStackTrace(System.err);
                    }
                }).setHandlerMapper(disp).create();
        webServer.start();
    }
}
