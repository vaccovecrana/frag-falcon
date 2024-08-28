package main

// Show debug output if this variable is defined (regardless of value).
const FF_DEBUG = "FF_DEBUG"

// Entry point. e.g. FF_ENTRYPOINT="["nats-server"]"
const FF_ENTRYPOINT = "FF_ENTRYPOINT"

// Command arguments. e.g. FF_CMD="["-arg0", "--arg1"]"
const FF_CMD = "FF_CMD"

// Working directory.
const FF_WORKINGDIR = "FF_WORKINGDIR"

// Numeric placeholders for drive mounts (JSON data)
// FF_MOUNT_0=dev/vda:/media/data:true:false
// <DEVICE>:<MOUNT_PATH>:<SYNC>:<READ_ONLY>
const FF_MOUNT = "FF_MOUNT"

// Numeric placeholders for nameservers
// e.g. FF_NS_0=1.1.1.1
const FF_NS = "FF_NS"

// Max number of file descriptors
// Typically used by databases or storage solutions
const FF_MAXFD = "FF_MAXFD"
