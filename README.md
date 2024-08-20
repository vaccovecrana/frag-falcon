# frag-falcon

Firecracker VM management.

## Quick start

Grab the latest release [here](https://github.com/vaccovecrana/frag-falcon/releases).

To run `flc`, you need:

- A `glibc` based Linux distribution with virtualization support. Support for `musl` is being [considered](https://github.com/vaccovecrana/frag-falcon/issues/9).
- The `tun` and `kvm` (intel or amd) kernel modules loaded.
- A Linux bridge VMs can attach to. The bridge needs to be attached to a router that can provide DHCP addresses.
- A Linux kernel. You can grab [this one](https://github.com/vaccovecrana/frag-falcon/raw/main/ff-test/src/test/resources/kernel/vmlinux-6.1.98) we use for testing, or [compile your own](https://github.com/firecracker-microvm/firecracker/tree/main/resources/guest_configs).
- The latest [firecracker](https://github.com/firecracker-microvm/firecracker/releases) release.

Make a directory to store kernels and virtual machines, and place at least one kernel in the `kernels` directory.

```
localhost:~/flc# tree
.
├── kernels
└── virtual-machines
3 directories, 0 files 
```

> Note: if you plan to run `flc` as a non-root user, you'll need to `setcap` on the `flc` binary to grant network management capabilities. See [here](https://github.com/vaccovecrana/frag-falcon/blob/main/ff-test/README.md) for details.

Start `flc`:

```
flc \
  --api-host=0.0.0.0 \
  --vm-dir=./virtual-machines \
  --krn-dir=./kernels \
  --fc-path=/usr/local/bin/firecracker
```

Open a browser and go to `http://<your-host>:7070`

Use the integrated UI to create a test VM using your target Linux kernel and network bridge.

<img width="668" alt="Screenshot 2024-08-19 at 10 37 48 PM" src="https://github.com/user-attachments/assets/e0abe564-6605-4902-bf62-84f4e79e43c9">

You can also create a VM with an API call too:

```
curl -i -X POST \
   -H "Content-Type:application/json" \
   -d \
'{
  "vm": {
    "tag": {
      "id": "new",
      "label": "test-vm-01",
      "description": "Test VM 01"
    },
    "image": { "source": "docker.io/hashicorp/http-echo:latest" },
    "config": {
      "bootsource": { "kernel_image_path": "/root/flc/kernels/vmlinux-6.1.98" },
      "machineconfig": { "vcpu_count": 1, "mem_size_mib": 512 }
    }
  },
  "network": { "dhcp": true, "brIf": "br0" },
  "rebuildInitRamFs": false
}' \
 'http://<your-host>:7070/api/v1/vm'
```

You will then have a list of VMs that you can start, stop, and inspect logs on.

<img width="562" alt="Screenshot 2024-08-19 at 10 56 55 PM" src="https://github.com/user-attachments/assets/7bdd401e-2f07-49da-8bb0-1afe4116e716">

The test VM I am running is using the `hashicorp/http-echo:latest` image. So I can `curl` it's IP address, just like any other machine in my internal network:

```
% curl http://172.16.4.107:5678
hello-world
```

## Resources/credits

- [TinyUntar](https://github.com/dsoprea/TinyUntar)
- [Firecracker API](https://github.com/firecracker-microvm/firecracker/blob/main/src/firecracker/swagger/firecracker.yaml)
- [Solar Bold Icons](https://www.svgrepo.com/collection/solar-bold-icons/1)
