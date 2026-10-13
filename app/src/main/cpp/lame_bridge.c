/* Birlevo LAME bridge, LGPL-2.0-or-later. See third_party/lame/COPYING. */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <lame.h>

typedef struct { lame_t lame; int channels; int finished; } Encoder;
static void failure(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/io/IOException");
    if (type != NULL) (*env)->ThrowNew(env, type, message);
}
#define JNI_METHOD(name) Java_com_example_contentstudio_audio_LameEncoder_##name
JNIEXPORT jlong JNICALL JNI_METHOD(nativeCreate)(JNIEnv *env, jclass type, jint rate, jint channels, jint kbps) {
    (void)type;
    if (channels < 1 || channels > 2 || kbps < 1 || rate < 8000 || rate > 48000) {
        failure(env, "Invalid MP3 settings"); return 0;
    }
    Encoder *encoder = calloc(1, sizeof(Encoder));
    if (encoder == NULL) { failure(env, "MP3 allocation failed"); return 0; }
    encoder->lame = lame_init(); encoder->channels = channels;
    if (encoder->lame == NULL) { free(encoder); failure(env, "MP3 initialization failed"); return 0; }
    lame_t lame = encoder->lame;
    lame_set_write_id3tag_automatic(lame, 0);
    if (lame_set_in_samplerate(lame, rate) < 0 || lame_set_out_samplerate(lame, rate) < 0 ||
        lame_set_num_channels(lame, channels) < 0 || lame_set_mode(lame, channels == 1 ? MONO : JOINT_STEREO) < 0 ||
        lame_set_brate(lame, kbps) < 0 || lame_set_VBR(lame, vbr_off) < 0 ||
        lame_set_quality(lame, 2) < 0 || lame_set_bWriteVbrTag(lame, 1) < 0 || lame_init_params(lame) < 0 ||
        lame_get_out_samplerate(lame) != rate || lame_get_brate(lame) != kbps || lame_get_num_channels(lame) != channels) {
        lame_close(lame); free(encoder); failure(env, "MP3 settings cannot be preserved"); return 0;
    }
    return (jlong)(intptr_t)encoder;
}
JNIEXPORT jint JNICALL JNI_METHOD(nativeEncode)(JNIEnv *env, jclass type, jlong handle, jshortArray input, jint frames, jbyteArray output) {
    (void)type;
    Encoder *encoder = (Encoder *)(intptr_t)handle;
    if (encoder == NULL || encoder->finished || input == NULL || output == NULL || frames < 1 || frames > 4096 ||
        (*env)->GetArrayLength(env, input) < frames * encoder->channels || (*env)->GetArrayLength(env, output) < 12800) {
        failure(env, "Invalid MP3 buffer"); return -1;
    }
    jshort *pcm = (*env)->GetShortArrayElements(env, input, NULL);
    if (pcm == NULL) return -1;
    unsigned char encoded[12800];
    int size = encoder->channels == 2 ? lame_encode_buffer_interleaved(encoder->lame, pcm, frames, encoded, sizeof(encoded)) :
        lame_encode_buffer(encoder->lame, pcm, pcm, frames, encoded, sizeof(encoded));
    (*env)->ReleaseShortArrayElements(env, input, pcm, JNI_ABORT);
    if (size < 0) { failure(env, "MP3 encoding failed"); return -1; }
    (*env)->SetByteArrayRegion(env, output, 0, size, (const jbyte *)encoded);
    return size;
}
JNIEXPORT jint JNICALL JNI_METHOD(nativeFlush)(JNIEnv *env, jclass type, jlong handle, jbyteArray output) {
    (void)type;
    Encoder *encoder = (Encoder *)(intptr_t)handle;
    if (encoder == NULL || encoder->finished || output == NULL || (*env)->GetArrayLength(env, output) < 12800) {
        failure(env, "Invalid MP3 flush"); return -1;
    }
    unsigned char encoded[12800];
    int size = lame_encode_flush(encoder->lame, encoded, sizeof(encoded)); encoder->finished = 1;
    if (size < 0) { failure(env, "MP3 flush failed"); return -1; }
    (*env)->SetByteArrayRegion(env, output, 0, size, (const jbyte *)encoded); return size;
}
JNIEXPORT jint JNICALL JNI_METHOD(nativeTag)(JNIEnv *env, jclass type, jlong handle, jbyteArray output) {
    (void)type;
    Encoder *encoder = (Encoder *)(intptr_t)handle;
    if (encoder == NULL || !encoder->finished || output == NULL) { failure(env, "Invalid MP3 tag request"); return -1; }
    unsigned char tag[12800]; size_t size = lame_get_lametag_frame(encoder->lame, tag, sizeof(tag));
    if (size < 1 || size > sizeof(tag) || size > (size_t)(*env)->GetArrayLength(env, output)) {
        failure(env, "Invalid MP3 delay/padding tag"); return -1;
    }
    (*env)->SetByteArrayRegion(env, output, 0, (jsize)size, (const jbyte *)tag); return (jint)size;
}
JNIEXPORT void JNICALL JNI_METHOD(nativeClose)(JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    Encoder *encoder = (Encoder *)(intptr_t)handle;
    if (encoder != NULL) { lame_close(encoder->lame); free(encoder); }
}
JNIEXPORT jstring JNICALL JNI_METHOD(version)(JNIEnv *env, jclass type) {
    (void)type; return (*env)->NewStringUTF(env, get_lame_version());
}
