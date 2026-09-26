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

func initDevMem() error {
	major := uint32(1)
	minor := uint32(1)
	devNum := unix.Mkdev(major, minor)
	mode := uint32(syscall.S_IFCHR | 0640)
	if _, err := os.Stat("/dev/mem"); os.IsNotExist(err) {
		if err := unix.Mknod("/dev/mem", mode, int(devNum)); err != nil {
			return fmt.Errorf("failed to create /dev/mem: %v", err)
		}
		if err := os.Chown("/dev/mem", 0, unix.Getegid()); err != nil {
			return fmt.Errorf("failed to set owner/group for /dev/mem: %v", err)
		}
	}
	_, err := os.OpenFile("/dev/mem", os.O_RDWR, 0640)
	if err != nil {
		return fmt.Errorf("failed to open /dev/mem: %v", err)
	}
	return nil
}

func initDevPts() error {
	devPtsPath := "/dev/pts"
	if _, err := os.Stat(devPtsPath); os.IsNotExist(err) {
		if err := os.MkdirAll(devPtsPath, 0755); err != nil {
			return fmt.Errorf("failed to create %s: %w", devPtsPath, err)
		}
	}
	if err := unix.Mount("devpts", devPtsPath, "devpts", 0, ""); err != nil {
		if err == unix.EBUSY {
			log.Printf("%s is already mounted", devPtsPath)
			return nil
		}
		return fmt.Errorf("failed to mount devpts on %s: %w", devPtsPath, err)
	}
	log.Println("Mounted devpts on", devPtsPath)
	return nil
}

func initMaxFileDescriptors() error {
	maxFdStr := os.Getenv(FF_MAXFD)
	if maxFdStr == "" {
		return nil
	}
	maxFd, err := strconv.Atoi(maxFdStr)
	if err != nil {
		return fmt.Errorf("invalid file descriptor limit value: %v", err)
	}
	var rLimit syscall.Rlimit
	if err := syscall.Getrlimit(syscall.RLIMIT_NOFILE, &rLimit); err != nil {
		return fmt.Errorf("failed to get current file descriptor limit: %v", err)
	}
	rLimit.Cur = uint64(maxFd)
	if rLimit.Max < uint64(maxFd) {
		rLimit.Max = uint64(maxFd)
	}
	if err := syscall.Setrlimit(syscall.RLIMIT_NOFILE, &rLimit); err != nil {
		return fmt.Errorf("failed to set file descriptor limit: %v", err)
	}
	fmt.Printf("Set maximum file descriptors to %d\n", maxFd)
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
