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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.transform.ArtifactTransformDependencies;
import org.gradle.api.artifacts.transform.PrimaryInput;
import org.gradle.api.artifacts.transform.Workspace;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.PropertyDetails;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

public class TransformerFromCallable extends AbstractTransformer<Callable<List<File>>> {

    private final Method workspaceSetter;
    private final Method primaryInputSetter;

    public TransformerFromCallable(Class<? extends Callable<List<File>>> implementationClass, Isolatable<Object[]> paramsSnapshot, HashCode secondaryInputsHash, InstantiatorFactory instantiatorFactory, ImmutableAttributes from) {
        super(implementationClass, paramsSnapshot, secondaryInputsHash, instantiatorFactory, from);
        // TODO: make this more general by hooking into `DefaultPropertyMetadataStore` or something similar
        this.workspaceSetter = findSetterAnnotatedWith(implementationClass, Workspace.class);
        this.primaryInputSetter = findSetterAnnotatedWith(implementationClass, PrimaryInput.class);
    }

    @Override
    public List<File> transform(File primaryInput, File outputDir, ArtifactTransformDependencies dependencies) {
        Callable<List<File>> transformer = newTransformer(dependencies);
        try {
            injectProperties(transformer, primaryInput, outputDir);
            List<File> result = transformer.call();
            validateOutputs(primaryInput, outputDir, result);
            return result;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void injectProperties(Callable<List<File>> transformer, File primaryInput, File outputDir) throws InvocationTargetException, IllegalAccessException {
        if (workspaceSetter != null) {
            workspaceSetter.invoke(transformer, outputDir);
        }
        if (primaryInputSetter != null) {
            primaryInputSetter.invoke(transformer, primaryInput);
        }
    }

    @Nullable
    private Method findSetterAnnotatedWith(Class<? extends Callable<List<File>>> implementationClass, Class<? extends Annotation> annotation) {
        ClassDetails classDetails = ClassInspector.inspect(implementationClass);
        Set<String> propertyNames = classDetails.getPropertyNames();
        for (String propertyName : propertyNames) {
            PropertyDetails property = classDetails.getProperty(propertyName);
            List<Method> setters = property.getSetters();
            Optional<Method> annotatedSetter = setters.stream().filter(method -> hasAnnotation(propertyName, method, implementationClass, annotation)).findFirst();
            if (annotatedSetter.isPresent()) {
                return annotatedSetter.get();
            }
        }
        return null;
    }

    private boolean hasAnnotation(String propertyName, Method setter, Class<?> type, Class<? extends Annotation> annotation) {
        if (setter.getAnnotation(annotation) != null) {
            return true;
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(propertyName)) {
                return field.getAnnotation(annotation) != null;
            }
        }
        return false;
    }
}
