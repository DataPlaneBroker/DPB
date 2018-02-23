import java.io.PrintWriter;
import java.util.Collection;

import uk.ac.lancs.networks.EndPoint;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceDescription;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.networks.transients.DummySwitch;
import uk.ac.lancs.networks.transients.TransientAggregator;
import uk.ac.lancs.networks.util.IdleExecutor;

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
    private static Trunk link(Aggregator aggregator, Network zwitch1,
                              String port1, Network zwitch2, String port2,
                              double bandwidth, int baseTag, int tagCount) {
        Terminal p1 = zwitch1.getTerminal(port1);
        Terminal p2 = zwitch2.getTerminal(port2);
        Trunk result = aggregator.addTrunk(p1, p2);
        result.provideBandwidth(bandwidth);
        result.defineLabelRange(baseTag, tagCount);
        result.setDelay(1.0);
        return result;
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        /* Model the Corsas at each site. */
        DummySwitch slough = new DummySwitch("slough");
        slough.addTerminal("vms");
        slough.addTerminal("bristol");
        slough.addTerminal("kcl");
        slough.addTerminal("edin");
        slough.addTerminal("lancs");

        DummySwitch bristol = new DummySwitch("bristol");
        bristol.addTerminal("vms");
        bristol.addTerminal("slough");

        DummySwitch kcl = new DummySwitch("kcl");
        kcl.addTerminal("vms");
        kcl.addTerminal("slough");

        DummySwitch edin = new DummySwitch("edin");
        edin.addTerminal("vms");
        edin.addTerminal("slough");

        DummySwitch lancs = new DummySwitch("lancs");
        lancs.addTerminal("vms");
        lancs.addTerminal("slough");

        /* Create an aggregator to control the site switches. */
        TransientAggregator aggregator =
            new TransientAggregator(IdleExecutor.INSTANCE, "initiate");

        /* Expose inferior switches' unlinked ports. */
        aggregator.addTerminal("lancs.vms", lancs.getTerminal("vms"));
        aggregator.addTerminal("bristol.vms", bristol.getTerminal("vms"));
        aggregator.addTerminal("kcl.vms", kcl.getTerminal("vms"));
        aggregator.addTerminal("edin.vms", edin.getTerminal("vms"));
        aggregator.addTerminal("slough.vms", slough.getTerminal("vms"));

        /* Link up the inferior switches within the superior. */
        link(aggregator, slough, "lancs", lancs, "slough", 1024.0, 1000, 40);
        link(aggregator, slough, "bristol", bristol, "slough", 1024.0 * 9.0,
             1000, 40);
        link(aggregator, slough, "kcl", kcl, "slough", 1024.0 * 9.0, 1000,
             40);
        link(aggregator, slough, "edin", edin, "slough", 1024.0, 1000, 40);

        Service c1 = aggregator.getControl().newService();
        class MyListener implements ServiceListener {
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
            public void
                failed(Collection<? extends EndPoint<? extends Terminal>> locations,
                       Throwable t) {
                System.out.println(name + " failed");
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

        c1.initiate(ServiceDescription.start().produce(100.0)
            .add(aggregator.getTerminal("lancs.vms"), 1234)
            .add(aggregator.getTerminal("bristol.vms"), 1111)
            .add(aggregator.getTerminal("slough.vms"), 2222)
            .add(aggregator.getTerminal("slough.vms"), 2223).create());

        IdleExecutor.processAll();

        aggregator.dumpStatus(new PrintWriter(System.out));
    }
}
