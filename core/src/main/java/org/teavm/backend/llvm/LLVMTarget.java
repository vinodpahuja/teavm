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
package org.teavm.backend.llvm;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.backend.common.LowLevelDependenciesContributor;
import org.teavm.backend.common.patches.ClassPatch;
import org.teavm.backend.llvm.rendering.LLVMRenderer;
import org.teavm.dependency.DependencyChecker;
import org.teavm.dependency.DependencyListener;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.Program;
import org.teavm.model.classes.TagRegistry;
import org.teavm.model.classes.VirtualTableProvider;
import org.teavm.model.lowlevel.ClassInitializerEliminator;
import org.teavm.model.lowlevel.ClassInitializerTransformer;
import org.teavm.model.lowlevel.ShadowStackTransformer;
import org.teavm.model.transformation.ClassInitializerInsertionTransformer;
import org.teavm.vm.BuildTarget;
import org.teavm.vm.TeaVMTarget;
import org.teavm.vm.TeaVMTargetController;
import org.teavm.vm.spi.TeaVMHostExtension;

public class LLVMTarget implements TeaVMTarget {
    private TeaVMTargetController controller;
    private ClassInitializerInsertionTransformer clinitInsertionTransformer;
    private ClassInitializerEliminator classInitializerEliminator;
    private ClassInitializerTransformer classInitializerTransformer;
    private ShadowStackTransformer shadowStackTransformer;

    @Override
    public void setController(TeaVMTargetController controller) {
        this.controller = controller;
        classInitializerEliminator = new ClassInitializerEliminator(controller.getUnprocessedClassSource());
        classInitializerTransformer = new ClassInitializerTransformer();
        shadowStackTransformer = new ShadowStackTransformer(controller.getUnprocessedClassSource());
        clinitInsertionTransformer = new ClassInitializerInsertionTransformer(controller.getUnprocessedClassSource());
    }

    @Override
    public List<TeaVMHostExtension> getHostExtensions() {
        return Collections.emptyList();
    }

    @Override
    public boolean requiresRegisterAllocation() {
        return false;
    }

    @Override
    public List<ClassHolderTransformer> getTransformers() {
        List<ClassHolderTransformer> transformers = new ArrayList<>();
        transformers.add(new ClassPatch());
        return transformers;
    }

    @Override
    public void contributeDependencies(DependencyChecker dependencyChecker) {
        LowLevelDependenciesContributor.contribute(dependencyChecker);
    }

    @Override
    public void afterOptimizations(Program program, MethodReader method, ListableClassReaderSource classes) {
        clinitInsertionTransformer.apply(method, program);
        classInitializerEliminator.apply(program);
        classInitializerTransformer.transform(program);
        shadowStackTransformer.apply(program, method);
    }

    @Override
    public List<DependencyListener> getDependencyListeners() {
        return Collections.emptyList();
    }

    @Override
    public void emit(ListableClassHolderSource classes, BuildTarget buildTarget, String outputName) throws IOException {
        VirtualTableProvider vtableProvider = VirtualTableProvider.create(classes);
        TagRegistry tagRegistry = new TagRegistry(classes);
        LayoutRegistry layoutProvider = new LayoutRegistry(classes);
        for (String className : classes.getClassNames()) {
            layoutProvider.addClass(className);
        }

        LLVMRenderer renderer = new LLVMRenderer(classes, layoutProvider, vtableProvider, tagRegistry);
        try (OutputStream output = buildTarget.createResource(outputName);
                OutputStreamWriter writer = new OutputStreamWriter(output, "UTF-8")) {
            renderer.renderClasses(classes.getClassNames(), writer);
        }
    }
}
