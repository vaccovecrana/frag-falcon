# Development/Debugging

The following commands are needed in order to run machine local tests.

TAP interfaces:

    # Create a bridge
    sudo ip link add name br0 type bridge
    
    # Create TAP interface, attach to bridge and assign IP
    sudo ip tuntap add name tap05 mode tap
    sudo ip link set tap05 master br0
    sudo ip address add 172.16.0.138/24 dev eth0
    sudo ip route add default via 172.16.0.1 dev eth0

    # Delete tap interface
    sudo ip link delete tap2

    # Send arp requests
    nping --arp --arp-target-mac f2:70:47:a5:39:5a --arp-target-ip 192.168.1.10 --interface eth0 192.168.1.255

Capture layer 2 traffic:

    sudo tcpdump -i br0 ether host f2:70:47:a5:39:5a

DHCP requests:

    tcpdump -i any 'udp and (port 67 or port 68)' -e -n -vv

Java/C network permissions:

    sudo setcap 'cap_net_admin+ep cap_net_bind_service+ep cap_net_raw+ep cap_sys_admin+ep' ~/Applications/zulu21.36.17-ca-jdk21.0.4-linux_x64/bin/java
    sudo setcap 'cap_net_admin+ep cap_net_bind_service+ep cap_net_raw+ep' ./ff-jni/build/core_test
    getcap ./build/fg_test

Network interface configurations in Arch Linux are at `/etc/systemd/network`.

Ignore specific interfaces in `/etc/NetworkManager/conf.d`.

    [keyfile]
    unmanaged-devices=interface-name:br0;interface-name:enp0s20f0u3

Firecracker control:

    rm -fv ./fc.sock && firecracker --api-sock ./fc.socket

VSOCK configuration:

    # Load the VSOCK modules
    modprobe vsock
    modprobe vmw_vsock_virtio_transport

    # Verify that /dev/vsock exists
    ls -la /dev/vsock

VSOCK echo process:

    socat vsock-listen:1234,fork EXEC:'/bin/cat'

Send a test payload:

    echo "Hello, VSOCK!" | socat - VSOCK-CONNECT:2:1234

Machine definition with volume mounts:

```yaml
vm:
  tag:
    label: uptime-kuma
    description: Uptime Kuma
  image:
    source: docker.io/louislam/uptime-kuma:latest
    envUsr:
    - key: FF_MOUNT_0
      val: '"{"Device": "/dev/vda", "Path": "/app/data", "Sync": true, "ReadOnly": false}"'
  config:
    bootsource:
      kernel_image_path: /home/jjzazuet/code/frag-falcon/ff-test/./src/test/resources/kernel/vmlinux-6.1.98
    machineconfig:
      mem_size_mib: 2048
      vcpu_count: 1
    drives:
    - drive_id: rootfs
      path_on_host: /home/jjzazuet/code/frag-falcon/ff-test/src/test/resources/disk.img
      is_root_device: false
network:
  brIf: br0
  dhcp: true
```