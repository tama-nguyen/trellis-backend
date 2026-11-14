/**
 * The runnable backend. Assembles the platform modules (each contributes an auto-configuration) and hosts the
 * app-level pieces: typed feature flags, the public app-config endpoint, operator endpoints and dev-only tools.
 * Business modules are added as Gradle dependencies and live in their own packages.
 */
package io.trellis.app;
