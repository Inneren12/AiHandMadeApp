package com.appforcross.editor.filters.discrete

/**
 * Backward-compatible alias to keep tests and public API working.
 * Do not remove without migrating test imports.
 */
@Suppress("unused")
public typealias Mode = RoleSpreadMorphology.Mode

@Deprecated("Use RoleSpreadMorphology.Mode explicitly", ReplaceWith("RoleSpreadMorphology.Mode"))
private typealias _ = Mode
