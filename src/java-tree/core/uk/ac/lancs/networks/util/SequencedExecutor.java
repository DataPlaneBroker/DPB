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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes actions on a single thread in the order they were submitted.
 * 
 * @author simpsons
 */
public class SequencedExecutor implements Executor, Runnable {
    private final List<Runnable> actions = new ArrayList<>();
    private boolean running = true;

    /**
     * Add an action to the queue.
     * 
     * @param action the action to queue
     * 
     * @throws IllegalStateException if the executor has been shut down
     * with {@link #shutdown()}
     */
    @Override
    public synchronized void execute(Runnable action) {
        if (!running) throw new IllegalStateException("shut down");
        actions.add(action);
        notify();
    }

    private synchronized Runnable nextAction() {
        while (running && actions.isEmpty())
            try {
                wait();
            } catch (InterruptedException e) {
                /* Try again. */
            }
        if (actions.isEmpty()) return null;
        return actions.remove(0);
    }

    /**
     * Shut this executor down. Subsequent calls to
     * {@link #execute(Runnable)} will throw an exception.
     */
    public synchronized void shutdown() {
        running = false;
        notifyAll();
    }

    /**
     * Wait until the queue is empty and the executor has been shut
     * down.
     * 
     * @throws InterruptedException if this thread is interrupted while
     * waiting for shutdown
     */
    public synchronized void waitForCompletion() throws InterruptedException {
        while (running && !actions.isEmpty())
            wait();
    }

    /**
     * Execute actions from the queue, until there are no more actions,
     * and the executor has been shutdown.
     */
    @Override
    public void run() {
        Runnable action;
        while ((action = nextAction()) != null)
            try {
                action.run();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Uncaught exception", t);
            }
    }

    private final Logger logger =
        Logger.getLogger(SequencedExecutor.class.getName());
}
