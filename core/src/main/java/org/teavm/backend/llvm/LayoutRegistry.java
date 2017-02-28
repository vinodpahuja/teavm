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

import java.util.HashMap;
import java.util.Map;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;

public class LayoutRegistry implements LayoutProvider {
    private ClassReaderSource classSource;
    private Map<FieldReference, Integer> layout = new HashMap<>();

    public LayoutRegistry(ClassReaderSource classSource) {
        this.classSource = classSource;
    }

    public void addClass(String className) {
        ClassReader cls = classSource.get(className);
        addClass(cls);
    }

    private void addClass(ClassReader cls) {
        int index = 0;
        if (cls.getParent() != null && !cls.getParent().equals(cls.getName())) {
            ++index;
        }
        for (FieldReader field : cls.getFields()) {
            if (!field.hasModifier(ElementModifier.STATIC)) {
                layout.put(field.getReference(), index++);
            }
        }
    }

    @Override
    public int getIndex(FieldReference field) {
        return layout.getOrDefault(field, -1);
    }
}
