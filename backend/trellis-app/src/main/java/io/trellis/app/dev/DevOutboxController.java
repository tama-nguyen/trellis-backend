package io.trellis.app.dev;

import io.trellis.app.dev.application.DevPingHandler;
import io.trellis.app.dev.application.DevPingService;
import io.trellis.common.context.RequestContext;
import io.trellis.platform.web.security.PublicEndpoint;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/dev/outbox/ping} writes an event; {@code GET .../received} shows what the handler saw. */
@RestController
@Profile("dev")
@PublicEndpoint
@RequestMapping(DevOutboxController.BASE)
class DevOutboxController {
    static final String BASE = "/api/dev/outbox";

    private final DevPingService service;
    private final DevPingHandler handler;

    DevOutboxController(DevPingService service, DevPingHandler handler) {
        this.service = service;
        this.handler = handler;
    }

    @PostMapping("/ping")
    PingResponse ping(RequestContext ctx, @RequestParam(defaultValue = "hello") String message) {
        return new PingResponse(service.ping(ctx, message));
    }

    @GetMapping("/received")
    ReceivedResponse received() {
        return new ReceivedResponse(handler.received(), handler.lastEventId());
    }

    record PingResponse(UUID eventId) {}

    record ReceivedResponse(long received, UUID lastEventId) {}
}
