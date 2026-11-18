/**
 * The {@code publishing} feature of this module, {@code domain} layer. Features share the module because they
 * share a lifecycle (decision T26); they keep separate packages so a feature that outgrows the module can be
 * lifted out later. The fixed sub-packages of decision T17 go inside this one.
 */
package io.trellis.learningcontent.domain.publishing;
