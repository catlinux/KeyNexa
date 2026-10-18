# Design system

[English](DESIGN.md) | [Español](es/DESIGN.md)

> **Status:** the mark is the maintainer's own (2026-10-13). The visual direction "A, light and sober" (built on this palette, with a matching dark theme) and the Manrope font were chosen by the maintainer on 2026-10-16; the mockups are kept in `design/mockups/`. The tokens below are implemented in `apps/android`; changing a value is a one-line edit, and the contrast test keeps the palette accessible. **Direction A now covers every screen** (unlock, vault list, group, entry, entry editor and settings); the `fastlane/` screenshots still have to be taken again on a phone.

## Principles

1. **Trust before beauty.** A password manager is judged on whether it feels safe. Plain, calm, predictable screens; no decoration that competes with the data.
2. **Say what happened, never leave the user guessing.** Every failure explains the cause in plain language and what to do next. Silence about a save, a copy or an error is a bug.
3. **Secrets stay hidden by default** and are revealed only by a deliberate action, for a short time.
4. **One-handed on a phone.** Primary actions sit in the lower half; touch targets are at least 48 dp.
5. **Accessible by construction.** WCAG AA contrast, scalable text, a visible focus, a description for every icon, nothing conveyed by colour alone.

## Identity

- **Mark:** a hexagon split navy and sky blue, with a separate small hexagon "node" at its upper right, an inner hexagonal ring, and a shield with a keyhole at the centre. Source: `design/icon/KeyNexa.jpg`, supplied by the maintainer (made with ChatGPT). It is traced to vector paths in `apps/android/src/main/res/drawable/ic_launcher_foreground.xml` (two colours, `#03265D` and `#06A6F3`, on white) and `ic_launcher_monochrome.xml` (themed icons, Android 13+), and scaled so the hexagon's corners touch the 66 dp safe zone of adaptive icons.
- **Name:** "KeyNexa", set in the interface font in a medium weight, never stylised.
- **Colour idea:** the icon's navy as the brand colour and its sky blue as the secondary, darkened where needed so text stays readable; neutral blue-greys for surfaces. Red is reserved for errors and destructive actions.
- **Fixed palette, no dynamic colour:** the app does not take its colours from the wallpaper, so it looks the same, and keeps its checked contrast, on every device.

## Colour

Tokens live in `apps/android/.../ui/theme/Palette.kt` and map one to one onto Material 3 roles. Contrast ratios are the lowest for each role pair, checked by `PaletteContrastTest` (text at least 4.5:1, interface edges at least 3:1).

| Role | Light | Dark | Notes |
| --- | --- | --- | --- |
| `primary` / `onPrimary` | `#0A3D91` / `#FFFFFF` (10.1:1) | `#AAC7FF` / `#002F68` (7.7:1) | Main actions, links, focus ring |
| `primaryContainer` / `onPrimaryContainer` | `#D6E3FF` / `#001A43` (13.3:1) | `#0A3D91` / `#D6E3FF` (7.8:1) | Selected items, tonal buttons |
| `secondary` / `onSecondary` | `#00658F` / `#FFFFFF` (6.5:1) | `#84CFFF` / `#00344C` (7.7:1) | Secondary actions, links in text |
| `secondaryContainer` / `onSecondaryContainer` | `#C7E7FF` / `#001E2E` (13.3:1) | `#004C6C` / `#C7E7FF` (7.2:1) | Confirmations, chips |
| `error` / `onError` | `#B3261E` / `#FFFFFF` (6.5:1) | `#F2B8B5` / `#601410` (7.7:1) | Errors, destructive actions |
| `errorContainer` / `onErrorContainer` | `#FBDAD7` / `#410002` (13.2:1) | `#8C1D18` / `#FBDAD7` (7.0:1) | Error banners |
| `background` / `onBackground` | `#F7F8FC` / `#171B26` (16.2:1) | `#0F121A` / `#E6E9F2` (15.4:1) | Screen background |
| `surface` / `onSurface` | `#FFFFFF` / `#171B26` (17.2:1) | `#161A24` / `#E6E9F2` (14.3:1) | Cards, sheets, fields |
| `surfaceVariant` / `onSurfaceVariant` | `#E6E9F2` / `#434A5C` (7.3:1) | `#2A3040` / `#BCC3D6` (7.5:1) | Secondary text, field backgrounds |
| `outline` | `#6B7285` (4.5:1 on background) | `#8A91A6` (5.5:1 on surface) | Field borders, dividers that carry meaning |

## Typography

The interface uses the Material 3 type scale with **Manrope**, bundled as one variable font (`ui/shared/src/commonRes/font/manrope.ttf`, SIL OFL 1.1, see [CREDITS](../CREDITS.md)); passwords and codes keep the platform monospace font.

| Use | Style | Size / weight |
| --- | --- | --- |
| Screen title | `headlineSmall` | 24 sp / extra bold |
| Section title | `titleMedium` | 16 sp / medium |
| Body, field values | `bodyLarge` | 16 sp / regular |
| Secondary text | `bodyMedium` | 14 sp / regular |
| Buttons, labels | `labelLarge` | 14 sp / medium |
| Passwords and codes | `bodyLarge` with a monospace family | 16 sp, characters grouped when revealed |

Text must scale with the system font size up to 200 % without clipping.

## Spacing, shape and elevation

- **Grid:** 4 dp. Common steps: 4, 8, 12, 16, 24, 32, 48. Screen side padding 16 dp (24 dp on tablets).
- **Touch targets:** at least 48 x 48 dp, with at least 8 dp between them.
- **Shape:** cards 16–18 dp with a 1 dp border, fields 14 dp, main buttons 54–56 dp with 14 dp corners, the header band 24 dp on its bottom corners, chips fully rounded, dialogs 28 dp.
- **Elevation:** flat. Tone and borders separate layers; shadows only for menus and dialogs.

## Components

| Component | Behaviour |
| --- | --- |
| **Vault card** | Name, location (folder or provider) and last opened. Tap to open; long press for options (remove from list, never delete the file). |
| **Hex badge** | A small point-up hexagon with the initials of a vault, a group or an entry. Decorative: the row's own text already names it. |
| **Brand mark** | The KeyNexa hexagon (two blues, inner ring, shield with keyhole and a small node) with the name below it on the unlock screen. Decorative but labelled for screen readers. |
| **Header band** | The top band of the vault screens: `primary` in light and `primaryContainer` in dark (screens read `LocalDarkTheme`), carrying the title, the status and the main actions. |
| **Group grid** | The subgroups of a group as a two-column grid of cards: a hexagon badge with the group's initial (its colours chosen from the name), the name in bold and *N entries*. |
| **Entries card** | The entries of a group in a single card with separators: a hexagon badge with up to two initials, the title in bold, the user name, a word when the entry has expired and a **2FA** tag when it holds a one-time code. |
| **Band search** | A field inside the header band that is not a text field: tapping it opens the search screen. It shows *Search in <name>* and reads as a button. |
| **Unlock form** | Password field with show/hide, key file picker, an *Unlock* button disabled while working, and a progress indicator for long key derivation. After a wrong password the field keeps focus and the message appears below it. |
| **Password keypad** | Drawn by KeyNexa under the master password field ([ADR-0010](decisions/0010-in-app-password-keypad.md)): a fixed QWERTY page with ñ, a digits and symbols page and an accents page, shift with caps lock, delete and *Done*. Keys at least 48 dp high, special keys a little wider; no enlarged preview of the pressed key, only a short vibration. The field above it is read-only and shows dots; a text button below it switches to the system keyboard. On by default; can be turned off in settings. While a screen reader is on, the system keyboard is used instead, because it does not read passwords aloud. |
| **Entry row** | Title, user name, and an icon; a favicon or the entry icon at the start. Never shows the password. |
| **Field row** | A small uppercase label above the value, and 44–48 dp icon buttons at the end: *copy*, and *reveal* on a secret (each description names the field, never the value). A secret shows as dots; revealing hides again automatically or when the screen turns off. |
| **Field card** | The fields of one entry (user name, password, one-time code, URL and any custom field) in a single card, radius 18 dp, with a thin separator between rows. Notes, attachments and the entry's dates live in cards of their own. |
| **Action bar** | The fixed bar at the bottom of the entry: a full-width *Edit* button and a square, outlined *Delete* button in the error colour with its own description. |
| **Section card** | A card (surface, 1 dp border, 16 dp radius) with a title in capitals over rows of the same height separated by a thin line: the sections of the settings screen and the groups of fields of the entry editor. |
| **Vault row** | A vault in the home list: a hexagon avatar with its initial, the name in bold, where it lives, and the action to take it off the list. |
| **Password generator** | Length slider, character classes, a live strength bar with a text label, *Use* and *Regenerate*. |
| **Search bar** | Searches title, user name, URL and tags in the open vault only; never sends anything anywhere. |
| **Primary action** | A floating *New entry* button, bottom end. Destructive actions use the error colour and a confirmation. |
| **Dialog** | Title, a plain sentence of consequences, two buttons, the safe one on the right. |
| **Snackbar** | Short confirmations (*Copied. The clipboard clears in 30 s*) with an *Undo* when the action can be undone. |

## States

Every screen that loads or changes data defines all of these:

- **Loading:** a progress indicator and, when it takes more than a second, a line saying what is happening (*Opening the vault…*). Key derivation on a phone can take seconds.
- **Empty:** an illustration-free message that says what is missing and offers the next step (*No entries yet. Add the first one.*).
- **Error:** the cause, the effect on the user's data, and a button for the next step. Errors never blame the user and never include secrets.
- **Locked:** the vault is closed after the auto-lock time or when the app goes to the background; only the unlock form is shown, with no data visible in the recent-apps preview.
- **Conflict or damaged file:** a banner at the top of the vault explaining what KeyNexa found and what it did (see the messages below).

## Security messages

These are the exact situations the interface must name. Final texts live in `strings.xml` (English and Spanish).

| Situation | Message idea |
| --- | --- |
| Wrong password | "That password doesn't open this vault. Check it and try again." |
| Damaged or incomplete file | "This file looks damaged or is still being copied. KeyNexa has not changed it. Trying again in a moment." |
| Saved | "Saved." (with the time, in the vault's status line) |
| Save failed | "Could not save. Your changes are still here and the file was not modified. [Retry] [Save a copy]" |
| Changed by another device | "This vault changed on another device. KeyNexa merged the changes and kept a backup." |
| Conflict copy found | "A conflict copy of this vault was found. [Review] [Ignore]" |
| Copy to clipboard | "Copied. The clipboard clears in 30 seconds." |
| Weak password | A strength bar and a word, never a blocking warning. |
| Auto-locked | "Locked after 5 minutes without use." |

## Themes

Light and dark are chosen in Settings (Automatic follows the system; Light or Dark force one of them). Both palettes are defined together and tested together. Surfaces in dark theme are blue-black, not pure black, to keep edges visible.

## Accessibility checklist

- Contrast guaranteed by `PaletteContrastTest` for every role pair above.
- Every icon button has a content description; decorative images have none.
- Focus order follows reading order; every control is reachable and operable with TalkBack and a keyboard.
- Errors are announced to screen readers and are not shown by colour alone (an icon and text come with the red).
- Animations are short (200-300 ms) and respect the system *remove animations* setting.

## Open decisions for the maintainer

1. Palette and font: decided on 2026-10-16 (direction A, Manrope).
