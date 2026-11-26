# Parlor branch archive

`branches.tsv` records every remote branch tip in the 2026-11-20
audit snapshot. The archive commit's parents retain the recorded branch
histories. Existing branches remain available for active work and release
governance.

To recover a retired branch, look up its recorded object ID and run
`git branch <restored-name> <recorded-object-id>`. Use `git show <object-id>`
to inspect a recorded tip without checking it out.

`branches-before-smart-lobby-cleanup.tsv` records the November 24 protected
Smart Lobby checkpoint, exact branch tips, reachability, and dispositions.
Fetch this archive before using the restoration command above. For example,
`git fetch origin archive/branches-2026-11-20` followed by
`git branch restored/smart-lobby-admission 98011a4f19ce8ea574c5ba5671d2255a3372d7cb`
restores the exact merged admission tip.

`branches-before-361-cleanup.tsv` records the 2026-11-26 remote branch
inventory at main 0c1b8241. Reachability describes ancestry before this
archive update. The four previously unpreserved tips are parents of this
archive commit. No branch was deleted by this archive update.
