# 9. CI uploads every tag to Play's testing tracks; production is a human promotion

Date: 2026-10-06

Status: Accepted

## Context

Until this decision nothing in `release.yml` talked to Play. A release meant downloading
`app-release.aab` from the GitHub release, uploading it in Play Console, typing release notes and
starting the rollout by hand. The hand-off had two weak points: the file uploaded was whatever was
on disk, not provably the one `upgrade_test` had passed, and the only way to put a build in front of
real Play (needed to exercise in-app updates, [ADR-0008](0008-in-app-updates-flexible-only.md))
was internal app sharing, done by hand per build.

The owner's releases already went to closed testing, open testing and a staged production rollout
in one console session (Play listing on 2026-10-06: `2.2.5.0-rc3` completed on `alpha` and `beta`,
`inProgress` on `production`).

Safe-Box is a password vault whose highest-severity failure mode is silent and unrecoverable
(`symmetricDataKey` regeneration, see
[persistence-and-crypto.md](../architecture/persistence-and-crypto.md)). A production rollout is
therefore not something to hand to a script on the strength of a green pipeline.

## Decision

1. **Every `v*` tag, RC and stable, is uploaded to the internal (`internal`), closed testing
   (`alpha`) and open testing (`beta`) tracks as completed releases, in one Play edit**, by a
   `release_on_play` job that runs after `release_on_github`, so Play never holds a build GitHub
   does not and a Play-side refusal can be re-run alone.
2. **Committing the edit sends the changes for review; the job does not try to hold them.** The
   API's `changesNotSentForReview` is not a general hold (see the alternatives), so the testing
   tracks are reviewed on their own and reach testers without anyone touching the console (`internal`
   is available immediately without review).
3. **Production is reached only by promoting in Play Console** (*Closed testing → Promote release
   → Production*, bundle and notes pre-filled, rollout percentage chosen there). The service
   account is granted *Release apps to testing tracks* and nothing else, so neither the workflow
   nor a leaked `PLAY_SERVICE_ACCOUNT_JSON` can ship to users.
4. **Release notes are per version, optional, and committed:**
   `distribution/whatsnew/<MAJOR.MINOR.DBVERSION.FIX>/whatsnew-<locale>`, picked by the tag's base
   version. No directory means the release is uploaded without notes and they are typed in the
   console when promoting; a directory that exists must be valid (`scripts/lib/release-notes.sh`).
5. **The upload action is `r0adkll/upload-google-play`, pinned to a commit.** It is the one
   third-party action that receives a credential able to publish the app.

## Why

- **All three testing tracks (`internal`, `alpha`, `beta`) in one edit.** Closed and open testing are
  where every release already went by hand, while internal gives instant availability without waiting
  on Play's automated testing-track review. Including all three in the same edit adds zero console
  steps.
- **Testing tracks, not production-as-draft.** A draft on production is installable nowhere and
  needs the production permission on the service account.
- **Notes keyed by version, not a single file.** A single `whatsnew-en-US` either ships the previous
  release's text when forgotten or needs a gate that treats forgetting as a failure. Keyed
  directories cannot reuse old text, degrade to "no notes" when forgotten, and accumulate history.
  Same layout as fastlane's `changelogs/`, keyed by tag because `versionCode` exists only inside CI.
- **Notes are optional.** Verified in the action's `src/whatsnew.ts` (v1.1.5): an empty
  `whatsNewDirectory` reads nothing, and Play accepts a track release without notes. Promotion
  pre-fills the form from the source release, so a note set in CI is still editable before
  production.
- **Why a validity check at all.** The same source shows every file name's suffix after
  `whatsnew-` is sent as the language verbatim, and other names are ignored. A typo therefore drops
  the notes silently or sends `en-US.txt` as a language; the 500-character limit is enforced by Play
  only when the edit is committed, after the bundle upload. Both are cheaper to catch in the PR.

## Alternatives rejected

| Alternative | Why not |
|---|---|
| Commit without sending for review (`changesNotSentForReview=true`), add production by hand, submit all tracks together | Not a knob. Play accepts the flag only when it cannot send automatically (rejected or un-sent changes already queued); otherwise the commit fails with `400 Changes are sent for review automatically. The query parameter changesNotSentForReview must not be set` (gradle-play-publisher #970, 2021; sceneview #2211, May 2026, through this same action). Submitting together would also only delay the testers. |
| Closed + open testing at 100% and production as a 10% staged rollout, every tag | Considered the same day and dropped: it puts a production rollout, however small, on every tag of a vault with an unrecoverable failure mode, needs the production permission on the service account, and the step it saves is the one that should stay deliberate. It also cannot be expressed with the action (one status for all tracks) and would have needed an own publisher. |
| Internal track only, promote from there | One more console step per release than the tracks the owner was already releasing to by hand, for no safety the testing-only permission does not already give. |
| Gradle Play Publisher (`com.github.triplet.play`) | Another plugin on the AGP classpath (see the module trap in `PROJECT_FACTS.md`), applies to local builds, and brings listing management nobody asked for. One upload does not justify it. |
| fastlane `supply` | A Ruby toolchain nothing else in the repository uses. |
| Stable tags → production as `draft` | Not installable anywhere; needs the production permission on the service account. |
| Stable tags → production `completed` with a `userFraction` | Hands-off production for a vault with an unrecoverable failure mode, to save one click. |
| Single `whatsnew-en-US` + "differs from N-1" gate | Fooled by a typo fix, rejects a legitimately identical hotfix note, and cannot express "no notes this time". |
| Notes from the tag annotation | Every tag in this repository is lightweight, and the text would be unreviewable. |
| Notes from GitHub's `generate_release_notes` | PR titles with `#numbers` and internal categories, not user-facing, and well over 500 characters. |
| Keyless auth (Workload Identity Federation) | The action requires a service-account JSON; there is no ADC path. Scope and rotate the key instead. |

## Consequences

- `inAppUpdatePriority` is now settable, since it exists only in the Publishing API. ADR-0008's
  "CI does not publish to Play" premise no longer holds; its decision stands until a forced-update
  need exists.
- The in-app-update manual test needs two tags, not internal app sharing
  ([in-app-updates.md](../architecture/in-app-updates.md#manual-test-with-real-play)).
- Open testing is public, so a tag is visible to anyone who opted in, RC included; that was already
  the case by hand.
- A tag pushed while a promotion is still in review makes Play withdraw that submission and re-send
  it together with the new changes (`edits.commit` default `changesInReviewBehavior =
  CANCEL_IN_REVIEW_AND_SUBMIT`, discovery document 2026-10-06). The review restarts; nothing is
  lost.
- `PLAY_SERVICE_ACCOUNT_JSON` is a long-lived credential. If it leaks, revoke the key in GCP and
  halt the release in the console; the blast radius is the three testing tracks.
- Store listing text and screenshots stay manual ([store-listing.md](../store-listing.md)).
- Procedure, setup and failure modes: [release-and-ci skill](../../.agents/skills/release-and-ci/SKILL.md#play-upload).
