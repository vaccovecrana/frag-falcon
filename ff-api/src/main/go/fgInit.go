package main

import (
	"fmt"
	"log"

	"github.com/lorenzosaino/go-sysctl"
	"github.com/pilebones/go-udev/crawler"
)

func initKernelParams() error {
	if exists("/etc/sysctl.conf") {
		log.Println("Loading kernel parameters...")
		if err := sysctl.LoadConfigAndApply(); err == nil {
			if isDebugEnabled() {
				v, _ := sysctl.GetAll()
				log.Println("Current kernel parameters", v)
			}
		} else {
			log.Println("Failed to load kernel parameters", err)
			return err
		}
	}
	return nil
}

func initDev() {
	if err := initDevMem(); err != nil {
		log.Println("Failed to create /dev/mem device", err)
	}
	if err := initDevNull(); err != nil {
		log.Println("Failed to create /dev/null device", err)
	}
	if err := initDevPts(); err != nil {
		log.Println("Failed to create /dev/pts", err)
	}
	if err := initMaxFileDescriptors(); err != nil {
		fmt.Println(err)
		return
	}
	log.Println("Enumerating existing devices...")
	queue := make(chan crawler.Device)
	errors := make(chan error)
	crawler.ExistingDevices(queue, errors, nil)
	for {
		select {
		case device, more := <-queue:
			if !more {
				log.Printf("Finished enumerating devices\n")
				return
			}
			if err := createDevice(device); err != nil {
				log.Println("Failed to create device", device.KObj, "-", err)
			}
		case err := <-errors:
			log.Println("ERROR:", err)
		}
	}
}
