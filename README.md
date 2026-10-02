# HaptiKit

Give people and apps their own vibration so you know who's messaging without looking. Kotlin, Jetpack Compose, Room.

## Build
Open the folder in Android Studio and run, or from a terminal:
```
gradlew assembleDebug
```

## Using it
1. **Patterns** (home screen): three built-ins to start. Tap ▶ to feel one. Each card shows its rhythm as a waveform; ⋮ lets you edit, duplicate or delete it.
2. **New pattern**: name it, then record on the big pad. A tap makes a short pulse; *hold* for a longer buzz (the phone vibrates live while you hold). The gaps between presses become pauses. The *Strength* slider applies to the next beats. Undo the last beat, clear, or preview before saving.
3. **Assignments** (the card at the top): turn on notification access when asked, then pick a contact or an app and choose its pattern. Search, the *Assigned* filter and the ▶ preview in the picker make this quick.
4. **Backup**: ⋮ → *Export backup* / *Import backup* saves or restores all patterns and assignments as a JSON file.

Switches on the Assignments screen: pause HaptiKit entirely, and stay quiet while the phone is on silent.

## How it works (and its limits)
Android doesn't let one app silence another app's vibration, so HaptiKit listens for notifications and plays your pattern right after the normal buzz. That second, distinctive rhythm is what you learn to recognise. To keep it pleasant:
- Ongoing notifications (music, downloads, navigation), group summaries and silent updates never trigger it.
- The same app won't trigger it twice within 2 seconds (message bursts).

**Contacts** are matched from the phone number, email or contact link that messaging apps attach to the notification, falling back to the sender's name. Most messengers (SMS, WhatsApp, Telegram, Signal…) provide one of these; apps that hide the sender won't match a contact, but per-app assignments still work.
