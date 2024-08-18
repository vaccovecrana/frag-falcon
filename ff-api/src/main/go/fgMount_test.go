package main

import (
	"log"
	"testing"
)

func TestParseMount(t *testing.T) {
	tests := []struct {
		input          string
		expectedDevice string
		expectedPath   string
		expectedSync   bool
		expectedRO     bool
		expectError    bool
	}{
		{
			input:          "/dev/vda:/media/data:true:false",
			expectedDevice: "/dev/vda",
			expectedPath:   "/media/data",
			expectedSync:   true,
			expectedRO:     false,
			expectError:    false,
		},
		{
			input:          "/dev/vdb:/media/backup:true:true",
			expectedDevice: "/dev/vdb",
			expectedPath:   "/media/backup",
			expectedSync:   true,
			expectedRO:     true,
			expectError:    false,
		},
		{
			input:       "/dev/vdc:/media/invalid:true:maybe",
			expectError: true,
		},
		{
			input:       "/dev/vdd:/media/missing_part",
			expectError: true,
		},
		{
			input:       "",
			expectError: true,
		},
	}

	for _, test := range tests {
		t.Run(test.input, func(t *testing.T) {
			mount, err := parseMount(test.input)

			if test.expectError {
				if err == nil {
					t.Errorf("expected error, got nil")
				}
				return
			}

			if err != nil {
				t.Errorf("unexpected error: %v", err)
				return
			}

			if mount.Device != test.expectedDevice {
				t.Errorf("expected Device %s, got %s", test.expectedDevice, mount.Device)
			}

			if mount.Path != test.expectedPath {
				t.Errorf("expected Path %s, got %s", test.expectedPath, mount.Path)
			}

			if mount.Sync != test.expectedSync {
				t.Errorf("expected Sync %v, got %v", test.expectedSync, mount.Sync)
			}

			if mount.ReadOnly != test.expectedRO {
				t.Errorf("expected ReadOnly %v, got %v", test.expectedRO, mount.ReadOnly)
			}
		})
	}

	log.Println("SpMount tests - Done.")
}
