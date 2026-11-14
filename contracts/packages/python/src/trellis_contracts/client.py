"""``TrellisApi``: every generated resource, bound to one transport."""

from trellis_contracts.api.resources import GeneratedResources
from trellis_contracts.transport import HttpTransport


class TrellisApi(GeneratedResources):
    """Typed access to the backend API: ``TrellisApi(transport).notice.list_notices()``."""

    def __init__(self, http: HttpTransport) -> None:
        self.http = http
