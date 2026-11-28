# trellis-contracts (Python)

Generated from the Trellis backend contracts: event payload models and a typed client for the REST
API. Build CI uploads distributions; registry publication is deferred. Never edit generated modules.
After the first release, configure the selected registry and pin a version:

```bash
uv add trellis-contracts==1.4.0
```

```python
from trellis_contracts import TrellisApi
from trellis_contracts.events import PAYLOAD_MODELS, envelope

api = TrellisApi(my_http_transport)          # anything implementing HttpTransport
page = api.notification.list_notifications(first=20)     # typed request and response

model = PAYLOAD_MODELS[("notification.published", 1)]
payload = model.model_validate(message["payload"])
```

What is inside:

| Module                     | What                                                                 |
| -------------------------- | -------------------------------------------------------------------- |
| `trellis_contracts.events` | envelope + one module per `contracts/events/<type>/vN.schema.json`, and `PAYLOAD_MODELS` |
| `trellis_contracts.api`    | pydantic models of the OpenAPI schemas, one resource class per tag    |
| `trellis_contracts.transport` | the `HttpTransport` protocol the consumer implements               |
| `trellis_contracts.client` | `TrellisApi`, the resources bound to one transport                    |

Models are tolerant readers (`extra="ignore"`), so a consumer pinned to an older minor version keeps
working when the backend adds a field. Release process and versioning:
`docs/architecture/contracts.md` in the backend repo.
