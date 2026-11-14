"""PluginRegistry loading rules: lazy import, disabled-with-reason, aliases, type checks."""

from abc import ABC, abstractmethod

import pytest

from trellis_core.errors import PluginError
from trellis_core.plugins import PluginRegistry, SupportStatus, get_plugin_meta, plugin
from trellis_events.worker import WorkerDefinition, worker_registry


class Base(ABC):
    @abstractmethod
    def run(self) -> str: ...


class Concrete(Base):
    def run(self) -> str:
        return "ran"


class StillAbstract(Base):
    pass


class Unrelated:
    pass


def _registry() -> PluginRegistry[Base]:
    return PluginRegistry[Base](Base)


def test_lazy_entry_is_imported_on_first_get_only() -> None:
    registry = _registry()
    registry.register_lazy("concrete", f"{__name__}:Concrete")
    assert [s.state for s in registry.summary()] == ["lazy"]
    assert registry.get("concrete") is Concrete
    assert [s.state for s in registry.summary()] == ["loaded"]


def test_missing_module_disables_only_that_plugin_and_names_the_extra() -> None:
    registry = _registry()
    registry.register_lazy("ghost", "trellis_no_such_module:Thing")
    registry.register("concrete", Concrete)
    with pytest.raises(PluginError, match=r"disabled: missing dependency .*--extra ghost"):
        registry.get("ghost")
    assert registry.get("concrete") is Concrete
    states = {s.name: s.state for s in registry.summary()}
    assert states == {"ghost": "disabled", "concrete": "loaded"}


def test_alias_resolves_to_target() -> None:
    registry = _registry()
    registry.register("concrete", Concrete)
    registry.register_alias("old-name", "concrete")
    assert registry.get("old-name") is Concrete


@pytest.mark.parametrize("target", ["StillAbstract", "Unrelated"])
def test_abstract_or_foreign_classes_are_rejected(target: str) -> None:
    registry = _registry()
    registry.register_lazy("bad", f"{__name__}:{target}")
    with pytest.raises(PluginError, match="disabled"):
        registry.get("bad")


def test_duplicate_names_are_rejected() -> None:
    registry = _registry()
    registry.register("concrete", Concrete)
    with pytest.raises(PluginError, match="already registered"):
        registry.register_lazy("concrete", f"{__name__}:Concrete")


def test_plugin_meta_is_not_inherited() -> None:
    @plugin(name="parent", description="d", status=SupportStatus.STABLE)
    class Parent:
        pass

    class Child(Parent):
        pass

    meta = get_plugin_meta(Parent)
    assert meta is not None
    assert meta.name == "parent"
    assert get_plugin_meta(Child) is None


def test_sample_worker_is_discovered_from_its_entry_point() -> None:
    registry = worker_registry()
    worker = registry.get("notice-stats")
    assert issubclass(worker, WorkerDefinition)
    meta = get_plugin_meta(worker)
    assert meta is not None
    assert meta.config is not None
