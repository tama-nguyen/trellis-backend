package io.trellis.app.ops;

import io.trellis.app.ops.application.OpsGate;
import io.trellis.common.context.RequestContext;
import io.trellis.platform.events.application.EventOperations;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recovery endpoints for operators: replay a DLQ, rebuild a read model. Gated by the {@code opsApi}
 * flag.
 */
@RestController
@RequestMapping(OpsController.BASE)
class OpsController {
  static final String BASE = "/api/ops";
  private static final String DEFAULT_REPLAY_MAX = "100";

  private final OpsGate gate;
  private final EventOperations operations;

  OpsController(OpsGate gate, EventOperations operations) {
    this.gate = gate;
    this.operations = operations;
  }

  @PostMapping("/events/{eventType}/dlq/replay")
  ReplayResponse replay(
      final RequestContext ctx,
      @PathVariable final String eventType,
      @RequestParam(defaultValue = DEFAULT_REPLAY_MAX) final int max) {
    gate.check();
    return new ReplayResponse(eventType, operations.replayDeadLetters(ctx, eventType, max));
  }

  @GetMapping("/read-models")
  List<String> readModels() {
    gate.check();
    return operations.readModelNames();
  }

  @PostMapping("/read-models/{name}/rebuild")
  RebuildResponse rebuild(final RequestContext ctx, @PathVariable final String name) {
    gate.check();
    return new RebuildResponse(name, operations.rebuildReadModel(ctx, name));
  }

  record ReplayResponse(String eventType, int replayed) {}

  record RebuildResponse(String readModel, long processed) {}
}
