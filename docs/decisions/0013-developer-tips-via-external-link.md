# 13. Developer tips are an external Buy Me a Coffee link: no Play Billing, no WebView, no in-app effect of a tip

Date: 2026-10-24

Status: Accepted

## Context

Safe Box is free, open source and ad-free, with no paid tier. Adding a voluntary tip option in
Settings → Support & Community has to stay on the right side of Google Play's Payments policy
([policy](https://support.google.com/googleplay/android-developer/answer/9858738),
[FAQ](https://support.google.com/googleplay/android-developer/answer/10281818)). The FAQ answers
the exact question:

> **Do direct tips or contributions from user to creator require Play’s billing system?**
> In cases where 100% of the tip or contribution from a user goes to the creator and the payment
> does not grant access to any digital content or services (including stickers, badges, special
> emojis etc.), then we regard this as a peer-to-peer payment and use of Google Play’s billing
> system is not required. If any of these things is not true, then Google Play’s billing system
> must be used in accordance with the policy requirements.

Three properties of the app also bear on the design:

1. **Away-timeout lock.** Leaving the app starts `ActiveSessionManager`'s logout timer; only a
   bounded system settings redirect (`OnDeviceSecurityRequiredOpenSettingsClicked`) pauses it.
2. **Package visibility on API 30+.** `<queries>` in `AndroidManifest.xml` declares only `https`
   (`ACTION_VIEW`). The existing *Rate on Play Store* row uses `market://` and still resolves via
   `resolveActivity` because `com.android.vending` is in Android's `forceQueryable` list
   (verified on the API 35 Play image via `adb shell dumpsys package queries`, 2026-10-24).
3. **Single Activity.** Firebase's automatic `screen_view` is one screen for the whole app, so
   Settings has no screen-impression denominator.

## Decision

1. **Outbound browser link only.** Settings → Support & Community gets a final `TextPreference`
   row (*Buy me a coffee*, `Icons.Outlined.Coffee`) that opens
   `https://buymeacoffee.com/ni3verma` via `Context.openExternalUrl` (`Intent.ACTION_VIEW`),
   identical to the GitHub and Play rows.
2. **A tip has zero effect inside the app — permanently.** The app never learns whether a tip
   occurred and never grants a badge, theme, thank-you screen, suppressed prompt, or any other
   state. The row copy states *"Safe Box is free, open source, and ad-free — every feature stays
   free for everyone. If it helps you, a small tip keeps development going."* The Buy Me a Coffee
   page keeps Extras, Memberships, Wishlist and Commissions disabled (verified 2026-10-24 against
   the Inertia `data-page` payload on `https://buymeacoffee.com/ni3verma`):
   ```sh
   curl -sL https://buymeacoffee.com/ni3verma | grep -o 'data-page="[^"]*"' | python3 -c \
     'import html,json,sys; p=json.loads(html.unescape(sys.stdin.read()[11:-1]))["props"]; \
      print({k:p.get(k) for k in ("is_payout_connected","membership_enabled","reward_count","wishlist_count")}); \
      print({k:p["project"].get(k) for k in ("project_enable","commission_enable")})'
   # {'is_payout_connected': True, 'membership_enabled': False, 'reward_count': 0, 'wishlist_count': None}
   # {'project_enable': '', 'commission_enable': ''}
   ```
3. **Do not pause `ActiveSessionManager`.** A browser checkout is an unbounded absence; the away
   timeout locks the vault on schedule, matching the GitHub and Play rows.
4. **Accept the 5 % platform-fee ambiguity as a revertible testing-track canary.** Buy Me a Coffee
   deducts a 5 % fee, so "100 % to the creator" is not literal (the FAQ targets platforms that take
   a creator cut; here the developer is the creator). Because the feature has no persisted state or
   schema change, it is a single-commit revert if Play review ever objects on the internal/closed/
   open testing tracks ([ADR-0009](0009-play-upload-testing-tracks.md)).

## Alternatives rejected

- **In-app WebView or embedded BMC widget:** Payments policy §4 explicitly names in-app webviews as
  a prohibited channel when steering applies; a card-entry form inside a password manager is
  phishing-shaped UX; privacy mode's `FLAG_SECURE` and autofill interact poorly with third-party
  checkout pages.
- **Google Play Billing consumable tip SKU:** 15 % fee plus the Play Billing library, consumable
  acknowledgement flow and purchase-state handling for a voluntary tip with no entitlement.
- **Custom Tabs (`androidx.browser`):** adds a dependency while still stopping `MainActivity` (so
  the away-timeout behaviour is unchanged) and diverges from the sibling rows.
- **Post-backup or after-N-logins tip nudges:** interruptive in a utility app and unsupported by
  any demand data; instrument the passive row first ([ADR-0010](0010-empty-vault-is-the-onboarding.md)
  principle).

## Reading the telemetry

Each tap logs `open_buy_me_a_coffee` (`AnalyticsKey.OPEN_BUY_ME_A_COFFEE`, no parameters). Because
Settings has no `screen_view`, compare `open_buy_me_a_coffee` event and user counts against the
same-surface baselines `open_github` and `open_play_store`. Completed tips come from the Buy Me a
Coffee dashboard as an unattributed total across the Settings row, `README.md`, and `.github/FUNDING.yml`
(comparing `supporters` against `open_buy_me_a_coffee` is an upper-bound reference, not a Settings-only
conversion rate).
