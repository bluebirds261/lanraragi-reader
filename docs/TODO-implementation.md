# TODO implementation baseline

This file records the requested UX/data-flow changes tracked while implementing the reader/library/download refactor.

1. Reader bottom bar: merge grid with the page slider; while slider is visible show five page previews centered on the target page; move previews with the slider; put remaining reader settings into a drawer button to the right of TOC.
2. Reader bottom bar remains visible longer and any interaction resets the hide timer.
3. Local archives open a detail view before entering the reader.
4. Reader TOC is backed by the same server TOC API used by detail editing; add-TOC defaults to the current page.
5. Cover loading should prefer the thumbnail endpoint and avoid redundant URL/cache misses.
6. Library refresh returns the scroll position to the top.
7. Keep only the three most recent reading sessions, including cloud and local archives; tapping a session resumes its progress.
8. Local archives are never uploaded automatically.
9. Local gallery loading should avoid unnecessary rescans and use local file models directly for static page resources.
10. Local background scanning should only run when the local source fingerprint changes.
11. Original downloads and offline-cache downloads share the same DownloadManager queue/concurrency policy.
12. Download navigation badge is visible only while active download tasks exist and equals the active task count.
13. Download screen adds a top-right action menu: refresh / delete / pause all / start all.
14. Saved cloud and local galleries should read pages from local storage when available.
15. DownloadManager concurrency is configurable.
16. Move the server card from About to Connection and edit it via a dialog.
