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

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.IOUtils;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.jvm.Jvm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

public class Jstack {
    public static Jstack forJvm(Jvm jvm) {
        return new Jstack(jvm.getExecutable("jstack"));
    }

    private final File jstackExecutable;

    private Jstack(File jstackExecutable) {
        this.jstackExecutable = jstackExecutable;
    }

    public String invoke(ProcessHandle process) {
        try {
            return doInvoke(process);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public String doInvoke(ProcessHandle process) throws IOException, InterruptedException {
        String pid = String.valueOf(process.pid());
        JstackResult result = invokeJstack(pid);
        if (result.exitCode != 0) {
            result = invokeJstack("-F", pid);
        }
        if (result.exitCode != 0) {
            throw new IllegalStateException("Failed to collect stack traces for process " + pid + "\n" + result.error);
        }
        return result.stacks;
    }

    private JstackResult invokeJstack(String... args) throws IOException, InterruptedException {
        List<String> jstackArgs = ImmutableList.<String>builder().add(jstackExecutable.getAbsolutePath()).add(args).build();
        Process jstack = new ProcessBuilder(jstackArgs).start();
        String stacks = IOUtils.toString(jstack.getInputStream(), Charset.defaultCharset());
        String err = IOUtils.toString(jstack.getErrorStream(), Charset.defaultCharset());
        int exitCode = jstack.waitFor();
        return new JstackResult(exitCode, stacks, err);
    }

    private static class JstackResult {
        private final int exitCode;
        private final String stacks;
        private final String error;

        private JstackResult(int exitCode, String stacks, String error) {
            this.exitCode = exitCode;
            this.stacks = stacks;
            this.error = error;
        }
    }
}
