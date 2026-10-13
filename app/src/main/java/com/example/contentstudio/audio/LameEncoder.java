/* Birlevo LAME bridge, LGPL-2.0-or-later. See third_party/lame/COPYING. */
package com.example.contentstudio.audio;

/** Bounded private JNI handle; synchronized close prevents concurrent use-after-free. */
final class LameEncoder implements AutoCloseable {
    static { System.loadLibrary("birlevo_mp3"); }
    private long handle;
    LameEncoder(int rate, int channels, int bitrate) { handle = nativeCreate(rate, channels, bitrate / 1000); }
    private long active() { if (handle == 0) throw new IllegalStateException("MP3 encoder closed"); return handle; }
    synchronized int encode(short[] pcm, int frames, byte[] output) { return nativeEncode(active(), pcm, frames, output); }
    synchronized int flush(byte[] output) { return nativeFlush(active(), output); }
    synchronized int tag(byte[] output) { return nativeTag(active(), output); }
    @Override public synchronized void close() { if (handle != 0) { nativeClose(handle); handle = 0; } }
    static native String version();
    private static native long nativeCreate(int rate, int channels, int kbps);
    private static native int nativeEncode(long handle, short[] pcm, int frames, byte[] output);
    private static native int nativeFlush(long handle, byte[] output);
    private static native int nativeTag(long handle, byte[] output);
    private static native void nativeClose(long handle);
}
