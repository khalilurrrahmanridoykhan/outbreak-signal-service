# Repository setup

The GitHub settings for this repository are not clicked together by hand. They
are applied by [`scripts/setup-github-repo.sh`](../scripts/setup-github-repo.sh),
which is idempotent and safe to re-run. This page explains each setting and how
to check it.

```bash
scripts/setup-github-repo.sh
```

It needs the GitHub CLI, logged in as someone with admin rights on the
repository.

## What is configured, and why

| Area | Setting | Reason |
|---|---|---|
| Description, topics | Description plus 20 topics | Makes the repository discoverable and states plainly what it is |
| Features | Issues and Discussions on; Wiki and Projects off | Questions go to Discussions, bugs to Issues; documentation lives in `docs/` next to the code |
| Merging | Merge commits only; head branches deleted after merge | Keeps each phase visible as one merge in the history |
| Dependabot | Alerts and security updates on; version updates in `.github/dependabot.yml` | Known-vulnerable dependencies are flagged and patched |
| Secret scanning | Scanning and push protection on | Blocks a committed credential before it lands |
| Private vulnerability reporting | On | Gives reporters a private channel (see `SECURITY.md`) |
| Labels | Standard set plus `area:*` labels | Consistent triage |
| Milestones | One per phase | The public roadmap |
| Ruleset `main protection` | Pull request required, checks `build`, `container`, `commit-lint`, `codeql` must pass, conversations resolved, no force push, no deletion | `main` always builds and is only changed by reviewed history. Approvals stay at 0 because there is one maintainer and GitHub does not allow approving your own pull request |
| Ruleset `release tags are immutable` | `phase-*` and `v*` tags cannot be deleted or moved | Published versions stay trustworthy |

## Not scriptable

- **Social preview image** (1280x640): upload in *Settings > General > Social
  preview*.
- **Pinning to the profile:** done by the owner in the GitHub UI.

## Checking the result

```bash
REPO=khalilurrrahmanridoykhan/outbreak-signal-service

gh repo view "$REPO" --json visibility,description,repositoryTopics,hasIssuesEnabled,hasDiscussionsEnabled,hasWikiEnabled,mergeCommitAllowed,squashMergeAllowed,rebaseMergeAllowed,deleteBranchOnMerge
gh api "repos/$REPO" --jq '.security_and_analysis'
gh api "repos/$REPO/private-vulnerability-reporting"
gh api "repos/$REPO/rulesets" --jq '.[] | {name, enforcement, target}'
gh label list --repo "$REPO"
gh api "repos/$REPO/milestones?state=all" --jq '.[].title'
```

Compare the output with the table above. Do not assume a call worked because it
did not print an error.
