"""Generated Trellis contracts for Python.

``trellis_contracts.events``  pydantic models of the event envelope and every payload contract,
                              plus ``PAYLOAD_MODELS`` mapping ``(eventType, eventVersion)`` to a model.
``trellis_contracts.api``     pydantic models of the REST API and one resource class per OpenAPI tag.
``trellis_contracts.client``  ``TrellisApi``: the resource classes bound to an ``HttpTransport``.

The package carries no HTTP stack of its own: the consuming application passes something that
implements :class:`trellis_contracts.transport.HttpTransport`, so retries, authentication and error
mapping stay where they belong. See docs/architecture/contracts.md in the backend repo.
"""

from trellis_contracts.client import TrellisApi
from trellis_contracts.transport import HttpResponse, HttpTransport

__all__ = ["HttpResponse", "HttpTransport", "TrellisApi"]
