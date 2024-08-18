package main

import (
	"errors"
	"fmt"
	"log"
	"os"
	"strings"
	"syscall"
)

var ext4MountOptions = strings.Join([]string{
	"journal_checksum",
	"journal_ioprio=0",
	"barrier=1",
	"data=ordered",
	"errors=remount-ro",
}, ",")

func MountExt4(mount SpMount) error {
	var flags uintptr
	if mount.ReadOnly {
		flags |= syscall.MS_RDONLY
	}
	if mount.Sync {
		flags |= syscall.MS_SYNCHRONOUS | syscall.MS_DIRSYNC
	}
	err := syscall.Mount(mount.Device, mount.Path, "ext4", flags, ext4MountOptions)
	return os.NewSyscallError("mount", err)
}

type SpMount struct {
	Device   string
	Path     string
	Sync     bool
	ReadOnly bool
}

func parseBool(value string) (bool, error) {
	if value == "true" {
		return true, nil
	} else if value == "false" {
		return false, nil
	}
	return false, errors.New("invalid boolean value")
}

func parseMount(arg string) (*SpMount, error) {
	parts := strings.Split(arg, ":")
	if len(parts) != 4 {
		return nil, fmt.Errorf("invalid mount format: %s", parts)
	}
	device := parts[0]
	path := parts[1]
	sync, err := parseBool(parts[2])
	if err != nil {
		return nil, fmt.Errorf("invalid sync value: %s", parts[2])
	}
	readOnly, err := parseBool(parts[3])
	if err != nil {
		return nil, fmt.Errorf("invalid read-only value: %s", parts[3])
	}
	return &SpMount{
		Device:   device,
		Path:     path,
		Sync:     sync,
		ReadOnly: readOnly,
	}, nil
}

func initMounts(envKv []string) ([]SpMount, error) {
	mounts := make([]SpMount, 0)
	for _, e := range envKv {
		pair := strings.SplitN(e, "=", 2)
		if strings.HasPrefix(pair[0], FF_MOUNT) {
			log.Println("Mounting", pair[1])
			mount, err := parseMount(pair[1])
			if err != nil {
				return nil, err
			}
			if err := os.MkdirAll(mount.Path, os.ModePerm); err != nil {
				return nil, err
			} else if err := MountExt4(*mount); err != nil {
				return nil, err
			}
			mounts = append(mounts, *mount)
		}
	}
	return mounts, nil
}
