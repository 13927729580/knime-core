/* 
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2006
 * Universitaet Konstanz, Germany.
 * Lehrstuhl fuer Angewandte Informatik
 * Prof. Dr. Michael R. Berthold
 * 
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner.
 * -------------------------------------------------------------------
 * 
 * History
 *   Aug 31, 2006 (wiswedel): created
 */
package org.knime.core.data.container;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.Flushable;
import java.io.IOException;

/**
 * Wrapper class of a DataOutputStream that also allows to write UTF strings
 * longer than 65535. This class delegates all method calles, except for 
 * the writeUTF method, an delegates it to an underlying output stream. 
 * 
 * <p>The writeUTF method of this class uses a longer header (10 bytes in total)
 * to encode the utf length when processing long strings. This class has been
 * written to overcome a limitation in java ... which is not going to be fixed,
 * see also the sun bug report at 
 * <a href="http://bugs.sun.com/bugdatabase">
 * http://bugs.sun.com/bugdatabase</a>, bug id 4025564.
 * 
 * @see DataOutputStream#writeUTF(String)
 * @author wiswedel, University of Konstanz
 */
public class LongUTFDataOutputStream 
    implements DataOutput, Closeable, Flushable {
    
    private final DataOutputStream m_output;
    
    /** Wraps the DataOutputStream argument and delegates all method calls 
     * (except the writeUTF method) to it.
     * @param output The output stream to wrap.
     * @throws NullPointerException If the argument is <code>null</code>.
     */
    public LongUTFDataOutputStream(final DataOutputStream output) {
        if (output == null) {
            throw new NullPointerException("Argument must not be null.");
        }
        m_output = output;
    }

    /**
     * @throws IOException
     * @see java.io.FilterOutputStream#close()
     */
    public void close() throws IOException {
        m_output.close();
    }

    /**
     * @throws IOException
     * @see java.io.DataOutputStream#flush()
     */
    public void flush() throws IOException {
        m_output.flush();
    }

    /**
     * @param b
     * @param off
     * @param len
     * @throws IOException
     * @see java.io.DataOutputStream#write(byte[], int, int)
     */
    public void write(final byte[] b, final int off, final int len)
        throws IOException {
        m_output.write(b, off, len);
    }

    /**
     * @param b
     * @throws IOException
     * @see java.io.FilterOutputStream#write(byte[])
     */
    public void write(final byte[] b) throws IOException {
        m_output.write(b);
    }

    /**
     * @param b
     * @throws IOException
     * @see java.io.DataOutputStream#write(int)
     */
    public void write(final int b) throws IOException {
        m_output.write(b);
    }

    /**
     * @param v
     * @throws IOException
     * @see java.io.DataOutput#writeBoolean(boolean)
     */
    public void writeBoolean(final boolean v) throws IOException {
        m_output.writeBoolean(v);
    }

    /**
     * @param v
     * @throws IOException
     * @see java.io.DataOutput#writeByte(int)
     */
    public void writeByte(final int v) throws IOException {
        m_output.writeByte(v);
    }

    /**
     * @param s
     * @throws IOException
     * @see java.io.DataOutput#writeBytes(java.lang.String)
     */
    public void writeBytes(final String s) throws IOException {
        m_output.writeBytes(s);
    }

    /**
     * @param v
     * @throws IOException
     * @see java.io.DataOutput#writeChar(int)
     */
    public void writeChar(final int v) throws IOException {
        m_output.writeChar(v);
    }

    /**
     * @param s
     * @throws IOException
     * @see java.io.DataOutput#writeChars(java.lang.String)
     */
    public void writeChars(final String s) throws IOException {
        m_output.writeChars(s);
    }

    /**
     * @param v
     * @throws IOException
     * @see java.io.DataOutput#writeDouble(double)
     */
    public void writeDouble(final double v) throws IOException {
        m_output.writeDouble(v);
    }

    /**
     * @param v
     * @throws IOException
     * @see java.io.DataOutput#writeFloat(float)
     */
    public void writeFloat(final float v) throws IOException {
        m_output.writeFloat(v);
    }

    /**
     * @param v
     * @throws IOException
     * @see java.io.DataOutput#writeInt(int)
     */
    public void writeInt(final int v) throws IOException {
        m_output.writeInt(v);
    }

    /**
     * @param v
     * @throws IOException
     * @see java.io.DataOutput#writeLong(long)
     */
    public void writeLong(final long v) throws IOException {
        m_output.writeLong(v);
    }

    /**
     * @param v
     * @throws IOException
     * @see java.io.DataOutput#writeShort(int)
     */
    public void writeShort(final int v) throws IOException {
        m_output.writeShort(v);
    }

    /**
     * @param str
     * @throws IOException
     * @see java.io.DataOutput#writeUTF(java.lang.String)
     */
    public void writeUTF(final String str) throws IOException {
        long strLength = getUTFLength(str);
        if (strLength <= MAX_UTF_SHORT_SIZE) {
            writeShortUTF(str, strLength);
        } else {
            writeLongUTF(str, strLength);
        }
    }
    
    /*
     * The following code has been copied (mostly 1:1) from ObjectOutputStream.
     * It's the workaround for a limitation of the writeUTF method which 
     * does not allow to write strings longer than 65535 (UTF-)bytes (worst
     * case scenario 65535/3 characters.
     */
    
    /** maximum data block length. */
    private static final int MAX_BLOCK_SIZE = 1024;
    /** (tunable) length of char buffer (for writing strings). */
    private static final int CHAR_BUF_SIZE = 256;

    /** buffer for writing general/block data. */
    private final byte[] m_buf = new byte[MAX_BLOCK_SIZE];
    /** char buffer for fast string writes. */
    private final char[] m_cbuf = new char[CHAR_BUF_SIZE];
    
    private static final long MAX_UTF_SHORT_SIZE = 0xFFFFL - 1;
    /** Short that is written in front of UTF'ed byte stream whose length
     * is longer than 65535 bytes. */
    static final int USE_LONG_UTF = 0xFFFF; // -1 

    
    /**
     * Returns the length in bytes of the UTF encoding of the given string.
     */
    private long getUTFLength(final String s) {
        int len = s.length();
        long utflen = 0;
        for (int off = 0; off < len;) {
            int csize = Math.min(len - off, CHAR_BUF_SIZE);
            s.getChars(off, off + csize, m_cbuf, 0);
            for (int cpos = 0; cpos < csize; cpos++) {
                char c = m_cbuf[cpos];
                if (c >= 0x0001 && c <= 0x007F) {
                    utflen++;
                } else if (c > 0x07FF) {
                    utflen += 3;
                } else {
                    utflen += 2;
                }
            }
            off += csize;
        }
        return utflen;
    }

    /**
     * Writes the given string in UTF format. This method is used in situations
     * where the UTF encoding length of the string is already known; specifying
     * it explicitly avoids a prescan of the string to determine its UTF length.
     */
    private void writeShortUTF(
            final String s, final long utflen) throws IOException {
        writeShort((int)utflen);
        if (utflen == s.length()) {
            writeBytes(s);
        } else {
            writeUTFBody(s);
        }
    }

    /**
     * Writes given string in "long" UTF format, where the UTF encoding length
     * of the string is already known.
     */
    private void writeLongUTF(
            final String s, final long utflen) throws IOException {
        writeShort(USE_LONG_UTF);
        writeLong(utflen);
        if (utflen == s.length()) {
            writeBytes(s);
        } else {
            writeUTFBody(s);
        }
    }

    /**
     * Writes the "body" (i.e., the UTF representation minus the 2-byte or
     * 8-byte length header) of the UTF encoding for the given string.
     */
    private void writeUTFBody(final String s) throws IOException {
        int limit = MAX_BLOCK_SIZE - 3;
        int len = s.length();
        int pos = 0;
        for (int off = 0; off < len;) {
            int csize = Math.min(len - off, CHAR_BUF_SIZE);
            s.getChars(off, off + csize, m_cbuf, 0);
            for (int cpos = 0; cpos < csize; cpos++) {
                if (pos > limit) {
                    write(m_buf, 0, pos);
                    pos = 0;
                }
                char c = m_cbuf[cpos];
                if (c <= 0x007F && c != 0) {
                    m_buf[pos++] = (byte)c;
                } else if (c > 0x07FF) {
                    m_buf[pos + 2] = (byte)(0x80 | ((c >> 0) & 0x3F));
                    m_buf[pos + 1] = (byte)(0x80 | ((c >> 6) & 0x3F));
                    m_buf[pos + 0] = (byte)(0xE0 | ((c >> 12) & 0x0F));
                    pos += 3;
                } else {
                    m_buf[pos + 1] = (byte)(0x80 | ((c >> 0) & 0x3F));
                    m_buf[pos + 0] = (byte)(0xC0 | ((c >> 6) & 0x1F));
                    pos += 2;
                }
            }
            off += csize;
        }
        if (pos > 0) {
            write(m_buf, 0, pos);
        }
    }

}
