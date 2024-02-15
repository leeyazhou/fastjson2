package com.alibaba.fastjson2.reader;

import com.alibaba.fastjson2.JSONReader;

import java.util.Locale;

final class FieldReaderInt64Param<T>
        extends FieldReaderObjectParam<T> {
    FieldReaderInt64Param(
            String fieldName,
            Class fieldType,
            String paramName,
            int ordinal,
            long features,
            String format,
            Locale locale,
            Object defaultValue
    ) {
        super(fieldName, fieldType, fieldType, paramName, ordinal, features, format, locale, defaultValue);
    }

    @Override
    public Object readFieldValue(JSONReader jsonReader) {
        return jsonReader.readInt64();
    }
}
