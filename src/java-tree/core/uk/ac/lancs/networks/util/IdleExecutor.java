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

/**
 * Runs tasks later on the same thread.
 * 
 * @author simpsons
 */
public final class IdleExecutor implements Executor {
    private IdleExecutor() {}

    /**
     * @resume The sole instance of this executor
     */
    public static final IdleExecutor INSTANCE = new IdleExecutor();

    private final ThreadLocal<List<Runnable>> queue = new ThreadLocal<>() {
        @Override
        protected List<Runnable> initialValue() {
            return new ArrayList<>();
        }
    };

    /**
     * Schedule an action for execution.
     * 
     * @param command the action to be executed
     */
    @Override
    public void execute(Runnable command) {
        queue.get().add(command);
    }

    private boolean internalProcess() {
        List<Runnable> q = queue.get();
        if (q.isEmpty()) return false;
        q.remove(0).run();
        return true;
    }

    /**
     * Run at most one task now. The first task off the thread's queue
     * is removed and executed.
     * 
     * @return {@code true} if a task was executed
     */
    public static boolean process() {
        return INSTANCE.internalProcess();
    }

    /**
     * Run all tasks until exhausted.
     * 
     * @return {@code true} if at least one task was executed
     */
    public static boolean processAll() {
        if (!process()) return false;
        while (process())
            ;
        return true;
    }
}
