/*
 *  Copyright 2020 Alexey Andreev.
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

package org.teavm.classlib.java.util.concurrent;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TCloneNotSupportedException;
import org.teavm.classlib.java.lang.TCloneable;
import org.teavm.classlib.java.lang.TIllegalArgumentException;
import org.teavm.classlib.java.lang.TIllegalStateException;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.classlib.java.util.TAbstractCollection;
import org.teavm.classlib.java.util.TAbstractMap;
import org.teavm.classlib.java.util.TAbstractSet;
import org.teavm.classlib.java.util.TCollection;
import org.teavm.classlib.java.util.TConcurrentModificationException;
import org.teavm.classlib.java.util.TIterator;
import org.teavm.classlib.java.util.TMap;
import org.teavm.classlib.java.util.TNoSuchElementException;
import org.teavm.classlib.java.util.TSet;
import org.teavm.interop.Rename;

public class TConcurrentHashMap<K, V> extends TAbstractMap<K, V> implements TCloneable, TSerializable {
    transient int elementCount;
    transient HashEntry<K, V>[] elementData;
    transient int modCount;
    transient volatile int version;
    private static final int DEFAULT_SIZE = 16;
    final float loadFactor;
    int threshold;
    private transient TSet<K> cachedKeySet;
    private transient TCollection<V> cachedValues;

    static class HashEntry<K, V> extends TMapEntry<K, V> {
        final int origKeyHash;
        HashEntry<K, V> next;
        volatile int version;

        HashEntry(K theKey, int hash) {
            super(theKey, null);
            this.origKeyHash = hash;
        }

        HashEntry(K theKey, V theValue) {
            super(theKey, theValue);
            origKeyHash = theKey == null ? 0 : computeHashCode(theKey);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object clone() {
            HashEntry<K, V> entry = (HashEntry<K, V>) super.clone();
            if (next != null) {
                entry.next = (HashEntry<K, V>) next.clone();
            }
            return entry;
        }
    }

    private static class AbstractMapIterator<K, V>  {
        private int position;
        int expectedModCount;
        HashEntry<K, V> futureEntry;
        HashEntry<K, V> currentEntry;
        HashEntry<K, V> prevEntry;

        final TConcurrentHashMap<K, V> associatedMap;

        AbstractMapIterator(TConcurrentHashMap<K, V> hm) {
            associatedMap = hm;
            expectedModCount = hm.modCount;
            futureEntry = null;
        }

        public boolean hasNext() {
            if (futureEntry != null) {
                return true;
            }
            while (position < associatedMap.elementData.length) {
                if (associatedMap.elementData[position] == null) {
                    position++;
                } else {
                    return true;
                }
            }
            return false;
        }

        final void checkConcurrentMod() throws ConcurrentModificationException {
            if (expectedModCount != associatedMap.modCount) {
                throw new TConcurrentModificationException();
            }
        }

        final void makeNext() {
            checkConcurrentMod();
            if (!hasNext()) {
                throw new TNoSuchElementException();
            }
            if (futureEntry == null) {
                currentEntry = associatedMap.elementData[position++];
                futureEntry = currentEntry.next;
                prevEntry = null;
            } else {
                if (currentEntry != null) {
                    prevEntry = currentEntry;
                }
                currentEntry = futureEntry;
                futureEntry = futureEntry.next;
            }
        }

        public final void remove() {
            checkConcurrentMod();
            if (currentEntry == null) {
                throw new TIllegalStateException();
            }
            if (prevEntry == null) {
                int index = currentEntry.origKeyHash & (associatedMap.elementData.length - 1);
                associatedMap.elementData[index] = associatedMap.elementData[index].next;
            } else {
                prevEntry.next = currentEntry.next;
            }
            currentEntry = null;
            expectedModCount++;
            associatedMap.modCount++;
            associatedMap.elementCount--;
        }
    }


    private static class EntryIterator<K, V> extends AbstractMapIterator<K, V>
            implements TIterator<Entry<K, V>> {
        EntryIterator(TConcurrentHashMap<K, V> map) {
            super(map);
        }

        @Override
        public Entry<K, V> next() {
            makeNext();
            return currentEntry;
        }
    }

    private static class KeyIterator<K, V> extends AbstractMapIterator<K, V> implements TIterator<K> {
        KeyIterator(TConcurrentHashMap<K, V> map) {
            super(map);
        }

        @Override
        public K next() {
            makeNext();
            return currentEntry.key;
        }
    }

    private static class ValueIterator<K, V> extends AbstractMapIterator<K, V> implements TIterator<V> {
        ValueIterator(TConcurrentHashMap<K, V> map) {
            super(map);
        }

        @Override
        public V next() {
            makeNext();
            return currentEntry.value;
        }
    }

    static class HashMapEntrySet<K, V> extends TAbstractSet<Entry<K, V>> {
        private final TConcurrentHashMap<K, V> associatedMap;

        public HashMapEntrySet(TConcurrentHashMap<K, V> hm) {
            associatedMap = hm;
        }

        TConcurrentHashMap<K, V> hashMap() {
            return associatedMap;
        }

        @Override
        public int size() {
            return associatedMap.elementCount;
        }

        @Override
        public void clear() {
            associatedMap.clear();
        }

        @Override
        public boolean remove(Object object) {
            if (object instanceof TMap.Entry) {
                Entry<?, ?> oEntry = (Entry<?, ?>) object;
                HashEntry<K, V> entry = associatedMap.getEntryByKeyAndValue(oEntry.getKey(), oEntry.getValue());
                if (entry != null) {
                    associatedMap.removeEntry(entry);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean contains(Object object) {
            if (object instanceof TMap.Entry) {
                Entry<?, ?> oEntry = (Entry<?, ?>) object;
                return associatedMap.getEntryByKeyAndValue(oEntry.getKey(), oEntry.getValue()) != null;
            }
            return false;
        }

        @Override
        public TIterator<Entry<K, V>> iterator() {
            return new EntryIterator<>(associatedMap);
        }
    }

    @SuppressWarnings("unchecked")
    HashEntry<K, V>[] newElementArray(int s) {
        return new HashEntry[s];
    }

    public TConcurrentHashMap() {
        this(DEFAULT_SIZE);
    }

    public TConcurrentHashMap(int capacity) {
        this(capacity, 0.75f);  // default load factor of 0.75
    }

    private static int calculateCapacity(int x) {
        if (x >= 1 << 30) {
            return 1 << 30;
        }
        if (x == 0) {
            return 16;
        }
        x = x - 1;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return x + 1;
    }

    public TConcurrentHashMap(int capacity, float loadFactor) {
        if (capacity >= 0 && loadFactor > 0) {
            capacity = calculateCapacity(capacity);
            elementCount = 0;
            elementData = newElementArray(capacity);
            this.loadFactor = loadFactor;
            computeThreshold();
        } else {
            throw new TIllegalArgumentException();
        }
    }

    public TConcurrentHashMap(TMap<? extends K, ? extends V> map) {
        this(calculateCapacity(map.size()));
        putAllImpl(map);
    }

    @Override
    public void clear() {
        if (elementCount > 0) {
            elementCount = 0;
            Arrays.fill(elementData, null);
            modCount++;
            version++;
        }
    }

    @Rename("clone")
    @SuppressWarnings("unchecked")
    public TObject clone0() {
        try {
            TConcurrentHashMap<K, V> map = (TConcurrentHashMap<K, V>) super.clone();
            map.elementCount = 0;
            map.elementData = newElementArray(elementData.length);
            map.putAll(this);

            return map;
        } catch (TCloneNotSupportedException e) {
            return null;
        }
    }

    private void computeThreshold() {
        threshold = (int) (elementData.length * loadFactor);
    }

    @Override
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        repeatTable: do {
            int lock = version;

            if (value != null) {
                for (int i = 0; i < elementData.length; i++) {
                    repeatEntry: do {
                        HashEntry<K, V> first = elementData[i];
                        HashEntry<K, V> entry = first;
                        if (entry == null) {
                            break;
                        }

                        int elementLock = entry.version;
                        while (entry != null) {
                            boolean equal = areEqualValues(value, entry.value);
                            if (lock != version) {
                                continue repeatTable;
                            }
                            if (equal) {
                                if (first != elementData[i] || elementLock != first.version) {
                                    continue repeatEntry;
                                }
                                return true;
                            }
                            entry = entry.next;
                        }
                    } while (true);
                }
            } else {
                for (int i = 0; i < elementData.length; i++) {
                    HashEntry<K, V> entry = elementData[i];
                    while (entry != null) {
                        if (entry.value == null) {
                            return true;
                        }
                        entry = entry.next;
                    }
                }
            }
            return false;
        } while (true);
    }

    @Override
    public TSet<Entry<K, V>> entrySet() {
        return new HashMapEntrySet<>(this);
    }

    @Override
    public V get(Object key) {
        HashEntry<K, V> m = getEntry(key);
        if (m != null) {
            return m.value;
        }
        return null;
    }

    final HashEntry<K, V> getEntry(Object key) {
        return getEntry(key, key != null ? computeHashCode(key) : 0);
    }

    final HashEntry<K, V> getEntry(Object key, int hash) {
        if (key == null) {
            return findNullKeyEntry();
        } else {
            repeatTable:
            do {
                int lock = version;
                int index = hash & (elementData.length - 1);

                repeatElement:
                do {
                    HashEntry<K, V> first = elementData[index];
                    if (first == null) {
                        return null;
                    }
                    HashEntry<K, V> m = first;
                    int elementLock = first.version;

                    while (m != null) {
                        if (m.origKeyHash == hash) {
                            boolean equal = areEqualKeys(key, m.key);
                            if (version != lock) {
                                continue repeatTable;
                            }
                            if (equal) {
                                if (first != elementData[index] || first.version != elementLock) {
                                    continue repeatElement;
                                }
                                return m;
                            }
                        }
                        m = m.next;
                    }

                    return null;
                } while (true);
            } while (true);
        }
    }

    final HashEntry<K, V> getEntryByKeyAndValue(Object key, Object value) {
        if (key == null) {
            return findNullKeyEntry();
        } else {
            int hash = computeHashCode(key);
            repeatTable:
            do {
                int lock = version;
                int index = hash & (elementData.length - 1);

                repeatElement:
                do {
                    HashEntry<K, V> first = elementData[index];
                    if (first == null) {
                        return null;
                    }
                    HashEntry<K, V> m = first;
                    int elementLock = first.version;

                    while (m != null) {
                        if (m.origKeyHash == hash) {
                            boolean equal = areEqualKeys(key, m.key);
                            if (version != lock) {
                                continue repeatTable;
                            }
                            if (equal) {
                                if (first != elementData[index] || first.version != elementLock) {
                                    continue repeatElement;
                                }
                                equal = areEqualValues(value, m.value);
                                if (version != lock) {
                                    continue repeatTable;
                                }
                                if (first != elementData[index] || first.version != elementLock) {
                                    continue repeatElement;
                                }
                                return equal ? m : null;
                            }
                        }
                        m = m.next;
                    }

                    return null;
                } while (true);
            } while (true);
        }
    }

    final HashEntry<K, V> findNullKeyEntry() {
        HashEntry<K, V> m = elementData[0];
        while (m != null && m.key != null) {
            m = m.next;
        }
        return m;
    }

    @Override
    public boolean isEmpty() {
        return elementCount == 0;
    }

    @Override
    public TSet<K> keySet() {
        if (cachedKeySet == null) {
            cachedKeySet = new TAbstractSet<K>() {
                @Override public boolean contains(Object object) {
                    return containsKey(object);
                }
                @Override public int size() {
                    return TConcurrentHashMap.this.size();
                }
                @Override public void clear() {
                    TConcurrentHashMap.this.clear();
                }
                @Override public boolean remove(Object key) {
                    HashEntry<K, V> entry = TConcurrentHashMap.this.getEntry(key);
                    if (entry != null) {
                        TConcurrentHashMap.this.removeEntry(entry);
                        return true;
                    }
                    return false;
                }
                @Override public TIterator<K> iterator() {
                    return new KeyIterator<>(TConcurrentHashMap.this);
                }
            };
        }
        return cachedKeySet;
    }

    @Override
    public V put(K key, V value) {
        return putImpl(key, value);
    }

    V putImpl(K key, V value) {
        int hash = key != null ? computeHashCode(key) : 0;
        HashEntry<K, V> entry = getEntry(key, hash);
        int index = hash & (elementData.length - 1);

        if (entry == null) {
            entry = createHashedEntry(key, index, hash);
            modCount++;
            if (++elementCount > threshold) {
                rehash();
            }
        }

        V result = entry.value;
        entry.value = value;
        return result;
    }

    HashEntry<K, V> createHashedEntry(K key, int index, int hash) {
        HashEntry<K, V> entry = new HashEntry<>(key, hash);
        entry.next = elementData[index];
        elementData[index] = entry;
        return entry;
    }

    @Override
    public void putAll(TMap<? extends K, ? extends V> map) {
        if (!map.isEmpty()) {
            putAllImpl(map);
        }
    }

    private void putAllImpl(TMap<? extends K, ? extends V> map) {
        int capacity = elementCount + map.size();
        if (capacity > threshold) {
            rehash(capacity);
        }
        for (TIterator<? extends Entry<? extends K, ? extends V>> iter = map.entrySet().iterator(); iter.hasNext();) {
            Entry<? extends K, ? extends V> entry = iter.next();
            putImpl(entry.getKey(), entry.getValue());
        }
    }

    void rehash(int capacity) {
        int length = calculateCapacity(capacity == 0 ? 1 : capacity << 1);

        HashEntry<K, V>[] newData = newElementArray(length);
        for (int i = 0; i < elementData.length; i++) {
            HashEntry<K, V> entry = elementData[i];
            elementData[i] = null;
            while (entry != null) {
                int index = entry.origKeyHash & (length - 1);
                HashEntry<K, V> next = entry.next;
                entry.next = newData[index];
                newData[index] = entry;
                entry = next;
            }
        }
        elementData = newData;
        computeThreshold();
        version++;
    }

    void rehash() {
        rehash(elementData.length);
    }

    @Override
    public V remove(Object key) {
        HashEntry<K, V> entry = getEntry(key);
        if (entry == null) {
            return null;
        }
        removeEntry(entry);
        return entry.value;
    }

    final void removeEntry(HashEntry<K, V> entry) {
        int index = entry.origKeyHash & (elementData.length - 1);
        HashEntry<K, V> m = elementData[index];
        if (m == entry) {
            elementData[index] = entry.next;
        } else {
            m.version++;
            while (m.next != entry) {
                m = m.next;
            }
            m.next = entry.next;
        }
        modCount++;
        elementCount--;
    }

    @Override
    public int size() {
        return elementCount;
    }

    @Override
    public TCollection<V> values() {
        if (cachedValues == null) {
            cachedValues = new TAbstractCollection<V>() {
                @Override public boolean contains(Object object) {
                    return containsValue(object);
                }
                @Override public int size() {
                    return TConcurrentHashMap.this.size();
                }
                @Override public void clear() {
                    TConcurrentHashMap.this.clear();
                }
                @Override public TIterator<V> iterator() {
                    return new ValueIterator<>(TConcurrentHashMap.this);
                }
            };
        }
        return cachedValues;
    }

    static int computeHashCode(Object key) {
        return key.hashCode();
    }

    static boolean areEqualKeys(Object key1, Object key2) {
        return (key1 == key2) || key1.equals(key2);
    }

    static boolean areEqualValues(Object value1, Object value2) {
        return (value1 == value2) || value1.equals(value2);
    }
}
