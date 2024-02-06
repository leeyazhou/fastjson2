package com.alibaba.fastjson2.reader;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.util.TypeUtils;

import java.lang.reflect.Field;

final class FieldReaderInt16ValueField<T>
        extends FieldReader<T> {
    FieldReaderInt16ValueField(String fieldName, Class fieldType, int ordinal, long features, String format, Short defaultValue, Field field) {
        super(fieldName, fieldType, fieldType, ordinal, features, format, null, defaultValue, null, field);
    }

    @Override
    public void readFieldValue(JSONReader jsonReader, T object) {
        int fieldInt = jsonReader.readInt32Value();
        try {
            field.setShort(object, (short) fieldInt);
        } catch (Exception e) {
            throw new JSONException(jsonReader.info("set " + fieldName + " error"), e);
        }
    }

    @Override
    public void accept(T object, float value) {
        accept(object, Short.valueOf((short) value));
    }

    @Override
    public void accept(T object, double value) {
        accept(object, Short.valueOf((short) value));
    }

    @Override
    public void accept(T object, Object value) {
        short shortValue = TypeUtils.toShortValue(value);
        try {
            field.setShort(object, shortValue);
        } catch (Exception e) {
            throw new JSONException("set " + fieldName + " error", e);
        }
    }

    @Override
    public void accept(T object, int value) {
        try {
            field.setShort(object, (short) value);
        } catch (Exception e) {
            throw new JSONException("set " + fieldName + " error", e);
        }
    }

    @Override
    public void accept(T object, long value) {
        try {
            field.setShort(object, (short) value);
        } catch (Exception e) {
            throw new JSONException("set " + fieldName + " error", e);
        }
    }

    @Override
    public Object readFieldValue(JSONReader jsonReader) {
        return (short) jsonReader.readInt32Value();
    }
}
