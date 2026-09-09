package com.lanraragi.reader.data.local.incremental

/** Pure scanner comparing a current SAF tree to the persisted node snapshot. */
class IncrementalScanner {
    fun scan(previous: Collection<SafNode>, currentRoots: Collection<SafNode>): ScanDelta {
        val old = flatten(previous).associateBy { it.identity }
        val now = flatten(currentRoots).associateBy { it.identity }
        val added = mutableListOf<ScanItem>(); val changed = mutableListOf<ScanItem>()
        val removed = mutableListOf<ScanItem>(); val unchanged = mutableListOf<ScanItem>(); val unavailable = mutableListOf<ScanItem>()
        now.values.forEach { n ->
            val prior = old[n.identity]
            when {
                !n.readable -> unavailable += ScanItem(n.identity, n, ScanDisposition.UNAVAILABLE)
                prior == null -> added += ScanItem(n.identity, n, ScanDisposition.ADDED)
                prior.fingerprint != n.fingerprint -> changed += ScanItem(n.identity, n, ScanDisposition.CHANGED)
                else -> unchanged += ScanItem(n.identity, n, ScanDisposition.UNCHANGED)
            }
        }
        old.values.filter { it.identity !in now }.forEach { removed += ScanItem(it.identity, null, ScanDisposition.REMOVED) }
        return ScanDelta(added, changed, removed, unchanged, unavailable)
    }

    private fun flatten(roots: Collection<SafNode>): List<SafNode> = buildList {
        fun visit(n: SafNode) { add(n); if (n.directory) n.children.forEach(::visit) }
        roots.forEach(::visit)
    }
}
