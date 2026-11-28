# Restore reviewed Parlor branches

The companion TSV identifies each retired branch, exact tip and ancestry carrier. The iOS diagnostics and native-link preflight are historical runs. The branch-name-specific investigation workflows can be replayed by restoring the corresponding original name. Their historical run and artifact identities are unchanged.

To inspect any archived commit, first fetch the single archive branch and create a local branch at the recorded tip. To replay a branch-specific workflow, publish the exact recorded tip under its original branch name only when that investigation is scheduled.

```sh
git fetch origin main archive/branches-2026-11-20
git branch restored/diagnostic-ios-input c62fdc739ec1fb1f9663abbe7524b472a627e0ae
```

Protected `main`, `release` and `testing`, the held #258 candidate, the active native lab and the scoring branch are retained. The archive preserves both the exact native lab and the current scoring test candidate.
