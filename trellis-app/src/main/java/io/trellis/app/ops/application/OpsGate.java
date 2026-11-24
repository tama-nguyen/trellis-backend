package io.trellis.app.ops.application;

import io.trellis.app.config.FeatureFlags;
import io.trellis.common.aop.RequiresFeature;
import org.springframework.stereotype.Service;

/** Feature gate for the operator API: 404 while the {@code opsApi} flag is off. */
@Service
public class OpsGate {
  @RequiresFeature(flag = FeatureFlags.OPS_API)
  public void check() {
    // The aspect does the work; reaching this line means the flag is on.
  }
}
