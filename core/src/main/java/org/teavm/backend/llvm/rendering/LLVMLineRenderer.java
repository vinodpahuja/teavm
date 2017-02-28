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

import java.io.IOException;
import java.io.Writer;

public class LLVMLineRenderer implements LLVMLineVisitor {
    private Writer writer;
    private int indentLevel;

    public LLVMLineRenderer(Writer writer) {
        this.writer = writer;
    }

    @Override
    public void visit(LLVMSingleLine line) {
        try {
            for (int i = 0; i < indentLevel; ++i) {
                writer.append("    ");
            }
            writer.append(line.getText()).append("\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void visit(LLVMBlock block) {
        ++indentLevel;
        for (LLVMLine part : block.getLines()) {
            part.acceptVisitor(this);
        }
        --indentLevel;
    }
}
