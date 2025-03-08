package io.vacco.ff.graal;

import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import org.graalvm.nativeimage.hosted.Feature;

public class FgFeature implements Feature {

  @Override public void beforeAnalysis(BeforeAnalysisAccess access) {
    // Treat JNI calls in "io.vacco.ff.**" classes as calls to a built-in library.
    PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("io_vacco_ff");
  }

}
