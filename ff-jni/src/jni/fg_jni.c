#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

#include "../fg/fg_tap.h"
#include "../fg/fg_raw.h"
#include "../fg/fg_proc.h"

////////////////////////////////////////////////////
//              Process management                //
////////////////////////////////////////////////////

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_spawnProcess(
        JNIEnv *env, jclass cls, jstring vmId, jstring command,
        jobjectArray args, jstring logPath, jstring ldLibraryPath) {
    const char *vm_id = (*env)->GetStringUTFChars(env, vmId, 0);
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

    jint result = spawn_process(vm_id, cmd, argv, log_path, ld_library_path);

    (*env)->ReleaseStringUTFChars(env, vmId, vm_id);
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

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_waitProcess(JNIEnv *env, jclass cls, jint pid, jint timeoutMs) {
    (void) env;
    (void) cls;
    int status;
    long waited = 0;
    for (;;) {
        pid_t r = waitpid((pid_t) pid, &status, WNOHANG);
        if (r == (pid_t) pid) {
            return WIFEXITED(status) ? WEXITSTATUS(status) : (128 + WTERMSIG(status));
        }
        if (r < 0) {
            return -1;
        }
        usleep(50000);
        waited += 50;
        if (timeoutMs > 0 && waited >= timeoutMs) {
            return -2;
        }
    }
}

////////////////////////////////////////////////////
//            TAP device management               //
////////////////////////////////////////////////////

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_tapCreate(JNIEnv *env, jclass cls, jstring jIfName) {
    (void) cls;
    const char *ifName = (*env)->GetStringUTFChars(env, jIfName, NULL);
    if (ifName == NULL) {
        return -1;
    }
    int result = create_tap_device(ifName);
    (*env)->ReleaseStringUTFChars(env, jIfName, ifName);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_tapDelete(JNIEnv *env, jclass cls, jstring ifName) {
    (void) cls;
    const char *interfaceName = (*env)->GetStringUTFChars(env, ifName, 0);
    if (interfaceName == NULL) {
        return -1;
    }
    int result = delete_tap_device(interfaceName);
    (*env)->ReleaseStringUTFChars(env, ifName, interfaceName);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_tapAttach(JNIEnv *env, jclass cls, jstring ifName, jstring brId) {
    (void) cls;
    const char *if_name = (*env)->GetStringUTFChars(env, ifName, NULL);
    const char *br_name = (*env)->GetStringUTFChars(env, brId, NULL);
    if (if_name == NULL || br_name == NULL) {
        if (if_name != NULL) (*env)->ReleaseStringUTFChars(env, ifName, if_name);
        if (br_name != NULL) (*env)->ReleaseStringUTFChars(env, brId, br_name);
        return -1;
    }
    int result = attach_tap_to_bridge(if_name, br_name);
    (*env)->ReleaseStringUTFChars(env, ifName, if_name);
    (*env)->ReleaseStringUTFChars(env, brId, br_name);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_tapDetach(JNIEnv *env, jclass cls, jstring jIfName, jstring jBrId) {
    (void) cls;
    const char *ifName = (*env)->GetStringUTFChars(env, jIfName, NULL);
    const char *brId = (*env)->GetStringUTFChars(env, jBrId, NULL);
    if (ifName == NULL || brId == NULL) {
        if (ifName != NULL) (*env)->ReleaseStringUTFChars(env, jIfName, ifName);
        if (brId != NULL) (*env)->ReleaseStringUTFChars(env, jBrId, brId);
        return -1;
    }
    int result = detach_tap_from_bridge(ifName, brId);
    (*env)->ReleaseStringUTFChars(env, jIfName, ifName);
    (*env)->ReleaseStringUTFChars(env, jBrId, brId);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_io_vacco_ff_net_FgJni_getMacAddress(JNIEnv *env, jclass cls, jstring ifName) {
    (void) cls;
    const char *interfaceName = (*env)->GetStringUTFChars(env, ifName, NULL);
    if (interfaceName == NULL) {
        return NULL;
    }
    unsigned char mac[6];
    int result = get_mac_address(interfaceName, mac);
    (*env)->ReleaseStringUTFChars(env, ifName, interfaceName);
    if (result != 0) {
        return NULL;
    }
    jbyteArray macAddress = (*env)->NewByteArray(env, 6);
    if (macAddress == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, macAddress, 0, 6, (jbyte *) mac);
    return macAddress;
}

////////////////////////////////////////////////////
//           Raw socket communication             //
////////////////////////////////////////////////////

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_rawCreate(JNIEnv *env, jclass cls, jstring interfaceName) {
    (void) cls;
    const char *iface = (*env)->GetStringUTFChars(env, interfaceName, NULL);
    if (iface == NULL) {
        return -1;
    }
    int sock = create_raw_socket(iface);
    (*env)->ReleaseStringUTFChars(env, interfaceName, iface);
    return sock;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_rawSend(JNIEnv *env, jclass cls, jint socketHandle, jbyteArray payload) {
    (void) cls;
    jbyte *buffer = (*env)->GetByteArrayElements(env, payload, NULL);
    jsize len = (*env)->GetArrayLength(env, payload);
    if (buffer == NULL) {
        return -2;
    }
    int sent = send_raw_packet(socketHandle, (unsigned char *) buffer, len);
    (*env)->ReleaseByteArrayElements(env, payload, buffer, 0);
    return sent;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_rawReceive(JNIEnv *env, jclass cls, jint socketHandle, jbyteArray buffer, jint timeoutSeconds) {
    (void) cls;
    jsize bufferSize = (*env)->GetArrayLength(env, buffer);
    if (buffer == NULL || bufferSize == 0) {
        return -1;
    }
    unsigned char *nativeBuffer = (unsigned char *) malloc(bufferSize);
    if (nativeBuffer == NULL) {
        return -2;
    }
    int received = receive_raw_packet(socketHandle, nativeBuffer, bufferSize, timeoutSeconds);
    if (received < 0) {
        free(nativeBuffer);
        return -3;
    }
    (*env)->SetByteArrayRegion(env, buffer, 0, received, (jbyte *) nativeBuffer);
    free(nativeBuffer);
    return received;
}

JNIEXPORT void JNICALL Java_io_vacco_ff_net_FgJni_rawClose(JNIEnv *env, jclass cls, jint socketHandle) {
    (void) env;
    (void) cls;
    close_raw_socket(socketHandle);
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_rawPromisc(JNIEnv *env, jclass cls, jstring interfaceName, jboolean enabled) {
    (void) cls;
    const char *interface = (*env)->GetStringUTFChars(env, interfaceName, 0);
    int result = set_promiscuous_mode(interface, enabled ? 1 : 0);
    (*env)->ReleaseStringUTFChars(env, interfaceName, interface);
    return result;
}
