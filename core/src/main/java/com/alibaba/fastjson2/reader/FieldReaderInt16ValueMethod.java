package com.alibaba.fastjson2.reader;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONSchema;
import com.alibaba.fastjson2.util.TypeUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Locale;

final class FieldReaderInt16ValueMethod<T> extends FieldReaderObjectMethod<T> {
    FieldReaderInt16ValueMethod(String fieldName, Type fieldType, Class fieldClass, int ordinal, long features, String format, Locale locale, Short defaultValue, JSONSchema schema, Method setter) {
        super(fieldName, fieldType, fieldClass, ordinal, features, format, locale, defaultValue, schema, setter);
    }

    @Override
    public void readFieldValue(JSONReader jsonReader, T object) {
        int fieldInt = jsonReader.readInt32Value();

        if (schema != null) {
            schema.assertValidate(fieldInt);
        }

        try {
            method.invoke(object, (short) fieldInt);
        } catch (Exception e) {
            throw new JSONException("set " + fieldName + " error", e);
        }
    }

    @Override
    public void accept(T object, Object value) {
        if (value == null) {
            value = 0;
        }

        if (schema != null) {
            schema.assertValidate(value);
        }

        try {
            method.invoke(object
                    , TypeUtils.toShortValue(value));
        } catch (Exception e) {
            throw new JSONException("set " + fieldName + " error", e);
        }
    }

    @Override
    public void accept(T object, long value) {
        try {
            method.invoke(object, (short) value);
        } catch (Exception e) {
            throw new JSONException("set " + fieldName + " error", e);
        }
    }

    @Override
    public Object readFieldValue(JSONReader jsonReader) {
        return jsonReader.readInt32Value();
    }
}
