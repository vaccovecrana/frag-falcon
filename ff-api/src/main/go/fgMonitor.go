package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/exec"
	"os/signal"
	"syscall"
	"time"
)

func shutdown(cmd *exec.Cmd, mounts []SpMount, terminate bool) {
	if terminate {
		log.Println("Terminating process", cmd)
		cmd.Process.Signal(syscall.SIGINT)
		time.Sleep(3 * time.Second) // TODO parameterize shutdown standby time.
	}
	for _, mount := range mounts {
		log.Println("Unmounting", mount)
		if err := syscall.Unmount(mount.Path, 0); err != nil {
			log.Println("Unable to unmount storage", mount)
		}
	}
	syscall.Reboot(syscall.LINUX_REBOOT_CMD_POWER_OFF)
}

func getArray(envVar string) ([]string, error) {
	envVal := os.Getenv(envVar)
	if envVal == "" {
		return []string{}, nil
	}
	var envArr []string
	err := json.Unmarshal([]byte(envVal), &envArr)
	if err != nil {
		return nil, fmt.Errorf("error parsing [%s]: %v", envVar, err)
	}
	return envArr, nil
}

func monitor() {
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.Signal(38)) // SIGRTMIN+4

	if mounts, err := initMounts(os.Environ()); err != nil {
		log.Println("Unable to initialize mount points", err)
	} else if entryPoint, err := getArray(FF_ENTRYPOINT); err != nil {
		log.Println("Unable to load entry point", err)
	} else if cmdArgs, err := getArray(FF_CMD); err != nil {
		log.Println("Unable to load command", err)
	} else if len(entryPoint) == 0 && len(cmdArgs) == 0 {
		log.Println("No command to execute: both entry point and command arguments are missing")
	} else {
		command := append(entryPoint, cmdArgs...)
		log.Println("Executing", command)
		cmd := exec.Command(command[0])
		if len(command) > 1 {
			cmd = exec.Command(command[0], command[1:]...)
		}
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		cmd.Stdin = os.Stdin
		cmd.Env = os.Environ()
		workingDir := os.Getenv(FF_WORKINGDIR)
		if workingDir != "" {
			cmd.Dir = workingDir
		}
		if err := cmd.Start(); err != nil {
			log.Println("Process", entryPoint, "failed to start", err)
		} else {
			go func() {
				<-signals
				shutdown(cmd, mounts, true)
			}()
			if err := cmd.Wait(); err != nil {
				log.Println("Process", entryPoint, "finished abnormally", err)
			} else {
				log.Println("Process", entryPoint, "finished normally")
			}
		}
		shutdown(nil, mounts, false)
	}
}
