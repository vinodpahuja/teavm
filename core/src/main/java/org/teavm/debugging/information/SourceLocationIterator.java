/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.debugging.information;

public class SourceLocationIterator {
    private DebugInformation debugInformation;
    private LayerIterator layerIterator;
    private LayerSourceLocationIterator[] layerSourceIterators;
    private boolean endReached;
    private int currentLayer;
    private GeneratedLocation location;
    private int fileId;
    private int line;

    public SourceLocationIterator(DebugInformation debugInformation) {
        this.debugInformation = debugInformation;
        layerIterator = new LayerIterator(debugInformation);
        layerSourceIterators = new LayerSourceLocationIterator[debugInformation.layerCount()];
        for (int i = 0; i < layerSourceIterators.length; ++i) {
            layerSourceIterators[i] = new LayerSourceLocationIterator(debugInformation, debugInformation.layers[i]);
        }

        if (!layerIterator.isEndReached()) {
            location = layerIterator.getLocation();
            currentLayer = layerIterator.getLayer();
            layerIterator.next();
        } else {
            currentLayer = 0;
        }
        LayerSourceLocationIterator currentIterator = layerSourceIterators[currentLayer];
        fileId = currentIterator.getFileNameId();
        line = currentIterator.getLine();
        currentIterator.next();
    }

    public GeneratedLocation getLocation() {
        return location;
    }

    public boolean isEndReached() {
        return endReached;
    }

    public void next() {
        if (endReached) {
            throw new IllegalStateException();
        }

        LayerSourceLocationIterator currentIterator = layerSourceIterators[currentLayer];

        if (!currentIterator.isEndReached() && !layerIterator.isEndReached()
                && currentIterator.getLocation().compareTo(layerIterator.getLocation()) >= 0) {
            currentLayer = layerIterator.getLayer();
            location = layerIterator.getLocation();
            layerIterator.next();

            currentIterator = layerSourceIterators[currentLayer];
            do {
                fileId = currentIterator.getFileNameId();
                line = currentIterator.getLine();
                currentIterator.next();
            } while (!currentIterator.isEndReached() && currentIterator.getLocation().compareTo(location) <= 0);
        } else if (currentIterator.isEndReached()) {
            endReached = true;
        } else {
            location = currentIterator.getLocation();
            fileId = currentIterator.getFileNameId();
            line = currentIterator.getLine();
            currentIterator.next();
        }
    }

    public int getFileNameId() {
        if (isEndReached()) {
            throw new IllegalStateException("End already reached");
        }
        return fileId;
    }

    public String getFileName() {
        int fileId = getFileNameId();
        return fileId >= 0 ? debugInformation.getFileName(fileId) : null;
    }

    public int getLine() {
        if (isEndReached()) {
            throw new IllegalStateException("End already reached");
        }
        return line;
    }
}
