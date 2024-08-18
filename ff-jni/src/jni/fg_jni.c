#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "../fg/fg_tap.h"
#include "../fg/fg_raw.h"
#include "../fg/fg_proc.h"
#include "../fg/fg_unix.h"
#include "../fg/fg_vsock.h"

////////////////////////////////////////////////////
//              Process  management               //
////////////////////////////////////////////////////

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_fork(JNIEnv *env, jclass cls, jstring vmId, jstring command, jobjectArray args, jstring logPath) {
    const char *vm_id = (*env)->GetStringUTFChars(env, vmId, 0);
    const char *cmd = (*env)->GetStringUTFChars(env, command, 0);

    const char *log_path = NULL;
    if (logPath != NULL) {
        log_path = (*env)->GetStringUTFChars(env, logPath, 0);
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

    jint result = spawn_process(vm_id, cmd, argv, log_path);
    (*env)->ReleaseStringUTFChars(env, vmId, vm_id);
    (*env)->ReleaseStringUTFChars(env, command, cmd);

    if (logPath != NULL) {
        (*env)->ReleaseStringUTFChars(env, logPath, log_path);
    }
    for (int i = 0; i <= arg_len; ++i) {
        free(argv[i]);
    }
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_terminate(JNIEnv *env, jclass cls, jint pid) {
    return terminate_process((pid_t) pid);
}

////////////////////////////////////////////////////
//            TAP device management               //
////////////////////////////////////////////////////

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_tapCreate(JNIEnv *env, jclass cls, jstring jIfName) {
    const char *ifName = (*env)->GetStringUTFChars(env, jIfName, NULL);
    if (ifName == NULL) {
        return -1;
    }
    int result = create_tap_device(ifName);
    (*env)->ReleaseStringUTFChars(env, jIfName, ifName);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_tapDelete(JNIEnv *env, jclass cls, jstring ifName) {
    const char *interfaceName = (*env)->GetStringUTFChars(env, ifName, 0);
    if (interfaceName == NULL) {
        return -1;
    }
    int result = delete_tap_device(interfaceName);
    (*env)->ReleaseStringUTFChars(env, ifName, interfaceName);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_tapAttach(JNIEnv *env, jclass cls, jstring ifName, jstring brId) {
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
    (*env)->SetByteArrayRegion(env, macAddress, 0, 6, (jbyte *)mac);
    return macAddress;
}

////////////////////////////////////////////////////
//           Raw socket communication             //
////////////////////////////////////////////////////

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_rawCreate(JNIEnv *env, jclass cls, jstring interfaceName) {
    const char *iface = (*env)->GetStringUTFChars(env, interfaceName, NULL);
    if (iface == NULL) {
        return -1;
    }
    int sock = create_raw_socket(iface);
    (*env)->ReleaseStringUTFChars(env, interfaceName, iface);
    return sock;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_rawSend(JNIEnv *env, jclass cls, jint socketHandle, jbyteArray payload) {
    jbyte *buffer = (*env)->GetByteArrayElements(env, payload, NULL);
    jsize len = (*env)->GetArrayLength(env, payload);
    if (buffer == NULL) {
        return -2;
    }
    int sent = send_raw_packet(socketHandle, (unsigned char *)buffer, len);
    (*env)->ReleaseByteArrayElements(env, payload, buffer, 0);
    return sent;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_rawReceive(JNIEnv *env, jclass cls, jint socketHandle, jbyteArray buffer, jint timeoutSeconds) {
    jsize bufferSize = (*env)->GetArrayLength(env, buffer);
    if (buffer == NULL || bufferSize == 0) {
        return -1;
    }
    unsigned char *nativeBuffer = (unsigned char *)malloc(bufferSize);
    if (nativeBuffer == NULL) {
        return -2;
    }
    int received = receive_raw_packet(socketHandle, nativeBuffer, bufferSize, timeoutSeconds);
    if (received < 0) {
        free(nativeBuffer);
        return -3;
    }
    (*env)->SetByteArrayRegion(env, buffer, 0, received, (jbyte *)nativeBuffer);
    free(nativeBuffer);
    return received;
}

JNIEXPORT void JNICALL Java_io_vacco_ff_net_FgJni_rawClose(JNIEnv *env, jclass cls, jint socketHandle) {
    close_raw_socket(socketHandle);
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_rawPromisc(JNIEnv *env, jclass cls, jstring interfaceName, jboolean enabled) {
    const char *interface = (*env)->GetStringUTFChars(env, interfaceName, 0);
    int result = set_promiscuous_mode(interface, enabled ? 1 : 0);
    (*env)->ReleaseStringUTFChars(env, interfaceName, interface);
    return result;
}

////////////////////////////////////////////////////
//            UNIX socket communication           //
////////////////////////////////////////////////////

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_unixOpen(JNIEnv *env, jclass cls, jstring socketPath) {
    const char *socket_path = (*env)->GetStringUTFChars(env, socketPath, 0);
    jint result = unix_open(socket_path);
    (*env)->ReleaseStringUTFChars(env, socketPath, socket_path);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_unixSend(JNIEnv *env, jclass cls, jint socketHandle, jbyteArray buffer) {
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, 0);
    jsize buf_len = (*env)->GetArrayLength(env, buffer);
    jint result = unix_send(socketHandle, buf, buf_len);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_unixReceive(JNIEnv *env, jclass cls, jint socketHandle, jbyteArray buffer, jint timeoutMs) {
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, 0);
    jsize buf_len = (*env)->GetArrayLength(env, buffer);
    jint result = unix_receive(socketHandle, buf, buf_len, timeoutMs);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_unixClose(JNIEnv *env, jclass cls, jint socketHandle) {
    return unix_close(socketHandle);
}

////////////////////////////////////////////////////
//               VSOCK communication              //
////////////////////////////////////////////////////

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_vSocketOpen(JNIEnv *env, jclass cls, jstring address) {
    const char *socket_addr = (*env)->GetStringUTFChars(env, address, 0);
    jint result = vsock_open(socket_addr);
    (*env)->ReleaseStringUTFChars(env, address, socket_addr);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_vSockSend(JNIEnv *env, jclass cls, jint socketHandle, jbyteArray buffer) {
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, 0);
    jsize buf_len = (*env)->GetArrayLength(env, buffer);
    jint result = vsock_send(socketHandle, buf, buf_len);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_vSockReceive(JNIEnv *env, jclass cls, jint socketHandle, jbyteArray buffer) {
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, 0);
    jsize buf_len = (*env)->GetArrayLength(env, buffer);
    jint result = vsock_receive(socketHandle, buf, buf_len);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    return result;
}

JNIEXPORT jint JNICALL Java_io_vacco_ff_net_FgJni_vSocketClose(JNIEnv *env, jclass cls, jint socketHandle) {
    return vsock_close(socketHandle);
}
