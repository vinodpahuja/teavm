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

import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.classStruct;
import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.dataStruct;
import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.methodType;
import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.renderType;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.backend.llvm.LayoutProvider;
import org.teavm.interop.Address;
import org.teavm.interop.Structure;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodReader;
import org.teavm.model.ValueType;
import org.teavm.model.classes.StringPool;
import org.teavm.model.classes.TagRegistry;
import org.teavm.model.classes.VirtualTable;
import org.teavm.model.classes.VirtualTableEntry;
import org.teavm.model.classes.VirtualTableProvider;
import org.teavm.runtime.RuntimeClass;
import org.teavm.runtime.RuntimeObject;

public class LLVMRenderer {
    private ClassReaderSource classSource;
    private VirtualTableProvider vtableProvider;
    private LayoutProvider layoutProvider;
    private TagRegistry tagRegistry;
    private StringPool stringPool = new StringPool();
    private LLVMBlock rootBlock;
    private Map<String, Boolean> isStructureClasses = new HashMap<>();

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

    public void renderClasses(Collection<String> classNames, Writer writer) throws IOException {
        rootBlock = new LLVMBlock();

        List<String> stackRoots = new ArrayList<>();
        for (String className : classNames) {
            if (className.equals(Structure.class.getName()) || className.equals(Address.class.getName())) {
                continue;
            }

            rootBlock.comment("class " + className);

            ClassReader cls = classSource.get(className);
            boolean isTop = cls == null || cls.getParent() == null || cls.getParent().equals(cls.getName());

            if (!isStructure(className)) {
                LLVMStructure structure = new LLVMStructure(classStruct(className));
                if (!isTop) {
                    structure.addField(classStruct(cls.getParent()), "<parent>");
                } else {
                    structure.addField(dataStruct(RuntimeClass.class.getName()));
                }
                emitVirtualTableEntries(vtableProvider.lookup(className), structure);
                rootBlock.add(structure);
            }

            LLVMStructure structure = new LLVMStructure(dataStruct(className));
            if (cls.getParent() == null || !cls.getParent().equals(Structure.class.getName())) {
                structure.addField(!isTop ? dataStruct(cls.getParent()) : dataStruct(RuntimeObject.class.getName()));
            }

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
            rootBlock.line("");
        }

        for (String className : classNames) {
            ClassReader cls = classSource.get(className);

            for (MethodReader method : cls.getMethods()) {
                LLVMMethodRenderer methodRenderer = new LLVMMethodRenderer(classSource, stringPool,
                        layoutProvider, vtableProvider);
                methodRenderer.setRootBlock(rootBlock);
                methodRenderer.renderMethod(method);
                rootBlock.line("");
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

        rootBlock.acceptVisitor(new LLVMLineRenderer(writer));
    }

    private void emitVirtualTableEntries(VirtualTable vtable, LLVMStructure structure) {
        if (vtable == null) {
            return;
        }

        for (VirtualTableEntry entry : vtable.getEntries().values()) {
            structure.addField(methodType(entry.getMethod()), entry.getMethod().toString());
        }
    }

    private boolean isReference(ValueType type) {
        return type instanceof ValueType.Object || type instanceof ValueType.Array;
    }

    private boolean isStructure(String className) {
        return isStructureClasses.computeIfAbsent(className, name -> {
            if (name.equals(Structure.class.getName())) {
                return true;
            }
            ClassReader cls = classSource.get(name);
            if (cls == null) {
                return false;
            }
            if (cls.getParent() != null) {
                return isStructure(cls.getParent());
            }
            return false;
        });
    }
}
