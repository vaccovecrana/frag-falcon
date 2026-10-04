#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "../fg/fg_proc.h"
#include "../fg/fg_root.h"
#include "../fg/fg_extract.h"

////////////////////////////////////////////////////
//              Process management                //
////////////////////////////////////////////////////

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_spawnProcess(
        JNIEnv *env, jclass cls, jstring command,
        jobjectArray args, jstring logPath, jstring ldLibraryPath) {
    const char *cmd = (*env)->GetStringUTFChars(env, command, 0);

    const char *log_path = NULL;
    if (logPath != NULL) {
        log_path = (*env)->GetStringUTFChars(env, logPath, 0);
    }
    const char *ld_library_path = NULL;
    if (ldLibraryPath != NULL) {
        ld_library_path = (*env)->GetStringUTFChars(env, ldLibraryPath, 0);
    }

    jsize arg_len = (*env)->GetArrayLength(env, args);
    char *argv[arg_len + 2]; // +2 for command and NULL terminator
    argv[0] = strdup(cmd);
    for (int i = 0; i < arg_len; ++i) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        argv[i + 1] = strdup(arg_str);
        (*env)->ReleaseStringUTFChars(env, arg, arg_str);
    }
    argv[arg_len + 1] = NULL;

    jint result = spawn_process(cmd, argv, log_path, ld_library_path);

    (*env)->ReleaseStringUTFChars(env, command, cmd);
    if (logPath != NULL) {
        (*env)->ReleaseStringUTFChars(env, logPath, log_path);
    }
    if (ldLibraryPath != NULL) {
        (*env)->ReleaseStringUTFChars(env, ldLibraryPath, ld_library_path);
    }
    for (int i = 0; i <= arg_len; ++i) {
        free(argv[i]);
    }
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_terminate(JNIEnv *env, jclass cls, jint pid) {
    (void) env;
    return terminate_process((pid_t) pid);
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_extractTar(
        JNIEnv *env, jclass cls, jstring tarPath, jstring rootDir) {
    (void) cls;
    const char *tar_path = (*env)->GetStringUTFChars(env, tarPath, 0);
    const char *root_dir = (*env)->GetStringUTFChars(env, rootDir, 0);
    long root_fd = fg_root_open(root_dir);
    int result;
    if (root_fd < 0) {
        result = (int) root_fd;
    } else {
        result = fg_extract_tar((int) root_fd, tar_path);
        close((int) root_fd);
    }
    (*env)->ReleaseStringUTFChars(env, tarPath, tar_path);
    (*env)->ReleaseStringUTFChars(env, rootDir, root_dir);
    return result;
}

JNIEXPORT jstring JNICALL Java_io_vacco_ff_net_FgJni_strerror(JNIEnv *env, jclass cls, jint err) {
    (void) cls;
    const char *msg = strerror((int) err);
    return (*env)->NewStringUTF(env, msg != NULL ? msg : "unknown error");
}

