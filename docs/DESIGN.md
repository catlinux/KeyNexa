# Design system

[English](DESIGN.md) | [Español](es/DESIGN.md)

> **Status:** the mark is the maintainer's own (2026-10-13). The rest is a proposal pending approval (palette details, font, manual theme switch). The tokens below are implemented in `app-android`; changing a value is a one-line edit, and the contrast test keeps the palette accessible.

## Principles

1. **Trust before beauty.** A password manager is judged on whether it feels safe. Plain, calm, predictable screens; no decoration that competes with the data.
2. **Say what happened, never leave the user guessing.** Every failure explains the cause in plain language and what to do next. Silence about a save, a copy or an error is a bug.
3. **Secrets stay hidden by default** and are revealed only by a deliberate action, for a short time.
4. **One-handed on a phone.** Primary actions sit in the lower half; touch targets are at least 48 dp.
5. **Accessible by construction.** WCAG AA contrast, scalable text, a visible focus, a description for every icon, nothing conveyed by colour alone.

## Identity

- **Mark:** a hexagon split navy and sky blue, with a separate small hexagon "node" at its upper right, an inner hexagonal ring, and a shield with a keyhole at the centre. Source: `design/icon/KeyNexa.jpg`, supplied by the maintainer (made with ChatGPT). It is traced to vector paths in `app-android/src/main/res/drawable/ic_launcher_foreground.xml` (two colours, `#03265D` and `#06A6F3`, on white) and `ic_launcher_monochrome.xml` (themed icons, Android 13+), and scaled so the hexagon's corners touch the 66 dp safe zone of adaptive icons.
- **Name:** "KeyNexa", set in the interface font in a medium weight, never stylised.
- **Colour idea:** the icon's navy as the brand colour and its sky blue as the secondary, darkened where needed so text stays readable; neutral blue-greys for surfaces. Red is reserved for errors and destructive actions.
- **Fixed palette, no dynamic colour:** the app does not take its colours from the wallpaper, so it looks the same, and keeps its checked contrast, on every device.

## Colour

Tokens live in `app-android/.../ui/theme/Palette.kt` and map one to one onto Material 3 roles. Contrast ratios are the lowest for each role pair, checked by `PaletteContrastTest` (text at least 4.5:1, interface edges at least 3:1).

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

The interface uses the Material 3 type scale with the platform sans-serif font. A bundled font is optional and not decided: Inter or Manrope (both SIL OFL 1.1, compatible with GPL-3.0) are the candidates, to be added with their licence in `CREDITS.md` only if the maintainer wants them.

| Use | Style | Size / weight |
| --- | --- | --- |
| Screen title | `headlineMedium` | 28 sp / regular |
| Section title | `titleMedium` | 16 sp / medium |
| Body, field values | `bodyLarge` | 16 sp / regular |
| Secondary text | `bodyMedium` | 14 sp / regular |
| Buttons, labels | `labelLarge` | 14 sp / medium |
| Passwords and codes | `bodyLarge` with a monospace family | 16 sp, characters grouped when revealed |

Text must scale with the system font size up to 200 % without clipping.

## Spacing, shape and elevation

- **Grid:** 4 dp. Common steps: 4, 8, 12, 16, 24, 32, 48. Screen side padding 16 dp (24 dp on tablets).
- **Touch targets:** at least 48 x 48 dp, with at least 8 dp between them.
- **Shape:** Material 3 defaults: 12 dp for cards and fields, fully rounded for buttons and chips, 28 dp for dialogs.
- **Elevation:** flat. Tone and borders separate layers; shadows only for menus and dialogs.

## Components

| Component | Behaviour |
| --- | --- |
| **Vault card** | Name, location (folder or provider) and last opened. Tap to open; long press for options (remove from list, never delete the file). |
| **Unlock form** | Password field with show/hide, key file picker, an *Unlock* button disabled while working, and a progress indicator for long key derivation. After a wrong password the field keeps focus and the message appears below it. |
| **Entry row** | Title, user name, and an icon; a favicon or the entry icon at the start. Never shows the password. |
| **Field row** | Label above value. A secret shows as dots with *Reveal* and *Copy* actions; revealing hides again after 10 seconds or when the screen turns off. |
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

Light and dark follow the system setting; there is no separate in-app switch in the first versions. Both palettes are defined together and tested together. Surfaces in dark theme are blue-black, not pure black, to keep edges visible.

## Accessibility checklist

- Contrast guaranteed by `PaletteContrastTest` for every role pair above.
- Every icon button has a content description; decorative images have none.
- Focus order follows reading order; every control is reachable and operable with TalkBack and a keyboard.
- Errors are announced to screen readers and are not shown by colour alone (an icon and text come with the red).
- Animations are short (200-300 ms) and respect the system *remove animations* setting.

## Open decisions for the maintainer

1. Approve the palette derived from the icon.
2. Bundle Inter or Manrope, or keep the system font.
3. Whether to offer a manual light/dark switch.
