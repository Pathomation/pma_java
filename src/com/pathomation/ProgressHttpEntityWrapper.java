package com.pathomation;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

import java.io.*;

/**
 * HttpEntityWrapper with a progress callback
 */

public class ProgressHttpEntityWrapper extends HttpEntityWrapper {

    private final ProgressCallback progressCallback;
    private String filename;

    public static interface ProgressCallback {
        public void progress(long bytesRead, long transferred, long totalBytes, String filename);
    }

    public ProgressHttpEntityWrapper(final HttpEntity entity, String filename, final ProgressCallback progressCallback) {
        super(entity);
        this.progressCallback = progressCallback;
        this.filename = filename;
    }

    @Override
    public void writeTo(final OutputStream out) throws IOException {
        this.wrappedEntity.writeTo(out instanceof ProgressFilterOutputStream ? out
                : new ProgressFilterOutputStream(out, this.progressCallback, getContentLength(), this.filename));
    }

    public static class ProgressFilterOutputStream extends FilterOutputStream {

        private final ProgressCallback progressCallback;
        private long transferred;

        private long bytesRead;
        private long totalBytes;
        private String filename;

        ProgressFilterOutputStream(final OutputStream out, final ProgressCallback progressCallback,
                                   final Long totalBytes, String filename) {
            super(out);
            this.progressCallback = progressCallback;
            this.transferred = 0;
            this.totalBytes = totalBytes;
            this.filename = filename;
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            // super.write(byte b[], int off, int len) calls write(int b)
            out.write(b, off, len);
            this.transferred += len;
            this.bytesRead = len;
            this.progressCallback.progress(this.bytesRead, this.transferred, this.totalBytes, this.filename);
        }

        @Override
        public void write(final int b) throws IOException {
            out.write(b);
            Core core = new Core();
            try {
                core.bytes.put((long) b);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            this.transferred++;
            this.progressCallback.progress(this.bytesRead, this.transferred, this.totalBytes, this.filename);
        }

        private float getCurrentProgress() {
            System.out.println("getCurrentProgress: " + this.transferred);
            return ((float) this.transferred / this.totalBytes) * 100;
        }

    }
}
