package main

import (
	"fmt"
	"log"
	"os"
	"syscall"
)

const banner = `
--------------------------
   ________    ___  ______
  / __/ __/___/ _ \/_  __/
 / _// _//___/ , _/ / /   
/_/ /_/     /_/|_| /_/    
--------------------------`

func mountFs(source, target, fstype string, flags uintptr) error {
	if err := os.MkdirAll(target, 0755); err != nil {
		return fmt.Errorf("failed to create mount target %s: %v", target, err)
	}
	if err := syscall.Mount(source, target, fstype, flags, ""); err != nil {
		return fmt.Errorf("failed to mount %s to %s: %v", source, target, err)
	}
	log.Printf("Mounted %s to %s", source, target)
	return nil
}

func isDebugEnabled() bool {
	_, debugDev := os.LookupEnv(FF_DEBUG)
	return debugDev
}

func main() {
	log.Println(banner)
	flags := uintptr(syscall.MS_NOSUID | syscall.MS_NODEV | syscall.MS_NOEXEC | syscall.MS_RELATIME)
	if err := mountFs("proc", "/proc", "proc", flags); err != nil {
		log.Fatalf("Failed to mount /proc: %v", err)
	} else if err := mountFs("sysfs", "/sys", "sysfs", flags); err != nil {
		log.Fatalf("Failed to mount /sys: %v", err)
	} else {
		initResolv(os.Environ(), "/etc/resolv.conf")
		initKernelParams()
		initDev()
		monitor()
	}
}
