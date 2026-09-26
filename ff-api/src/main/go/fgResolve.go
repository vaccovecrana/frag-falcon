package main

import (
	"bufio"
	"log"
	"os"
	"strings"
)

func initResolv(envKv []string, path string) error {
	nameServers := make([]string, 0)
	for _, e := range envKv {
		pair := strings.SplitN(e, "=", 2)
		if strings.HasPrefix(pair[0], FF_NS) {
			log.Println("Adding nameserver", pair[1])
			nameServers = append(nameServers, pair[1])
		}
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	writer := bufio.NewWriter(file)
	for _, str := range nameServers {
		_, _ = writer.WriteString("nameserver " + str + "\n")
	}
	writer.Flush()
	file.Close()
	return nil
}
