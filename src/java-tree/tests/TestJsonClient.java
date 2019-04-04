
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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import uk.ac.lancs.networks.jsoncmd.JsonChannel;
import uk.ac.lancs.networks.jsoncmd.JsonChannelManager;
import uk.ac.lancs.networks.jsoncmd.MultiplexingJsonClient;

/**
 * 
 * 
 * @author simpsons
 */
public class TestJsonClient {
    static final Executor executor = Executors.newCachedThreadPool();

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket(args[0], 6666);
        JsonChannel base = new SocketJsonChannel(socket);
        JsonChannelManager mplex = new MultiplexingJsonClient(base, true);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Manager");
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constr = new GridBagConstraints();
            frame.setLayout(layout);
            JButton newSession = new JButton("New");
            newSession.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JsonChannel channel = mplex.getChannel();
                    executor.execute(new ChannelDisplay(channel, "Client"));
                }
            });
            constr.fill = GridBagConstraints.BOTH;
            layout.setConstraints(newSession, constr);
            frame.getContentPane().add(newSession);

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    SwingUtilities.invokeLater(() -> System.exit(0));
                }
            });

            frame.pack();
            frame.setSize(frame.getPreferredSize());
            frame.setVisible(true);
        });
    }
}
