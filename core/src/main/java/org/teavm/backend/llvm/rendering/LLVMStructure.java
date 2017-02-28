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

public class LLVMStructure {
    private final String name;
    private final List<LLVMField> fields = new ArrayList<>();

    public LLVMStructure(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<LLVMField> getFields() {
        return fields;
    }

    public void addField(String type, String name) {
        fields.add(new LLVMField(type, name));
    }

    public void addField(String type) {
        fields.add(new LLVMField(type, null));
    }
}
