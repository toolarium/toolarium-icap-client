/*
 * ICAPHeaderMap.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;


/**
 * Header map which keeps the insertion order but looks up keys case-insensitive (RFC 3507 §4.3 / RFC 7230 §3.2:
 * header field names are case-insensitive). The first spelling of a key is kept.
 *
 * @author Patrick Meier
 */
public class ICAPHeaderMap extends LinkedHashMap<String, List<String>> {
    private static final long serialVersionUID = 4483172957520361347L;
    private final Map<String, String> keyIndex;


    /**
     * Constructor for ICAPHeaderMap
     */
    public ICAPHeaderMap() {
        super();
        keyIndex = new HashMap<>();
    }


    /**
     * Constructor for ICAPHeaderMap
     *
     * @param headers the headers to copy
     */
    public ICAPHeaderMap(Map<String, List<String>> headers) {
        this();
        if (headers != null) {
            putAll(headers);
        }
    }


    /**
     * @see java.util.HashMap#put(java.lang.Object, java.lang.Object)
     */
    @Override
    public List<String> put(String key, List<String> value) {
        if (key == null) {
            return super.put(key, value);
        }

        String normalizedKey = normalize(key);
        String storedKey = keyIndex.get(normalizedKey);
        if (storedKey != null && super.containsKey(storedKey)) {
            return super.put(storedKey, value);
        }

        keyIndex.put(normalizedKey, key);
        return super.put(key, value);
    }


    /**
     * @see java.util.HashMap#putAll(java.util.Map)
     */
    @Override
    public void putAll(Map<? extends String, ? extends List<String>> m) {
        for (Map.Entry<? extends String, ? extends List<String>> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }


    /**
     * @see java.util.LinkedHashMap#get(java.lang.Object)
     */
    @Override
    public List<String> get(Object key) {
        return super.get(resolveKey(key));
    }


    /**
     * @see java.util.LinkedHashMap#getOrDefault(java.lang.Object, java.lang.Object)
     */
    @Override
    public List<String> getOrDefault(Object key, List<String> defaultValue) {
        return super.getOrDefault(resolveKey(key), defaultValue);
    }


    /**
     * @see java.util.HashMap#containsKey(java.lang.Object)
     */
    @Override
    public boolean containsKey(Object key) {
        return super.containsKey(resolveKey(key));
    }


    /**
     * @see java.util.HashMap#remove(java.lang.Object)
     */
    @Override
    public List<String> remove(Object key) {
        Object storedKey = resolveKey(key);
        if (key instanceof String) {
            keyIndex.remove(normalize((String) key));
        }
        return super.remove(storedKey);
    }


    /**
     * @see java.util.HashMap#remove(java.lang.Object, java.lang.Object)
     */
    @Override
    public boolean remove(Object key, Object value) {
        if (!containsKey(key) || !Objects.equals(get(key), value)) {
            return false;
        }
        remove(key);
        return true;
    }


    /**
     * @see java.util.LinkedHashMap#clear()
     */
    @Override
    public void clear() {
        keyIndex.clear();
        super.clear();
    }


    /**
     * @see java.util.HashMap#putIfAbsent(java.lang.Object, java.lang.Object)
     */
    @Override
    public List<String> putIfAbsent(String key, List<String> value) {
        List<String> current = get(key);
        if (current == null) {
            put(key, value);
        }
        return current;
    }


    /**
     * @see java.util.HashMap#computeIfAbsent(java.lang.Object, java.util.function.Function)
     */
    @Override
    public List<String> computeIfAbsent(String key, Function<? super String, ? extends List<String>> mappingFunction) {
        List<String> current = get(key);
        if (current != null) {
            return current;
        }

        List<String> value = mappingFunction.apply(key);
        if (value != null) {
            put(key, value);
        }
        return value;
    }


    /**
     * @see java.util.HashMap#computeIfPresent(java.lang.Object, java.util.function.BiFunction)
     */
    @Override
    public List<String> computeIfPresent(String key, BiFunction<? super String, ? super List<String>, ? extends List<String>> remappingFunction) {
        List<String> current = get(key);
        if (current == null) {
            return null;
        }

        List<String> value = remappingFunction.apply(key, current);
        if (value == null) {
            remove(key);
        } else {
            put(key, value);
        }
        return value;
    }


    /**
     * @see java.util.HashMap#compute(java.lang.Object, java.util.function.BiFunction)
     */
    @Override
    public List<String> compute(String key, BiFunction<? super String, ? super List<String>, ? extends List<String>> remappingFunction) {
        List<String> value = remappingFunction.apply(key, get(key));
        if (value == null) {
            remove(key);
        } else {
            put(key, value);
        }
        return value;
    }


    /**
     * @see java.util.HashMap#merge(java.lang.Object, java.lang.Object, java.util.function.BiFunction)
     */
    @Override
    public List<String> merge(String key, List<String> value, BiFunction<? super List<String>, ? super List<String>, ? extends List<String>> remappingFunction) {
        List<String> current = get(key);
        List<String> newValue = value;
        if (current != null) {
            newValue = remappingFunction.apply(current, value);
        }

        if (newValue == null) {
            remove(key);
        } else {
            put(key, newValue);
        }
        return newValue;
    }


    /**
     * @see java.util.HashMap#replace(java.lang.Object, java.lang.Object)
     */
    @Override
    public List<String> replace(String key, List<String> value) {
        if (!containsKey(key)) {
            return null;
        }
        return put(key, value);
    }


    /**
     * @see java.util.HashMap#replace(java.lang.Object, java.lang.Object, java.lang.Object)
     */
    @Override
    public boolean replace(String key, List<String> oldValue, List<String> newValue) {
        if (!containsKey(key) || !Objects.equals(get(key), oldValue)) {
            return false;
        }
        put(key, newValue);
        return true;
    }


    /**
     * Resolve the stored key of a given key
     *
     * @param key the key
     * @return the stored key or the key itself
     */
    private Object resolveKey(Object key) {
        if (key instanceof String) {
            String storedKey = keyIndex.get(normalize((String) key));
            if (storedKey != null) {
                return storedKey;
            }
        }
        return key;
    }


    /**
     * Normalize a key
     *
     * @param key the key
     * @return the normalized key
     */
    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
