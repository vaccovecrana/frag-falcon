package main

import (
	"log"
	"testing"
)

func TestResolv(t *testing.T) {
	env := make([]string, 0)
	env = append(env, "FF_NS_0=1.1.1.1")
	env = append(env, "FF_NS_1=8.8.8.8")
	if err := initResolv(env, "/tmp/gopher-ns.txt"); err == nil {
		log.Println("Nameserver data written.")
	}
}
