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
package org.teavm.model.classes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StringPool {
    private Map<String, Integer> indexes = new HashMap<>();
    private List<String> data = new ArrayList<>();

    public int lookup(String cst) {
        return indexes.computeIfAbsent(cst, key -> {
            int result = data.size();
            data.add(key);
            return result;
        });
    }

    public int size() {
        return data.size();
    }

    public String get(int index) {
        return data.get(index);
    }
}
