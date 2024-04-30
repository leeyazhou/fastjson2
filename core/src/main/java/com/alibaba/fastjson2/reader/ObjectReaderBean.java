package com.alibaba.fastjson2.reader;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.filter.ExtraProcessor;
import com.alibaba.fastjson2.function.Function;
import com.alibaba.fastjson2.function.Supplier;
import com.alibaba.fastjson2.util.Fnv;
import com.alibaba.fastjson2.util.TypeUtils;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.List;

import static com.alibaba.fastjson2.JSONReader.Feature.IgnoreAutoTypeNotMatch;
import static com.alibaba.fastjson2.JSONReader.Feature.SupportAutoType;

public abstract class ObjectReaderBean<T>
        implements ObjectReader<T> {
    protected final Class objectClass;
    protected final Supplier<T> creator;
    protected final Function buildFunction;
    protected final long features;
    private String typeName;
    private long typeNameHash;

    protected FieldReader extraFieldReader;

    protected boolean hasDefaultValue;
    protected final boolean serializable;

    protected JSONReader.AutoTypeBeforeHandler autoTypeBeforeHandler;

    protected ObjectReaderBean(
            Class objectClass,
            Supplier<T> creator,
            String typeName,
            long features,
            Function buildFunction
    ) {
        this.objectClass = objectClass;
        this.creator = creator;
        this.buildFunction = buildFunction;
        this.features = features;
        this.typeName = typeName;
        this.typeNameHash = typeName != null ? Fnv.hashCode64(typeName) : 0;

        this.serializable = objectClass != null && Serializable.class.isAssignableFrom(objectClass);
    }

    protected String getTypeName() {
        if (typeName == null) {
            if (objectClass != null) {
                typeName = TypeUtils.getTypeName(objectClass);
            }
        }
        return typeName;
    }

    protected long getTypeNameHash() {
        if (typeNameHash == 0) {
            String typeName = this.getTypeName();
            if (typeName != null) {
                this.typeNameHash = Fnv.hashCode64(typeName);
            }
        }
        return typeNameHash;
    }

    @Override
    public Class<T> getObjectClass() {
        return objectClass;
    }

    protected T processObjectInputSingleItemArray(
            JSONReader jsonReader,
            Type fieldType,
            Object fieldName,
            long features
    ) {
        String message = "expect {, but [, class " + this.typeName;
        if (fieldName != null) {
            message += ", parent fieldName " + fieldName;
        }
        String info = jsonReader.info(message);

        long featuresAll = jsonReader.features(features);
        if ((featuresAll & JSONReader.Feature.SupportSmartMatch.mask) != 0) {
            Type itemType = fieldType == null ? this.objectClass : fieldType;
            List list = jsonReader.readArray(itemType);
            if (list.size() == 1) {
                return (T) list.get(0);
            }

            if (list != null) {
                if (list.size() == 0) {
                    return null;
                }
                if (list.size() == 1) {
                    return (T) list.get(0);
                }
            }
        }
        throw new JSONException(info);
    }

    protected void processExtra(JSONReader jsonReader, Object object) {
        processExtra(jsonReader, object, 0);
    }

    protected void processExtra(JSONReader jsonReader, Object object, long features) {
        if (extraFieldReader != null && object != null) {
            extraFieldReader.processExtra(jsonReader, object);
            return;
        }

        if ((jsonReader.features(features) & JSONReader.Feature.SupportSmartMatch.mask) != 0) {
            String fieldName = jsonReader.getFieldName();
            if (fieldName.startsWith("is", 0)) {
                String fieldName1 = fieldName.substring(2);
                long hashCode64LCase = Fnv.hashCode64LCase(fieldName1);
                FieldReader fieldReader = getFieldReaderLCase(hashCode64LCase);
                if (fieldReader != null && fieldReader.fieldClass == Boolean.class) {
                    fieldReader.readFieldValue(jsonReader, object);
                    return;
                }
            }
        }

        ExtraProcessor extraProcessor = jsonReader.context.extraProcessor;
        if (extraProcessor != null) {
            String fieldName = jsonReader.getFieldName();
            Type type = extraProcessor.getType(fieldName);
            Object extraValue = jsonReader.read(type);
            extraProcessor.processExtra(object, fieldName, extraValue);
            return;
        }

        if ((jsonReader.features(features) & JSONReader.Feature.ErrorOnUnknownProperties.mask) != 0) {
            throw new JSONException("Unknown Property " + jsonReader.getFieldName());
        }

        jsonReader.skipValue();
    }

    public void acceptExtra(Object object, String fieldName, Object fieldValue, long features) {
        if (extraFieldReader == null || object == null) {
            if ((features & JSONReader.Feature.ErrorOnUnknownProperties.mask) != 0) {
                throw new JSONException("Unknown Property " + fieldName);
            }

            return;
        }
        extraFieldReader.acceptExtra(object, fieldName, fieldValue);
    }

    public final ObjectReader checkAutoType(JSONReader jsonReader, Class expectClass, long features) {
        if (jsonReader.nextIfMatchTypedAny()) {
            long typeHash = jsonReader.readTypeHashCode();
            JSONReader.Context context = jsonReader.context;
            long features3 = jsonReader.features(features | this.features);
            JSONReader.AutoTypeBeforeHandler autoTypeFilter = context.getContextAutoTypeBeforeHandler();
            if (autoTypeFilter != null) {
                Class<?> filterClass = autoTypeFilter.apply(typeHash, expectClass, features);
                if (filterClass == null) {
                    String typeName = jsonReader.getString();
                    filterClass = autoTypeFilter.apply(typeName, expectClass, features);

                    if (filterClass != null && !expectClass.isAssignableFrom(filterClass)) {
                        if ((jsonReader.features(features) & IgnoreAutoTypeNotMatch.mask) == 0) {
                            throw new JSONException("type not match. " + typeName + " -> " + expectClass.getName());
                        }

                        filterClass = expectClass;
                    }
                }

                return context.getObjectReader(filterClass);
            }

            ObjectReader autoTypeObjectReader = jsonReader.getObjectReaderAutoType(typeHash, expectClass, features);

            if (autoTypeObjectReader == null) {
                throw new JSONException(jsonReader.info("auotype not support"));
            }

            Class autoTypeObjectReaderClass = autoTypeObjectReader.getObjectClass();
            if (expectClass != null
                    && autoTypeObjectReaderClass != null
                    && !expectClass.isAssignableFrom(autoTypeObjectReaderClass)) {
                if ((features3 & IgnoreAutoTypeNotMatch.mask) != 0) {
                    return context.getObjectReader(expectClass);
                }

                throw new JSONException("type not match. " + typeName + " -> " + expectClass.getName());
            }

            if (typeHash == this.getTypeNameHash()) {
                return this;
            }

            if ((features3 & SupportAutoType.mask) == 0) {
                return null;
            }

            return autoTypeObjectReader;
        }
        return null;
    }

    protected void initDefaultValue(T object) {
    }

    public void readObject(JSONReader jsonReader, Object object, long features) {
        if (jsonReader.nextIfNull()) {
            jsonReader.nextIfComma();
            return;
        }

        boolean objectStart = jsonReader.nextIfObjectStart();
        if (!objectStart) {
            throw new JSONException(jsonReader.info());
        }

        while (true) {
            if (jsonReader.nextIfObjectEnd()) {
                break;
            }

            long hash = jsonReader.readFieldNameHashCode();
            FieldReader fieldReader = getFieldReader(hash);
            if (fieldReader == null && jsonReader.isSupportSmartMatch(features | getFeatures())) {
                long nameHashCodeLCase = jsonReader.getNameHashCodeLCase();
                fieldReader = getFieldReaderLCase(nameHashCodeLCase);
            }

            if (fieldReader == null) {
                processExtra(jsonReader, object, features);
                continue;
            }

            fieldReader.readFieldValue(jsonReader, object);
        }

        jsonReader.nextIfComma();
    }

    @Override
    public T readObject(JSONReader jsonReader, Type fieldType, Object fieldName, long features) {
        if (jsonReader.jsonb) {
            return readJSONBObject(jsonReader, fieldType, fieldName, features);
        }

        if (jsonReader.nextIfNullOrEmptyString()) {
            jsonReader.nextIfComma();
            return null;
        }

        long featuresAll = jsonReader.features(this.getFeatures() | features);
        if (jsonReader.isArray()) {
            if ((featuresAll & JSONReader.Feature.SupportArrayToBean.mask) != 0) {
                return readArrayMappingObject(jsonReader, fieldType, fieldName, features);
            }

            return processObjectInputSingleItemArray(jsonReader, fieldType, fieldName, featuresAll);
        }

        T object = null;
        boolean objectStart = jsonReader.nextIfObjectStart();
        if (!objectStart) {
            char ch = jsonReader.current();
            // skip for fastjson 1.x compatible
            if (ch == 't' || ch == 'f') {
                jsonReader.readBoolValue(); // skip
                return null;
            }

            if (ch != '"' && ch != '\'' && ch != '}') {
                throw new JSONException(jsonReader.info());
            }
        }

        for (int i = 0; ; i++) {
            if (jsonReader.nextIfObjectEnd()) {
                if (object == null) {
                    object = createInstance(jsonReader.context.features | features);
                    if (object != null && (featuresAll & JSONReader.Feature.InitStringFieldAsEmpty.mask) != 0) {
                        initStringFieldAsEmpty(object);
                    }
                }
                break;
            }

            JSONReader.Context context = jsonReader.context;
            long features3, hash = jsonReader.readFieldNameHashCode();
            JSONReader.AutoTypeBeforeHandler autoTypeFilter = this.autoTypeBeforeHandler;
            if (autoTypeFilter == null) {
                autoTypeFilter = context.getContextAutoTypeBeforeHandler();
            }

            if (i == 0
                    && hash == getTypeKeyHash()
                    && ((((features3 = (features | getFeatures() | context.features)) & SupportAutoType.mask) != 0) || autoTypeFilter != null)
            ) {
                ObjectReader reader = null;

                long typeHash = jsonReader.readTypeHashCode();
                if (autoTypeFilter != null) {
                    Class<?> filterClass = autoTypeFilter.apply(typeHash, objectClass, features3);
                    if (filterClass == null) {
                        filterClass = autoTypeFilter.apply(jsonReader.getString(), objectClass, features3);
                        if (filterClass != null) {
                            reader = context.getObjectReader(filterClass);
                        }
                    }
                }

                if (reader == null) {
                    reader = autoType(context, typeHash);
                }

                String typeName = null;
                if (reader == null) {
                    typeName = jsonReader.getString();
                    reader = context.getObjectReaderAutoType(
                            typeName, objectClass, features3
                    );

                    if (reader == null) {
                        throw new JSONException(jsonReader.info("No suitable ObjectReader found for" + typeName));
                    }
                }

                if (reader == this) {
                    continue;
                }

                FieldReader fieldReader = reader.getFieldReader(hash);
                if (fieldReader != null && typeName == null) {
                    typeName = jsonReader.getString();
                }

                object = (T) reader.readObject(
                        jsonReader, null, null, features | getFeatures()
                );

                if (fieldReader != null) {
                    fieldReader.accept(object, typeName);
                }

                return object;
            }

            FieldReader fieldReader = getFieldReader(hash);
            if (fieldReader == null && jsonReader.isSupportSmartMatch(features | getFeatures())) {
                long nameHashCodeLCase = jsonReader.getNameHashCodeLCase();
                fieldReader = getFieldReaderLCase(nameHashCodeLCase);
            }

            if (object == null) {
                object = createInstance(jsonReader.context.features | features);
            }

            if (fieldReader == null) {
                processExtra(jsonReader, object, features);
                continue;
            }

            fieldReader.readFieldValue(jsonReader, object);
        }

        jsonReader.nextIfComma();

        Function buildFunction = getBuildFunction();
        if (buildFunction != null) {
            object = (T) buildFunction.apply(object);
        }

        return object;
    }

    protected void initStringFieldAsEmpty(Object object) {
    }

    public JSONReader.AutoTypeBeforeHandler getAutoTypeBeforeHandler() {
        return autoTypeBeforeHandler;
    }

    public void setAutoTypeBeforeHandler(JSONReader.AutoTypeBeforeHandler autoTypeBeforeHandler) {
        this.autoTypeBeforeHandler = autoTypeBeforeHandler;
    }

    protected boolean readFieldValueWithLCase(
            JSONReader jsonReader,
            Object object,
            long hashCode64,
            long features2
    ) {
        if (jsonReader.isSupportSmartMatch(features2)) {
            long hashCode64L = jsonReader.getNameHashCodeLCase();
            if (hashCode64L != hashCode64) {
                com.alibaba.fastjson2.reader.FieldReader fieldReader
                        = this.getFieldReaderLCase(hashCode64L);
                if (fieldReader != null) {
                    fieldReader.readFieldValue(jsonReader, object);
                    return true;
                }
            }
        }

        return false;
    }
}
