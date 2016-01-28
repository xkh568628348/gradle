/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.jvm

import groovy.json.StringEscapeUtils
import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class JdkDeclarationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id 'jvm-component'
            }
        """
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "can declare an installed JDK and model report shows the resolved installed JDK"() {
        given:
        def jdks = AvailableJavaHomes.availableJdks.indexed().collect { i, jdk ->
            """
                    jdk$i(JdkSpec) {
                        path '${StringEscapeUtils.escapeJava(jdk.javaHome.toString())}'
                    }
            """
        }.join('')
        buildFile << """
            model {
                jdks {
                    $jdks
                }
            }
        """

        when:
        succeeds 'model', '--format=short'

        then:
        def report = ModelReportOutput.from(output)
        // for each declared JDK, there must be *at least* one installed JDK which Java Home corresponds
        // to the one declared. There may be less because they are deduplicated
        AvailableJavaHomes.availableJdks.eachWithIndex { jdk, i ->
            assert report.modelNode.installedJdks.'**'.@javaHome.any {
                it == jdk.javaHome.canonicalFile.absolutePath
            }
        }
    }
}
