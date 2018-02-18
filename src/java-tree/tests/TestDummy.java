
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

import java.io.PrintWriter;
import java.util.Collection;

import uk.ac.lancs.networks.EndPoint;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceDescription;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.transients.DummyNetwork;
import uk.ac.lancs.networks.util.IdleExecutor;

/**
 * 
 * 
 * @author simpsons
 */
public class TestDummy {

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        DummyNetwork zwitch = new DummyNetwork("dummy");

        Terminal left = zwitch.addTerminal("left");
        Terminal right = zwitch.addTerminal("right");
        Terminal up = zwitch.addTerminal("up");
        Terminal down = zwitch.addTerminal("down");

        /* Create a couple of dormant connections. */
        Service c1 = zwitch.getControl().newService();
        Service c2 = zwitch.getControl().newService();

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
        MyListener cl2 = new MyListener("c2");
        c2.addListener(cl2);

        /* Initiate some connections. */
        c1.initiate(ServiceDescription.start().add(left, 1, 10.0)
            .add(down, 1, 10.0).create());
        c2.initiate(ServiceDescription.start().add(left, 4, 7.0)
            .add(right, 6, 7.0).add(up, 3, 7.0).create());

        /* Wait until there's nothing to do. */
        IdleExecutor.processAll();

        /* Show the current status. */
        zwitch.dump(new PrintWriter(System.out));

        System.out.println("End of program");
    }
}
