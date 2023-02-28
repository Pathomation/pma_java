package com.pathomation;

import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipart;
import org.apache.http.util.ByteArrayBuffer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.List;

public class HttpCustomMultipart extends HttpMultipart {

    private final List<FormBodyPart> parts;

    public HttpCustomMultipart (
            final String subType,
            final Charset charset,
            final String boundary,
            final List<FormBodyPart> parts) {
        super(subType, charset, boundary);
        this.parts = parts;
    }

    @Override
    public List<FormBodyPart> getBodyParts() {
        return this.parts;
    }

    @Override
    public void writeTo(final OutputStream out) throws IOException {
        doWriteTo(out, true);
    }

    void doWriteTo(final OutputStream out, final boolean writeContent) throws IOException {
//        final ByteArrayBuffer boundary = encode(this.charset, getBoundary());
        for (final FormBodyPart part : getBodyParts()) {
            if (writeContent) {
                part.getBody().writeTo(out);
            }
        }
    }
    
    private static ByteArrayBuffer encode(
            final Charset charset, final String string) {
        final ByteBuffer encoded = charset.encode(CharBuffer.wrap(string));
        final ByteArrayBuffer bab = new ByteArrayBuffer(encoded.remaining());
        bab.append(encoded.array(), encoded.position(), encoded.remaining());
        return bab;
    }

    @Override
    protected void formatMultipartHeader(
            final FormBodyPart part,
            final OutputStream out) throws IOException {

        // For strict, we output all fields with MIME-standard encoding.
        // final Header header = part.getHeader();
        // for (final MinimalField field : header) {
        // writeField(field, out);
        // }
    }
}