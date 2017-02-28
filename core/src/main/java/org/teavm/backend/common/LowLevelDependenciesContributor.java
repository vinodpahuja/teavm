/*
 *  Copyright 2017 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.backend.common;

import java.util.Arrays;
import org.teavm.dependency.ClassDependency;
import org.teavm.dependency.DependencyChecker;
import org.teavm.interop.Address;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.runtime.Allocator;
import org.teavm.runtime.ExceptionHandling;
import org.teavm.runtime.RuntimeArray;
import org.teavm.runtime.RuntimeClass;
import org.teavm.runtime.RuntimeJavaObject;
import org.teavm.runtime.RuntimeObject;

public final class LowLevelDependenciesContributor {
    private LowLevelDependenciesContributor() {
    }

    public static void contribute(DependencyChecker dependencyChecker) {
        dependencyChecker.linkMethod(new MethodReference(Allocator.class, "allocate",
                RuntimeClass.class, Address.class), null).use();
        dependencyChecker.linkMethod(new MethodReference(Allocator.class, "allocateArray",
                RuntimeClass.class, int.class, Address.class), null).use();
        dependencyChecker.linkMethod(new MethodReference(Allocator.class, "allocateMultiArray",
                RuntimeClass.class, Address.class, int.class, RuntimeArray.class), null).use();

        dependencyChecker.linkMethod(new MethodReference(Allocator.class, "<clinit>", void.class), null).use();

        dependencyChecker.linkMethod(new MethodReference(ExceptionHandling.class, "throwException",
                Throwable.class, void.class), null).use();

        dependencyChecker.linkMethod(new MethodReference(ExceptionHandling.class, "catchException",
                Throwable.class), null).use();

        dependencyChecker.linkField(new FieldReference("java.lang.Object", "monitor"), null);

        ClassDependency runtimeClassDep = dependencyChecker.linkClass(RuntimeClass.class.getName(), null);
        ClassDependency runtimeObjectDep = dependencyChecker.linkClass(RuntimeObject.class.getName(), null);
        ClassDependency runtimeJavaObjectDep = dependencyChecker.linkClass(RuntimeJavaObject.class.getName(), null);
        ClassDependency runtimeArrayDep = dependencyChecker.linkClass(RuntimeArray.class.getName(), null);
        for (ClassDependency classDep : Arrays.asList(runtimeClassDep, runtimeObjectDep, runtimeJavaObjectDep,
                runtimeArrayDep)) {
            for (FieldReader field : classDep.getClassReader().getFields()) {
                dependencyChecker.linkField(field.getReference(), null);
            }
        }
    }
}
