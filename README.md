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

`branches-after-initializer-benchmark.tsv` records the November 26 remote
inventory. The simulator-only Kotlin/Native experiment at 0ef20ab6 is
preserved as an archive parent. It rebuilt the unsigned simulator wrapper
in 27m58s including setup and packaging. The optimized app was not run by
the Debug Swift-host test lane, so the flag was not promoted to main.

`branches-with-initializer-runtime.tsv` records the remote branch snapshot
before archiving the Release runtime experiment at `c1850dda`. Its source is
an explicit parent of this archive. The benchmark ref remains the source
of [Production verification run 45784196286](https://github.com/parlor-game/parlor/actions/runs/45784196286).
This archive update preserves the source and does not establish a test result.

`branches-with-party-night-integration.tsv` records the Party Night
integration at `c5b41d1b`, preserved as a parent of the archive.
The prior installed verification applies to `0b13c789`. The integrated
revision has its own [focused Desktop run 45785588407](https://github.com/parlor-game/parlor/actions/runs/45785588407).

`branches-with-workflow-contract-preview.tsv` records the initializer
benchmark's targeted contract correction at `08d853e6`. The exact
Release runtime experiment source remains `c1850dda`. The scoped Linux
check is [run 45787194920](https://github.com/parlor-game/parlor/actions/runs/45787194920).

`branches-with-lobby-rules-preview.tsv` records the exact `b4281ab6`
candidate of the lobby rules integration, preserved as a parent.
[Run 45787593246](https://github.com/parlor-game/parlor/actions/runs/45787593246)
tests only the affected room lobby rendering on that source.

`branches-with-party-completion-preview.tsv` records the source of
[run 45789071111](https://github.com/parlor-game/parlor/actions/runs/45789071111),
which tests only the changed Whodunit and Mafia Party-completion guards.
