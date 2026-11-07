// Prewarmed parts for a list of widgets (= Pools.swift on iOS, the validator
// half so far). A chat creates a WidgetHost per bubble as it scrolls in; the
// validator's QuickJS context and bundle evaluation are the same for every
// widget, so they are built ahead of time on the shared validator thread and a
// host takes one ready-made (`WidgetHost.Options.pools`). On destroy the
// validator goes back with a fresh instance instead of a new context. The pool
// is filled by the app (`prewarm`), never implicitly; all members are main-thread.

package org.telegram.widgetsandbox

object ValidatorPool {
    /** How many idle validators to keep built; refilled on the shared validator thread. */
    var targetSize = 0
        set(value) { field = value; refill() }
    private var resources: SandboxResources? = null
    var created = 0; private set
    var taken = 0; private set
    var hits = 0; private set
    var recycled = 0; private set
    private val idle = ArrayList<TrustedValidator>()

    val count: Int get() = idle.size

    fun prewarm(resources: SandboxResources, n: Int) {
        this.resources = resources
        targetSize = maxOf(targetSize, n)
    }

    /** A ready validator for `theme`, or null when the pool is empty (the host then builds its own). */
    fun take(theme: String): TrustedValidator? {
        taken += 1
        val v = idle.removeLastOrNull() ?: run { refill(); return null }
        hits += 1
        // Pooled validators are created for "light"; the host's theme is applied by a fresh instance.
        v.perform { v.reset(theme) }
        refill()
        return v
    }

    /** Back into the pool with a fresh instance (cheaper than a new context); closed when the pool already holds twice its target. */
    fun recycle(validator: TrustedValidator) {
        if (idle.size >= maxOf(targetSize * 2, 1)) { validator.close(); return }
        validator.onDiag = { _, _ -> }
        validator.cpuSink = null
        validator.perform { validator.reset("light") }
        idle.add(validator)
        recycled += 1
    }

    /** Closes every idle validator (tests; a product keeps the pool for the life of the process). */
    fun drain() {
        idle.forEach { it.close() }
        idle.clear()
    }

    /** New contexts are built on the shared validator thread; the main thread never waits for the bundle. */
    private fun refill() {
        val res = resources ?: return
        while (idle.size < targetSize) {
            idle.add(TrustedValidator.background("light", res, TrustedValidator.sharedThread) { _, _ -> })
            created += 1
        }
    }

    val status: String get() = "validators $count/$targetSize (hits $hits/$taken, recycled $recycled, created $created)"
}
