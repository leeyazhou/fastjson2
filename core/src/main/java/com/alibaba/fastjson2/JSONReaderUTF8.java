package com.alibaba.fastjson2;

import com.alibaba.fastjson2.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static com.alibaba.fastjson2.JSONFactory.*;
import static com.alibaba.fastjson2.JSONReader.Feature.ErrorOnNullForPrimitives;
import static com.alibaba.fastjson2.util.JDKUtils.ANDROID_SDK_INT;
import static java.nio.charset.StandardCharsets.UTF_8;

class JSONReaderUTF8
        extends JSONReader {
    protected final byte[] bytes;
    protected final int length;
    protected final int start;
    protected final int end;

    protected int nameBegin;
    protected int nameEnd;
    protected int nameLength;

    protected boolean nameAscii;
    protected int referenceBegin;

    protected final InputStream in;

    protected byte[] byteBuf;
    protected char[] charBuf;
    protected final CacheItem cacheItem;

    {
        int cacheIndex = System.identityHashCode(Thread.currentThread()) & (CACHE_ITEMS.length - 1);
        cacheItem = CACHE_ITEMS[cacheIndex];
    }

    JSONReaderUTF8(Context ctx, InputStream is) {
        super(ctx, false);
        byte[] bytes = BYTES_UPDATER.getAndSet(cacheItem, null);
        if (bytes == null) {
            bytes = new byte[ctx.bufferSize];
        }

        int off = 0;
        try {
            for (; ; ) {
                int n = is.read(bytes, off, bytes.length - off);
                if (n == -1) {
                    break;
                }
                off += n;

                if (off == bytes.length) {
                    bytes = Arrays.copyOf(bytes, bytes.length + ctx.bufferSize);
                }
            }
        } catch (IOException ioe) {
            throw new JSONException("read error", ioe);
        }

        this.bytes = this.byteBuf = bytes;
        this.offset = 0;
        this.length = off;
        this.in = is;
        this.start = 0;
        this.end = length;
        next();

        if (ch == '/') {
            skipComment();
        }
    }

    JSONReaderUTF8(Context ctx, ByteBuffer buffer) {
        super(ctx, false);

        byte[] bytes = BYTES_UPDATER.getAndSet(cacheItem, null);
        final int remaining = buffer.remaining();
        if (bytes == null || bytes.length < remaining) {
            bytes = new byte[remaining];
        }
        buffer.get(bytes, 0, remaining);

        this.bytes = this.byteBuf = bytes;
        this.offset = 0;
        this.length = remaining;
        this.in = null;
        this.start = 0;
        this.end = length;
        next();

        if (ch == '/') {
            skipComment();
        }
    }

    JSONReaderUTF8(Context ctx, String str, byte[] bytes, int offset, int length) {
        super(ctx, false);

        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
        this.in = null;
        this.start = offset;
        this.end = offset + length;
        next();

        if (ch == '/') {
            skipComment();
        }
    }

    private void char_utf8(int ch, int offset) {
        final byte[] bytes = this.bytes;
        ch &= 0xFF;
        switch (ch >> 4) {
            case 12:
            case 13: {
                /* 110x xxxx   10xx xxxx*/
                ch = char2_utf8(ch, bytes[offset++], offset);
                break;
            }
            case 14: {
                /* 1110 xxxx  10xx xxxx  10xx xxxx */
                ch = char2_utf8(ch, bytes[offset++], bytes[offset++], offset);
                break;
            }
            default:
                /* 10xx xxxx,  1111 xxxx */
                throw new JSONException("malformed input around byte " + offset);
        }
        this.ch = (char) ch;
        this.offset = offset;
    }

    static int char2_utf8(int ch, int char2, int offset) {
        if ((char2 & 0xC0) != 0x80) {
            throw new JSONException("malformed input around byte " + offset);
        }
        return ((ch & 0x1F) << 6) | (char2 & 0x3F);
    }

    static int char2_utf8(int ch, int char2, int char3, int offset) {
        if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
            throw new JSONException("malformed input around byte " + offset);
        }
        return (((ch & 0x0F) << 12) | ((char2 & 0x3F) << 6) | (char3 & 0x3F));
    }

    @Override
    public boolean nextIfMatch(char m) {
        final byte[] bytes = this.bytes;
        int offset = this.offset;
        int ch = this.ch;
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch != m) {
            return false;
        }

        ch = offset == end ? EOI : bytes[offset++];
        while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch < 0) {
            char_utf8(ch, offset);
            return true;
        }

        this.offset = offset;
        this.ch = (char) ch;
        if (ch == '/') {
            skipComment();
        }

        return true;
    }

    @Override
    public boolean nextIfComma() {
        final byte[] bytes = this.bytes;
        int offset = this.offset;
        int ch = this.ch;
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch != ',') {
            return false;
        }

        ch = offset == end ? EOI : bytes[offset++];
        while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch < 0) {
            char_utf8(ch, offset);
            return true;
        }

        this.offset = offset;
        this.ch = (char) ch;
        if (ch == '/') {
            skipComment();
        }

        return true;
    }

    @Override
    public boolean nextIfArrayStart() {
        int ch = this.ch;
        if (ch != '[') {
            return false;
        }

        final byte[] bytes = this.bytes;
        int offset = this.offset;
        ch = offset == end ? EOI : bytes[offset++];
        while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch < 0) {
            char_utf8(ch, offset);
            return true;
        }

        this.ch = (char) ch;
        this.offset = offset;

        if (ch == '/') {
            skipComment();
        }
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() {
        int ch = this.ch;
        byte[] bytes = this.bytes;
        int offset = this.offset;
        if (ch != ']') {
            return false;
        }

        ch = offset == end ? EOI : bytes[offset++];
        while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch == ',') {
            comma = true;
            ch = offset == end ? EOI : bytes[offset++];
            while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        if (ch < 0) {
            char_utf8(ch, offset);
            return true;
        }

        this.ch = (char) ch;
        this.offset = offset;

        if (ch == '/') {
            skipComment();
        }
        return true;
    }

    @Override
    public final boolean nextIfSet() {
        final byte[] bytes = this.bytes;
        int offset = this.offset;
        int ch = this.ch;
        if (ch == 'S'
                && offset + 1 < end
                && bytes[offset] == 'e'
                && bytes[offset + 1] == 't') {
            offset += 2;
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
            this.offset = offset;
            this.ch = (char) ch;
            return true;
        }
        return false;
    }

    @Override
    public final boolean nextIfInfinity() {
        final byte[] bytes = this.bytes;
        int offset = this.offset;
        int ch = this.ch;
        if (ch == 'I'
                && offset + 6 < end
                && bytes[offset] == 'n'
                && bytes[offset + 1] == 'f'
                && bytes[offset + 2] == 'i'
                && bytes[offset + 3] == 'n'
                && bytes[offset + 4] == 'i'
                && bytes[offset + 5] == 't'
                && bytes[offset + 6] == 'y'
        ) {
            offset += 7;
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }

            this.offset = offset;
            this.ch = (char) ch;
            return true;
        }
        return false;
    }

    public boolean nextIfObjectStart() {
        int ch = this.ch;
        if (ch != '{') {
            return false;
        }

        final byte[] bytes = this.bytes;
        int offset = this.offset;
        ch = offset == end ? EOI : bytes[offset++];
        while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch < 0) {
            char_utf8(ch, offset);
            return true;
        }

        this.ch = (char) ch;
        this.offset = offset;

        if (ch == '/') {
            skipComment();
        }
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() {
        int ch = this.ch;
        byte[] bytes = this.bytes;
        int offset = this.offset;

        if (ch != '}') {
            return false;
        }

        ch = offset == end ? EOI : bytes[offset++];
        while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch == ',') {
            comma = true;
            ch = offset == end ? EOI : bytes[offset++];
            while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        if (ch < 0) {
            char_utf8(ch, offset);
            return true;
        }

        this.ch = (char) ch;
        this.offset = offset;

        if (ch == '/') {
            skipComment();
        }
        return true;
    }

    @Override
    public void next() {
        final byte[] bytes = this.bytes;
        int offset = this.offset;
        int ch = offset >= end ? EOI : bytes[offset++];
        while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch < 0) {
            char_utf8(ch, offset);
            return;
        }

        this.offset = offset;
        this.ch = (char) ch;
        if (ch == '/') {
            skipComment();
        }
    }

    @Override
    public final long readFieldNameHashCodeUnquote() {
        this.nameEscape = false;
        this.nameBegin = this.offset - 1;
        char first = ch;

        final byte[] bytes = this.bytes;
        long nameValue = 0;
        _for:
        for (int i = 0; offset <= end; ++i) {
            switch (ch) {
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                case '\f':
                case '\b':
                case '.':
                case '-':
                case '+':
                case '*':
                case '/':
                case '>':
                case '<':
                case '=':
                case '!':
                case '[':
                case ']':
                case '{':
                case '}':
                case '(':
                case ')':
                case ',':
                case ':':
                case EOI:
                    nameLength = i;
                    this.nameEnd = ch == EOI ? offset : offset - 1;
                    while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                        next();
                    }
                    break _for;
                default:
                    break;
            }

            if (ch == '\\') {
                nameEscape = true;
                ch = (char) bytes[offset++];
                switch (ch) {
                    case 'u': {
                        ch = char4(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]);
                        offset += 4;
                        break;
                    }
                    case 'x': {
                        ch = char2(bytes[offset], bytes[offset + 1]);
                        offset += 2;
                        break;
                    }
                    case '\\':
                    case '"':
                    case '.':
                    case '-':
                    case '+':
                    case '*':
                    case '/':
                    case '>':
                    case '<':
                    case '=':
                    case '@':
                    case ':':
                        break;
                    default:
                        ch = char1(ch);
                        break;
                }
            }

            if (ch > 0x7F || i >= 8 || (i == 0 && ch == 0)) {
                nameValue = 0;
                ch = first;
                offset = this.nameBegin + 1;
                break;
            }

            byte c = (byte) ch;
            switch (i) {
                case 0:
                    nameValue = c;
                    break;
                case 1:
                    nameValue = (c << 8) + (nameValue & 0xFFL);
                    break;
                case 2:
                    nameValue = (c << 16) + (nameValue & 0xFFFFL);
                    break;
                case 3:
                    nameValue = (c << 24) + (nameValue & 0xFFFFFFL);
                    break;
                case 4:
                    nameValue = (((long) c) << 32) + (nameValue & 0xFFFFFFFFL);
                    break;
                case 5:
                    nameValue = (((long) c) << 40L) + (nameValue & 0xFFFFFFFFFFL);
                    break;
                case 6:
                    nameValue = (((long) c) << 48L) + (nameValue & 0xFFFFFFFFFFFFL);
                    break;
                case 7:
                    nameValue = (((long) c) << 56L) + (nameValue & 0xFFFFFFFFFFFFFFL);
                    break;
                default:
                    break;
            }

            ch = offset == end ? EOI : (char) bytes[offset++];
        }

        long hashCode;

        if (nameValue != 0) {
            hashCode = nameValue;
        } else {
            hashCode = Fnv.MAGIC_HASH_CODE;
            _for:
            for (int i = 0; ; ++i) {
                if (ch == '\\') {
                    nameEscape = true;
                    ch = (char) bytes[offset++];
                    switch (ch) {
                        case 'u': {
                            ch = char4(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]);
                            offset += 4;
                            break;
                        }
                        case 'x': {
                            ch = char2(bytes[offset], bytes[offset + 1]);
                            offset += 2;
                            break;
                        }
                        case '\\':
                        case '"':
                        case '.':
                        case '-':
                        case '+':
                        case '*':
                        case '/':
                        case '>':
                        case '<':
                        case '=':
                        case '@':
                        case ':':
                            break;
                        default:
                            ch = char1(ch);
                            break;
                    }

                    hashCode ^= ch;
                    hashCode *= Fnv.MAGIC_PRIME;
                    next();
                    continue;
                }

                switch (ch) {
                    case ' ':
                    case '\n':
                    case '\r':
                    case '\t':
                    case '\f':
                    case '\b':
                    case '.':
                    case '-':
                    case '+':
                    case '*':
                    case '/':
                    case '>':
                    case '<':
                    case '=':
                    case '!':
                    case '[':
                    case ']':
                    case '{':
                    case '}':
                    case '(':
                    case ')':
                    case ',':
                    case ':':
                    case EOI:
                        nameLength = i;
                        this.nameEnd = ch == EOI ? offset : offset - 1;
                        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                            next();
                        }
                        break _for;
                    default:
                        break;
                }

                hashCode ^= ch;
                hashCode *= Fnv.MAGIC_PRIME;

                ch = offset == end ? EOI : (char) bytes[offset++];
            }
        }

        if (ch == ':') {
            ch = offset == end ? EOI : (char) bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : (char) bytes[offset++];
            }
        }

        return hashCode;
    }

    @Override
    public long readFieldNameHashCode() {
        final byte[] bytes = this.bytes;
        int ch = this.ch;
        if (ch != '"' && ch != '\'') {
            if ((context.features & Feature.AllowUnQuotedFieldNames.mask) != 0 && isFirstIdentifier(ch)) {
                return readFieldNameHashCodeUnquote();
            }
            if (ch == '}' || isNull()) {
                return -1;
            }

            String errorMsg, preFieldName;
            if (ch == '[' && nameBegin > 0 && (preFieldName = getFieldName()) != null) {
                errorMsg = "illegal fieldName input " + ch + ", previous fieldName " + preFieldName;
            } else {
                errorMsg = "illegal fieldName input" + ch;
            }

            throw new JSONException(info(errorMsg));
        }

        final int quote = ch;

        this.nameAscii = true;
        this.nameEscape = false;
        int offset = this.nameBegin = this.offset;

        long nameValue = 0;

        if (offset + 9 < end) {
            byte c0, c1, c2, c3, c4, c5, c6, c7;

            if ((c0 = bytes[offset]) == quote) {
                nameValue = 0;
            } else if ((c1 = bytes[offset + 1]) == quote && c0 != '\\' && c0 > 0) {
                nameValue = c0;
                this.nameLength = 1;
                this.nameEnd = offset + 1;
                offset += 2;
            } else if ((c2 = bytes[offset + 2]) == quote
                    && c0 != '\\' && c1 != '\\'
                    && c0 >= 0 && c1 > 0
            ) {
                nameValue = (c1 << 8)
                        + c0;
                this.nameLength = 2;
                this.nameEnd = offset + 2;
                offset += 3;
            } else if ((c3 = bytes[offset + 3]) == quote
                    && c0 != '\\' && c1 != '\\' && c2 != '\\'
                    && c0 >= 0 && c1 >= 0 && c2 > 0
            ) {
                nameValue
                        = (c2 << 16)
                        + (c1 << 8)
                        + c0;
                this.nameLength = 3;
                this.nameEnd = offset + 3;
                offset += 4;
            } else if ((c4 = bytes[offset + 4]) == quote
                    && c0 != '\\' && c1 != '\\' && c2 != '\\' && c3 != '\\'
                    && c0 >= 0 && c1 >= 0 && c2 >= 0 && c3 > 0
            ) {
                nameValue
                        = (c3 << 24)
                        + (c2 << 16)
                        + (c1 << 8)
                        + c0;
                this.nameLength = 4;
                this.nameEnd = offset + 4;
                offset += 5;
            } else if ((c5 = bytes[offset + 5]) == quote
                    && c0 != '\\' && c1 != '\\' && c2 != '\\' && c3 != '\\' && c4 != '\\'
                    && c0 >= 0 && c1 >= 0 && c2 >= 0 && c3 >= 0 && c4 > 0
            ) {
                nameValue
                        = (((long) c4) << 32)
                        + (c3 << 24)
                        + (c2 << 16)
                        + (c1 << 8)
                        + c0;
                this.nameLength = 5;
                this.nameEnd = offset + 5;
                offset += 6;
            } else if ((c6 = bytes[offset + 6]) == quote
                    && c0 != '\\' && c1 != '\\' && c2 != '\\' && c3 != '\\' && c4 != '\\' && c5 != '\\'
                    && c0 >= 0 && c1 >= 0 && c2 >= 0 && c3 >= 0 && c4 >= 0 && c5 > 0
            ) {
                nameValue
                        = (((long) c5) << 40)
                        + (((long) c4) << 32)
                        + (c3 << 24)
                        + (c2 << 16)
                        + (c1 << 8)
                        + c0;
                this.nameLength = 6;
                this.nameEnd = offset + 6;
                offset += 7;
            } else if ((c7 = bytes[offset + 7]) == quote
                    && c0 != '\\' && c1 != '\\' && c2 != '\\' && c3 != '\\' && c4 != '\\' && c5 != '\\' && c6 != '\\'
                    && c0 >= 0 && c1 >= 0 && c2 >= 0 && c3 >= 0 && c4 >= 0 && c5 >= 0 && c6 > 0
            ) {
                nameValue
                        = (((long) c6) << 48)
                        + (((long) c5) << 40)
                        + (((long) c4) << 32)
                        + (c3 << 24)
                        + (c2 << 16)
                        + (c1 << 8)
                        + c0;
                this.nameLength = 7;
                this.nameEnd = offset + 7;
                offset += 8;
            } else if (bytes[offset + 8] == quote
                    && c0 != '\\' && c1 != '\\' && c2 != '\\' && c3 != '\\' && c4 != '\\' && c5 != '\\' && c6 != '\\' && c7 != '\\'
                    && c0 >= 0 && c1 >= 0 && c2 >= 0 && c3 >= 0 && c4 >= 0 && c5 >= 0 && c6 >= 0 && c7 > 0
            ) {
                nameValue
                        = (((long) c7) << 56)
                        + (((long) c6) << 48)
                        + (((long) c5) << 40)
                        + (((long) c4) << 32)
                        + (c3 << 24)
                        + (c2 << 16)
                        + (c1 << 8)
                        + c0;
                this.nameLength = 8;
                this.nameEnd = offset + 8;
                offset += 9;
            }
        }

        if (nameValue == 0) {
            for (int i = 0; offset < end; offset++, i++) {
                ch = bytes[offset];

                if (ch == quote) {
                    if (i == 0) {
                        offset = this.nameBegin;
                        break;
                    }

                    this.nameLength = i;
                    this.nameEnd = offset;
                    offset++;
                    break;
                }

                if (ch == '\\') {
                    nameEscape = true;
                    ch = bytes[++offset];
                    switch (ch) {
                        case 'u': {
                            ch = char4(bytes[offset + 1], bytes[offset + 2], bytes[offset + 3], bytes[offset + 4]);
                            offset += 4;
                            break;
                        }
                        case 'x': {
                            ch = char2(bytes[offset + 1], bytes[offset + 2]);
                            offset += 2;
                            break;
                        }
                        case '\\':
                        case '"':
                        default:
                            ch = char1(ch);
                            break;
                    }
                    if (ch > 0xFF) {
                        nameAscii = false;
                    }
                } else if (ch == -61 || ch == -62) {
                    byte c1 = bytes[++offset];
                    ch = (char) (((ch & 0x1F) << 6)
                            | (c1 & 0x3F));
                    nameAscii = false;
                }

                if (ch > 0xFF || ch < 0 || i >= 8 || (i == 0 && ch == 0)) {
                    nameValue = 0;
                    offset = this.nameBegin;
                    break;
                }

                switch (i) {
                    case 0:
                        nameValue = (byte) ch;
                        break;
                    case 1:
                        nameValue = (((byte) ch) << 8) + (nameValue & 0xFFL);
                        break;
                    case 2:
                        nameValue = (((byte) ch) << 16) + (nameValue & 0xFFFFL);
                        break;
                    case 3:
                        nameValue = (((byte) ch) << 24) + (nameValue & 0xFFFFFFL);
                        break;
                    case 4:
                        nameValue = (((long) (byte) ch) << 32) + (nameValue & 0xFFFFFFFFL);
                        break;
                    case 5:
                        nameValue = (((long) (byte) ch) << 40L) + (nameValue & 0xFFFFFFFFFFL);
                        break;
                    case 6:
                        nameValue = (((long) (byte) ch) << 48L) + (nameValue & 0xFFFFFFFFFFFFL);
                        break;
                    case 7:
                        nameValue = (((long) (byte) ch) << 56L) + (nameValue & 0xFFFFFFFFFFFFFFL);
                        break;
                    default:
                        break;
                }
            }
        }

        long hashCode;
        if (nameValue != 0) {
            hashCode = nameValue;
        } else {
            hashCode = Fnv.MAGIC_HASH_CODE;
            for (int i = 0; ; ++i) {
                ch = bytes[offset];
                if (ch == '\\') {
                    nameEscape = true;
                    ch = bytes[++offset];
                    switch (ch) {
                        case 'u': {
                            ch = char4(bytes[offset + 1], bytes[offset + 2], bytes[offset + 3], bytes[offset + 4]);
                            offset += 4;
                            break;
                        }
                        case 'x': {
                            ch = char2(bytes[offset + 1], bytes[offset + 2]);
                            offset += 2;
                            break;
                        }
                        case '\\':
                        case '"':
                        default:
                            ch = char1(ch);
                            break;
                    }
                    offset++;
                    hashCode ^= ch;
                    hashCode *= Fnv.MAGIC_PRIME;
                    continue;
                }

                if (ch == quote) {
                    this.nameLength = i;
                    this.nameEnd = offset;
                    offset++;
                    break;
                }

                if (ch >= 0) {
                    offset++;
                } else {
                    ch &= 0xFF;
                    switch (ch >> 4) {
                        case 12:
                        case 13: {
                            /* 110x xxxx   10xx xxxx*/
                            ch = char2_utf8(ch, bytes[offset + 1], offset);
                            offset += 2;
                            nameAscii = false;
                            break;
                        }
                        case 14: {
                            ch = char2_utf8(ch, bytes[offset + 1], bytes[offset + 2], offset);
                            offset += 3;
                            nameAscii = false;
                            break;
                        }
                        default:
                            /* 10xx xxxx,  1111 xxxx */
                            throw new JSONException("malformed input around byte " + offset);
                    }
                }

                hashCode ^= ch;
                hashCode *= Fnv.MAGIC_PRIME;
            }
        }

        ch = offset == end ? EOI : bytes[offset++];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch != ':') {
            throw new JSONException(info("expect ':', but " + ch));
        }

        ch = offset == end ? EOI : bytes[offset++];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        this.offset = offset;
        this.ch = (char) ch;

        return hashCode;
    }

    @Override
    public long readValueHashCode() {
        int ch = this.ch;
        if (ch != '"' && ch != '\'') {
            return -1;
        }

        final byte[] bytes = this.bytes;
        final int quote = ch;

        this.nameAscii = true;
        this.nameEscape = false;
        int offset = this.nameBegin = this.offset;

        long nameValue = 0;
        for (int i = 0; offset < end; offset++, i++) {
            ch = bytes[offset];

            if (ch == quote) {
                if (i == 0) {
                    nameValue = 0;
                    offset = this.nameBegin;
                    break;
                }

                this.nameLength = i;
                this.nameEnd = offset;
                offset++;
                break;
            }

            if (ch == '\\') {
                nameEscape = true;
                ch = bytes[++offset];
                switch (ch) {
                    case 'u': {
                        ch = char4(bytes[offset + 1], bytes[offset + 2], bytes[offset + 3], bytes[offset + 4]);
                        offset += 4;
                        break;
                    }
                    case 'x': {
                        ch = char2(bytes[offset + 1], bytes[offset + 2]);
                        offset += 2;
                        break;
                    }
                    case '\\':
                    case '"':
                    default:
                        ch = char1(ch);
                        break;
                }
            } else if (ch == -61 || ch == -62) {
                ch = (char) (((ch & 0x1F) << 6) | (bytes[++offset] & 0x3F));
            }

            if (ch > 0xFF || ch < 0 || i >= 8 || (i == 0 && ch == 0)) {
                nameValue = 0;
                offset = this.nameBegin;
                break;
            }

            switch (i) {
                case 0:
                    nameValue = (byte) ch;
                    break;
                case 1:
                    nameValue = (((byte) ch) << 8) + (nameValue & 0xFFL);
                    break;
                case 2:
                    nameValue = (((byte) ch) << 16) + (nameValue & 0xFFFFL);
                    break;
                case 3:
                    nameValue = (((byte) ch) << 24) + (nameValue & 0xFFFFFFL);
                    break;
                case 4:
                    nameValue = (((long) (byte) ch) << 32) + (nameValue & 0xFFFFFFFFL);
                    break;
                case 5:
                    nameValue = (((long) (byte) ch) << 40L) + (nameValue & 0xFFFFFFFFFFL);
                    break;
                case 6:
                    nameValue = (((long) (byte) ch) << 48L) + (nameValue & 0xFFFFFFFFFFFFL);
                    break;
                case 7:
                    nameValue = (((long) (byte) ch) << 56L) + (nameValue & 0xFFFFFFFFFFFFFFL);
                    break;
                default:
                    break;
            }
        }

        long hashCode;
        if (nameValue != 0) {
            hashCode = nameValue;
        } else {
            hashCode = Fnv.MAGIC_HASH_CODE;
            for (int i = 0; ; ++i) {
                ch = bytes[offset];
                if (ch == '\\') {
                    nameEscape = true;
                    ch = bytes[++offset];
                    switch (ch) {
                        case 'u': {
                            ch = char4(bytes[offset + 1], bytes[offset + 2], bytes[offset + 3], bytes[offset + 4]);
                            offset += 4;
                            break;
                        }
                        case 'x': {
                            ch = char2(bytes[offset + 1], bytes[offset + 2]);
                            offset += 2;
                            break;
                        }
                        case '\\':
                        case '"':
                        default:
                            ch = char1(ch);
                            break;
                    }
                    offset++;
                    hashCode ^= ch;
                    hashCode *= Fnv.MAGIC_PRIME;
                    continue;
                }

                if (ch == '"') {
                    this.nameLength = i;
                    this.nameEnd = offset;
                    offset++;
                    break;
                }

                if (ch >= 0) {
                    offset++;
                } else {
                    switch ((ch & 0xFF) >> 4) {
                        case 12:
                        case 13: {
                            /* 110x xxxx   10xx xxxx*/
                            ch = char2_utf8(ch, bytes[offset + 1], offset);
                            offset += 2;
                            nameAscii = false;
                            break;
                        }
                        case 14: {
                            ch = char2_utf8(ch, bytes[offset + 1], bytes[offset + 2], offset);
                            offset += 3;
                            nameAscii = false;
                            break;
                        }
                        default:
                            /* 10xx xxxx,  1111 xxxx */
                            if ((ch >> 3) == -2) {
                                offset++;
                                int c2 = bytes[offset++];
                                int c3 = bytes[offset++];
                                int c4 = bytes[offset++];
                                int uc = ((ch << 18) ^
                                        (c2 << 12) ^
                                        (c3 << 6) ^
                                        (c4 ^ (((byte) 0xF0 << 18) ^
                                                ((byte) 0x80 << 12) ^
                                                ((byte) 0x80 << 6) ^
                                                ((byte) 0x80))));

                                if (((c2 & 0xc0) != 0x80 || (c3 & 0xc0) != 0x80 || (c4 & 0xc0) != 0x80) // isMalformed4
                                        ||
                                        // shortest form check
                                        !(uc >= 0x010000 && uc < 0X10FFFF + 1) // !Character.isSupplementaryCodePoint(uc)
                                ) {
                                    throw new JSONException("malformed input around byte " + offset);
                                } else {
                                    char x1 = (char) ((uc >>> 10) + ('\uD800' - (0x010000 >>> 10))); // Character.highSurrogate(uc);
                                    char x2 = (char) ((uc & 0x3ff) + '\uDC00'); // Character.lowSurrogate(uc);

                                    hashCode ^= x1;
                                    hashCode *= Fnv.MAGIC_PRIME;

                                    hashCode ^= x2;
                                    hashCode *= Fnv.MAGIC_PRIME;
                                    i++;
                                }
                                continue;
                            }
                            throw new JSONException("malformed input around byte " + offset);
                    }
                }

                hashCode ^= ch;
                hashCode *= Fnv.MAGIC_PRIME;
            }
        }

        ch = offset == end ? EOI : bytes[offset++];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        this.offset = offset;
        this.ch = (char) ch;

        return hashCode;
    }

    @Override
    public long getNameHashCodeLCase() {
        long hashCode = Fnv.MAGIC_HASH_CODE;
        int offset = nameBegin;
        final byte[] bytes = this.bytes;

        long nameValue = 0;
        for (int i = 0; offset < end; offset++) {
            int ch = bytes[offset];

            if (ch == '\\') {
                ch = bytes[++offset];
                switch (ch) {
                    case 'u': {
                        ch = char4(bytes[offset + 1], bytes[offset + 2], bytes[offset + 3], bytes[offset + 4]);
                        offset += 4;
                        break;
                    }
                    case 'x': {
                        ch = char2(bytes[offset + 1], bytes[offset + 2]);
                        offset += 2;
                        break;
                    }
                    case '\\':
                    case '"':
                    default:
                        ch = char1(ch);
                        break;
                }
            } else if (ch == -61 || ch == -62) {
                ch = ((ch & 0x1F) << 6) | (bytes[++offset] & 0x3F);
            } else if (ch == '"') {
                break;
            }

            if (i >= 8 || ch > 0xFF || ch < 0 || (i == 0 && ch == 0)) {
                nameValue = 0;
                offset = this.nameBegin;
                break;
            }

            if (ch == '_' || ch == '-' || ch == ' ') {
                int c1 = bytes[offset + 1];
                if (c1 != '"' && c1 != '\'' && c1 != ch) {
                    continue;
                }
            }

            if (ch >= 'A' && ch <= 'Z') {
                ch = (char) (ch + 32);
            }

            switch (i) {
                case 0:
                    nameValue = (byte) ch;
                    break;
                case 1:
                    nameValue = (((byte) ch) << 8) + (nameValue & 0xFFL);
                    break;
                case 2:
                    nameValue = (((byte) ch) << 16) + (nameValue & 0xFFFFL);
                    break;
                case 3:
                    nameValue = (((byte) ch) << 24) + (nameValue & 0xFFFFFFL);
                    break;
                case 4:
                    nameValue = (((long) (byte) ch) << 32) + (nameValue & 0xFFFFFFFFL);
                    break;
                case 5:
                    nameValue = (((long) (byte) ch) << 40L) + (nameValue & 0xFFFFFFFFFFL);
                    break;
                case 6:
                    nameValue = (((long) (byte) ch) << 48L) + (nameValue & 0xFFFFFFFFFFFFL);
                    break;
                case 7:
                    nameValue = (((long) (byte) ch) << 56L) + (nameValue & 0xFFFFFFFFFFFFFFL);
                    break;
                default:
                    break;
            }
            ++i;
        }

        if (nameValue != 0) {
            return nameValue;
        }

        if (nameAscii && !nameEscape) {
            for (int i = nameBegin; i < nameEnd; ++i) {
                int ch = bytes[i];
                if (ch >= 'A' && ch <= 'Z') {
                    ch = ch + 32;
                }

                if (ch == '_' || ch == '-' || ch == ' ') {
                    byte c1 = bytes[i + 1];
                    if (c1 != '"' && c1 != '\'' && c1 != ch) {
                        continue;
                    }
                }

                hashCode ^= ch;
                hashCode *= Fnv.MAGIC_PRIME;
            }
            return hashCode;
        }

        for (; ; ) {
            int ch = bytes[offset];
            if (ch == '\\') {
                ch = bytes[++offset];
                switch (ch) {
                    case 'u': {
                        ch = char4(bytes[offset + 1], bytes[offset + 2], bytes[offset + 3], bytes[offset + 4]);
                        offset += 4;
                        break;
                    }
                    case 'x': {
                        ch = char2(bytes[offset + 1], bytes[offset + 2]);
                        offset += 2;
                        break;
                    }
                    case '\\':
                    case '"':
                    default:
                        ch = char1(ch);
                        break;
                }
                offset++;
            } else if (ch == '"') {
                break;
            } else {
                if (ch >= 0) {
                    if (ch >= 'A' && ch <= 'Z') {
                        ch = ch + 32;
                    }
                    offset++;
                } else {
                    ch &= 0xFF;
                    switch (ch >> 4) {
                        case 12:
                        case 13: {
                            /* 110x xxxx   10xx xxxx*/
                            int c2 = bytes[offset + 1];
                            ch = ((ch & 0x1F) << 6) | (c2 & 0x3F);
                            offset += 2;
                            break;
                        }
                        case 14: {
                            int c2 = bytes[offset + 1];
                            int c3 = bytes[offset + 2];
                            ch = ((ch & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F);

                            offset += 3;
                            break;
                        }
                        default:
                            /* 10xx xxxx,  1111 xxxx */
                            throw new JSONException("malformed input around byte " + offset);
                    }
                }
            }

            if (ch == '_' || ch == '-' || ch == ' ') {
                continue;
            }

            hashCode ^= ch;
            hashCode *= Fnv.MAGIC_PRIME;
        }

        return hashCode;
    }

    final String getLatin1String(int offset, int length) {
        if (JDKUtils.ANDROID_SDK_INT >= 34) {
            return new String(bytes, offset, length, StandardCharsets.ISO_8859_1);
        }

        char[] charBuf = this.charBuf;
        if (charBuf == null) {
            this.charBuf = charBuf = CHARS_UPDATER.getAndSet(cacheItem, null);
        }
        if (charBuf == null || charBuf.length < length) {
            this.charBuf = charBuf = new char[length];
        }
        for (int i = 0; i < length; i++) {
            charBuf[i] = (char) (bytes[offset + i] & 0xFF);
        }
        return new String(charBuf, 0, length);
    }

    @Override
    public String getFieldName() {
        int length = nameEnd - nameBegin;
        if (!nameEscape) {
            return nameAscii ? getLatin1String(nameBegin, length)
                    : new String(bytes, nameBegin, length, StandardCharsets.UTF_8);
        }

        char[] chars = new char[nameLength];

        int offset = nameBegin;
        for (int i = 0; offset < nameEnd; ++i) {
            int c = bytes[offset];
            if (c < 0) {
                c &= 0xFF;
                switch (c >> 4) {
                    case 12:
                    case 13: {
                        /* 110x xxxx   10xx xxxx*/
                        int char2 = bytes[offset + 1];
                        if ((char2 & 0xC0) != 0x80) {
                            throw new JSONException("malformed input around byte " + offset);
                        }
                        c = (((c & 0x1F) << 6) | (char2 & 0x3F));
                        offset += 2;
                        break;
                    }
                    case 14: {
                        int char2 = bytes[offset + 1];
                        int char3 = bytes[offset + 2];
                        if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                            throw new JSONException("malformed input around byte " + (offset + 2));
                        }
                        c = (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F)));
                        offset += 3;
                        break;
                    }
                    default:
                        /* 10xx xxxx,  1111 xxxx */
                        throw new JSONException("malformed input around byte " + offset);
                }
                chars[i] = (char) c;
                continue;
            }

            if (c == '\\') {
                c = (char) bytes[++offset];
                switch (c) {
                    case 'u': {
                        int c1 = bytes[++offset];
                        int c2 = bytes[++offset];
                        int c3 = bytes[++offset];
                        int c4 = bytes[++offset];
                        c = char4(c1, c2, c3, c4);
                        break;
                    }
                    case 'x': {
                        int c1 = bytes[++offset];
                        int c2 = bytes[++offset];
                        c = char2(c1, c2);
                        break;
                    }
                    case '\\':
                    case '"':
                    case '.':
                    case '-':
                    case '+':
                    case '*':
                    case '/':
                    case '>':
                    case '<':
                    case '=':
                    case '@':
                    case ':':
                        break;
                    default:
                        c = char1(c);
                        break;
                }
            } else if (c == '"') {
                break;
            }
            chars[i] = (char) c;
            offset++;
        }

        return new String(chars);
    }

    @Override
    public String readFieldName() {
        final char quote = ch;
        if (quote != '"' && quote != '\'') {
            if ((context.features & Feature.AllowUnQuotedFieldNames.mask) != 0 && isFirstIdentifier(quote)) {
                return readFieldNameUnquote();
            }

            return null;
        }

        final byte[] bytes = this.bytes;
        this.nameAscii = true;
        this.nameEscape = false;
        int offset = this.nameBegin = this.offset;
        final int nameBegin = this.nameBegin;
        for (int i = 0; offset < end; ++i) {
            int ch = bytes[offset];
            if (ch == '\\') {
                nameEscape = true;
                ch = bytes[offset + 1];
                offset += (ch == 'u' ? 6 : (ch == 'x' ? 4 : 2));
                continue;
            }

            if (ch == quote) {
                this.nameLength = i;
                this.nameEnd = offset;
                offset++;
                ch = bytes[offset] & 0xff;

                while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                    offset++;
                    ch = bytes[offset] & 0xff;
                }
                if (ch != ':') {
                    throw syntaxError(offset, ch);
                }

                ch = ++offset == end ? EOI : bytes[offset++];

                while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                    ch = offset == end ? EOI : bytes[offset++];
                }

                this.offset = offset;
                this.ch = (char) ch;
                break;
            }

            if (ch >= 0) {
                offset++;
            } else {
                if (nameAscii) {
                    nameAscii = false;
                }
                ch &= 0xFF;
                switch (ch >> 4) {
                    case 12:
                    case 13:
                        /* 110x xxxx   10xx xxxx*/
                        offset += 2;
                        break;
                    case 14: {
                        offset += 3;
                        break;
                    }
                    default:
                        /* 10xx xxxx,  1111 xxxx */
                        throw syntaxError(offset, ch);
                }
            }
        }

        if (nameEnd < nameBegin) {
            throw syntaxError(offset, ch);
        }

        int length = nameEnd - nameBegin;
        if (!nameEscape) {
            if (nameAscii) {
                long nameValue0 = -1, nameValue1 = -1;
                switch (length) {
                    case 1:
                        nameValue0 = bytes[nameBegin];
                        break;
                    case 2:
                        nameValue0 = (bytes[nameBegin + 1] << 8) + bytes[nameBegin];
                        break;
                    case 3:
                        nameValue0
                                = (bytes[nameBegin + 2] << 16)
                                + (bytes[nameBegin + 1] << 8)
                                + (bytes[nameBegin]);
                        break;
                    case 4:
                        nameValue0
                                = (bytes[nameBegin + 3] << 24)
                                + (bytes[nameBegin + 2] << 16)
                                + (bytes[nameBegin + 1] << 8)
                                + (bytes[nameBegin]);
                        break;
                    case 5:
                        nameValue0
                                = (((long) bytes[nameBegin + 4]) << 32)
                                + (((long) bytes[nameBegin + 3]) << 24)
                                + (((long) bytes[nameBegin + 2]) << 16)
                                + (((long) bytes[nameBegin + 1]) << 8)
                                + ((long) bytes[nameBegin]);
                        break;
                    case 6:
                        nameValue0
                                = (((long) bytes[nameBegin + 5]) << 40)
                                + (((long) bytes[nameBegin + 4]) << 32)
                                + (((long) bytes[nameBegin + 3]) << 24)
                                + (((long) bytes[nameBegin + 2]) << 16)
                                + (((long) bytes[nameBegin + 1]) << 8)
                                + ((long) bytes[nameBegin]);
                        break;
                    case 7:
                        nameValue0
                                = (((long) bytes[nameBegin + 6]) << 48)
                                + (((long) bytes[nameBegin + 5]) << 40)
                                + (((long) bytes[nameBegin + 4]) << 32)
                                + (((long) bytes[nameBegin + 3]) << 24)
                                + (((long) bytes[nameBegin + 2]) << 16)
                                + (((long) bytes[nameBegin + 1]) << 8)
                                + ((long) bytes[nameBegin]);
                        break;
                    case 8:
                        nameValue0
                                = (((long) bytes[nameBegin + 7]) << 56)
                                + (((long) bytes[nameBegin + 6]) << 48)
                                + (((long) bytes[nameBegin + 5]) << 40)
                                + (((long) bytes[nameBegin + 4]) << 32)
                                + (((long) bytes[nameBegin + 3]) << 24)
                                + (((long) bytes[nameBegin + 2]) << 16)
                                + (((long) bytes[nameBegin + 1]) << 8)
                                + ((long) bytes[nameBegin]);
                        break;
                    case 9:
                        nameValue0 = bytes[nameBegin];
                        nameValue1
                                = (((long) bytes[nameBegin + 8]) << 56)
                                + (((long) bytes[nameBegin + 7]) << 48)
                                + (((long) bytes[nameBegin + 6]) << 40)
                                + (((long) bytes[nameBegin + 5]) << 32)
                                + (((long) bytes[nameBegin + 4]) << 24)
                                + (((long) bytes[nameBegin + 3]) << 16)
                                + (((long) bytes[nameBegin + 2]) << 8)
                                + ((long) bytes[nameBegin + 1]);
                        break;
                    case 10:
                        nameValue0
                                = (bytes[nameBegin + 1] << 8)
                                + (bytes[nameBegin]);
                        nameValue1
                                = (((long) bytes[nameBegin + 9]) << 56)
                                + (((long) bytes[nameBegin + 8]) << 48)
                                + (((long) bytes[nameBegin + 7]) << 40)
                                + (((long) bytes[nameBegin + 6]) << 32)
                                + (((long) bytes[nameBegin + 5]) << 24)
                                + (((long) bytes[nameBegin + 4]) << 16)
                                + (((long) bytes[nameBegin + 3]) << 8)
                                + ((long) bytes[nameBegin + 2]);
                        break;
                    case 11:
                        nameValue0
                                = (bytes[nameBegin + 2] << 16)
                                + (bytes[nameBegin + 1] << 8)
                                + (bytes[nameBegin]);
                        nameValue1
                                = (((long) bytes[nameBegin + 10]) << 56)
                                + (((long) bytes[nameBegin + 9]) << 48)
                                + (((long) bytes[nameBegin + 8]) << 40)
                                + (((long) bytes[nameBegin + 7]) << 32)
                                + (((long) bytes[nameBegin + 6]) << 24)
                                + (((long) bytes[nameBegin + 5]) << 16)
                                + (((long) bytes[nameBegin + 4]) << 8)
                                + ((long) bytes[nameBegin + 3]);
                        break;
                    case 12:
                        nameValue0
                                = (bytes[nameBegin + 3] << 24)
                                + (bytes[nameBegin + 2] << 16)
                                + (bytes[nameBegin + 1] << 8)
                                + (bytes[nameBegin]);
                        nameValue1
                                = (((long) bytes[nameBegin + 11]) << 56)
                                + (((long) bytes[nameBegin + 10]) << 48)
                                + (((long) bytes[nameBegin + 9]) << 40)
                                + (((long) bytes[nameBegin + 8]) << 32)
                                + (((long) bytes[nameBegin + 7]) << 24)
                                + (((long) bytes[nameBegin + 6]) << 16)
                                + (((long) bytes[nameBegin + 5]) << 8)
                                + ((long) bytes[nameBegin + 4]);
                        break;
                    case 13:
                        nameValue0
                                = (((long) bytes[nameBegin + 4]) << 32)
                                + (((long) bytes[nameBegin + 3]) << 24)
                                + (((long) bytes[nameBegin + 2]) << 16)
                                + (((long) bytes[nameBegin + 1]) << 8)
                                + ((long) bytes[nameBegin]);
                        nameValue1
                                = (((long) bytes[nameBegin + 12]) << 56)
                                + (((long) bytes[nameBegin + 11]) << 48)
                                + (((long) bytes[nameBegin + 10]) << 40)
                                + (((long) bytes[nameBegin + 9]) << 32)
                                + (((long) bytes[nameBegin + 8]) << 24)
                                + (((long) bytes[nameBegin + 7]) << 16)
                                + (((long) bytes[nameBegin + 6]) << 8)
                                + ((long) bytes[nameBegin + 5]);
                        break;
                    case 14:
                        nameValue0
                                = (((long) bytes[nameBegin + 5]) << 40)
                                + (((long) bytes[nameBegin + 4]) << 32)
                                + (((long) bytes[nameBegin + 3]) << 24)
                                + (((long) bytes[nameBegin + 2]) << 16)
                                + (((long) bytes[nameBegin + 1]) << 8)
                                + ((long) bytes[nameBegin]);
                        nameValue1
                                = (((long) bytes[nameBegin + 13]) << 56)
                                + (((long) bytes[nameBegin + 12]) << 48)
                                + (((long) bytes[nameBegin + 11]) << 40)
                                + (((long) bytes[nameBegin + 10]) << 32)
                                + (((long) bytes[nameBegin + 9]) << 24)
                                + (((long) bytes[nameBegin + 8]) << 16)
                                + (((long) bytes[nameBegin + 8]) << 8)
                                + ((long) bytes[nameBegin + 6]);
                        break;
                    case 15:
                        nameValue0
                                = (((long) bytes[nameBegin + 6]) << 48)
                                + (((long) bytes[nameBegin + 5]) << 40)
                                + (((long) bytes[nameBegin + 4]) << 32)
                                + (((long) bytes[nameBegin + 3]) << 24)
                                + (((long) bytes[nameBegin + 2]) << 16)
                                + (((long) bytes[nameBegin + 1]) << 8)
                                + ((long) bytes[nameBegin]);
                        nameValue1
                                = (((long) bytes[nameBegin + 14]) << 56)
                                + (((long) bytes[nameBegin + 13]) << 48)
                                + (((long) bytes[nameBegin + 12]) << 40)
                                + (((long) bytes[nameBegin + 11]) << 32)
                                + (((long) bytes[nameBegin + 10]) << 24)
                                + (((long) bytes[nameBegin + 9]) << 16)
                                + (((long) bytes[nameBegin + 8]) << 8)
                                + ((long) bytes[nameBegin + 7]);
                        break;
                    case 16:
                        nameValue0
                                = (((long) bytes[nameBegin + 7]) << 56)
                                + (((long) bytes[nameBegin + 6]) << 48)
                                + (((long) bytes[nameBegin + 5]) << 40)
                                + (((long) bytes[nameBegin + 4]) << 32)
                                + (((long) bytes[nameBegin + 3]) << 24)
                                + (((long) bytes[nameBegin + 2]) << 16)
                                + (((long) bytes[nameBegin + 1]) << 8)
                                + ((long) bytes[nameBegin]);
                        nameValue1
                                = (((long) bytes[nameBegin + 15]) << 56)
                                + (((long) bytes[nameBegin + 14]) << 48)
                                + (((long) bytes[nameBegin + 13]) << 40)
                                + (((long) bytes[nameBegin + 12]) << 32)
                                + (((long) bytes[nameBegin + 11]) << 24)
                                + (((long) bytes[nameBegin + 10]) << 16)
                                + (((long) bytes[nameBegin + 9]) << 8)
                                + ((long) bytes[nameBegin + 8]);
                        break;
                    default:
                        break;
                }

                if (nameValue0 != -1) {
                    if (nameValue1 != -1) {
                        long nameValue01 = nameValue0 ^ nameValue1;
                        int indexMask = ((int) (nameValue01 ^ (nameValue01 >>> 32))) & (NAME_CACHE2.length - 1);
                        NameCacheEntry2 entry = NAME_CACHE2[indexMask];
                        if (entry == null) {
                            String name = getLatin1String(nameBegin, length);
                            NAME_CACHE2[indexMask] = new NameCacheEntry2(name, nameValue0, nameValue1);
                            return name;
                        } else if (entry.value0 == nameValue0 && entry.value1 == nameValue1) {
                            return entry.name;
                        }
                    } else {
                        int indexMask = ((int) (nameValue0 ^ (nameValue0 >>> 32))) & (NAME_CACHE.length - 1);
                        NameCacheEntry entry = NAME_CACHE[indexMask];
                        if (entry == null) {
                            String name = getLatin1String(nameBegin, length);
                            NAME_CACHE[indexMask] = new NameCacheEntry(name, nameValue0);
                            return name;
                        } else if (entry.value == nameValue0) {
                            return entry.name;
                        }
                    }
                }
            }

            if (nameAscii) {
                // optimization for android
                if (ANDROID_SDK_INT < 34) {
                    char[] charBuf = this.charBuf;
                    if (charBuf == null) {
                        this.charBuf = charBuf = CHARS_UPDATER.getAndSet(cacheItem, null);
                    }
                    if (charBuf == null || charBuf.length < length) {
                        this.charBuf = charBuf = new char[length];
                    }
                    for (int i = 0; i < length; i++) {
                        charBuf[i] = (char) bytes[nameBegin + i];
                    }
                    return new String(charBuf, 0, length);
                } else {
                    return new String(bytes, nameBegin, length, StandardCharsets.ISO_8859_1);
                }
            }

            return new String(bytes, nameBegin, length, StandardCharsets.UTF_8);
        }

        return getFieldName();
    }

    @Override
    public final int readInt32Value() {
        boolean negative = false;
        int firstOffset = offset;
        int ch = this.ch;
        int offset = this.offset;
        final int firstChar = ch;
        final byte[] bytes = this.bytes;

        int intValue = 0;

        int quote = '\0';
        if (firstChar == '"' || firstChar == '\'') {
            quote = ch;
            ch = bytes[offset++];
        }

        if (ch == '-') {
            negative = true;
            ch = bytes[offset++];
        } else if (ch == '+') {
            ch = bytes[offset++];
        } else if (ch == ',') {
            throw numberError();
        }

        boolean overflow = false;
        while (ch >= '0' && ch <= '9') {
            if (!overflow) {
                int intValue10 = intValue * 10 + (ch - '0');
                if (intValue10 < intValue) {
                    overflow = true;
                    break;
                } else {
                    intValue = intValue10;
                }
            }
            if (offset == end) {
                ch = EOI;
                break;
            }
            ch = bytes[offset++];
        }

        boolean notMatch = ch < '0' || ch > '9';
        if (ch == '.'
                || ch == 'e'
                || ch == 'E'
                || ch == 't'
                || ch == 'f'
                || ch == 'n'
                || ch == '{'
                || ch == '['
                || overflow) {
            notMatch = true;
        } else if (quote != 0 && ch != quote) {
            notMatch = true;
        }

        if (notMatch) {
            readNumber0();
            if (wasNull && (context.features & ErrorOnNullForPrimitives.mask) == 0) {
                return 0;
            }
            if (valueType == JSON_TYPE_INT) {
                BigInteger bigInteger = getBigInteger();
                if (bigInteger.bitLength() <= 31) {
                    return bigInteger.intValue();
                } else {
                    throw numberError(offset, ch);
                }
            } else {
                return getInt32Value();
            }
        }

        if (quote != 0) {
            wasNull = firstOffset + 1 == offset;
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S') {
            ch = offset == end ? EOI : bytes[offset++];
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        this.ch = (char) ch;
        this.offset = offset;

        return negative ? -intValue : intValue;
    }

    @Override
    public final Integer readInt32() {
        boolean negative = false;
        int firstOffset = offset;
        int ch = this.ch;
        int offset = this.offset;
        final char firstChar = (char) ch;
        final byte[] bytes = this.bytes;

        int intValue = 0;

        int quote = '\0';
        if (firstChar == '"' || firstChar == '\'') {
            quote = ch;
            ch = bytes[offset++];
        }

        if (ch == '-') {
            negative = true;
            ch = bytes[offset++];
        } else if (ch == '+') {
            ch = bytes[offset++];
        } else if (ch == ',') {
            throw numberError();
        }

        boolean overflow = false;
        while (ch >= '0' && ch <= '9') {
            if (!overflow) {
                int intValue10 = intValue * 10 + (ch - '0');
                if (intValue10 < intValue) {
                    overflow = true;
                    break;
                } else {
                    intValue = intValue10;
                }
            }
            if (offset == end) {
                ch = EOI;
                break;
            }
            ch = bytes[offset++];
        }

        boolean notMatch = ch < '0' || ch > '9';
        if (ch == '.'
                || ch == 'e'
                || ch == 'E'
                || ch == 't'
                || ch == 'f'
                || ch == 'n'
                || ch == '{'
                || ch == '['
                || ch == EOI
                || overflow) {
            notMatch = true;
        } else if (quote != 0 && ch != quote) {
            notMatch = true;
        }

        if (notMatch) {
            readNumber0();
            if (wasNull) {
                return null;
            }
            if (valueType == JSON_TYPE_INT) {
                BigInteger bigInteger = getBigInteger();
                if (bigInteger.bitLength() <= 31) {
                    return bigInteger.intValue();
                } else {
                    throw numberError(offset, ch);
                }
            } else {
                return getInt32Value();
            }
        }

        if (quote != 0) {
            wasNull = firstOffset + 1 == offset;
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S') {
            ch = offset == end ? EOI : bytes[offset++];
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        this.ch = (char) ch;
        this.offset = offset;

        return wasNull ? null : (negative ? -intValue : intValue);
    }

    @Override
    public final long readInt64Value() {
        boolean negative = false;
        int firstOffset = offset;
        int ch = this.ch;
        int offset = this.offset;
        final char firstChar = (char) ch;
        final byte[] bytes = this.bytes;

        long longValue = 0;

        int quote = '\0';
        if (ch == '"' || ch == '\'') {
            quote = ch;
            ch = bytes[offset++];
        }

        if (ch == '-') {
            negative = true;
            ch = bytes[offset++];
        } else if (ch == '+') {
            ch = bytes[offset++];
        } else if (ch == ',') {
            throw numberError();
        }

        boolean overflow = false;
        while (ch >= '0' && ch <= '9') {
            if (!overflow) {
                long intValue10 = longValue * 10 + (ch - '0');
                if (intValue10 < longValue) {
                    overflow = true;
                    break;
                } else {
                    longValue = intValue10;
                }
            }
            if (offset == end) {
                ch = EOI;
                break;
            }
            ch = bytes[offset++];
        }

        boolean notMatch = ch < '0' || ch > '9';
        if (ch == '.'
                || ch == 'e'
                || ch == 'E'
                || ch == 't'
                || ch == 'f'
                || ch == 'n'
                || ch == '{'
                || ch == '['
                || overflow) {
            notMatch = true;
        } else if (quote != 0 && ch != quote) {
            notMatch = true;
        }

        if (notMatch) {
            this.offset = firstOffset;
            this.ch = firstChar;
            readNumber0();
            if (wasNull && (context.features & ErrorOnNullForPrimitives.mask) == 0) {
                return 0;
            }
            if (valueType == JSON_TYPE_INT) {
                BigInteger bigInteger = getBigInteger();
                if (bigInteger.bitLength() <= 63) {
                    return bigInteger.longValue();
                } else {
                    throw numberError(offset, ch);
                }
            } else {
                return getInt64Value();
            }
        }

        if (quote != 0) {
            wasNull = firstOffset + 1 == offset;
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S') {
            ch = offset == end ? EOI : bytes[offset++];
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : (char) bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        this.ch = (char) ch;
        this.offset = offset;

        return negative ? -longValue : longValue;
    }

    @Override
    public final Long readInt64() {
        boolean negative = false;
        int firstOffset = offset;
        int ch = this.ch;
        int offset = this.offset;
        final char firstChar = (char) ch;
        final byte[] bytes = this.bytes;

        long longValue = 0;

        int quote = '\0';
        if (ch == '"' || ch == '\'') {
            quote = ch;
            ch = bytes[offset++];
        }

        if (ch == '-') {
            negative = true;
            ch = bytes[offset++];
        } else if (ch == '+') {
            ch = bytes[offset++];
        } else if (ch == ',') {
            throw numberError();
        }

        boolean overflow = false;
        while (ch >= '0' && ch <= '9') {
            if (!overflow) {
                long intValue10 = longValue * 10 + (ch - '0');
                if (intValue10 < longValue) {
                    overflow = true;
                    break;
                } else {
                    longValue = intValue10;
                }
            }
            if (offset == end) {
                ch = EOI;
                break;
            }
            ch = bytes[offset++];
        }

        boolean notMatch = ch < '0' || ch > '9';
        if (ch == '.'
                || ch == 'e'
                || ch == 'E'
                || ch == 't'
                || ch == 'f'
                || ch == 'n'
                || ch == '{'
                || ch == '['
                || overflow) {
            notMatch = true;
        } else if (quote != 0 && ch != quote) {
            notMatch = true;
        }

        if (notMatch) {
            this.offset = firstOffset;
            this.ch = firstChar;
            readNumber0();
            if (wasNull) {
                return null;
            }
            if (valueType == JSON_TYPE_INT) {
                BigInteger bigInteger = getBigInteger();
                if (bigInteger.bitLength() <= 63) {
                    return bigInteger.longValue();
                } else {
                    throw numberError(offset, ch);
                }
            } else {
                return getInt64Value();
            }
        }

        if (quote != 0) {
            wasNull = firstOffset + 1 == offset;
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S') {
            ch = offset == end ? EOI : bytes[offset++];
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : (char) bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        this.ch = (char) ch;
        this.offset = offset;

        return wasNull ? null : (negative ? -longValue : longValue);
    }

    @Override
    public final double readDoubleValue() {
        boolean valid = false;
        this.wasNull = false;

        boolean value = false;
        double doubleValue = 0;

        final byte[] bytes = this.bytes;
        int quote = '\0';
        int ch = this.ch;
        int offset = this.offset;
        if (ch == '"' || ch == '\'') {
            quote = ch;
            ch = bytes[offset++];

            if (ch == quote) {
                ch = offset == end ? EOI : bytes[offset++];
                while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                    ch = offset == end ? EOI : bytes[offset++];
                }
                this.ch = (char) ch;
                this.offset = offset;
                nextIfComma();
                wasNull = true;
                return 0;
            }
        }

        final int start = offset;
        if (ch == '-') {
            negative = true;
            ch = bytes[offset++];
        } else {
            negative = false;
            if (ch == '+') {
                ch = bytes[offset++];
            }
        }

        valueType = JSON_TYPE_INT;
        boolean overflow = false;
        long longValue = 0;
        while (ch >= '0' && ch <= '9') {
            valid = true;
            if (!overflow) {
                long intValue10 = longValue * 10 + (ch - '0');
                if (intValue10 < longValue) {
                    overflow = true;
                } else {
                    longValue = intValue10;
                }
            }

            if (offset == end) {
                ch = EOI;
                offset++;
                break;
            }
            ch = bytes[offset++];
        }

        this.scale = 0;
        if (ch == '.') {
            valueType = JSON_TYPE_DEC;
            ch = bytes[offset++];
            while (ch >= '0' && ch <= '9') {
                valid = true;
                this.scale++;
                if (!overflow) {
                    long intValue10 = longValue * 10 + (ch - '0');
                    if (intValue10 < longValue) {
                        overflow = true;
                    } else {
                        longValue = intValue10;
                    }
                }

                if (offset == end) {
                    ch = EOI;
                    offset++;
                    break;
                }
                ch = bytes[offset++];
            }
        }

        int expValue = 0;
        if (ch == 'e' || ch == 'E') {
            boolean negativeExp = false;
            ch = bytes[offset++];

            if (ch == '-') {
                negativeExp = true;
                ch = bytes[offset++];
            } else if (ch == '+') {
                ch = bytes[offset++];
            }

            while (ch >= '0' && ch <= '9') {
                valid = true;
                int byteVal = (ch - '0');
                expValue = expValue * 10 + byteVal;
                if (expValue > MAX_EXP) {
                    throw new JSONException("too large exp value : " + expValue);
                }

                if (offset == end) {
                    ch = EOI;
                    offset++;
                    break;
                }
                ch = bytes[offset++];
            }

            if (negativeExp) {
                expValue = -expValue;
            }

            this.exponent = (short) expValue;
            valueType = JSON_TYPE_DEC;
        }

        if (offset == start) {
            if (ch == 'n' && bytes[offset] == 'u' && bytes[offset + 1] == 'l' && bytes[offset + 2] == 'l') {
                offset += 3;
                valid = true;
                if ((context.features & Feature.ErrorOnNullForPrimitives.mask) != 0) {
                    throw new JSONException(info("long value not support input null"));
                }

                wasNull = true;
                value = true;
                ch = offset == end ? EOI : bytes[offset++];
            } else if (ch == 't' && bytes[offset] == 'r' && bytes[offset + 1] == 'u' && bytes[offset + 2] == 'e') {
                offset += 3;
                valid = true;
                value = true;
                doubleValue = 1;
                ch = offset == end ? EOI : bytes[offset++];
            } else if (ch == 'f' && bytes[offset] == 'a' && bytes[offset + 1] == 'l' && bytes[offset + 2] == 's' && bytes[offset + 3] == 'e') {
                valid = true;
                offset += 4;
                doubleValue = 0;
                value = true;
                ch = offset == end ? EOI : bytes[offset++];
            } else if (ch == '{' && quote == 0) {
                valid = true;
                this.ch = (char) ch;
                this.offset = offset;
                Map<String, Object> obj = readObject();
                if (!obj.isEmpty()) {
                    throw new JSONException(info());
                }
                offset = this.offset;
                ch = this.ch;
                value = true;
                wasNull = true;
            } else if (ch == '[' && quote == 0) {
                valid = true;
                this.ch = (char) ch;
                this.offset = offset;
                List array = readArray();
                if (!array.isEmpty()) {
                    throw new JSONException(info());
                }
                offset = this.offset;
                ch = this.ch;
                value = true;
                wasNull = true;
            }
        }

        int len = offset - start;

        String str = null;
        if (quote != 0) {
            if (ch != quote) {
                this.offset = offset - 1;
                this.ch = (char) quote;
                str = readString();
                offset = this.offset;
            }

            ch = offset == end ? EOI : bytes[offset++];
        }

        if (!value) {
            if (!overflow) {
                if (longValue == 0) {
                    if (scale == 1) {
                        doubleValue = 0;
                        value = true;
                    }
                } else {
                    int scale = this.scale - expValue;
                    if (scale == 0) {
                        doubleValue = (double) longValue;
                        if (negative) {
                            doubleValue = -doubleValue;
                        }
                        value = true;
                    } else if ((long) (double) longValue == longValue) {
                        if (0 < scale && scale < DOUBLE_10_POW.length) {
                            doubleValue = (double) longValue / DOUBLE_10_POW[scale];
                            if (negative) {
                                doubleValue = -doubleValue;
                            }
                            value = true;
                        } else if (0 > scale && scale > -DOUBLE_10_POW.length) {
                            doubleValue = (double) longValue * DOUBLE_10_POW[-scale];
                            if (negative) {
                                doubleValue = -doubleValue;
                            }
                            value = true;
                        }
                    }

                    if (!value && scale > -128 && scale < 128) {
                        doubleValue = TypeUtils.doubleValue(negative ? -1 : 1, longValue, scale);
                        value = true;
                    }
                }
            }

            if (!value) {
                if (str != null) {
                    try {
                        doubleValue = Double.parseDouble(str);
                    } catch (NumberFormatException ex) {
                        throw new JSONException(info(), ex);
                    }
                } else {
                    doubleValue = TypeUtils.parseDouble(bytes, start - 1, len);
                }
            }

            if (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S') {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        if (!valid) {
            throw new JSONException(info("illegal input error"));
        }

        this.ch = (char) ch;
        this.offset = offset;
        return doubleValue;
    }

    @Override
    public final float readFloatValue() {
        boolean valid = false;
        this.wasNull = false;

        boolean value = false;
        float floatValue = 0;
        final byte[] chars = this.bytes;

        int quote = '\0';
        int ch = this.ch;
        int offset = this.offset;
        if (ch == '"' || ch == '\'') {
            quote = ch;
            ch = chars[offset++];

            if (ch == quote) {
                ch = offset == end ? EOI : chars[offset++];
                while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                    ch = offset == end ? EOI : chars[offset++];
                }
                this.ch = (char) ch;
                this.offset = offset;
                nextIfComma();
                wasNull = true;
                return 0;
            }
        }

        final int start = offset;
        if (ch == '-') {
            negative = true;
            ch = chars[offset++];
        } else {
            negative = false;
            if (ch == '+') {
                ch = chars[offset++];
            }
        }

        valueType = JSON_TYPE_INT;
        boolean overflow = false;
        long longValue = 0;

        while (ch >= '0' && ch <= '9') {
            valid = true;
            if (!overflow) {
                long intValue10 = longValue * 10 + (ch - '0');
                if (intValue10 < longValue) {
                    overflow = true;
                } else {
                    longValue = intValue10;
                }
            }

            if (offset == end) {
                ch = EOI;
                offset++;
                break;
            }
            ch = chars[offset++];
        }

        this.scale = 0;
        if (ch == '.') {
            valueType = JSON_TYPE_DEC;
            ch = chars[offset++];
            while (ch >= '0' && ch <= '9') {
                valid = true;
                this.scale++;
                if (!overflow) {
                    long intValue10 = longValue * 10 + (ch - '0');
                    if (intValue10 < longValue) {
                        overflow = true;
                    } else {
                        longValue = intValue10;
                    }
                }

                if (offset == end) {
                    ch = EOI;
                    offset++;
                    break;
                }
                ch = chars[offset++];
            }
        }

        int expValue = 0;
        if (ch == 'e' || ch == 'E') {
            boolean negativeExp = false;
            ch = chars[offset++];

            if (ch == '-') {
                negativeExp = true;
                ch = chars[offset++];
            } else if (ch == '+') {
                ch = chars[offset++];
            }

            while (ch >= '0' && ch <= '9') {
                valid = true;
                int byteVal = (ch - '0');
                expValue = expValue * 10 + byteVal;
                if (expValue > MAX_EXP) {
                    throw new JSONException("too large exp value : " + expValue);
                }

                if (offset == end) {
                    ch = EOI;
                    offset++;
                    break;
                }
                ch = chars[offset++];
            }

            if (negativeExp) {
                expValue = -expValue;
            }

            this.exponent = (short) expValue;
            valueType = JSON_TYPE_DEC;
        }

        if (offset == start) {
            if (ch == 'n' && chars[offset] == 'u' && chars[offset + 1] == 'l' && chars[offset + 2] == 'l') {
                offset += 3;
                valid = true;
                if ((context.features & Feature.ErrorOnNullForPrimitives.mask) != 0) {
                    throw new JSONException(info("long value not support input null"));
                }
                wasNull = true;
                value = true;
                ch = offset == end ? EOI : chars[offset++];
            } else if (ch == 't' && chars[offset] == 'r' && chars[offset + 1] == 'u' && chars[offset + 2] == 'e') {
                offset += 3;
                valid = true;
                value = true;
                floatValue = 1;
                ch = offset == end ? EOI : chars[offset++];
            } else if (ch == 'f'
                    && chars[offset] == 'a'
                    && chars[offset + 1] == 'l'
                    && chars[offset + 2] == 's'
                    && chars[offset + 3] == 'e') {
                offset += 4;
                valid = true;
                floatValue = 0;
                value = true;
                ch = offset == end ? EOI : chars[offset++];
            } else if (ch == '{' && quote == 0) {
                valid = true;
                this.ch = (char) ch;
                this.offset = offset;
                Map<String, Object> obj = readObject();
                if (!obj.isEmpty()) {
                    throw new JSONException(info());
                }
                ch = this.ch;
                offset = this.offset;
                value = true;
                wasNull = true;
            } else if (ch == '[' && quote == 0) {
                this.ch = (char) ch;
                this.offset = offset;
                valid = true;
                List array = readArray();
                if (!array.isEmpty()) {
                    throw new JSONException(info());
                }
                ch = this.ch;
                offset = this.offset;
                value = true;
                wasNull = true;
            }
        }

        int len = offset - start;

        String str = null;
        if (quote != 0) {
            if (ch != quote) {
                overflow = true;
                this.offset = offset - 1;
                this.ch = (char) quote;
                str = readString();
                offset = this.offset;
            }

            ch = offset >= end ? EOI : chars[offset++];
        }

        if (!value) {
            if (!overflow) {
                int scale = this.scale - expValue;
                if (scale == 0) {
                    floatValue = (float) longValue;
                    if (negative) {
                        floatValue = -floatValue;
                    }
                    value = true;
                } else if ((long) (float) longValue == longValue) {
                    if (0 < scale && scale < FLOAT_10_POW.length) {
                        floatValue = (float) longValue / FLOAT_10_POW[scale];
                        if (negative) {
                            floatValue = -floatValue;
                        }
                    } else if (0 > scale && scale > -FLOAT_10_POW.length) {
                        floatValue = (float) longValue * FLOAT_10_POW[-scale];
                        if (negative) {
                            floatValue = -floatValue;
                        }
                    }
                }

                if (!value && scale > -128 && scale < 128) {
                    floatValue = TypeUtils.floatValue(negative ? -1 : 1, longValue, scale);
                    value = true;
                }
            }

            if (!value) {
                if (str != null) {
                    try {
                        floatValue = Float.parseFloat(str);
                    } catch (NumberFormatException ex) {
                        throw new JSONException(info(), ex);
                    }
                } else {
                    floatValue = TypeUtils.parseFloat(chars, start - 1, len);
                }
            }

            if (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S') {
                ch = offset >= end ? EOI : chars[offset++];
            }
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset >= end ? EOI : chars[offset++];
        }

        if (this.comma = ch == ',') {
            ch = offset >= end ? EOI : chars[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset >= end ? EOI : chars[offset++];
            }
        }

        if (!valid) {
            throw new JSONException(info("illegal input error"));
        }

        this.ch = (char) ch;
        this.offset = offset;
        return floatValue;
    }

    protected void readString0() {
        char quote = this.ch;
        int valueLength;
        int offset = this.offset;
        int start = offset;
        boolean ascii = true;
        valueEscape = false;
        final byte[] bytes = this.bytes;

        for (int i = 0; ; ++i) {
            int c = bytes[offset];
            if (c == '\\') {
                valueEscape = true;
                c = bytes[offset + 1];
                offset += (c == 'u' ? 6 : (c == 'x' ? 4 : 2));
                continue;
            }

            if (c >= 0) {
                if (c == quote) {
                    valueLength = i;
                    break;
                }
                offset++;
            } else {
                switch ((c & 0xFF) >> 4) {
                    case 12:
                    case 13: {
                        /* 110x xxxx   10xx xxxx*/
                        offset += 2;
                        ascii = false;
                        break;
                    }
                    case 14: {
                        offset += 3;
                        ascii = false;
                        break;
                    }
                    default: {
                        /* 10xx xxxx,  1111 xxxx */
                        if ((c >> 3) == -2) {
                            offset += 4;
                            i++;
                            ascii = false;
                            break;
                        }

                        throw new JSONException("malformed input around byte " + offset);
                    }
                }
            }
        }

        String str;
        if (valueEscape) {
            char[] chars = new char[valueLength];
            offset = start;
            for (int i = 0; ; ++i) {
                int c = bytes[offset];
                if (c == '\\') {
                    c = bytes[++offset];
                    switch (c) {
                        case 'u': {
                            c = char4(bytes[offset + 1], bytes[offset + 2], bytes[offset + 3], bytes[offset + 4]);
                            offset += 4;
                            break;
                        }
                        case 'x': {
                            c = char2(bytes[offset + 1], bytes[offset + 2]);
                            offset += 2;
                            break;
                        }
                        case '\\':
                        case '"':
                            break;
                        default:
                            c = char1(c);
                            break;
                    }
                    chars[i] = (char) c;
                    offset++;
                } else if (c == '"') {
                    break;
                } else {
                    if (c >= 0) {
                        chars[i] = (char) c;
                        offset++;
                    } else {
                        switch ((c & 0xFF) >> 4) {
                            case 12:
                            case 13: {
                                /* 110x xxxx   10xx xxxx*/
                                offset++;
                                int c2 = bytes[offset++];
                                chars[i] = (char) (
                                        ((c & 0x1F) << 6) | (c2 & 0x3F));
                                break;
                            }
                            case 14: {
                                offset++;
                                int c2 = bytes[offset++];
                                int c3 = bytes[offset++];
                                chars[i] = (char)
                                        (((c & 0x0F) << 12) |
                                                ((c2 & 0x3F) << 6) |
                                                (c3 & 0x3F));
                                break;
                            }
                            default: {
                                /* 10xx xxxx,  1111 xxxx */
                                if ((c >> 3) == -2) {
                                    offset++;
                                    int c2 = bytes[offset++];
                                    int c3 = bytes[offset++];
                                    int c4 = bytes[offset++];
                                    int uc = ((c << 18) ^
                                            (c2 << 12) ^
                                            (c3 << 6) ^
                                            (c4 ^ (((byte) 0xF0 << 18) ^
                                                    ((byte) 0x80 << 12) ^
                                                    ((byte) 0x80 << 6) ^
                                                    ((byte) 0x80))));

                                    if (((c2 & 0xc0) != 0x80 || (c3 & 0xc0) != 0x80 || (c4 & 0xc0) != 0x80) // isMalformed4
                                            ||
                                            // shortest form check
                                            !(uc >= 0x010000 && uc < 0X10FFFF + 1) // !Character.isSupplementaryCodePoint(uc)
                                    ) {
                                        throw new JSONException("malformed input around byte " + offset);
                                    } else {
                                        chars[i++] = (char) ((uc >>> 10) + ('\uD800' - (0x010000 >>> 10))); // Character.highSurrogate(uc);
                                        chars[i] = (char) ((uc & 0x3ff) + '\uDC00'); // Character.lowSurrogate(uc);
                                    }
                                    break;
                                }

                                throw new JSONException("malformed input around byte " + offset);
                            }
                        }
                    }
                }
            }

            str = new String(chars);
        } else if (ascii) {
            str = getLatin1String(this.offset, offset - this.offset);
        } else {
            str = new String(bytes, this.offset, offset - this.offset, StandardCharsets.UTF_8);
        }

        int ch = bytes[++offset];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = bytes[++offset];
        }

        this.comma = ch == ',';
        this.offset = offset + 1;
        if (ch == ',') {
            next();
        } else {
            this.ch = (char) ch;
        }

        stringValue = str;
    }

    @Override
    public final boolean skipName() {
        char quote = ch;
        if (quote != '"' && quote != '\'') {
            if ((context.features & Feature.AllowUnQuotedFieldNames.mask) != 0) {
                readFieldNameHashCodeUnquote();
                return true;
            }
            throw notSupportName();
        }

        int offset = this.offset;
        final byte[] bytes = this.bytes;
        for (; ; ) {
            int ch = bytes[offset++];
            if (ch == '\\') {
                ch = bytes[offset];
                offset += (ch == 'u' ? 5 : (ch == 'x' ? 3 : 1));
                continue;
            }

            if (ch == quote) {
                ch = offset == end ? EOI : bytes[offset++];

                while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                    ch = offset == end ? EOI : bytes[offset++];
                }
                if (ch != ':') {
                    throw syntaxError(ch);
                }

                ch = offset == end ? EOI : bytes[offset++];

                while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                    ch = offset == end ? EOI : bytes[offset++];
                }

                this.offset = offset;
                this.ch = (char) ch;
                break;
            }
        }

        return true;
    }

    @Override
    public final void skipValue() {
        final byte[] bytes = this.bytes;
        int ch = this.ch;
        int offset = this.offset;
        switch (ch) {
            case '-':
            case '+':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case '.':
                boolean sign = ch == '-' || ch == '+';
                if (sign) {
                    if (offset < end) {
                        ch = bytes[offset++];
                    } else {
                        throw numberError(offset, ch);
                    }
                }
                boolean dot = ch == '.';
                boolean num = false;
                if (!dot && (ch >= '0' && ch <= '9')) {
                    num = true;
                    do {
                        ch = offset == end ? EOI : bytes[offset++];
                    } while (ch >= '0' && ch <= '9');
                }

                if (num && (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S')) {
                    ch = bytes[offset++];
                }

                boolean small = false;
                if (ch == '.') {
                    small = true;
                    ch = offset == end ? EOI : bytes[offset++];

                    if (ch >= '0' && ch <= '9') {
                        do {
                            ch = offset == end ? EOI : bytes[offset++];
                        } while (ch >= '0' && ch <= '9');
                    }
                }

                if (!num && !small) {
                    throw numberError(offset, ch);
                }

                if (ch == 'e' || ch == 'E') {
                    ch = bytes[offset++];

                    boolean eSign = false;
                    if (ch == '+' || ch == '-') {
                        eSign = true;
                        if (offset < end) {
                            ch = bytes[offset++];
                        } else {
                            throw numberError(offset, ch);
                        }
                    }

                    if (ch >= '0' && ch <= '9') {
                        do {
                            ch = offset == end ? EOI : bytes[offset++];
                        } while (ch >= '0' && ch <= '9');
                    } else if (eSign) {
                        throw numberError(offset, ch);
                    }
                }

                if (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S') {
                    ch = offset == end ? EOI : bytes[offset++];
                }
                break;
            case 't':
                if (offset + 3 > end) {
                    throw error(offset, ch);
                }
                if (bytes[offset] != 'r' || bytes[offset + 1] != 'u' || bytes[offset + 2] != 'e') {
                    throw error(offset, ch);
                }
                offset += 3;
                ch = offset == end ? EOI : bytes[offset++];
                break;
            case 'f':
                if (offset + 4 > end) {
                    throw error(offset, ch);
                }
                if (bytes[offset] != 'a' || bytes[offset + 1] != 'l' || bytes[offset + 2] != 's' || bytes[offset + 3] != 'e') {
                    throw error(offset, ch);
                }
                offset += 4;
                ch = offset == end ? EOI : bytes[offset++];
                break;
            case 'n':
                if (offset + 3 > end) {
                    throw error(offset, ch);
                }
                if (bytes[offset] != 'u' || bytes[offset + 1] != 'l' || bytes[offset + 2] != 'l') {
                    throw error(offset, ch);
                }
                offset += 3;
                ch = offset == end ? EOI : bytes[offset++];
                break;
            default:
                if (ch == '"' || ch == '\'') {
                    skipString();
                } else if (ch == '[') {
                    next();
                    for (int i = 0; ; ++i) {
                        if (this.ch == ']') {
                            next();
                            break;
                        }
                        if (i != 0 && !comma) {
                            throw valueError();
                        }
                        comma = false;
                        skipValue();
                    }
                } else if (ch == '{') {
                    next();
                    for (; ; ) {
                        if (this.ch == '}') {
                            comma = false;
                            next();
                            break;
                        }
                        skipName();
                        skipValue();
                    }
                } else if (ch == 'S' && nextIfSet()) {
                    skipValue();
                } else {
                    throw error(offset, ch);
                }
                ch = this.ch;
                offset = this.offset;
                break;
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch == ',') {
            comma = true;
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        if (!comma && ch != EOI && ch != '}' && ch != ']' && ch != EOI) {
            throw error(offset, ch);
        }

        this.ch = (char) ch;
        this.offset = offset;
    }

    @Override
    public final String getString() {
        if (stringValue != null) {
            return stringValue;
        }
        final byte[] bytes = this.bytes;
        int offset = nameBegin;
        int length = nameEnd - offset;
        if (!nameEscape) {
            return nameAscii
                    ? getLatin1String(nameBegin, length)
                    : new String(bytes, nameBegin, length, StandardCharsets.UTF_8);
        }

        char[] chars = new char[nameLength];

        for (int i = 0; ; ++i) {
            int c = bytes[offset];
            if (c < 0) {
                switch ((c & 0xFF) >> 4) {
                    case 12:
                    case 13: {
                        /* 110x xxxx   10xx xxxx*/
                        int char2 = bytes[offset + 1];
                        if ((char2 & 0xC0) != 0x80) {
                            throw new JSONException("malformed input around byte " + offset);
                        }
                        c = (((c & 0x1F) << 6) | (char2 & 0x3F));
                        offset += 2;
                        break;
                    }
                    case 14: {
                        int char2 = bytes[offset + 1];
                        int char3 = bytes[offset + 2];
                        if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                            throw new JSONException("malformed input around byte " + (offset + 2));
                        }
                        c = (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F)));
                        offset += 3;
                        break;
                    }
                    default:
                        /* 10xx xxxx,  1111 xxxx */
                        if ((c >> 3) == -2) {
                            int c2 = bytes[offset + 1];
                            int c3 = bytes[offset + 2];
                            int c4 = bytes[offset + 3];
                            offset += 4;
                            int uc = ((c << 18) ^
                                    (c2 << 12) ^
                                    (c3 << 6) ^
                                    (c4 ^ (((byte) 0xF0 << 18) ^
                                            ((byte) 0x80 << 12) ^
                                            ((byte) 0x80 << 6) ^
                                            ((byte) 0x80))));

                            if (((c2 & 0xc0) != 0x80 || (c3 & 0xc0) != 0x80 || (c4 & 0xc0) != 0x80) // isMalformed4
                                    ||
                                    // shortest form check
                                    !(uc >= 0x010000 && uc < 0X10FFFF + 1) // !Character.isSupplementaryCodePoint(uc)
                            ) {
                                throw new JSONException("malformed input around byte " + offset);
                            } else {
                                chars[i++] = (char) ((uc >>> 10) + ('\uD800' - (0x010000 >>> 10))); // Character.highSurrogate(uc);
                                chars[i] = (char) ((uc & 0x3ff) + '\uDC00'); // Character.lowSurrogate(uc);
                            }
                            continue;
                        } else {
                            c &= 0xFF;
                            offset++;
                            break;
                        }
                }
                chars[i] = (char) c;
                continue;
            }

            if (c == '\\') {
                c = (char) bytes[++offset];
                switch (c) {
                    case 'u': {
                        int c1 = bytes[offset + 1];
                        int c2 = bytes[offset + 2];
                        int c3 = bytes[offset + 3];
                        int c4 = bytes[offset + 4];
                        c = char4(c1, c2, c3, c4);
                        offset += 4;
                        break;
                    }
                    case 'x': {
                        int c1 = bytes[offset + 1];
                        int c2 = bytes[offset + 2];
                        c = char2(c1, c2);
                        offset += 2;
                        break;
                    }
                    case '\\':
                    case '"':
                        break;
                    default:
                        c = char1(c);
                        break;
                }
            } else if (c == '"') {
                break;
            }
            chars[i] = (char) c;
            offset++;
        }

        return stringValue = new String(chars);
    }

    protected final void skipString() {
        byte ch = (byte) this.ch;
        byte quote = ch;

        final byte[] bytes = this.bytes;
        int offset = this.offset;
        ch = bytes[offset++];
        for (; ; ) {
            if (ch == '\\') {
                ch = bytes[offset++];
                if (ch == 'u') {
                    offset += 4;
                } else if (ch == 'x') {
                    offset += 2;
                } else if (ch != '\\' && ch != '"') {
                    char1(ch);
                }
                ch = bytes[offset++];
                continue;
            }
            if (ch == quote) {
                ch = offset < end ? bytes[offset++] : (byte) EOI;
                break;
            }

            if (offset < end) {
                ch = bytes[offset++];
            } else {
                ch = EOI;
                break;
            }
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = bytes[offset++];
        }

        if (comma = (ch == ',')) {
            if (offset >= end) {
                this.offset = offset;
                this.ch = EOI;
                return;
            }

            ch = bytes[offset];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                offset++;
                if (offset >= end) {
                    this.ch = EOI;
                    this.offset = offset;
                    return;
                }
                ch = bytes[offset];
            }
            offset++;
        }
        this.offset = offset;
        this.ch = (char) ch;
    }

    @Override
    public final void skipComment() {
        int offset = this.offset;
        if (offset + 1 >= this.end) {
            throw new JSONException(info());
        }

        final byte[] bytes = this.bytes;
        byte ch = bytes[offset++];

        boolean multi;
        if (ch == '*') {
            multi = true;
        } else if (ch == '/') {
            multi = false;
        } else {
            return;
        }

        ch = bytes[offset++];

        while (true) {
            boolean endOfComment = false;
            if (multi) {
                if (ch == '*'
                        && offset <= end && bytes[offset] == '/') {
                    offset++;
                    endOfComment = true;
                }
            } else {
                endOfComment = ch == '\n';
            }

            if (endOfComment) {
                if (offset >= this.end) {
                    ch = EOI;
                    break;
                }

                ch = bytes[offset];

                while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                    offset++;
                    if (offset >= this.end) {
                        ch = EOI;
                        break;
                    }
                    ch = bytes[offset];
                }

                offset++;
                break;
            }

            if (offset >= this.end) {
                ch = EOI;
                break;
            }
            ch = bytes[offset++];
        }

        this.ch = (char) ch;
        this.offset = offset;

        if (ch == '/') {
            skipComment();
        }
    }

    @Override
    public String readString() {
        final byte[] bytes = this.bytes;
        if (ch == '"' || ch == '\'') {
            char quote = this.ch;
            int valueLength;
            int off = this.offset;
            final int start = off;
            boolean ascii = true;
            valueEscape = false;

            for (int i = 0; ; ++i) {
                if (off >= end) {
                    throw new JSONException("invalid escape character EOI");
                }

                int c = bytes[off];
                if (c == '\\') {
                    valueEscape = true;
                    c = bytes[++off];
                    switch (c) {
                        case 'u': {
                            off += 4;
                            break;
                        }
                        case 'x': {
                            off += 2;
                            break;
                        }
                        default:
                            break;
                    }
                    off++;
                    continue;
                }

                if (c >= 0) {
                    if (c == quote) {
                        valueLength = i;
                        break;
                    }
                    off++;
                } else {
                    switch ((c & 0xFF) >> 4) {
                        case 0b1100:
                        case 0b1101: {
                            /* 110x xxxx   10xx xxxx*/
                            off += 2;
                            ascii = false;
                            break;
                        }
                        case 0b1110: {
                            off += 3;
                            ascii = false;
                            break;
                        }
                        default: {
                            /* 10xx xxxx,  1111 xxxx */
                            if ((c >> 3) == -2) {
                                off += 4;
                                i++;
                                ascii = false;
                                break;
                            }

                            throw new JSONException("malformed input around byte " + off);
                        }
                    }
                }
            }

            String str;
            if (valueEscape) {
                char[] charBuf = this.charBuf;
                if (charBuf == null) {
                    this.charBuf = charBuf = CHARS_UPDATER.getAndSet(cacheItem, null);
                }
                if (charBuf == null || charBuf.length < valueLength) {
                    this.charBuf = charBuf = new char[valueLength];
                }

                off = start;
                for (int i = 0; ; ++i) {
                    int c = bytes[off];
                    if (c == '\\') {
                        c = bytes[++off];
                        if (c != '\\' && c != '"') {
                            switch (c) {
                                case 'u': {
                                    c = char4(bytes[off + 1], bytes[off + 2], bytes[off + 3], bytes[off + 4]);
                                    off += 4;
                                    break;
                                }
                                case 'x': {
                                    c = char2(bytes[off + 1], bytes[off + 2]);
                                    off += 2;
                                    break;
                                }
                                case 'b':
                                    c = '\b';
                                    break;
                                case 't':
                                    c = '\t';
                                    break;
                                case 'n':
                                    c = '\n';
                                    break;
                                case 'f':
                                    c = '\f';
                                    break;
                                case 'r':
                                    c = '\r';
                                    break;
                                default:
                                    c = char1(c);
                                    break;
                            }
                        }
                        charBuf[i] = (char) c;
                        off++;
                    } else if (c == quote) {
                        break;
                    } else {
                        if (c >= 0) {
                            charBuf[i] = (char) c;
                            off++;
                        } else {
                            switch ((c & 0xFF) >> 4) {
                                case 0b1100:
                                case 0b1101: {
                                    /* 110x xxxx   10xx xxxx*/
                                    charBuf[i] = (char) (((c & 0x1F) << 6) | (bytes[off + 1] & 0x3F));
                                    off += 2;
                                    break;
                                }
                                case 0b1110: {
                                    charBuf[i] = (char) (((c & 0x0F) << 12)
                                            | ((bytes[off + 1] & 0x3F) << 6)
                                            | ((bytes[off + 2] & 0x3F)));
                                    off += 3;
                                    break;
                                }
                                default: {
                                    /* 10xx xxxx,  1111 xxxx */
                                    if ((c >> 3) == -2) {
                                        int c2 = bytes[off + 1];
                                        int c3 = bytes[off + 2];
                                        int c4 = bytes[off + 3];
                                        off += 4;
                                        int uc = ((c << 18) ^
                                                (c2 << 12) ^
                                                (c3 << 6) ^
                                                (c4 ^ (((byte) 0xF0 << 18) ^
                                                        ((byte) 0x80 << 12) ^
                                                        ((byte) 0x80 << 6) ^
                                                        ((byte) 0x80))));

                                        if (((c2 & 0xc0) != 0x80 || (c3 & 0xc0) != 0x80 || (c4 & 0xc0) != 0x80) // isMalformed4
                                                ||
                                                // shortest form check
                                                !(uc >= 0x010000 && uc < 0X10FFFF + 1) // !Character.isSupplementaryCodePoint(uc)
                                        ) {
                                            throw new JSONException("malformed input around byte " + off);
                                        } else {
                                            charBuf[i++] = (char) ((uc >>> 10) + ('\uD800' - (0x010000 >>> 10))); // Character.highSurrogate(uc);
                                            charBuf[i] = (char) ((uc & 0x3ff) + '\uDC00'); // Character.lowSurrogate(uc);
                                        }
                                        break;
                                    }

                                    throw new JSONException("malformed input around byte " + off);
                                }
                            }
                        }
                    }
                }

                str = new String(charBuf, 0, valueLength);
                if (charBuf.length < CACHE_THRESHOLD) {
                    CHARS_UPDATER.lazySet(cacheItem, charBuf);
                }
            } else if (ascii) {
                int len = off - start;

                if (ANDROID_SDK_INT < 34) {
                    char[] charBuf = this.charBuf;
                    if (charBuf == null) {
                        this.charBuf = charBuf = CHARS_UPDATER.getAndSet(cacheItem, null);
                    }
                    if (charBuf == null || charBuf.length < len) {
                        this.charBuf = charBuf = new char[len];
                    }
                    for (int i = 0; i < len; i++) {
                        charBuf[i] = (char) bytes[start + i];
                    }
                    str = new String(charBuf, 0, len);
                } else {
                    str = new String(bytes, start, len, StandardCharsets.ISO_8859_1);
                }
            } else {
                str = new String(bytes, start, off - start, StandardCharsets.UTF_8);
            }

            if ((context.features & Feature.TrimString.mask) != 0) {
                str = str.trim();
            }

            int ch = ++off == end ? EOI : bytes[off++];
            while (ch <= ' ' && (1L << ch & SPACE) != 0) {
                ch = off == end ? EOI : bytes[off++];
            }

            if (comma = ch == ',') {
                ch = off == end ? EOI : bytes[off++];
                while (ch <= ' ' && (1L << ch & SPACE) != 0) {
                    ch = off == end ? EOI : bytes[off++];
                }
            }

            this.ch = (char) ch;
            this.offset = off;
            return str;
        }

        switch (ch) {
            case '[':
                return toString(
                        readArray());
            case '{':
                return toString(
                        readObject());
            case '-':
            case '+':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                readNumber0();
                Number number = getNumber();
                return number.toString();
            case 't':
            case 'f':
                boolValue = readBoolValue();
                return boolValue ? "true" : "false";
            case 'n': {
                readNull();
                return null;
            }
            default:
                throw new JSONException(info("illegal input : " + ch));
        }
    }

    @Override
    public final void readNumber0() {
        boolean valid = false;
        this.wasNull = false;
        this.mag0 = 0;
        this.mag1 = 0;
        this.mag2 = 0;
        this.mag3 = 0;
        this.negative = false;
        this.exponent = 0;
        this.scale = 0;
        int firstOffset = offset;

        final byte[] bytes = this.bytes;
        int ch = this.ch;
        int offset = this.offset;
        int quote = '\0';
        if (ch == '"' || ch == '\'') {
            quote = ch;
            ch = (char) bytes[offset++];

            if (ch == quote) {
                ch = offset == end ? EOI : bytes[offset++];
                while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                    ch = offset == end ? EOI : bytes[offset++];
                }

                this.ch = (char) ch;
                this.offset = offset;
                comma = nextIfComma();
                wasNull = true;
                return;
            }
        }

        final int start = offset;

        final int multmin;
        if (ch == '-') {
            if (offset == end) {
                throw new JSONException(info("illegal input"));
            }
            multmin = -214748364; // limit / 10;
            negative = true;
            ch = bytes[offset++];
        } else {
            if (ch == '+') {
                if (offset == end) {
                    throw new JSONException(info("illegal input"));
                }
                ch = bytes[offset++];
            }
            multmin = -214748364; // limit / 10;
        }

        // if (result < limit + digit) {
        boolean intOverflow = false;
        valueType = JSON_TYPE_INT;
        while (ch >= '0' && ch <= '9') {
            valid = true;
            if (!intOverflow) {
                int digit = ch - '0';
                mag3 *= 10;
                if (mag3 < multmin) {
                    intOverflow = true;
                } else {
                    mag3 -= digit;
                    if (mag3 < multmin) {
                        intOverflow = true;
                    }
                }
            }
            if (offset == end) {
                ch = EOI;
                offset++;
                break;
            }
            ch = bytes[offset++];
        }

        if (ch == '.') {
            valueType = JSON_TYPE_DEC;
            ch = bytes[offset++];
            while (ch >= '0' && ch <= '9') {
                valid = true;
                if (!intOverflow) {
                    int digit = ch - '0';
                    mag3 *= 10;
                    if (mag3 < multmin) {
                        intOverflow = true;
                    } else {
                        mag3 -= digit;
                        if (mag3 < multmin) {
                            intOverflow = true;
                        }
                    }
                }

                this.scale++;
                if (offset == end) {
                    ch = EOI;
                    offset++;
                    break;
                }
                ch = bytes[offset++];
            }
        }

        if (intOverflow) {
            int numStart = negative ? start : start - 1;
            int numDigits = scale > 0 ? offset - 2 - numStart : offset - 1 - numStart;
            if (numDigits > 38) {
                valueType = JSON_TYPE_BIG_DEC;
                if (negative) {
                    numStart--;
                }
                stringValue = new String(bytes, numStart, offset - 1 - numStart);
            } else {
                bigInt(bytes, numStart, offset - 1);
            }
        } else {
            mag3 = -mag3;
        }

        if (ch == 'e' || ch == 'E') {
            boolean negativeExp = false;
            int expValue = 0;
            ch = bytes[offset++];

            if (ch == '-') {
                negativeExp = true;
                ch = bytes[offset++];
            } else if (ch == '+') {
                ch = (char) bytes[offset++];
            }

            while (ch >= '0' && ch <= '9') {
                valid = true;
                int byteVal = (ch - '0');
                expValue = expValue * 10 + byteVal;
                if (expValue > MAX_EXP) {
                    throw new JSONException("too large exp value : " + expValue);
                }

                if (offset == end) {
                    ch = EOI;
                    break;
                }
                ch = bytes[offset++];
            }

            if (negativeExp) {
                expValue = -expValue;
            }

            this.exponent = (short) expValue;
            valueType = JSON_TYPE_DEC;
        }

        if (offset == start) {
            if (ch == 'n') {
                if (bytes[offset] == 'u'
                        && bytes[offset + 1] == 'l'
                        && bytes[offset + 2] == 'l'
                ) {
                    offset += 3;
                    valid = true;
                    wasNull = true;
                    valueType = JSON_TYPE_NULL;
                    ch = offset == end ? EOI : bytes[offset++];
                }
            } else if (ch == 't' && bytes[offset] == 'r' && bytes[offset + 1] == 'u' && bytes[offset + 2] == 'e') {
                offset += 3;
                valid = true;
                boolValue = true;
                valueType = JSON_TYPE_BOOL;
                ch = offset == end ? EOI : bytes[offset++];
            } else if (ch == 'f'
                    && bytes[offset] == 'a'
                    && bytes[offset + 1] == 'l'
                    && bytes[offset + 2] == 's'
                    && bytes[offset + 3] == 'e') {
                valid = true;
                offset += 4;
                boolValue = false;
                valueType = JSON_TYPE_BOOL;
                ch = offset == end ? EOI : bytes[offset++];
            } else if (ch == '{' && quote == 0) {
                this.offset = offset;
                this.ch = (char) ch;
                this.complex = readObject();
                valueType = JSON_TYPE_OBJECT;
                return;
            } else if (ch == '[' && quote == 0) {
                this.offset = offset;
                this.ch = (char) ch;
                this.complex = readArray();
                valueType = JSON_TYPE_ARRAY;
                return;
            }
        }

        if (quote != 0) {
            if (ch != quote) {
                this.offset = firstOffset;
                this.ch = (char) quote;
                readString0();
                valueType = JSON_TYPE_STRING;
                return;
            }

            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S') {
            switch (ch) {
                case 'B':
                    valueType = JSON_TYPE_INT8;
                    break;
                case 'S':
                    valueType = JSON_TYPE_INT16;
                    break;
                case 'L':
                    valueType = JSON_TYPE_INT64;
                    break;
                case 'F':
                    valueType = JSON_TYPE_FLOAT;
                    break;
                case 'D':
                    valueType = JSON_TYPE_DOUBLE;
                    break;
                default:
                    break;
            }
            ch = offset == end ? EOI : bytes[offset++];
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        if (!valid) {
            throw new JSONException(info("illegal input error"));
        }

        this.offset = offset;
        this.ch = (char) ch;
    }

    @Override
    public final boolean readIfNull() {
        final byte[] bytes = this.bytes;
        int ch = this.ch;
        int offset = this.offset;
        if (ch == 'n'
                && bytes[offset] == 'u'
                && bytes[offset + 1] == 'l'
                && bytes[offset + 2] == 'l') {
            if (offset + 3 == end) {
                ch = EOI;
            } else {
                ch = (char) bytes[offset + 3];
            }
            offset += 4;
        } else {
            return false;
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }
        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : (char) bytes[offset++];

            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        this.offset = offset;
        this.ch = (char) ch;
        return true;
    }

    @Override
    public final boolean isArray() {
        return this.ch == '[';
    }

    @Override
    public final boolean isNull() {
        return ch == 'n' && offset < end && bytes[offset] == 'u';
    }

    @Override
    public final Date readNullOrNewDate() {
        final byte[] bytes = this.bytes;
        int ch;
        int offset = this.offset;

        Date date = null;
        if (offset + 2 < end
                && bytes[offset] == 'u'
                && bytes[offset + 1] == 'l'
                && bytes[offset + 2] == 'l') {
            if (offset + 3 == end) {
                ch = EOI;
            } else {
                ch = bytes[offset + 3];
            }
            offset += 4;
        } else if (offset + 1 < end
                && bytes[offset] == 'e'
                && bytes[offset + 1] == 'w') {
            if (offset + 3 == end) {
                ch = EOI;
            } else {
                ch = bytes[offset + 2];
            }
            offset += 3;

            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }

            if (offset + 4 < end
                    && ch == 'D'
                    && bytes[offset] == 'a'
                    && bytes[offset + 1] == 't'
                    && bytes[offset + 2] == 'e') {
                if (offset + 3 == end) {
                    ch = EOI;
                } else {
                    ch = bytes[offset + 3];
                }
                offset += 4;
            } else {
                throw new JSONException("json syntax error, not match new Date" + offset);
            }

            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }

            if (ch != '(' || offset >= end) {
                throw new JSONException("json syntax error, not match new Date" + offset);
            }
            ch = bytes[offset++];

            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }

            this.ch = (char) ch;
            this.offset = offset;
            long millis = readInt64Value();

            ch = this.ch;
            offset = this.offset;

            if (ch != ')') {
                throw new JSONException("json syntax error, not match new Date" + offset);
            }
            ch = offset >= end ? EOI : bytes[offset++];

            date = new Date(millis);
        } else {
            throw new JSONException("json syntax error, not match null or new Date" + offset);
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }
        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        this.offset = offset;
        this.ch = (char) ch;
        return date;
    }

    @Override
    public final boolean nextIfNull() {
        if (ch == 'n' && offset + 2 < end && bytes[offset] == 'u') {
            this.readNull();
            return true;
        }
        return false;
    }

    @Override
    public final void readNull() {
        final byte[] bytes = this.bytes;
        int offset = this.offset;
        int ch;
        if (bytes[offset] == 'u'
                && bytes[offset + 1] == 'l'
                && bytes[offset + 2] == 'l') {
            offset += 3;
            ch = offset == end ? EOI : bytes[offset++];
        } else {
            throw new JSONException("json syntax error, not match null" + offset);
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset >= end ? EOI : bytes[offset++];
        }
        if (comma = (ch == ',')) {
            ch = offset >= end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset >= end ? EOI : bytes[offset++];
            }
        }
        this.ch = (char) ch;
        this.offset = offset;
    }

    @Override
    public final int getStringLength() {
        if (ch != '"' && ch != '\'') {
            throw new JSONException("date only support string input");
        }
        final char quote = ch;

        int len = 0;
        int i = offset;
        byte[] bytes = this.bytes;

        int i8 = i + 8;
        if (i8 < end && i8 < bytes.length) {
            if (bytes[i] != quote
                    && bytes[i + 1] != quote
                    && bytes[i + 2] != quote
                    && bytes[i + 3] != quote
                    && bytes[i + 4] != quote
                    && bytes[i + 5] != quote
                    && bytes[i + 6] != quote
                    && bytes[i + 7] != quote
            ) {
                i += 8;
                len += 8;
            }
        }

        for (; i < end; ++i, ++len) {
            if (bytes[i] == quote) {
                break;
            }
        }
        return len;
    }

    public final LocalDate readLocalDate() {
        final byte[] bytes = this.bytes;
        int offset = this.offset;
        if (ch == '"' || ch == '\'') {
            Context context = this.context;
            if (context.dateFormat == null
                    || context.formatyyyyMMddhhmmss19
                    || context.formatyyyyMMddhhmmssT19
                    || context.formatyyyyMMdd8
                    || context.formatISO8601
            ) {
                char quote = ch;
                int c10 = offset + 10;
                if (c10 < bytes.length
                        && c10 < end
                        && bytes[offset + 4] == '-'
                        && bytes[offset + 7] == '-'
                        && bytes[offset + 10] == quote
                ) {
                    byte y0 = bytes[offset];
                    byte y1 = bytes[offset + 1];
                    byte y2 = bytes[offset + 2];
                    byte y3 = bytes[offset + 3];
                    byte m0 = bytes[offset + 5];
                    byte m1 = bytes[offset + 6];
                    byte d0 = bytes[offset + 8];
                    byte d1 = bytes[offset + 9];

                    int year;
                    int month;
                    if (y0 >= '0' && y0 <= '9'
                            && y1 >= '0' && y1 <= '9'
                            && y2 >= '0' && y2 <= '9'
                            && y3 >= '0' && y3 <= '9'
                    ) {
                        year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
                        if (m0 >= '0' && m0 <= '9' && m1 >= '0' && m1 <= '9') {
                            month = (m0 - '0') * 10 + (m1 - '0');
                            int dom;
                            if (d0 >= '0' && d0 <= '9' && d1 >= '0' && d1 <= '9') {
                                dom = (d0 - '0') * 10 + (d1 - '0');

                                LocalDate ldt;
                                try {
                                    if (year == 0 && month == 0 && dom == 0) {
                                        ldt = null;
                                    } else {
                                        ldt = LocalDate.of(year, month, dom);
                                    }
                                } catch (DateTimeException ex) {
                                    throw new JSONException(info("read date error"), ex);
                                }

                                this.offset = offset + 11;
                                next();
                                if (comma = (this.ch == ',')) {
                                    next();
                                }
                                return ldt;
                            }
                        }
                    }
                }
            }
        }
        return super.readLocalDate();
    }

    public final OffsetDateTime readOffsetDateTime() {
        final byte[] bytes = this.bytes;
        final int offset = this.offset;
        final Context context = this.context;
        if (this.ch == '"' || this.ch == '\'') {
            if (context.dateFormat == null
                    || context.formatyyyyMMddhhmmss19
                    || context.formatyyyyMMddhhmmssT19
                    || context.formatyyyyMMdd8
                    || context.formatISO8601
            ) {
                char quote = this.ch;
                byte c10;
                int off21 = offset + 19;
                if (off21 < bytes.length
                        && off21 < end
                        && bytes[offset + 4] == '-'
                        && bytes[offset + 7] == '-'
                        && ((c10 = bytes[offset + 10]) == ' ' || c10 == 'T')
                        && bytes[offset + 13] == ':'
                        && bytes[offset + 16] == ':'
                ) {
                    byte y0 = bytes[offset];
                    byte y1 = bytes[offset + 1];
                    byte y2 = bytes[offset + 2];
                    byte y3 = bytes[offset + 3];
                    byte m0 = bytes[offset + 5];
                    byte m1 = bytes[offset + 6];
                    byte d0 = bytes[offset + 8];
                    byte d1 = bytes[offset + 9];
                    byte h0 = bytes[offset + 11];
                    byte h1 = bytes[offset + 12];
                    byte i0 = bytes[offset + 14];
                    byte i1 = bytes[offset + 15];
                    byte s0 = bytes[offset + 17];
                    byte s1 = bytes[offset + 18];

                    int year;
                    int month;
                    if (y0 >= '0' && y0 <= '9'
                            && y1 >= '0' && y1 <= '9'
                            && y2 >= '0' && y2 <= '9'
                            && y3 >= '0' && y3 <= '9'
                    ) {
                        year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10 + (y3 - '0');
                    } else {
                        ZonedDateTime zdt = readZonedDateTime();
                        return zdt == null ? null : zdt.toOffsetDateTime();
                    }

                    if (m0 >= '0' && m0 <= '9'
                            && m1 >= '0' && m1 <= '9'
                    ) {
                        month = (m0 - '0') * 10 + (m1 - '0');
                    } else {
                        ZonedDateTime zdt = readZonedDateTime();
                        return zdt == null ? null : zdt.toOffsetDateTime();
                    }

                    int dom;
                    if (d0 >= '0' && d0 <= '9'
                            && d1 >= '0' && d1 <= '9'
                    ) {
                        dom = (d0 - '0') * 10 + (d1 - '0');
                    } else {
                        ZonedDateTime zdt = readZonedDateTime();
                        return zdt == null ? null : zdt.toOffsetDateTime();
                    }

                    int hour;
                    if (h0 >= '0' && h0 <= '9'
                            && h1 >= '0' && h1 <= '9'
                    ) {
                        hour = (h0 - '0') * 10 + (h1 - '0');
                    } else {
                        ZonedDateTime zdt = readZonedDateTime();
                        return zdt == null ? null : zdt.toOffsetDateTime();
                    }

                    int minute;
                    if (i0 >= '0' && i0 <= '9'
                            && i1 >= '0' && i1 <= '9'
                    ) {
                        minute = (i0 - '0') * 10 + (i1 - '0');
                    } else {
                        ZonedDateTime zdt = readZonedDateTime();
                        return zdt == null ? null : zdt.toOffsetDateTime();
                    }

                    int second;
                    if (s0 >= '0' && s0 <= '9'
                            && s1 >= '0' && s1 <= '9'
                    ) {
                        second = (s0 - '0') * 10 + (s1 - '0');
                    } else {
                        ZonedDateTime zdt = readZonedDateTime();
                        return zdt == null ? null : zdt.toOffsetDateTime();
                    }

                    LocalDate localDate;
                    try {
                        if (year == 0 && month == 0 && dom == 0) {
                            localDate = null;
                        } else {
                            localDate = LocalDate.of(year, month, dom);
                        }
                    } catch (DateTimeException ex) {
                        throw new JSONException(info("read date error"), ex);
                    }

                    int nanoSize = -1;
                    int len = 0;
                    for (int start = offset + 19, i = start, end = offset + 31; i < end && i < this.end && i < bytes.length; ++i) {
                        if (bytes[i] == quote && bytes[i - 1] == 'Z') {
                            nanoSize = i - start - 2;
                            len = i - offset + 1;
                            break;
                        }
                    }
                    if (nanoSize != -1 || len == 21) {
                        int nano = nanoSize <= 0 ? 0 : DateUtils.readNanos(bytes, nanoSize, offset + 20);
                        LocalTime localTime = LocalTime.of(hour, minute, second, nano);
                        LocalDateTime ldt = LocalDateTime.of(localDate, localTime);
                        OffsetDateTime oft = OffsetDateTime.of(ldt, ZoneOffset.UTC);
                        this.offset += len;
                        next();
                        if (comma = (this.ch == ',')) {
                            next();
                        }
                        return oft;
                    }
                }
            }
        }
        ZonedDateTime zdt = readZonedDateTime();
        return zdt == null ? null : zdt.toOffsetDateTime();
    }

    @Override
    public final OffsetTime readOffsetTime() {
        final byte[] bytes = this.bytes;
        final int offset = this.offset;
        final Context context = this.context;
        if (this.ch == '"' || this.ch == '\'') {
            if (context.dateFormat == null) {
                char quote = this.ch;
                int off10 = offset + 8;
                if (off10 < bytes.length
                        && off10 < end
                        && bytes[offset + 2] == ':'
                        && bytes[offset + 5] == ':'
                ) {
                    byte h0 = bytes[offset];
                    byte h1 = bytes[offset + 1];
                    byte i0 = bytes[offset + 3];
                    byte i1 = bytes[offset + 4];
                    byte s0 = bytes[offset + 6];
                    byte s1 = bytes[offset + 7];

                    int hour;
                    if (h0 >= '0' && h0 <= '9'
                            && h1 >= '0' && h1 <= '9'
                    ) {
                        hour = (h0 - '0') * 10 + (h1 - '0');
                    } else {
                        throw new JSONException(this.info("illegal offsetTime"));
                    }

                    int minute;
                    if (i0 >= '0' && i0 <= '9'
                            && i1 >= '0' && i1 <= '9'
                    ) {
                        minute = (i0 - '0') * 10 + (i1 - '0');
                    } else {
                        throw new JSONException(this.info("illegal offsetTime"));
                    }

                    int second;
                    if (s0 >= '0' && s0 <= '9'
                            && s1 >= '0' && s1 <= '9'
                    ) {
                        second = (s0 - '0') * 10 + (s1 - '0');
                    } else {
                        throw new JSONException(this.info("illegal offsetTime"));
                    }

                    int nanoSize = -1;
                    int len = 0;
                    for (int start = offset + 8, i = start, end = offset + 25; i < end && i < this.end && i < bytes.length; ++i) {
                        byte b = bytes[i];
                        if (nanoSize == -1 && (b == 'Z' || b == '+' || b == '-')) {
                            nanoSize = i - start - 1;
                        }
                        if (b == quote) {
                            len = i - offset;
                            break;
                        }
                    }

                    int nano = nanoSize <= 0 ? 0 : DateUtils.readNanos(bytes, nanoSize, offset + 9);

                    ZoneOffset zoneOffset;
                    int zoneOffsetSize = len - 9 - nanoSize;
                    if (zoneOffsetSize <= 1) {
                        zoneOffset = ZoneOffset.UTC;
                    } else {
                        String zonedId = new String(bytes, offset + 9 + nanoSize, zoneOffsetSize);
                        zoneOffset = ZoneOffset.of(zonedId);
                    }
                    LocalTime localTime = LocalTime.of(hour, minute, second, nano);
                    OffsetTime oft = OffsetTime.of(localTime, zoneOffset);
                    this.offset += len + 2;
                    next();
                    if (comma = (this.ch == ',')) {
                        next();
                    }
                    return oft;
                }
            }
        }
        throw new JSONException(this.info("illegal offsetTime"));
    }

    @Override
    protected final ZonedDateTime readZonedDateTimeX(int len) {
        if (!isString()) {
            throw new JSONException("date only support string input");
        }

        if (len < 19) {
            return null;
        }

        ZonedDateTime zdt;
        if (len == 30 && bytes[offset + 29] == 'Z') {
            LocalDateTime ldt = DateUtils.parseLocalDateTime29(bytes, offset);
            zdt = ZonedDateTime.of(ldt, ZoneOffset.UTC);
        } else if (len == 29 && bytes[offset + 28] == 'Z') {
            LocalDateTime ldt = DateUtils.parseLocalDateTime28(bytes, offset);
            zdt = ZonedDateTime.of(ldt, ZoneOffset.UTC);
        } else if (len == 28 && bytes[offset + 27] == 'Z') {
            LocalDateTime ldt = DateUtils.parseLocalDateTime27(bytes, offset);
            zdt = ZonedDateTime.of(ldt, ZoneOffset.UTC);
        } else if (len == 27 && bytes[offset + 26] == 'Z') {
            LocalDateTime ldt = DateUtils.parseLocalDateTime26(bytes, offset);
            zdt = ZonedDateTime.of(ldt, ZoneOffset.UTC);
        } else {
            zdt = DateUtils.parseZonedDateTime(bytes, offset, len, context.zoneId);
        }

        if (zdt == null) {
            return null;
        }

        offset += (len + 1);
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return zdt;
    }

    @Override
    public final LocalDate readLocalDate8() {
        if (!isString()) {
            throw new JSONException("localDate only support string input");
        }

        LocalDate ldt;
        try {
            ldt = DateUtils.parseLocalDate8(bytes, offset);
        } catch (DateTimeException ex) {
            throw new JSONException(info("read date error"), ex);
        }

        offset += 9;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    public final LocalDate readLocalDate9() {
        if (!isString()) {
            throw new JSONException("localDate only support string input");
        }

        LocalDate ldt;
        try {
            ldt = DateUtils.parseLocalDate9(bytes, offset);
        } catch (DateTimeException ex) {
            throw new JSONException(info("read date error"), ex);
        }

        offset += 10;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    public final LocalDate readLocalDate10() {
        if (!isString()) {
            throw new JSONException("localDate only support string input");
        }

        LocalDate ldt;
        try {
            ldt = DateUtils.parseLocalDate10(bytes, offset);
        } catch (DateTimeException ex) {
            throw new JSONException(info("read date error"), ex);
        }
        if (ldt == null) {
            return null;
        }

        offset += 11;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    protected final LocalDate readLocalDate11() {
        if (!isString()) {
            throw new JSONException("localDate only support string input");
        }

        LocalDate ldt = DateUtils.parseLocalDate11(bytes, offset);
        if (ldt == null) {
            return null;
        }

        offset += 11;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    protected final LocalDateTime readLocalDateTime17() {
        if (!isString()) {
            throw new JSONException("date only support string input");
        }

        LocalDateTime ldt = DateUtils.parseLocalDateTime17(bytes, offset);
        if (ldt == null) {
            return null;
        }

        offset += 18;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    protected final LocalTime readLocalTime5() {
        if (ch != '"' && ch != '\'') {
            throw new JSONException("localTime only support string input");
        }

        LocalTime time = DateUtils.parseLocalTime5(bytes, offset);
        if (time == null) {
            return null;
        }

        offset += 6;
        next();
        if (comma = (ch == ',')) {
            next();
        }

        return time;
    }

    @Override
    protected final LocalTime readLocalTime8() {
        if (!isString()) {
            throw new JSONException("localTime only support string input");
        }

        LocalTime time = DateUtils.parseLocalTime8(bytes, offset);
        if (time == null) {
            return null;
        }

        offset += 9;
        next();
        if (comma = (ch == ',')) {
            next();
        }

        return time;
    }

    @Override
    protected final LocalTime readLocalTime9() {
        if (ch != '"' && ch != '\'') {
            throw new JSONException("localTime only support string input");
        }

        LocalTime time = DateUtils.parseLocalTime8(bytes, offset);
        if (time == null) {
            return null;
        }

        offset += 10;
        next();
        if (comma = (ch == ',')) {
            next();
        }

        return time;
    }

    @Override
    protected final LocalTime readLocalTime10() {
        if (!isString()) {
            throw new JSONException("localTime only support string input");
        }

        LocalTime time = DateUtils.parseLocalTime10(bytes, offset);
        if (time == null) {
            return null;
        }

        offset += 11;
        next();
        if (comma = (ch == ',')) {
            next();
        }

        return time;
    }

    @Override
    protected final LocalTime readLocalTime11() {
        if (!isString()) {
            throw new JSONException("localTime only support string input");
        }

        LocalTime time = DateUtils.parseLocalTime11(bytes, offset);
        if (time == null) {
            return null;
        }

        offset += 12;
        next();
        if (comma = (ch == ',')) {
            next();
        }

        return time;
    }

    @Override
    protected final LocalTime readLocalTime12() {
        if (!isString()) {
            throw new JSONException("localTime only support string input");
        }

        LocalTime time = DateUtils.parseLocalTime12(bytes, offset);
        if (time == null) {
            return null;
        }

        offset += 13;
        next();
        if (comma = (ch == ',')) {
            next();
        }

        return time;
    }

    @Override
    protected final LocalTime readLocalTime18() {
        if (!isString()) {
            throw new JSONException("localTime only support string input");
        }

        LocalTime time = DateUtils.parseLocalTime18(bytes, offset);
        if (time == null) {
            return null;
        }

        offset += 19;
        next();
        if (comma = (ch == ',')) {
            next();
        }

        return time;
    }

    @Override
    protected final LocalDateTime readLocalDateTime12() {
        if (!isString()) {
            throw new JSONException("date only support string input");
        }

        LocalDateTime ldt = DateUtils.parseLocalDateTime12(bytes, offset);
        if (ldt == null) {
            return null;
        }

        offset += 13;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    protected final LocalDateTime readLocalDateTime14() {
        if (!isString()) {
            throw new JSONException("date only support string input");
        }

        LocalDateTime ldt = DateUtils.parseLocalDateTime14(bytes, offset);
        if (ldt == null) {
            return null;
        }

        offset += 15;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    protected final LocalDateTime readLocalDateTime16() {
        if (!isString()) {
            throw new JSONException("date only support string input");
        }

        LocalDateTime ldt = DateUtils.parseLocalDateTime16(bytes, offset);
        if (ldt == null) {
            return null;
        }

        offset += 17;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    protected final LocalDateTime readLocalDateTime18() {
        if (!isString()) {
            throw new JSONException("date only support string input");
        }

        LocalDateTime ldt = DateUtils.parseLocalDateTime18(bytes, offset);

        offset += 19;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    protected final LocalDateTime readLocalDateTime19() {
        if (!isString()) {
            throw new JSONException("date only support string input");
        }

        LocalDateTime ldt = DateUtils.parseLocalDateTime19(bytes, offset);
        if (ldt == null) {
            return null;
        }

        offset += 20;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    protected final LocalDateTime readLocalDateTime20() {
        if (!isString()) {
            throw new JSONException("date only support string input");
        }

        LocalDateTime ldt = DateUtils.parseLocalDateTime20(bytes, offset);
        if (ldt == null) {
            return null;
        }

        offset += 21;
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    @Override
    public final long readMillis19() {
        char quote = ch;
        if (quote != '"' && quote != '\'') {
            throw new JSONException("date only support string input");
        }

        if (offset + 18 >= end) {
            wasNull = true;
            return 0;
        }

        long millis = DateUtils.parseMillis19(bytes, offset, context.zoneId);

        if (bytes[offset + 19] != quote) {
            throw new JSONException(info("illegal date input"));
        }
        offset += 20;
        next();
        if (comma = (ch == ',')) {
            next();
        }

        return millis;
    }

    @Override
    protected final LocalDateTime readLocalDateTimeX(int len) {
        if (!isString()) {
            throw new JSONException("date only support string input");
        }

        LocalDateTime ldt = DateUtils.parseLocalDateTimeX(bytes, offset, len);
        if (ldt == null) {
            return null;
        }

        offset += (len + 1);
        next();
        if (comma = (ch == ',')) {
            next();
        }
        return ldt;
    }

    public final BigDecimal readBigDecimal() {
        boolean valid = false;
        final byte[] bytes = this.bytes;
        int ch = this.ch;
        int offset = this.offset;
        boolean value = false;

        BigDecimal decimal = null;
        int quote = '\0';
        if (ch == '"' || ch == '\'') {
            quote = ch;
            ch = bytes[offset++];

            if (ch == quote) {
                this.ch = offset == end ? EOI : (char) bytes[offset++];
                this.offset = offset;
                nextIfComma();
                return null;
            }
        }

        final int start = offset;
        if (ch == '-') {
            negative = true;
            ch = bytes[offset++];
        } else {
            negative = false;
            if (ch == '+') {
                ch = bytes[offset++];
            }
        }

        valueType = JSON_TYPE_INT;
        boolean overflow = false;
        long longValue = 0;
        while (ch >= '0' && ch <= '9') {
            valid = true;
            if (!overflow) {
                long r = longValue * 10;
                if ((longValue | 10) >>> 31 == 0L || (r / 10 == longValue)) {
                    longValue = r + (ch - '0');
                } else {
                    overflow = true;
                }
            }

            if (offset == end) {
                ch = EOI;
                offset++;
                break;
            }
            ch = bytes[offset++];
        }

        this.scale = 0;
        if (ch == '.') {
            valueType = JSON_TYPE_DEC;
            ch = bytes[offset++];
            while (ch >= '0' && ch <= '9') {
                valid = true;
                this.scale++;
                if (!overflow) {
                    long r = longValue * 10;
                    if ((longValue | 10) >>> 31 == 0L || (r / 10 == longValue)) {
                        longValue = r + (ch - '0');
                    } else {
                        overflow = true;
                    }
                }

                if (offset == end) {
                    ch = EOI;
                    offset++;
                    break;
                }
                ch = bytes[offset++];
            }
        }

        int expValue = 0;
        if (ch == 'e' || ch == 'E') {
            boolean negativeExp;
            ch = bytes[offset++];
            if ((negativeExp = ch == '-') || ch == '+') {
                ch = bytes[offset++];
            }

            while (ch >= '0' && ch <= '9') {
                valid = true;
                int byteVal = (ch - '0');
                expValue = expValue * 10 + byteVal;
                if (expValue > MAX_EXP) {
                    throw new JSONException("too large exp value : " + expValue);
                }

                if (offset == end) {
                    ch = EOI;
                    offset++;
                    break;
                }
                ch = bytes[offset++];
            }

            if (negativeExp) {
                expValue = -expValue;
            }

            this.exponent = (short) expValue;
            valueType = JSON_TYPE_DEC;
        }

        if (offset == start) {
            if (ch == 'n' && bytes[offset++] == 'u' && bytes[offset++] == 'l' && bytes[offset++] == 'l') {
                if ((context.features & Feature.ErrorOnNullForPrimitives.mask) != 0) {
                    throw new JSONException(info("long value not support input null"));
                }

                wasNull = true;
                value = true;
                ch = offset == end ? EOI : bytes[offset];
                offset++;
                valid = true;
            } else if (ch == 't' && offset + 3 <= end && bytes[offset] == 'r' && bytes[offset + 1] == 'u' && bytes[offset + 2] == 'e') {
                valid = true;
                offset += 3;
                value = true;
                decimal = BigDecimal.ONE;
                ch = offset == end ? EOI : bytes[offset];
                offset++;
            } else if (ch == 'f' && offset + 4 <= end
                    && bytes[offset] == 'a'
                    && bytes[offset + 1] == 'l'
                    && bytes[offset + 2] == 's'
                    && bytes[offset + 3] == 'e') {
                valid = true;
                offset += 4;
                decimal = BigDecimal.ZERO;
                value = true;
                ch = offset == end ? EOI : bytes[offset];
                offset++;
            } else if (ch == '{' && quote == 0) {
                JSONObject jsonObject = new JSONObject();
                readObject(jsonObject, 0);
                wasNull = false;
                return decimal(jsonObject);
            } else if (ch == '[' && quote == 0) {
                List array = readArray();
                if (!array.isEmpty()) {
                    throw new JSONException(info());
                }
                wasNull = true;
                return null;
            }
        }

        int len = offset - start;

        if (quote != 0) {
            if (ch != quote) {
                String str = readString();
                try {
                    return TypeUtils.toBigDecimal(str);
                } catch (NumberFormatException e) {
                    throw new JSONException(info(e.getMessage()), e);
                }
            } else {
                ch = offset >= end ? EOI : bytes[offset++];
            }
        }
        if (!value) {
            if (expValue == 0 && !overflow && longValue != 0) {
                decimal = BigDecimal.valueOf(negative ? -longValue : longValue, scale);
                value = true;
            }

            if (!value) {
                decimal = TypeUtils.parseBigDecimal(bytes, start - 1, len);
            }

            if (ch == 'L' || ch == 'F' || ch == 'D' || ch == 'B' || ch == 'S') {
                ch = offset >= end ? EOI : bytes[offset++];
            }
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            // next inline
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        if (!valid) {
            throw new JSONException(info("illegal input error"));
        }

        this.ch = (char) ch;
        this.offset = offset;
        return decimal;
    }

    @Override
    public final UUID readUUID() {
        int ch = this.ch;
        if (ch == 'n') {
            readNull();
            return null;
        }

        if (ch != '"' && ch != '\'') {
            throw new JSONException(info("syntax error, can not read uuid"));
        }
        final int quote = ch;
        final byte[] bytes = this.bytes;
        int offset = this.offset;
        long hi = 0, lo = 0;
        if (offset + 36 < bytes.length
                && bytes[offset + 36] == quote
                && bytes[offset + 8] == '-'
                && bytes[offset + 13] == '-'
                && bytes[offset + 18] == '-'
                && bytes[offset + 23] == '-'
        ) {
            for (int i = 0; i < 8; i++) {
                hi = (hi << 4) + UUID_VALUES[bytes[offset + i] - '0'];
            }
            for (int i = 9; i < 13; i++) {
                hi = (hi << 4) + UUID_VALUES[bytes[offset + i] - '0'];
            }
            for (int i = 14; i < 18; i++) {
                hi = (hi << 4) + UUID_VALUES[bytes[offset + i] - '0'];
            }

            for (int i = 19; i < 23; i++) {
                lo = (lo << 4) + UUID_VALUES[bytes[offset + i] - '0'];
            }
            for (int i = 24; i < 36; i++) {
                lo = (lo << 4) + UUID_VALUES[bytes[offset + i] - '0'];
            }
            offset += 37;
        } else if (offset + 32 < bytes.length && bytes[offset + 32] == quote) {
            for (int i = 0; i < 16; i++) {
                hi = (hi << 4) + UUID_VALUES[bytes[offset + i] - '0'];
            }
            for (int i = 16; i < 32; i++) {
                lo = (lo << 4) + UUID_VALUES[bytes[offset + i] - '0'];
            }
            offset += 33;
        } else {
            String str = readString();
            if (str.isEmpty()) {
                return null;
            }
            return UUID.fromString(str);
        }

        ch = offset == end ? EOI : bytes[offset++];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        this.offset = offset;
        if (comma = (ch == ',')) {
            next();
        } else {
            this.ch = (char) ch;
        }

        return new UUID(hi, lo);
    }

    @Override
    public final String readPattern() {
        if (ch != '/') {
            throw new JSONException("illegal pattern");
        }

        final byte[] bytes = this.bytes;
        int offset = this.offset;
        int start = offset;
        for (; offset < end; ++offset) {
            if (bytes[offset] == '/') {
                break;
            }
        }
        String str = new String(bytes, start, offset - start, UTF_8);
        int ch = ++offset == end ? EOI : bytes[offset++];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : bytes[offset++];
            }
        }

        this.offset = offset;
        this.ch = (char) ch;

        return str;
    }

    @Override
    public boolean nextIfNullOrEmptyString() {
        final char first = this.ch;
        final int end = this.end;
        int offset = this.offset;
        byte[] bytes = this.bytes;
        if (first == 'n'
                && offset + 2 < end
                && bytes[offset] == 'u'
                && bytes[offset + 1] == 'l'
                && bytes[offset + 2] == 'l'
        ) {
            offset += 3;
        } else if ((first == '"' || first == '\'') && offset < end && bytes[offset] == first) {
            offset++;
        } else {
            return false;
        }

        int ch = offset == end ? EOI : bytes[offset++];

        while (ch >= 0 && ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        while (ch >= 0 && ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch < 0) {
            char_utf8(ch, offset);
            return true;
        }

        this.offset = offset;
        this.ch = (char) ch;
        return true;
    }

    @Override
    public final boolean nextIfMatchIdent(char c0, char c1, char c2) {
        if (ch != c0) {
            return false;
        }

        final byte[] bytes = this.bytes;
        int offset = this.offset;
        if (offset + 2 > end || bytes[offset] != c1 || bytes[offset + 1] != c2) {
            return false;
        }

        offset += 2;
        int ch = offset == end ? EOI : bytes[offset++];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }
        if (offset == this.offset + 3 && ch != EOI && ch != '(' && ch != '[' && ch != ']' && ch != ')' && ch != ':' && ch != ',') {
            return false;
        }

        this.offset = offset;
        this.ch = (char) ch;
        return true;
    }

    @Override
    public final boolean nextIfMatchIdent(char c0, char c1, char c2, char c3) {
        if (ch != c0) {
            return false;
        }

        final byte[] bytes = this.bytes;
        int offset = this.offset;
        if (offset + 3 > end
                || bytes[offset] != c1
                || bytes[offset + 1] != c2
                || bytes[offset + 2] != c3) {
            return false;
        }

        offset += 3;
        int ch = offset == end ? EOI : bytes[offset++];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }
        if (offset == this.offset + 4 && ch != EOI && ch != '(' && ch != '[' && ch != ']' && ch != ')' && ch != ':' && ch != ',') {
            return false;
        }

        this.offset = offset;
        this.ch = (char) ch;
        return true;
    }

    @Override
    public final boolean nextIfMatchIdent(char c0, char c1, char c2, char c3, char c4) {
        if (ch != c0) {
            return false;
        }

        final byte[] bytes = this.bytes;
        int offset = this.offset;
        if (offset + 4 > end
                || bytes[offset] != c1
                || bytes[offset + 1] != c2
                || bytes[offset + 2] != c3
                || bytes[offset + 3] != c4) {
            return false;
        }

        offset += 4;
        int ch = offset == end ? EOI : bytes[offset++];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }
        if (offset == this.offset + 5 && ch != EOI && ch != '(' && ch != '[' && ch != ']' && ch != ')' && ch != ':' && ch != ',') {
            return false;
        }

        this.offset = offset;
        this.ch = (char) ch;
        return true;
    }

    @Override
    public final boolean nextIfMatchIdent(char c0, char c1, char c2, char c3, char c4, char c5) {
        if (ch != c0) {
            return false;
        }

        final byte[] bytes = this.bytes;
        int offset = this.offset;
        if (offset + 5 > end
                || bytes[offset] != c1
                || bytes[offset + 1] != c2
                || bytes[offset + 2] != c3
                || bytes[offset + 3] != c4
                || bytes[offset + 4] != c5) {
            return false;
        }

        offset += 5;
        int ch = offset == end ? EOI : bytes[offset++];
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }
        if (offset == this.offset + 6 && ch != EOI && ch != '(' && ch != '[' && ch != ']' && ch != ')' && ch != ':' && ch != ',') {
            return false;
        }

        this.offset = offset;
        this.ch = (char) ch;
        return true;
    }

    @Override
    public final byte[] readHex() {
        int offset = this.offset;
        final byte[] bytes = this.bytes;
        int ch = this.ch;
        if (ch == 'x') {
            ch = offset == end ? EOI : bytes[offset++];
        }

        final int quote = ch;
        if (quote != '\'' && quote != '"') {
            throw new JSONException("illegal state. " + ch);
        }
        int start = offset;
        ch = offset == end ? EOI : bytes[offset++];

        for (; ; ) {
            if ((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F')) {
                // continue;
            } else if (ch == quote) {
                ch = offset == end ? EOI : bytes[offset++];
                break;
            } else {
                throw new JSONException("illegal state. " + ch);
            }
            ch = offset == end ? EOI : bytes[offset++];
        }

        int len = offset - start - 2;
        if (ch == EOI) {
            len++;
        }

        if (len % 2 != 0) {
            throw new JSONException("illegal state. " + len);
        }

        byte[] hex = new byte[len / 2];
        for (int i = 0; i < hex.length; ++i) {
            byte c0 = bytes[start + i * 2];
            byte c1 = bytes[start + i * 2 + 1];

            int b0 = c0 - (c0 <= 57 ? 48 : 55);
            int b1 = c1 - (c1 <= 57 ? 48 : 55);
            hex[i] = (byte) ((b0 << 4) | b1);
        }

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : bytes[offset++];
        }

        if (ch != ',' || offset >= end) {
            this.offset = offset;
            this.ch = (char) ch;
            return hex;
        }

        comma = true;
        ch = offset == end ? EOI : bytes[offset++];
        while (ch == '\0' || (ch <= ' ' && ((1L << ch) & SPACE) != 0)) {
            ch = offset == end ? EOI : bytes[offset++];
        }
        this.offset = offset;
        this.ch = (char) ch;
        if (this.ch == '/') {
            skipComment();
        }

        return hex;
    }

    @Override
    public boolean isReference() {
        // should be codeSize <= FreqInlineSize 325
        final byte[] bytes = this.bytes;
        int ch = this.ch;
        int offset = this.offset;

        if (ch != '{') {
            return false;
        }

        if (offset == end) {
            return false;
        }

        ch = bytes[offset];
        while (ch >= 0 && ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            offset++;
            if (offset >= end) {
                return false;
            }
            ch = bytes[offset];
        }

        int quote = ch;
        if ((quote != '"' && quote != '\'')
                || this.offset + 5 >= end
                || (bytes[offset + 1] != '$'
                || bytes[offset + 2] != 'r'
                || bytes[offset + 3] != 'e'
                || bytes[offset + 4] != 'f'
                || bytes[offset + 5] != quote
                || offset + 6 >= end)
        ) {
            return false;
        }

        offset += 6;
        ch = bytes[offset];
        while (ch >= 0 && ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            if (++offset >= end) {
                return false;
            }
            ch = bytes[offset];
        }

        if (ch != ':' || offset + 1 >= end) {
            return false;
        }

        ch = bytes[++offset];
        while (ch >= 0 && ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            if (++offset >= end) {
                return false;
            }
            ch = bytes[offset];
        }

        if (ch != quote || (offset + 1 < end && bytes[offset + 1] == '#')) {
            return false;
        }

        this.referenceBegin = offset;
        return true;
    }

    @Override
    public final String readReference() {
        if (referenceBegin == end) {
            return null;
        }
        final byte[] chars = this.bytes;
        this.offset = referenceBegin;
        this.ch = (char) chars[offset++];

        String reference = readString();

        int ch = this.ch;
        int offset = this.offset;
        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : chars[offset++];
        }

        if (ch != '}') {
            throw new JSONException("illegal reference : " + reference);
        }

        ch = offset == end ? EOI : chars[offset++];

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset == end ? EOI : chars[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset == end ? EOI : chars[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset == end ? EOI : chars[offset++];
            }
        }

        this.ch = (char) ch;
        this.offset = offset;

        return reference;
    }

    public final boolean readBoolValue() {
        wasNull = false;
        boolean val;
        final byte[] bytes = this.bytes;
        int offset = this.offset;
        int ch = this.ch;
        if (ch == 't'
                && offset + 2 < bytes.length
                && bytes[offset] == 'r'
                && bytes[offset + 1] == 'u'
                && bytes[offset + 2] == 'e'
        ) {
            offset += 3;
            val = true;
        } else if (ch == 'f'
                && offset + 3 < bytes.length
                && bytes[offset] == 'a'
                && bytes[offset + 1] == 'l'
                && bytes[offset + 2] == 's'
                && bytes[offset + 3] == 'e'
        ) {
            offset += 4;
            val = false;
        } else if (ch == '-' || (ch >= '0' && ch <= '9')) {
            readNumber();
            if (valueType == JSON_TYPE_INT) {
                if ((context.features & Feature.NonZeroNumberCastToBooleanAsTrue.mask) != 0) {
                    return mag0 != 0 || mag1 != 0 || mag2 != 0 || mag3 != 0;
                } else {
                    return mag0 == 0
                            && mag1 == 0
                            && mag2 == 0
                            && mag3 == 1;
                }
            }
            return false;
        } else if (ch == 'n' && offset + 2 < bytes.length
                && bytes[offset] == 'u'
                && bytes[offset + 1] == 'l'
                && bytes[offset + 2] == 'l'
        ) {
            if ((context.features & Feature.ErrorOnNullForPrimitives.mask) != 0) {
                throw new JSONException(info("boolean value not support input null"));
            }

            wasNull = true;
            offset += 3;
            val = false;
        } else if (ch == '"') {
            if (offset + 1 < bytes.length
                    && bytes[offset + 1] == '"'
            ) {
                byte c0 = bytes[offset];
                offset += 2;
                if (c0 == '0' || c0 == 'N') {
                    val = false;
                } else if (c0 == '1' || c0 == 'Y') {
                    val = true;
                } else {
                    throw new JSONException("can not convert to boolean : " + c0);
                }
            } else {
                String str = readString();
                if ("true".equalsIgnoreCase(str)) {
                    return true;
                }

                if ("false".equalsIgnoreCase(str)) {
                    return false;
                }

                if (str.isEmpty() || "null".equalsIgnoreCase(str)) {
                    wasNull = true;
                    return false;
                }
                throw new JSONException("can not convert to boolean : " + str);
            }
        } else {
            throw new JSONException("syntax error : " + ch);
        }

        ch = offset == end ? EOI : (char) bytes[offset++];

        while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
            ch = offset >= end ? EOI : bytes[offset++];
        }

        if (comma = (ch == ',')) {
            ch = offset >= end ? EOI : bytes[offset++];
            while (ch <= ' ' && ((1L << ch) & SPACE) != 0) {
                ch = offset >= end ? EOI : bytes[offset++];
            }
        }
        this.offset = offset;
        this.ch = (char) ch;

        return val;
    }

    @Override
    public final String info(String message) {
        int line = 1, column = 0;
        for (int i = 0; i < offset && i < end; i++, column++) {
            if (bytes[i] == '\n') {
                column = 1;
                line++;
            }
        }

        StringBuilder buf = new StringBuilder();

        if (message != null && !message.isEmpty()) {
            buf.append(message).append(", ");
        }

        buf.append("offset ").append(offset)
                .append(", character ").append(ch)
                .append(", line ").append(line)
                .append(", column ").append(column)
                .append(", fastjson-version ").append(JSON.VERSION)
                .append(line > 1 ? '\n' : ' ');

        String str = new String(bytes, this.start, length < 65535 ? length : 65535);
        buf.append(str);
        return buf.toString();
    }

    @Override
    public final void close() {
        CacheItem cacheItem = this.cacheItem;
        final byte[] byteBuf = this.byteBuf;
        if (byteBuf != null && byteBuf.length < CACHE_THRESHOLD) {
            BYTES_UPDATER.lazySet(cacheItem, byteBuf);
        }

        final char[] charBuf = this.charBuf;
        if (charBuf != null && charBuf.length < CACHE_THRESHOLD) {
            CHARS_UPDATER.lazySet(cacheItem, charBuf);
        }

        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // ignored
            }
        }
    }
}
