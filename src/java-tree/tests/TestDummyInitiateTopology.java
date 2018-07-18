import java.io.PrintWriter;

import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
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
                              double bandwidth, int baseTag, int tagCount)
        throws NetworkManagementException {
        Terminal p1 = zwitch1.getControl().getTerminal(port1);
        Terminal p2 = zwitch2.getControl().getTerminal(port2);
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
        aggregator.addTerminal("lancs.vms",
                               lancs.getControl().getTerminal("vms"));
        aggregator.addTerminal("bristol.vms",
                               bristol.getControl().getTerminal("vms"));
        aggregator.addTerminal("kcl.vms",
                               kcl.getControl().getTerminal("vms"));
        aggregator.addTerminal("edin.vms",
                               edin.getControl().getTerminal("vms"));
        aggregator.addTerminal("slough.vms",
                               slough.getControl().getTerminal("vms"));

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

            ServiceStatus lastStableStatus = ServiceStatus.DORMANT;

            @Override
            public void newStatus(ServiceStatus newStatus) {
                System.out.printf("%s: now %s from %s%n", name, newStatus,
                                  lastStableStatus);
                if (newStatus.isStable()) lastStableStatus = newStatus;
            }
        }
        MyListener cl1 = new MyListener("c1");
        c1.addListener(cl1);

        c1.define(Segment.start().produce(100.0)
            .add(aggregator.getControl().getTerminal("lancs.vms"), 1234)
            .add(aggregator.getControl().getTerminal("bristol.vms"), 1111)
            .add(aggregator.getControl().getTerminal("slough.vms"), 2222)
            .add(aggregator.getControl().getTerminal("slough.vms"), 2223)
            .create());

        IdleExecutor.processAll();

        aggregator.dumpStatus(new PrintWriter(System.out));
    }
}
