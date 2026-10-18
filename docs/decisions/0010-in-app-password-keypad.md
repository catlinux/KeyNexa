# ADR-0010: In-app keypad for the master password

[English](0010-in-app-password-keypad.md) | [Español](../es/decisions/0010-in-app-password-keypad.md)

- **Status:** Accepted (2026-10-18)
- **Date:** 2026-10-18
- **Addresses:** threat T22 in [THREAT_MODEL](../THREAT_MODEL.md) (keylogger or malware captures the master password)

## Context

Today the master password is typed with the system keyboard. The keyboard is another app: it sees every key, may learn words, may sync with a cloud account, and a malicious or compromised keyboard is a keylogger by design. KeyNexa already marks the field as a password (no suggestions, no learning on well-behaved keyboards), blocks screenshots by default, ignores touches under overlays and says quietly when a keyboard or accessibility service does not come with the system (0.31.0). None of that helps when the keyboard itself records what is typed.

A keypad drawn by KeyNexa inside its own window removes the keyboard from the path: the characters never leave the app. It does not stop everything: an accessibility service can still read what the screen exposes, and a compromised system can read anything (out of scope, see T22).

Constraints: the master password may contain any character (accents, symbols, other alphabets); the app must stay usable with TalkBack and large text; the password must not be kept as an immutable `String` longer than needed; nothing may be logged.

## Options

1. **Keep only the system keyboard.** No work, but T22 stays fully open for keyboards.
2. **An in-app keypad, optional, next to the system keyboard.** The user chooses per device; the system keyboard stays for characters the keypad lacks and for anyone who prefers it.
3. **An in-app keypad only.** Strongest, but a password with a character the keypad lacks could never be typed again, and some users depend on their own keyboard (accessibility, other alphabets).
4. **A full input method (IME) shipped by KeyNexa.** Would serve other apps too, but needs a system-wide service, more permissions and a much larger attack surface: the opposite of what is wanted.

## Decision

Option 2.

1. **What it is:** a keypad drawn with Compose inside the unlock screen. It is not an input method: it is not registered with the system, needs no permission, and no other app can use it or see it.
2. **When it is used:** only in fields where a master password is typed: the unlock screen, the create-vault screen (password and confirmation) and the "other password" dialog of conflict copies; never for titles, notes or search. A setting "Type passwords with the KeyNexa keypad" is **on by default** (decision of the owner: it is the safer choice), and each password field has a button to switch to the system keyboard for that attempt, so a character the keypad lacks can always be typed. The password fields of entries may follow later, as their own step.
3. **Layout:** a QWERTY letter page with shift and caps lock; a digits and symbols page covering every printable ASCII character; an accents page with the common Latin letters (á é í ó ú à è ì ò ù ä ë ï ö ü â ê î ô û ñ ç, also uppercase). Characters outside these pages are typed with the system keyboard (option to switch stays visible). The layout is fixed, not shuffled: shuffling hurts usability and only helps against screen recording, which `FLAG_SECURE` already blocks by default.
4. **No visual echo of the key:** pressed keys get no enlarged preview bubble (it would show each character to anyone looking and to screen recording when screenshots are allowed); feedback is a short vibration and the key's pressed state.
5. **Memory:** the typed characters live in a `CharArray` buffer owned by the unlock form (`PasswordBuffer`: append, delete last, wipe, copy out). It is wiped when the password is submitted, when the screen is left, when the app locks or goes to the background, and when it grows (the old array is wiped after copying). The field shows dots only; "show password" renders the characters only while it is on, and that transient text is accepted as with the current field.
6. **Accessibility:** every key is a real button with a spoken label ("a", "capital A", "at sign"), at least 44 dp high and with spacing, and the keypad follows the font scale. A screen reader therefore announces each key: that is the price of accessibility and the reason the quiet notice exists. Keys are never hidden from accessibility services.
7. **Nothing else changes:** key files, biometric unlock, the safe-save pipeline and the vault format are untouched. Code lives in `app-android` only (`ui/unlock/` and a JVM-testable `PasswordBuffer` and layout model); no new dependency.

## Consequences

- A malicious or cloud-synced keyboard no longer sees the master password when the keypad is used. Accessibility services and a compromised system still can (documented residual risk in T22).
- The password stops being held as a `String` while typed with the keypad, which also improves on the current field.
- Typing is slower than with a familiar keyboard; the switch to the system keyboard is one tap away, and the setting can be turned off.
- Passwords with characters outside the pages need the system keyboard; the app never blocks that.
- More UI code to maintain and to test for accessibility (TalkBack, large text, small screens).

## Implementation plan

1. `PasswordBuffer` and the keypad layout model with JVM tests (wipe on every path, growth wipes the old array, every printable ASCII character reachable).
2. The keypad composable and its use on the unlock screen: setting (on by default), switch button, wiping on leave, lock and background. Strings in English and Spanish.
3. Device check with TalkBack and large text; then the create-vault screen and the conflict-copy password dialog.
4. Docs in both languages: DESIGN (keypad component), THREAT_MODEL (T7 and T22), README feature list, TODO and CHANGELOG.

## Addendum (2026-10-18, before any release)

Point 6 left a gap: with a screen reader on, the keypad announces every key aloud, while the system keyboard follows the Android setting that keeps passwords from being spoken (off by default, except with headphones). So while a screen reader is on, the setting does not apply and master passwords are typed with the system keyboard; the user can still switch to the keypad on purpose. The owner does not need the keypad with a screen reader, and this keeps it from ever being less private than the system keyboard.
