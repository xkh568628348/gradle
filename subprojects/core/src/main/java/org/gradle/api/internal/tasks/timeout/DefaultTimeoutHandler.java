/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.timeout;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ManagedScheduledExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.jvm.Jvm;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DefaultTimeoutHandler implements TimeoutHandler, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DefaultTimeoutHandler.class);

    private final ManagedScheduledExecutor executor;

    public DefaultTimeoutHandler(ManagedScheduledExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Timeout start(Thread taskExecutionThread, Duration timeout) {
        InterruptOnTimeout interrupter = new InterruptOnTimeout(taskExecutionThread);
        ScheduledFuture<?> timeoutTask = executor.schedule(interrupter, timeout.toMillis(), TimeUnit.MILLISECONDS);
        return new DefaultTimeout(timeoutTask, interrupter);
    }

    @Override
    public void stop() {
        executor.stop();
    }

    private static final class DefaultTimeout implements Timeout {
        private final ScheduledFuture<?> timeoutTask;
        private final InterruptOnTimeout interrupter;

        private DefaultTimeout(ScheduledFuture<?> timeoutTask, InterruptOnTimeout interrupter) {
            this.timeoutTask = timeoutTask;
            this.interrupter = interrupter;
        }

        @Override
        public void stop() {
            timeoutTask.cancel(true);
        }

        @Override
        public boolean timedOut() {
            return interrupter.interrupted;
        }
    }

    private static class InterruptOnTimeout implements Runnable {
        private final Thread thread;
        private boolean interrupted;

        private InterruptOnTimeout(Thread thread) {
            this.thread = thread;
        }

        @Override
        public void run() {
            interrupted = true;
            collectStacktrackes();
            thread.interrupt();
        }

        private void collectStacktrackes() {
            if (!Jvm.current().getJavaVersion().isJava9Compatible()) {
                return;
            }
            try (BufferedWriter out = Files.newBufferedWriter(Paths.get("Users/oehme/Desktop/stacks.txt"), StandardOpenOption.APPEND)) {
                collectStacksInto(ProcessHandle.current(), out);
            } catch (Exception e) {
                LOGGER.error("Could not collect stacktraces for timed out thread " + thread, e);
            }
        }

        private void collectStacksInto(ProcessHandle process, BufferedWriter out) {
            try {
                String stacks = Jstack.forJvm(Jvm.current()).invoke(process);
                out.write("Process " + process.pid());
                out.write(stacks);
                out.write("\n------------------\n");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to collect stack traces for process " + process.pid());
            }
            process.children().forEach((child) -> collectStacksInto(child, out));
        }
    }
}
