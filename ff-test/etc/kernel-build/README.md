## Kernel build

    wget https://mirrors.edge.kernel.org/pub/linux/kernel/v6.x/linux-6.1.98.tar.xz
    cp microvm-kernel-ci-x86_64-6.1.config ./linux-6.1.98/.config
    make -j28

This obscure stack overflow answer helped in getting Kernel 6.1 to compile and run under firecracker:

- https://unix.stackexchange.com/questions/779763/which-linux-kernel-config-options-are-required-to-get-qemu-virtio-to-work

Set `CONFIG_X86_MPPARSE=y`. Compiles with no questions asked. Why?
