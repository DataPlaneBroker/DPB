
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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.json.Json;
import javax.json.JsonObject;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import uk.ac.lancs.networks.jsoncmd.JsonChannel;

class ChannelDisplay implements Runnable {
    private final JsonChannel channel;
    private JFrame frame;
    private JTextArea output;

    public ChannelDisplay(JsonChannel channel, String title) {
        System.err.printf("New session%n");
        this.channel = channel;
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame(title);
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constr = new GridBagConstraints();
            frame.setLayout(layout);
            output = new JTextArea(10, 30);
            output.setEditable(false);
            constr.fill = GridBagConstraints.BOTH;
            constr.weightx = constr.weighty = 1.0;
            constr.gridx = 0;
            constr.gridy = 0;
            layout.setConstraints(output, constr);
            frame.getContentPane().add(output);
            JTextField input = new JTextField();
            input.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String text = input.getText();
                    input.setText("");
                    SwingUtilities.invokeLater(() -> {
                        JsonObject msg = Json.createObjectBuilder()
                            .add("msg", text).build();
                        channel.write(msg);
                    });
                }
            });
            constr.weightx = 1.0;
            constr.weighty = 0.0;
            constr.gridy = 1;
            layout.setConstraints(input, constr);
            frame.getContentPane().add(input);

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    SwingUtilities.invokeLater(() -> channel.close());
                }
            });

            frame.pack();
            frame.setSize(frame.getPreferredSize());
            frame.setVisible(true);
        });
    }

    @Override
    public void run() {
        System.err.printf("Waiting...%n");
        JsonObject msg;
        while ((msg = channel.read()) != null) {
            final JsonObject msgf = msg;
            SwingUtilities.invokeLater(() -> {
                output.append(msgf.getString("msg"));
                output.append("\n");
            });
            // System.out.printf("Message: %s%n", msg.getString("msg"));
        }
        System.out.printf("Terminated%n");
        frame.dispose();
    }
}
