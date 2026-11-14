"""Plugin metadata (``@plugin``) and the lazy, entry-point based :class:`PluginRegistry`.

``@plugin`` is the Python equivalent of a Java annotation read by reflection: it attaches a frozen
:class:`PluginMeta` to a class, and docs, ``trellis worker list`` and the registry read it from there.

:class:`PluginRegistry` discovers plugins from an entry point group only; nothing registers itself
as an import side effect. Each key is in one of three states:

* *lazy*: ``"module:attr"`` not imported yet (importing every plugin at startup is slow and makes one
  broken plugin break all of them);
* *loaded*: the class, checked to be a concrete subclass of the registry's base;
* *disabled*: import failed; the exception is kept and reported with the reason, and a missing
  module turns into "install the ``<extra>`` extra".

Adapted from DataHub's ``datahub.ingestion.api.registry.PluginRegistry`` and
``datahub.ingestion.api.decorators`` (re-implemented; DataHub's cwd-on-sys.path import is left out
on purpose because it loads code from wherever the process happens to run).
"""

import contextlib
import importlib
import inspect
import threading
from collections.abc import Callable
from dataclasses import dataclass, field
from enum import IntEnum
from importlib.metadata import entry_points
from typing import cast

from trellis_core.config import ConfigModel
from trellis_core.errors import PluginError

PLUGIN_META_ATTR = "__trellis_plugin_meta__"


class SupportStatus(IntEnum):
    """Ordered so ``status >= SupportStatus.STABLE`` reads naturally."""

    UNKNOWN = 0
    INCUBATING = 1
    TESTING = 2
    STABLE = 3


@dataclass(frozen=True)
class PluginMeta:
    name: str
    description: str
    config: type[ConfigModel] | None = None
    status: SupportStatus = SupportStatus.INCUBATING
    capabilities: frozenset[str] = field(default_factory=frozenset)


def plugin[C: type](
    *,
    name: str,
    description: str,
    config: type[ConfigModel] | None = None,
    status: SupportStatus = SupportStatus.INCUBATING,
    capabilities: frozenset[str] = frozenset(),
) -> Callable[[C], C]:
    """Class decorator that records :class:`PluginMeta` on the class.

    ``cls.__dict__`` (not ``getattr``) is used when reading, so a subclass never inherits its
    parent's identity by accident; it must declare its own ``@plugin``.
    """
    meta = PluginMeta(name, description, config, status, capabilities)

    def decorate(cls: C) -> C:
        setattr(cls, PLUGIN_META_ATTR, meta)
        return cls

    return decorate


def get_plugin_meta(cls: type) -> PluginMeta | None:
    meta = cls.__dict__.get(PLUGIN_META_ATTR)
    return meta if isinstance(meta, PluginMeta) else None


@dataclass(frozen=True)
class PluginStatus:
    name: str
    state: str  # "lazy" | "loaded" | "disabled"
    target: str
    reason: str | None = None


class PluginRegistry[T]:
    """Name -> plugin class, loaded on first use. ``T`` is the base class every plugin extends.

    ``base`` is typed ``type`` (not ``type[T]``) because mypy rejects abstract classes there; pass
    the type argument explicitly: ``PluginRegistry[WorkerDefinition](WorkerDefinition, ...)``.
    """

    def __init__(
        self,
        base: type,
        entry_point_group: str | None = None,
        *,
        install_hint: str = "uv sync --extra {extra}",
    ) -> None:
        self._base = base
        self._group = entry_point_group
        self._install_hint = install_hint
        self._lock = threading.RLock()
        self._entries: dict[str, str | type[T] | Exception] = {}
        self._aliases: dict[str, str] = {}
        self._entry_points_loaded = entry_point_group is None

    # -- registration ---------------------------------------------------------------------------

    def register(self, name: str, cls: type[T]) -> None:
        with self._lock:
            self._ensure_new(name)
            self._entries[name] = self._check(name, cls)

    def register_lazy(self, name: str, target: str) -> None:
        """``target`` is ``"package.module:ClassName"``."""
        if ":" not in target:
            raise PluginError(f"plugin {name!r}: target must be 'module:attr', got {target!r}")
        with self._lock:
            self._ensure_new(name)
            self._entries[name] = target

    def register_disabled(self, name: str, reason: Exception) -> None:
        with self._lock:
            self._ensure_new(name)
            self._entries[name] = reason

    def register_alias(self, alias: str, target: str) -> None:
        """Keeps an old plugin name working after a rename."""
        with self._lock:
            self._ensure_new(alias)
            self._aliases[alias] = target

    def _ensure_new(self, name: str) -> None:
        if name in self._entries or name in self._aliases:
            raise PluginError(f"plugin {name!r} is already registered")

    # -- lookup ---------------------------------------------------------------------------------

    def get(self, name: str) -> type[T]:
        self._load_entry_points()
        with self._lock:
            key = self._aliases.get(name, name)
            if key not in self._entries:
                known = ", ".join(sorted(self._entries)) or "none"
                raise PluginError(f"unknown plugin {name!r} (known: {known})")
            entry = self._entries[key]
            if isinstance(entry, str):
                entry = self._import(key, entry)
                self._entries[key] = entry
            if isinstance(entry, Exception):
                raise PluginError(
                    f"plugin {key!r} is disabled: {self._reason(key, entry)}"
                ) from entry
            return entry

    def names(self) -> list[str]:
        self._load_entry_points()
        with self._lock:
            return sorted(self._entries)

    def summary(self, *, load: bool = False) -> list[PluginStatus]:
        """Every plugin with its state; ``load=True`` imports lazy ones to surface broken plugins."""
        out: list[PluginStatus] = []
        for name in self.names():
            if load:
                with contextlib.suppress(PluginError):
                    self.get(name)
            with self._lock:
                entry = self._entries[name]
            if isinstance(entry, str):
                out.append(PluginStatus(name, "lazy", entry))
            elif isinstance(entry, Exception):
                out.append(PluginStatus(name, "disabled", "", self._reason(name, entry)))
            else:
                out.append(PluginStatus(name, "loaded", f"{entry.__module__}:{entry.__qualname__}"))
        return out

    # -- internals ------------------------------------------------------------------------------

    def _load_entry_points(self) -> None:
        with self._lock:
            if self._entry_points_loaded or self._group is None:
                return
            for ep in entry_points(group=self._group):
                if ep.name not in self._entries:
                    self._entries[ep.name] = ep.value
            self._entry_points_loaded = True

    def _import(self, name: str, target: str) -> type[T] | Exception:
        module_name, _, attr = target.partition(":")
        try:
            module = importlib.import_module(module_name)
            obj: object = module
            for part in attr.split("."):
                obj = getattr(obj, part)
            if not isinstance(obj, type):
                raise PluginError(f"{target} is not a class")
            return self._check(name, obj)
        except Exception as exc:  # noqa: BLE001 - any import failure disables only this plugin
            return exc

    def _check(self, name: str, cls: type) -> type[T]:
        if not issubclass(cls, self._base):
            raise PluginError(f"plugin {name!r}: {cls.__qualname__} is not a {self._base.__name__}")
        if inspect.isabstract(cls):
            raise PluginError(f"plugin {name!r}: {cls.__qualname__} is abstract")
        return cast("type[T]", cls)

    def _reason(self, name: str, exc: Exception) -> str:
        if isinstance(exc, ModuleNotFoundError):
            hint = self._install_hint.format(extra=name)
            return f"missing dependency {exc.name!r}; install it with: {hint}"
        return f"{type(exc).__name__}: {exc}"
