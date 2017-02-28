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
package org.teavm.backend.llvm.rendering;

import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.methodType;
import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.renderType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.teavm.backend.llvm.LayoutProvider;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.classes.StringPool;
import org.teavm.model.classes.TagRegistry;
import org.teavm.model.classes.VirtualTable;
import org.teavm.model.classes.VirtualTableEntry;
import org.teavm.model.classes.VirtualTableProvider;

public class LLVMRenderer {
    private ClassReaderSource classSource;
    private VirtualTableProvider vtableProvider;
    private LayoutProvider layoutProvider;
    private TagRegistry tagRegistry;
    private StringPool stringPool = new StringPool();
    private LLVMBlock rootBlock;

    public LLVMRenderer(ClassReaderSource classSource, LayoutProvider layoutProvider,
            VirtualTableProvider vtableProvider, TagRegistry tagRegistry) {
        this.classSource = classSource;
        this.layoutProvider = layoutProvider;
        this.vtableProvider = vtableProvider;
        this.tagRegistry = tagRegistry;
    }

    public LLVMBlock getRootBlock() {
        return rootBlock;
    }

    public void renderClasses(Collection<String> classNames) throws IOException {
        List<String> stackRoots = new ArrayList<>();
        for (String className : classNames) {
            rootBlock.comment("class " + className);

            LLVMStructure structure = new LLVMStructure("vtable." + className);
            ClassReader cls = classSource.get(className);
            boolean isTop = cls == null || cls.getParent() == null || cls.getParent().equals(cls.getName());
            if (!isTop) {
                structure.addField("%vtable." + cls.getParent(), "<parent>");
            } else {
                structure.addField("%vtable");
            }
            emitVirtualTableEntries(vtableProvider.lookup(className), false, structure);
            if (structure.getFields().isEmpty() && isTop) {
                structure.addField("%itable");
            }
            rootBlock.add(structure);

            structure = new LLVMStructure("class." + className);
            structure.addField(!isTop ? "%class." + cls.getParent() : "%teavm.Object");

            List<String> gcFields = new ArrayList<>();
            for (FieldReader field : cls.getFields()) {
                if (!field.hasModifier(ElementModifier.STATIC)) {
                    if (isReference(field.getType())) {
                        gcFields.add("i32 ptrtoint (i8** getelementptr (%class." + className + ", "
                                + "%class." + className + "* null, i32 0, i32 " + structure.getFields().size() + ") "
                                + "to i32)");
                    }
                    structure.addField(renderType(field.getType()), field.getName());
                }
            }
            rootBlock.add(structure);
        }

        for (String className : classNames) {
            ClassReader cls = classSource.get(className);

            for (MethodReader method : cls.getMethods()) {
                /*LLVMMethodRenderer methodRenderer = new LLVMMethodRenderer(appendable, classSource, stringPool,
                        layoutProvider, vtableProvider, tagRegistry, cs -> addCallSite(cs));
                methodRenderer.renderMethod(method);*/
            }

            //renderClassInitializer(cls);
        }

        /*for (String className : classNames) {
            ClassReader cls = classSource.get(className);

            VirtualTable vtable = vtableProvider.lookup(cls.getName());
            appendable.append("@vtable." + className + " = private global ");
            renderVirtualTableValues(cls, vtable, 0);
            appendable.append(", align 8\n");

            for (FieldReader field : cls.getFields()) {
                if (field.hasModifier(ElementModifier.STATIC)) {
                    String fieldRef = "@" + mangleField(field.getReference());
                    Object initialValue = field.getInitialValue();
                    String initialValueStr = initialValue != null ? initialValue.toString()
                            : defaultValue(field.getType());
                    appendable.append(fieldRef + " = private global " + renderType(field.getType()) + " "
                            + initialValueStr + "\n");
                    stackRoots.add(fieldRef);
                }
            }
        }*/

        /*for (ObjectLayout layout : layouts) {
            String className = layout.className;
            appendable.append("@fields." + className + " = private constant [" + layout.fields.size() + " x i32] [\n");
            for (int i = 0; i < layout.fields.size(); ++i) {
                if (i > 0) {
                    appendable.append(",\n");
                }
                appendable.append("    " + layout.fields.get(i));
            }
            appendable.append("\n]\n");
        }
        */

        /*
        String stackRootDataType = "[" + stackRoots.size() + " x i8**]";
        appendable.append("@teavm.stackRoots = constant %teavm.stackRoots { i64 " + stackRoots.size() + ", "
                + " i8*** bitcast (" + stackRootDataType + "* @teavm.stackRootsData to i8***) }\n");
        appendable.append("@teavm.stackRootsData = private constant " + stackRootDataType + " [");
        for (int i = 0; i < stackRoots.size(); ++i) {
            if (i > 0) {
                appendable.append(",");
            }
            appendable.append("\n    i8** " + stackRoots.get(i));
        }
        appendable.append("]\n");

        renderCallSites();
        */
    }

    private void emitVirtualTableEntries(VirtualTable vtable, boolean fqn, LLVMStructure structure) {
        if (vtable == null) {
            return;
        }

        for (VirtualTableEntry entry : vtable.getEntries().values()) {
            MethodReference method = entry.getImplementor();

            structure.addField(methodType(method.getDescriptor()),
                    fqn ? method.toString() : method.getDescriptor().toString());
        }
    }

    private boolean isReference(ValueType type) {
        return type instanceof ValueType.Object || type instanceof ValueType.Array;
    }
}
