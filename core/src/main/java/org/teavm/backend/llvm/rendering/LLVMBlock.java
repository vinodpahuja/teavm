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

import java.util.ArrayList;
import java.util.List;

public class LLVMBlock extends LLVMLine {
    private List<LLVMLine> lines = new ArrayList<>();

    public List<LLVMLine> getLines() {
        return lines;
    }

    @Override
    public void acceptVisitor(LLVMLineVisitor visitor) {
        visitor.visit(this);
    }

    public LLVMBlock innerBlock() {
        LLVMBlock block = new LLVMBlock();
        lines.add(block);
        return block;
    }

    public LLVMBlock line(String text) {
        lines.add(new LLVMSingleLine(text));
        return this;
    }

    public LLVMBlock comment(String... commentLines) {
        for (String commentLine : commentLines) {
            line("; " + commentLine);
        }
        return this;
    }

    public LLVMBlock add(LLVMStructure structure) {
        line(structure.getName() + " = type {");

        LLVMBlock innerBlock = innerBlock();
        for (int i = 0; i < structure.getFields().size() - 1; ++i) {
            LLVMField field = structure.getFields().get(i);

            StringBuilder sb = new StringBuilder(field.getType() + ",");
            if (field.getName() != null) {
                sb.append(" ;").append(field.getName());
            }
            innerBlock.line(sb.toString());
        }

        LLVMField lastField = structure.getFields().get(structure.getFields().size() - 1);
        StringBuilder sb = new StringBuilder(lastField.getType());
        if (lastField.getName() != null) {
            sb.append(" ;").append(lastField.getName());
        }
        innerBlock.line(sb.toString());

        line("}");

        return this;
    }

    public LLVMBlock add(String prefix, LLVMConstant constant, String suffix) {
        AddConstantVisitor visitor = new AddConstantVisitor();
        visitor.sb.append(prefix);
        constant.acceptVisitor(visitor);
        visitor.sb.append(suffix);
        line(visitor.sb.toString());

        return this;
    }

    private class AddConstantVisitor implements LLVMConstantVisitor {
        StringBuilder sb = new StringBuilder();

        @Override
        public void visit(LLVMSimpleConstant constant) {
            sb.append(constant.getType() + " " + constant.getValue());
        }

        @Override
        public void visit(LLVMStructureConstant constant) {
            sb.append(constant.getType() + " {");
            line(sb.toString());

            LLVMBlock entriesBlock = innerBlock();
            for (int i = 0; i < constant.getFields().size(); ++i) {
                String suffix = i < constant.getFields().size() - 1 ? "," : "";
                entriesBlock.add("", constant.getFields().get(i), suffix);
            }

            sb.setLength(0);
            sb.append("}");
        }
    }
}
