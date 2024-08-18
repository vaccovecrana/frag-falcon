package main

import (
	"fmt"
	"log"
	"os"
	"path"
	"path/filepath"
	"strconv"
	"syscall"

	"github.com/pilebones/go-udev/crawler"
	"golang.org/x/sys/unix"
)

func initDevNull() error {
	devPath := "/dev/null"
	major := uint32(1)
	minor := uint32(3)
	devNum := unix.Mkdev(major, minor)
	mode := uint32(syscall.S_IFCHR | 0666)
	if stat, err := os.Stat(devPath); err == nil {
		if stat.Mode() != os.FileMode(mode) {
			log.Printf("%s exists but has incorrect permissions: %v, fixing permissions...", devPath, stat.Mode())
			if err := os.Chmod(devPath, os.FileMode(mode)); err != nil {
				return fmt.Errorf("failed to chmod %s: %w", devPath, err)
			}
		} else {
			log.Println(devPath, "already exists with correct permissions")
		}
		return nil
	} else {
		log.Println("Initializing", devPath)
	}
	if err := os.MkdirAll(filepath.Dir(devPath), 0755); err != nil {
		return err
	}
	if err := syscall.Mknod(devPath, mode, int(devNum)); err != nil {
		return err
	}
	if err := os.Chmod(devPath, os.FileMode(mode)); err != nil {
		return fmt.Errorf("failed to chmod %s after creation: %w", devPath, err)
	}
	return nil
}

func exists(path string) bool {
	if _, err := os.Stat(path); err == nil {
		return true
	}
	return false
}

func isType(maj int, min int, devType string) bool {
	path := fmt.Sprintf("/sys/dev/%s/%d:%d", devType, maj, min)
	exists := exists(path)
	return exists
}

func createDevice(device crawler.Device) error {
	if devName, ok := device.Env["DEVNAME"]; ok {
		if major, ok := device.Env["MAJOR"]; ok {
			if minor, ok := device.Env["MINOR"]; ok {
				if iMaj, err := strconv.Atoi(major); err == nil {
					if iMin, err := strconv.Atoi(minor); err == nil {
						if fInfo, err := os.Stat(device.KObj); err == nil {

							dst := fmt.Sprintf("/dev/%s", devName)
							devNum := unix.Mkdev(uint32(iMaj), uint32(iMin))
							mode := fInfo.Mode()
							perm := os.FileMode.Perm(mode)

							switch {
							case (isType(iMaj, iMin, "char")):
								perm |= syscall.S_IFCHR
							case (isType(iMaj, iMin, "block")):
								perm |= syscall.S_IFBLK
							default:
								return fmt.Errorf("%s is not a device", device.KObj)
							}

							oldMask := syscall.Umask(int(0))
							if err := os.MkdirAll(path.Dir(dst), 0755); err != nil {
								return err
							} else if isDebugEnabled() {
								log.Println("Create device at", device.KObj, "on", dst, "mode", perm, "dev", devNum, "with env", device.Env)
							}
							if err := syscall.Mknod(dst, uint32(perm), int(devNum)); err != nil {
								return err
							}
							syscall.Umask(oldMask)
						} else {
							return err
						}
					} else {
						return err
					}
				} else {
					return err
				}
			}
		}
	} // else { log.Println("Skipping device", device.KObj) }

	return nil

}
