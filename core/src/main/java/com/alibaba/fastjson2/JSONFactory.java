package com.alibaba.fastjson2;

import com.alibaba.fastjson2.filter.Filter;
import com.alibaba.fastjson2.function.Function;
import com.alibaba.fastjson2.function.Supplier;
import com.alibaba.fastjson2.reader.*;
import com.alibaba.fastjson2.time.ZoneId;
import com.alibaba.fastjson2.util.NameCacheEntry;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public final class JSONFactory {
    static long defaultReaderFeatures;
    static String defaultReaderFormat;
    static ZoneId defaultReaderZoneId;

    static long defaultWriterFeatures;
    static String defaultWriterFormat;
    static ZoneId defaultWriterZoneId;

    static Supplier<Map> defaultObjectSupplier;
    static Supplier<List> defaultArraySupplier;

    static final NameCacheEntry[] NAME_CACHE = new NameCacheEntry[8192];
    static final NameCacheEntry2[] NAME_CACHE2 = new NameCacheEntry2[8192];

    static Class JSON_OBJECT_CLASS_1x;
    static Supplier JSON_OBJECT_1x_SUPPLIER;
    static Function JSON_OBJECT_1x_BUILDER;
    static Class JSON_ARRAY_CLASS_1x;
    static Supplier JSON_ARRAY_1x_SUPPLIER;
    static volatile boolean JSON_REFLECT_1x_ERROR;

    static final class NameCacheEntry2 {
        final String name;
        final long value0;
        final long value1;

        public NameCacheEntry2(String name, long value0, long value1) {
            this.name = name;
            this.value0 = value0;
            this.value1 = value1;
        }
    }

    static final BigDecimal LOW = BigDecimal.valueOf(-9007199254740991L);
    static final BigDecimal HIGH = BigDecimal.valueOf(9007199254740991L);
    static final BigInteger LOW_BIGINT = BigInteger.valueOf(-9007199254740991L);
    static final BigInteger HIGH_BIGINT = BigInteger.valueOf(9007199254740991L);

    static final char[] CA = new char[]{
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
            'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
            'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
            'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
            'w', 'x', 'y', 'z', '0', '1', '2', '3',
            '4', '5', '6', '7', '8', '9', '+', '/'
    };

    static final int[] DIGITS2 = new int[]{
            +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0,
            +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0,
            +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0,
            +0, +1, +2, +3, +4, +5, +6, +7, +8, +9, +0, +0, +0, +0, +0, +0,
            +0, 10, 11, 12, 13, 14, 15, +0, +0, +0, +0, +0, +0, +0, +0, +0,
            +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0, +0,
            +0, 10, 11, 12, 13, 14, 15
    };

    static final float[] FLOAT_10_POW = {
            1.0e0f, 1.0e1f, 1.0e2f, 1.0e3f, 1.0e4f, 1.0e5f,
            1.0e6f, 1.0e7f, 1.0e8f, 1.0e9f, 1.0e10f
    };

    static final double[] DOUBLE_10_POW = {
            1.0e0, 1.0e1, 1.0e2, 1.0e3, 1.0e4,
            1.0e5, 1.0e6, 1.0e7, 1.0e8, 1.0e9,
            1.0e10, 1.0e11, 1.0e12, 1.0e13, 1.0e14,
            1.0e15, 1.0e16, 1.0e17, 1.0e18, 1.0e19,
            1.0e20, 1.0e21, 1.0e22
    };

    static final Double DOUBLE_ZERO = Double.valueOf(0);

    static final CacheItem[] CACHE_ITEMS;

    static {
        final CacheItem[] items = new CacheItem[16];
        for (int i = 0; i < items.length; i++) {
            items[i] = new CacheItem();
        }
        CACHE_ITEMS = items;
    }

    static final int CACHE_THRESHOLD = 1024 * 1024;
    static final AtomicReferenceFieldUpdater<CacheItem, char[]> CHARS_UPDATER
            = AtomicReferenceFieldUpdater.newUpdater(CacheItem.class, char[].class, "chars");
    static final AtomicReferenceFieldUpdater<CacheItem, byte[]> BYTES_UPDATER
            = AtomicReferenceFieldUpdater.newUpdater(CacheItem.class, byte[].class, "bytes");

    static final class CacheItem {
        volatile char[] chars;
        volatile byte[] bytes;
    }

    public static final ObjectWriterProvider defaultObjectWriterProvider = new ObjectWriterProvider();
    public static final ObjectReaderProvider defaultObjectReaderProvider = new ObjectReaderProvider();

    static final ObjectReader<JSONArray> ARRAY_READER = ObjectReaderImplList.JSON_ARRAY_READER;
    static final ObjectReader<JSONObject> OBJECT_READER = ObjectReaderImplMap.INSTANCE_OBJECT;

    static final char[] UUID_LOOKUP;
    static final byte[] UUID_VALUES;

    static {
        UUID_LOOKUP = new char[256];
        UUID_VALUES = new byte['f' + 1 - '0'];
        for (int i = 0; i < 256; i++) {
            int hi = (i >> 4) & 15;
            int lo = i & 15;
            UUID_LOOKUP[i] = (char) (((hi < 10 ? '0' + hi : 'a' + hi - 10) << 8) + (lo < 10 ? '0' + lo : 'a' + lo - 10));
        }
        for (char c = '0'; c <= '9'; c++) {
            UUID_VALUES[c - '0'] = (byte) (c - '0');
        }
        for (char c = 'a'; c <= 'f'; c++) {
            UUID_VALUES[c - '0'] = (byte) (c - 'a' + 10);
        }
        for (char c = 'A'; c <= 'F'; c++) {
            UUID_VALUES[c - '0'] = (byte) (c - 'A' + 10);
        }
    }

    /**
     * @param objectSupplier
     * @since 2.0.15
     */
    public static void setDefaultObjectSupplier(Supplier<Map> objectSupplier) {
        defaultObjectSupplier = objectSupplier;
    }

    /**
     * @param arraySupplier
     * @since 2.0.15
     */
    public static void setDefaultArraySupplier(Supplier<List> arraySupplier) {
        defaultArraySupplier = arraySupplier;
    }

    public static Supplier<Map> getDefaultObjectSupplier() {
        return defaultObjectSupplier;
    }

    public static Supplier<List> getDefaultArraySupplier() {
        return defaultArraySupplier;
    }

    public static JSONWriter.Context createWriteContext() {
        return new JSONWriter.Context(defaultObjectWriterProvider);
    }

    public static JSONWriter.Context createWriteContext(ObjectWriterProvider provider, JSONWriter.Feature... features) {
        JSONWriter.Context context = new JSONWriter.Context(provider);
        context.config(features);
        return context;
    }

    public static JSONWriter.Context createWriteContext(JSONWriter.Feature... features) {
        return new JSONWriter.Context(defaultObjectWriterProvider, features);
    }

    public static JSONReader.Context createReadContext() {
        return new JSONReader.Context(defaultObjectReaderProvider);
    }

    public static JSONReader.Context createReadContext(long features) {
        return new JSONReader.Context(defaultObjectReaderProvider, features);
    }

    public static JSONReader.Context createReadContext(JSONReader.Feature... features) {
        return new JSONReader.Context(defaultObjectReaderProvider, features);
    }

    public static JSONReader.Context createReadContext(Filter filter, JSONReader.Feature... features) {
        JSONReader.Context context = new JSONReader.Context(defaultObjectReaderProvider, features);
        context.config(filter);
        return context;
    }

    public static JSONReader.Context createReadContext(Filter[] filters, JSONReader.Feature... features) {
        JSONReader.Context context = new JSONReader.Context(defaultObjectReaderProvider, features);
        context.config(filters);
        return context;
    }

    public static JSONReader.Context createReadContext(ObjectReaderProvider provider, JSONReader.Feature... features) {
        if (provider == null) {
            provider = defaultObjectReaderProvider;
        }

        JSONReader.Context context = new JSONReader.Context(provider);
        context.config(features);
        return context;
    }

    public static JSONReader.Context createReadContext(SymbolTable symbolTable) {
        return new JSONReader.Context(defaultObjectReaderProvider, symbolTable);
    }

    public static JSONReader.Context createReadContext(SymbolTable symbolTable, JSONReader.Feature... features) {
        JSONReader.Context context = new JSONReader.Context(defaultObjectReaderProvider, symbolTable);
        context.config(features);
        return context;
    }

    public static JSONReader.Context createReadContext(Supplier<Map> objectSupplier, JSONReader.Feature... features) {
        JSONReader.Context context = new JSONReader.Context(defaultObjectReaderProvider);
        context.setObjectSupplier(objectSupplier);
        context.config(features);
        return context;
    }

    public static JSONReader.Context createReadContext(
            Supplier<Map> objectSupplier,
            Supplier<List> arraySupplier,
            JSONReader.Feature... features) {
        JSONReader.Context context = new JSONReader.Context(defaultObjectReaderProvider);
        context.setObjectSupplier(objectSupplier);
        context.setArraySupplier(arraySupplier);
        context.config(features);
        return context;
    }

    public static ObjectWriterProvider getDefaultObjectWriterProvider() {
        return defaultObjectWriterProvider;
    }

    public static ObjectReaderProvider getDefaultObjectReaderProvider() {
        return defaultObjectReaderProvider;
    }

    public static void setFastjson1x(
            Class jsonObjectClass,
            Supplier jsonObjectSuppier,
            Function jsonObjectBuilder,
            Class jsonArrayClass,
            Supplier jsonArraySupplier
    ) {
        JSON_OBJECT_CLASS_1x = jsonObjectClass;
        JSON_OBJECT_1x_SUPPLIER = jsonObjectSuppier;
        JSON_OBJECT_1x_BUILDER = jsonObjectBuilder;
        JSON_ARRAY_CLASS_1x = jsonArrayClass;
        JSON_ARRAY_1x_SUPPLIER = jsonArraySupplier;
    }

    public static Class getClassJSONObject1x() {
        if (JSON_OBJECT_CLASS_1x == null && !JSON_REFLECT_1x_ERROR) {
            try {
                JSON_OBJECT_CLASS_1x = Class.forName("com.alibaba.fastjson.JSONObject");
            } catch (ClassNotFoundException ignored) {
                JSON_REFLECT_1x_ERROR = true;
            }
        }

        return JSON_OBJECT_CLASS_1x;
    }

    public static Class getClassJSONArray1x() {
        if (JSON_ARRAY_CLASS_1x == null && !JSON_REFLECT_1x_ERROR) {
            try {
                JSON_ARRAY_CLASS_1x = Class.forName("com.alibaba.fastjson.JSONArray");
            } catch (ClassNotFoundException ignored) {
                JSON_REFLECT_1x_ERROR = true;
            }
        }

        return JSON_ARRAY_CLASS_1x;
    }

    public static Function getBuilderJSONObject1x() {
        if (JSON_OBJECT_1x_BUILDER == null && !JSON_REFLECT_1x_ERROR) {
            Class classJSONObject1x = getClassJSONObject1x();
            if (classJSONObject1x != null) {
                Constructor constructor;
                try {
                    constructor = classJSONObject1x.getConstructor(Map.class);
                } catch (NoSuchMethodException e) {
                    throw new JSONException("create JSONObject1 error");
                }
                JSON_OBJECT_1x_BUILDER = new ConstructorFunction(constructor);
            }
        }

        return JSON_OBJECT_1x_BUILDER;
    }

    private static final class ConstructorFunction
            implements Function {
        final Constructor constructor;

        ConstructorFunction(Constructor constructor) {
            this.constructor = constructor;
        }

        @Override
        public Object apply(Object arg) {
            try {
                return constructor.newInstance(arg);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new JSONException("create JSONObject1 error");
            }
        }
    }
}
