package io.vacco.ff;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Ext4FileSystem {
  private static final int BLOCK_SIZE = 1024;
  private static final int INODE_SIZE = 128;
  private static final int SUPERBLOCK_OFFSET = 1024;
  private static final int SUPERBLOCK_SIZE = 1024;

  private ByteBuffer buffer;
  private int totalBlocks;
  private int totalInodes;
  private int currentInode;
  private int currentBlock;

  public Ext4FileSystem(int sizeMB) {
    int sizeBytes = sizeMB * 1024 * 1024;
    this.totalBlocks = sizeBytes / BLOCK_SIZE;
    this.totalInodes = this.totalBlocks / 4; // Simplified calculation for demonstration
    this.currentInode = 10; // Start after reserved inodes
    this.currentBlock = 10; // Start after reserved blocks

    buffer = ByteBuffer.allocate(sizeBytes);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    initializeSuperblock();
    initializeBlockGroupDescriptorTable();
    initializeBitmapsAndTables();
  }

  private void initializeSuperblock() {
    buffer.position(SUPERBLOCK_OFFSET);

    // Writing a simple ext4 superblock
    buffer.putInt(0xEF53); // Signature
    buffer.putInt(totalInodes); // Total inodes
    buffer.putInt(totalBlocks); // Total blocks
    buffer.putInt(0); // Reserved blocks
    buffer.putInt(totalBlocks - 10); // Free blocks (simplified)
    buffer.putInt(totalInodes - 10); // Free inodes (simplified)
    buffer.putInt(1); // First data block
    buffer.putInt(BLOCK_SIZE); // Block size (2^10 = 1024)
    buffer.putInt(0); // Fragment size
    buffer.putInt(1000); // Blocks per group (simplified)
    buffer.putInt(1000); // Fragments per group (simplified)
    buffer.putInt(100); // Inodes per group (simplified)
    buffer.putInt((int) (System.currentTimeMillis() / 1000)); // Mount time
    buffer.putInt((int) (System.currentTimeMillis() / 1000)); // Write time
    buffer.putShort((short) 0); // Mount count
    buffer.putShort((short) 0); // Max mount count
    buffer.putShort((short) 0xEF53); // Magic signature
    buffer.putShort((short) 0); // State
    buffer.putShort((short) 1); // Errors
    buffer.putShort((short) 0); // Minor revision level
    buffer.putInt(0); // Last check
    buffer.putInt(0); // Check interval
    buffer.putInt(0); // Creator OS
    buffer.putInt(0); // Revision level
    buffer.putShort((short) INODE_SIZE); // Default inode size
    buffer.putShort((short) BLOCK_SIZE); // First non-reserved inode
  }

  private void initializeBlockGroupDescriptorTable() {
    buffer.position(SUPERBLOCK_OFFSET + SUPERBLOCK_SIZE);
    buffer.putInt(2); // Block bitmap block (simplified)
    buffer.putInt(3); // Inode bitmap block (simplified)
    buffer.putInt(4); // Inode table block (simplified)
    buffer.putShort((short) 0); // Free blocks count
    buffer.putShort((short) 0); // Free inodes count
    buffer.putShort((short) 0); // Used directories count
    buffer.putShort((short) 0); // Padding
    buffer.putInt(0); // Reserved
    buffer.putInt(0); // Reserved
    buffer.putInt(0); // Reserved
  }

  private void initializeBitmapsAndTables() {
    // Initialize block bitmap, inode bitmap, and inode table with simple data
    buffer.position(BLOCK_SIZE * 2); // Block bitmap block
    buffer.putInt(0xFFFFFFFF); // All blocks used

    buffer.position(BLOCK_SIZE * 3); // Inode bitmap block
    buffer.putInt(0xFFFFFFFF); // All inodes used

    buffer.position(BLOCK_SIZE * 4); // Inode table block
    // Simplified inode table initialization
    buffer.putInt(0); // Inode 1 (root directory)
    buffer.putInt(0); // Inode 2 (lost+found directory)
  }

  public void addDirectory(String path) {
    // Simplified directory addition
    int inodeNumber = currentInode++;
    int blockNumber = currentBlock++;
    buffer.position(BLOCK_SIZE * blockNumber);
    buffer.putInt(inodeNumber); // Simplified inode entry for directory
    // Additional directory entry setup
  }

  public void addFile(String path, byte[] content) {
    // Simplified file addition
    int inodeNumber = currentInode++;
    int blockNumber = currentBlock++;
    buffer.position(BLOCK_SIZE * blockNumber);
    buffer.putInt(inodeNumber); // Simplified inode entry for file
    buffer.put(content); // Write file content to the block
    // Additional file entry setup
  }

  public void writeToFile(File outputFile) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
      fos.write(buffer.array());
    }
  }

  public static void main(String[] args) {

    File outputFile = new File("./build/out-ext4.img");
    int sizeMB = 1;

    try {
      Ext4FileSystem ext4 = new Ext4FileSystem(sizeMB);
      ext4.addDirectory("/test_dir");
      ext4.addFile("/test_dir/hello.txt", "Hello, World!".getBytes());
      ext4.writeToFile(outputFile);
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(2);
    }
  }
}
