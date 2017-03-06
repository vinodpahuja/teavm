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

import static org.teavm.backend.common.RuntimeMembers.CLASS_CLASS;
import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.classInstance;
import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.classStruct;
import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.dataStruct;
import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.methodType;
import static org.teavm.backend.llvm.rendering.LLVMRenderingHelper.renderType;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.backend.common.Mangling;
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
import org.teavm.runtime.RuntimeJavaObject;
import org.teavm.runtime.RuntimeObject;

public class LLVMRenderer {
    private ClassReaderSource classSource;
    private VirtualTableProvider vtableProvider;
    private LayoutProvider layoutProvider;
    private TagRegistry tagRegistry;
    private StringPool stringPool = new StringPool();
    private LLVMBlock rootBlock;
    private Map<String, Boolean> isStructureClasses = new HashMap<>();
    private Set<ValueType> referencedClasses = new HashSet<>();

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
                        gcFields.add("i32 ptrtoint (i8** getelementptr (" + classStruct(className) + ", "
                                + classStruct(className) + "* null, i32 0, i32 " + structure.getFields().size() + ") "
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
                referencedClasses.addAll(methodRenderer.getReferencedTypes());
            }

            //renderClassInitializer(cls);
        }

        for (ValueType valueType : referencedClasses) {
            renderClassInstance(valueType);
        }

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

    private void renderClassInstance(ValueType valueType) {
        String size = "0";
        String parent = "null";
        int flags = 0;
        int tag = 0;
        String itemRef = "null";
        if (valueType instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) valueType).getKind()) {
                case BOOLEAN:
                case BYTE:
                case SHORT:
                case CHARACTER:
                case INTEGER:
                case FLOAT:
                    size = "4";
                    break;
                case LONG:
                case DOUBLE:
                    size = "8";
                    break;
            }
            flags |= RuntimeClass.PRIMITIVE;
        } else if (valueType instanceof ValueType.Object) {
            String className = ((ValueType.Object) valueType).getClassName();
            size = dataStruct(className);
            size = "ptrtoint (" + size + "* getelementptr (" + size + "* null, i32 1) to i32)";
            size = "sub (i32 " + size + ", 1)";
            size = "shr (i32 " + size + ", 2)";
            size = "add (i32 " + size + ", 1)";
            size = "shl (i32 " + size + ", 2)";
            List<TagRegistry.Range> ranges = tagRegistry.getRanges(className);
            tag = ranges.stream().mapToInt(range -> range.lower).min().orElse(0);
            ClassReader cls = classSource.get(className);
            if (cls != null && cls.getParent() != null) {
                parent = classInstance(ValueType.object(cls.getParent()));
            }
            if (isStructure(className)) {
                return;
            }
        } else if (valueType instanceof ValueType.Array) {
            size = "4";
            parent = classInstance(ValueType.object(Object.class.getName()));
            ValueType itemType = ((ValueType.Array) valueType).getItemType();
            itemRef = classInstance(itemType);
            if (itemType instanceof ValueType.Object) {
                itemRef = "bitcast (" + classStruct(((ValueType.Object) itemType).getClassName()) + "* " + itemRef
                        + " to " + dataStruct(RuntimeClass.class.getName()) + "*)";
            }
        }

        ValueType arrayType = ValueType.arrayOf(valueType);
        String arrayRef = "null";
        if (referencedClasses.contains(arrayType)) {
            arrayRef = classInstance(arrayType);
        }

        LLVMStructureConstant runtimeObject = new LLVMStructureConstant();
        runtimeObject.setType(dataStruct(RuntimeObject.class.getName()));
        runtimeObject.addSimpleField("i32", "0");

        LLVMStructureConstant runtimeJavaObject = new LLVMStructureConstant();
        runtimeJavaObject.setType(dataStruct(RuntimeJavaObject.class.getName()));
        runtimeJavaObject.getFields().add(runtimeObject);
        runtimeJavaObject.addSimpleField(dataStruct(RuntimeObject.class.getName()) + "*", "null");

        LLVMStructureConstant runtimeClass = new LLVMStructureConstant();
        runtimeClass.setType(dataStruct(RuntimeClass.class.getName()));
        runtimeClass.getFields().add(runtimeJavaObject);
        runtimeClass.addSimpleField("i32", size);
        runtimeClass.addSimpleField("i32", String.valueOf(flags));
        runtimeClass.addSimpleField("i32", String.valueOf(tag));
        runtimeClass.addSimpleField("i32", "0");
        runtimeClass.addSimpleField(dataStruct(RuntimeClass.class.getName()) + "*", itemRef);
        runtimeClass.addSimpleField(dataStruct(RuntimeClass.class.getName()) + "*", arrayRef);
        runtimeClass.addSimpleField(dataStruct(RuntimeClass.class.getName()) + "*", parent);
        runtimeClass.addSimpleField("i32 (" + dataStruct(RuntimeClass.class.getName()) + "*)*", "null");
        runtimeClass.addSimpleField("i8*", "null");

        LLVMConstant constant = runtimeClass;
        if (valueType instanceof ValueType.Object) {
            String className = ((ValueType.Object) valueType).getClassName();
            VirtualTable vtable = vtableProvider.lookup(className);
            constant = createVirtualTable(constant, vtable);
        }

        rootBlock.add(classInstance(valueType) + " = private global ", constant, ";");
    }

    private LLVMConstant createVirtualTable(LLVMConstant innermostConstant, VirtualTable vtable) {
        LLVMStructureConstant constant = new LLVMStructureConstant();
        constant.setType(classStruct(vtable.getClassName()));
        ClassReader cls = classSource.get(vtable.getClassName());

        LLVMConstant parentConstant;
        if (cls.getParent() != null) {
            parentConstant = createVirtualTable(innermostConstant, vtableProvider.lookup(cls.getParent()));
        } else {
            parentConstant = innermostConstant;
        }
        constant.getFields().add(parentConstant);

        List<VirtualTableEntry> entries = new ArrayList<>(vtable.getEntries().values());
        for (int i = 0; i < entries.size(); ++i) {
            VirtualTableEntry entry = entries.get(i);
            LLVMSimpleConstant entryConstant = new LLVMSimpleConstant();
            entryConstant.setType(methodType(entry.getMethod()));
            entryConstant.setValue(entry.getImplementor() != null
                    ? "@" + Mangling.mangleMethod(entry.getImplementor())
                    : "null");
            constant.getFields().add(entryConstant);
        }

        return constant;
    }

    private void emitVirtualTableEntries(VirtualTable vtable, LLVMStructure structure) {
        if (vtable == null) {
            return;
        }

        for (VirtualTableEntry entry : vtable.getEntries().values()) {
            structure.addField(methodType(entry.getMethod()), entry.getMethod().toString());
        }
    }

    private void emitIsSupertype(ValueType valueType) {
        if (valueType instanceof ValueType.Primitive) {
            return;
        }
        rootBlock.line("define i32 @" + Mangling.mangleIsSupertype(valueType)
                + "(" + dataStruct(CLASS_CLASS) + "* subtype) {");
        LLVMBlock block = rootBlock.innerBlock();
        if (valueType instanceof ValueType.Object) {
            emitIsClassSupertype(((ValueType.Object) valueType).getClassName(), block);
        }

        rootBlock.line("}");
    }

    private void emitIsClassSupertype(String className, LLVMBlock block) {
        List<TagRegistry.Range> ranges = tagRegistry.getRanges(className);
        if (ranges.isEmpty()) {
            block.line("ret i32 0");
            return;
        }

        block.line("%subtypeRef = getelementptr " + dataStruct(CLASS_CLASS) + ", "
                + dataStruct(CLASS_CLASS) + "*, i32 0, i32 3");
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
