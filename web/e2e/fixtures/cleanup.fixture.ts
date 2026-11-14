import { test as base } from "@playwright/test";

export type Cleanup = {
  /** Register an undo step for something the test created; runs after the test, even on failure. */
  track: (label: string, undo: () => Promise<void>) => void;
};

/** Undo steps run newest first, and one failing step does not stop the rest. */
export const cleanupFixture = base.extend<{ cleanup: Cleanup }>({
  cleanup: async ({}, use) => {
    const steps: { label: string; undo: () => Promise<void> }[] = [];
    await use({ track: (label, undo) => steps.push({ label, undo }) });
    const failures: string[] = [];
    for (const step of steps.reverse()) {
      try {
        await step.undo();
      } catch (error) {
        failures.push(`${step.label}: ${String(error)}`);
      }
    }
    if (failures.length > 0) console.warn(`[cleanup] ${failures.length} step(s) failed`, failures);
  },
});
