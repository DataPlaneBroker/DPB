import java.io.PrintWriter;
import java.util.Arrays;

import uk.ac.lancs.switches.Connection;
import uk.ac.lancs.switches.ConnectionListener;
import uk.ac.lancs.switches.ConnectionRequest;
import uk.ac.lancs.switches.DummySwitch;
import uk.ac.lancs.switches.Port;
import uk.ac.lancs.switches.SwitchManagement;
import uk.ac.lancs.switches.aggregate.Aggregator;
import uk.ac.lancs.switches.aggregate.TransientAggregator;
import uk.ac.lancs.switches.aggregate.TrunkManagement;

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

/**
 * 
 * 
 * @author simpsons
 */
public class TestDummyInitiateTopology {
    private static TrunkManagement
        link(Aggregator aggregator, SwitchManagement zwitch1, String port1,
             SwitchManagement zwitch2, String port2, double bandwidth,
             int baseTag, int tagCount) {
        Port p1 = zwitch1.getPort(port1);
        Port p2 = zwitch2.getPort(port2);
        TrunkManagement result = aggregator.addTrunk(p1, p2);
        result.releaseBandwidth(bandwidth);
        result.defineLabelRange(baseTag, tagCount);
        result.setDelay(1.0);
        return result;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        /* Model the Corsas at each site. */
        DummySwitch slough = new DummySwitch(IdleExecutor.INSTANCE, "slough");
        slough.addPort("vms");
        slough.addPort("bristol");
        slough.addPort("kcl");
        slough.addPort("edin");
        slough.addPort("lancs");

        DummySwitch bristol =
            new DummySwitch(IdleExecutor.INSTANCE, "bristol");
        bristol.addPort("vms");
        bristol.addPort("core");

        DummySwitch kcl = new DummySwitch(IdleExecutor.INSTANCE, "kcl");
        kcl.addPort("vms");
        kcl.addPort("core");

        DummySwitch edin = new DummySwitch(IdleExecutor.INSTANCE, "edin");
        edin.addPort("vms");
        edin.addPort("core");

        DummySwitch lancs = new DummySwitch(IdleExecutor.INSTANCE, "lancs");
        lancs.addPort("vms");
        lancs.addPort("core");

        /* Create an aggregator to control the site switches. */
        TransientAggregator aggregator =
            new TransientAggregator(IdleExecutor.INSTANCE, "initiate");

        /* Expose inferior switches' unlinked ports. */
        aggregator.addPort("lancs.vms", lancs.getPort("vms"));
        aggregator.addPort("bristol.vms", bristol.getPort("vms"));
        aggregator.addPort("kcl.vms", kcl.getPort("vms"));
        aggregator.addPort("edin.vms", edin.getPort("vms"));
        aggregator.addPort("slough.vms", slough.getPort("vms"));

        /* Link up the inferior switches within the superior. */
        link(aggregator, slough, "lancs", lancs, "core", 1024.0, 1000, 40);
        link(aggregator, slough, "bristol", bristol, "core", 1024.0 * 9.0,
             1000, 40);
        link(aggregator, slough, "kcl", kcl, "core", 1024.0 * 9.0, 1000, 40);
        link(aggregator, slough, "edin", edin, "core", 1024.0, 1000, 40);

        Connection c1 = aggregator.getControl().newConnection();
        class MyListener implements ConnectionListener {
            final String name;

            MyListener(String name) {
                this.name = name;
            }

            @Override
            public void released() {
                System.out.println(name + " released");
            }

            @Override
            public void ready() {
                System.out.println(name + " ready");
            }

            @Override
            public void failed(Throwable t) {
                System.out.println(name + " failed: " + t);
            }

            @Override
            public void deactivated() {
                System.out.println(name + " deactivated");
            }

            @Override
            public void activated() {
                System.out.println(name + " activated");
            }
        }
        MyListener cl1 = new MyListener("c1");
        c1.addListener(cl1);

        c1.initiate(ConnectionRequest.of(Arrays
            .asList(aggregator.getPort("lancs.vms").getEndPoint(1234),
                    aggregator.getPort("bristol.vms").getEndPoint(1111),
                    aggregator.getPort("slough.vms").getEndPoint(2222),
                    aggregator.getPort("slough.vms").getEndPoint(2223)),
                                         100.0));

        IdleExecutor.processAll();

        aggregator.dump(new PrintWriter(System.out));
    }
}
