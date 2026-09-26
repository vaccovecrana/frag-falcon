package io.vacco.ff.firecracker;

import java.lang.Long;

/**
 * Describes the balloon device statistics.
 */
public class BalloonStats {
  public Long actual_mib;

  public Long actual_pages;

  public Long available_memory;

  public Long disk_caches;

  public Long free_memory;

  public Long hugetlb_allocations;

  public Long hugetlb_failures;

  public Long major_faults;

  public Long minor_faults;

  public Long swap_in;

  public Long swap_out;

  public Long target_mib;

  public Long target_pages;

  public Long total_memory;

  /**
   * Actual amount of memory (in MiB) the device is holding.
   */
  public BalloonStats actual_mib(Long actual_mib) {
    this.actual_mib = actual_mib;
    return this;
  }

  /**
   * Actual number of pages the device is holding.
   */
  public BalloonStats actual_pages(Long actual_pages) {
    this.actual_pages = actual_pages;
    return this;
  }

  /**
   * An estimate of how much memory is available (in bytes) for starting new applications, without pushing the system to swap.
   */
  public BalloonStats available_memory(Long available_memory) {
    this.available_memory = available_memory;
    return this;
  }

  /**
   * The amount of memory, in bytes, that can be quickly reclaimed without additional I/O. Typically these pages are used for caching files from disk.
   */
  public BalloonStats disk_caches(Long disk_caches) {
    this.disk_caches = disk_caches;
    return this;
  }

  /**
   * The amount of memory not being used for any purpose (in bytes).
   */
  public BalloonStats free_memory(Long free_memory) {
    this.free_memory = free_memory;
    return this;
  }

  /**
   * The number of successful hugetlb page allocations in the guest.
   */
  public BalloonStats hugetlb_allocations(Long hugetlb_allocations) {
    this.hugetlb_allocations = hugetlb_allocations;
    return this;
  }

  /**
   * The number of failed hugetlb page allocations in the guest.
   */
  public BalloonStats hugetlb_failures(Long hugetlb_failures) {
    this.hugetlb_failures = hugetlb_failures;
    return this;
  }

  /**
   * The number of major page faults that have occurred.
   */
  public BalloonStats major_faults(Long major_faults) {
    this.major_faults = major_faults;
    return this;
  }

  /**
   * The number of minor page faults that have occurred.
   */
  public BalloonStats minor_faults(Long minor_faults) {
    this.minor_faults = minor_faults;
    return this;
  }

  /**
   * The amount of memory that has been swapped in (in bytes).
   */
  public BalloonStats swap_in(Long swap_in) {
    this.swap_in = swap_in;
    return this;
  }

  /**
   * The amount of memory that has been swapped out to disk (in bytes).
   */
  public BalloonStats swap_out(Long swap_out) {
    this.swap_out = swap_out;
    return this;
  }

  /**
   * Target amount of memory (in MiB) the device aims to hold.
   */
  public BalloonStats target_mib(Long target_mib) {
    this.target_mib = target_mib;
    return this;
  }

  /**
   * Target number of pages the device aims to hold.
   */
  public BalloonStats target_pages(Long target_pages) {
    this.target_pages = target_pages;
    return this;
  }

  /**
   * The total amount of memory available (in bytes).
   */
  public BalloonStats total_memory(Long total_memory) {
    this.total_memory = total_memory;
    return this;
  }

  public static BalloonStats balloonStats() {
    return new BalloonStats();
  }
}
