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

import org.teavm.backend.common.Mangling;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.util.VariableType;

public final class LLVMRenderingHelper {
    private LLVMRenderingHelper() {
    }

    public static String renderType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return "i32";
                case BYTE:
                    return "i32";
                case SHORT:
                case CHARACTER:
                    return "i32";
                case INTEGER:
                    return "i32";
                case LONG:
                    return "i64";
                case FLOAT:
                    return "float";
                case DOUBLE:
                    return "double";
            }
        } else if (type instanceof ValueType.Array) {
            return "i8*";
        } else if (type instanceof ValueType.Void) {
            return "void";
        } else if (type instanceof ValueType.Object) {
            return "i8*";
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    public static String renderType(VariableType type) {
        switch (type) {
            case INT:
                return "i32";
            case LONG:
                return "i64";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case BYTE_ARRAY:
            case CHAR_ARRAY:
            case SHORT_ARRAY:
            case INT_ARRAY:
            case LONG_ARRAY:
            case FLOAT_ARRAY:
            case DOUBLE_ARRAY:
            case OBJECT_ARRAY:
            case OBJECT:
                return "i8*";
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    public static String renderItemType(VariableType type) {
        switch (type) {
            case BYTE_ARRAY:
                return "i8";
            case SHORT_ARRAY:
            case CHAR_ARRAY:
                return "i16";
            case INT_ARRAY:
                return "i32";
            case LONG_ARRAY:
                return "i64";
            case FLOAT_ARRAY:
                return "float";
            case DOUBLE_ARRAY:
                return "double";
            case OBJECT_ARRAY:
                return "i8*";
            default:
                throw new IllegalArgumentException("Not an array type: " + type);
        }
    }

    public static String methodType(MethodDescriptor method) {
        StringBuilder sb = new StringBuilder();
        sb.append(renderType(method.getResultType())).append(" (i8*");
        for (int i = 0; i < method.parameterCount(); ++i) {
            sb.append(", ").append(renderType(method.parameterType(i)));
        }
        sb.append(")*");
        return sb.toString();
    }

    public static String renderType(NumericOperandType type) {
        switch (type) {
            case INT:
                return "i32";
            case LONG:
                return "i64";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
        }
        throw new IllegalArgumentException("Unknown operand type: " + type);
    }

    public static String dataStruct(String className) {
        return "%data$" + Mangling.mangleString(className);
    }

    public static String classStruct(String className) {
        return "%class$" + Mangling.mangleString(className);
    }

    public static String classInstance(ValueType type) {
        return "@class$" + Mangling.mangleType(type);
    }

    public static String classInitializer(String className) {
        return "@clinit$" + Mangling.mangleString(className);
    }

    public static String isInstanceFunction(ValueType type) {
        return "@instanceof$" + Mangling.mangleType(type);
    }
}
