/*
 *  Copyright 2023 Alexey Andreev.
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
package org.teavm.classlib.impl.console;

import org.teavm.backend.wasm.runtime.WasmGCSupport;
import org.teavm.classlib.PlatformDetector;
import org.teavm.jso.JSBody;

public class JSStdoutPrintStream extends JsConsolePrintStream {
    @Override
    public void print(String s) {
        if (s == null) {
            s = "null";
        }
        if (PlatformDetector.isWebAssemblyGC()) {
            for (int i = 0; i < s.length(); ++i) {
                WasmGCSupport.putCharStdout(s.charAt(i));
            }
        } else {
            writeJs(s);
        }
    }

    @JSBody(params = "b", script = "$rt_putStdout(b);")
    private static native void writeJs(String s);
}
