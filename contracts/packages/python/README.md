# trellis-contracts (Python)

Generated from the Trellis backend contracts: event payload models and a typed client for the REST
API. Published from the backend repo on a release tag; never edit the generated modules, and never
copy the schemas into a consumer.

```bash
uv add trellis-contracts==1.4.0 --index trellis=https://gitlab.example.test/api/v4/projects/<id>/packages/pypi/simple
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
