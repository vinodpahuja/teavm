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

import org.teavm.model.FieldReference;
import org.teavm.runtime.RuntimeArray;
import org.teavm.runtime.RuntimeClass;
import org.teavm.runtime.RuntimeObject;

public final class RuntimeMembers {
    public static final String CLASS_CLASS = RuntimeClass.class.getName();

    public static final FieldReference OBJECT_CLASS_REFERENCE_FIELD = new FieldReference(RuntimeObject.class.getName(),
            "classReference");
    public static final FieldReference ARRAY_SIZE_FIELD = new FieldReference(RuntimeArray.class.getName(), "size");
    public static final FieldReference CLASS_FLAGS_FIELD = new FieldReference(CLASS_CLASS, "flags");

    private RuntimeMembers() {
    }
}
