# Play-Eintrag — Vorlagen und Formularantworten

Alles, was die Play Console beim ersten Upload abfragt, hier vorformuliert.
Kopiervorlagen stehen in Codeblöcken, damit Zeichenzahlen nicht durch
Formatierung verfälscht werden.

Namen und Formulierungen aus der Ratgeberliteratur, an der sich die Mechanik
orientiert, bleiben draußen — Methoden sind frei, Buchtitel und ihr Wortlaut
nicht.

---

## 1. Eintragstexte

**Die Store-Sprache ist Englisch.** Die App ist durchgehend englisch — es gibt
nur `values`, keinen `values-de`-Ordner. Ein deutscher Eintrag würde jemandem
„Gewohnheiten aufbauen“ versprechen und eine englische App liefern. In der Play
Console ist **English (United States)** als Standardsprache zu wählen.

Deutsch kann später als zusätzliche Übersetzung dazukommen — sinnvollerweise
erst, wenn auch die App übersetzt ist. Die deutschen Fassungen stehen unten
aufgehoben.

### App-Name (max. 30 Zeichen)

```
H4b1ts: Habits & Focus
```

22 Zeichen. Die Marke vorn, dahinter die beiden Suchbegriffe. „Habits“ allein
wäre im Store unauffindbar, „H4b1ts“ allein sucht niemand.

### Kurzbeschreibung (max. 80 Zeichen)

```
Build habits, lock the apps that get in the way. No account, no internet.
```

73 Zeichen.

### Vollbeschreibung (max. 4000 Zeichen)

```
H4b1ts is a habit tracker that can also lock apps. The two belong together:
building a good habit and dropping a bad one are the same job seen from two
sides.

IDENTITY, NOT A SCORE

Every habit hangs on a sentence: "I am someone who ..." Each time you tick it
off, that is one vote for the sentence. The streak is not the goal; the person
keeping it is.

NEVER MISS TWICE

One missed day does not break your streak. Two in a row does. A bad day is an
accident, two are the start of a new habit - and that is what the app warns you
about, instead of punishing the first slip.

THE TWO-MINUTE VERSION

Every habit stores a smaller version for bad days. Not "run for an hour" but
"put your running shoes on". On the days when nothing works, that still does.

THE SHIELD

Pick the apps you open too often. H4b1ts puts a lock screen over them that
shows what reaching for them costs right now. Not a wall, but friction: a
countdown, a typing task, and a one-minute exception when you genuinely need
one.

You can also tie an app to a habit - the run first, then the feed.

FOCUS SESSIONS

Start a session and for its length only what you allowed beforehand is there.
The session shows your habits and tasks for the day rather than just shutting
you out.

ALSO INSIDE

- Tasks with repeats and reminders
- Notes with pictures
- Looking back: a heatmap of your habits, daily counts over roughly a year
- Light and dark themes, two accent colours, a pixel-art interface

YOUR DATA STAYS WITH YOU

H4b1ts has no internet permission. That means the app cannot open a network
connection at all. No account, no sign-in, no analytics libraries, no ads,
nothing transmitted - not because we promise it, but because the program
cannot.

Everything lives in your device's private app storage and disappears without a
trace when you uninstall.

PERMISSIONS

To lock apps, H4b1ts needs usage access so it can tell which app is in the
foreground. Only the name of the visible app is read, and only in memory. No
usage history and no usage time is stored.
```

Rund 2.100 Zeichen, reichlich Luft unter dem Limit.

### Deutsch — aufgehoben für eine spätere Übersetzung

Nicht eintragen, solange die App englisch ist.

- Name: `H4b1ts: Gewohnheiten & Fokus` (28 Zeichen)
- Kurz: `Gewohnheiten aufbauen, ablenkende Apps sperren. Ohne Konto, ohne Datenabfluss.` (78)
- Die deutsche Vollbeschreibung steht in der Git-Historie dieses Dokuments.

---

## 2. Data Safety (Datensicherheit)

Die Antworten sind hier ungewöhnlich einfach, weil die App keine
`INTERNET`-Berechtigung deklariert und damit keine Verbindung aufbauen kann.

| Frage | Antwort |
|---|---|
| Werden Nutzerdaten erhoben oder geteilt? | **Nein** |
| Werden Daten an Dritte weitergegeben? | **Nein** |
| Verschlüsselung bei der Übertragung | entfällt — es wird nichts übertragen |
| Können Nutzer Löschung beantragen? | Nein nötig; Löschung erfolgt lokal über App-Daten löschen oder Deinstallation |
| Datenschutzerklärung | `https://oggary.github.io/h4b1ts/datenschutz.html` |

**Wichtig für das Formular:** „Erhebung" im Sinne von Play bedeutet
*Übertragung vom Gerät weg*. Daten, die die App ausschließlich lokal speichert
und nie sendet, gelten ausdrücklich **nicht** als erhoben. Gewohnheiten,
Notizen und Bilder sind deshalb korrekt mit „Nein" beantwortet.

---

## 3. Begründungen für Berechtigungen

Vorformulierte Texte für die Stellen, an denen die Console eine Erklärung
verlangt. Auf Deutsch und Englisch, weil das Review-Formular je nach
Kontoeinstellung Englisch erwartet.

### Vordergrunddienst `FOREGROUND_SERVICE_SPECIAL_USE`

> Die App blockiert vom Nutzer selbst ausgewählte Apps. Dafür muss sie
> erkennen, welche App im Vordergrund ist, solange der Bildschirm an ist.
> Keiner der vordefinierten Vordergrunddienst-Typen beschreibt diesen
> Anwendungsfall: Es werden keine Daten synchronisiert, keine Medien
> abgespielt, keine Standorte verfolgt. Der Dienst pausiert, sobald der
> Bildschirm ausgeht, und entfällt bei leerer Sperrliste.

> The app blocks apps the user has chosen to block. To do so it must know
> which app is in the foreground while the screen is on. None of the
> predefined foreground service types describes this use case: no data is
> synced, no media played, no location tracked. The service pauses as soon as
> the screen turns off and is not started when the block list is empty.

### Zugriff auf Nutzungsdaten `PACKAGE_USAGE_STATS`

> Die Berechtigung ist der einzige Weg, den Vordergrundwechsel ohne
> Bedienungshilfe-Dienst zu erkennen. Ausgewertet wird ausschließlich der
> Paketname der aktuell sichtbaren App, und zwar nur im Arbeitsspeicher. Er
> wird nicht gespeichert, nicht protokolliert und verlässt das Gerät nicht;
> die App hat keine Internet-Berechtigung. Nutzungsverlauf und Nutzungsdauer
> werden nicht ausgelesen.

### Nicht nötig

- **Bedienungshilfe** — der `play`-Build enthält keinen
  `AccessibilityService`. Im gemergten Release-Manifest steht weder der Dienst
  noch `BIND_ACCESSIBILITY_SERVICE`.
- **`QUERY_ALL_PACKAGES`** — bewusst vermieden, abgefragt werden nur Apps mit
  Launcher-Eintrag.
- **Exakte Alarme** — `ReminderScheduler` nutzt `setWindow`, nicht `setExact`.
  Weder `SCHEDULE_EXACT_ALARM` noch `USE_EXACT_ALARM` sind deklariert.

---

## 4. Grafiken

| Element | Format | Stand |
|---|---|---|
| App-Symbol | 512 × 512 PNG, 32 Bit | **vorhanden** — `branding/playstore-icon-512.png` |
| Feature-Grafik | 1024 × 500 PNG/JPG | **vorhanden** — `branding/playstore-feature-1024x500.png` |
| Screenshots Telefon | mind. 2, max. 8; kurze Seite ≥ 320 px | **6 vorhanden** — `branding/screenshots/`, 1080 × 2340 |
| Screenshots Tablet | optional | — |

Aufgenommen auf einem Galaxy S25 (Android 16) aus dem signierten
Release-Build, mit erteilten Berechtigungen:

| Datei | Zeigt |
|---|---|
| `1-today.png` | Tagesansicht mit Serie und Fortschritt |
| `2-habits.png` | Gewohnheiten mit Identitätssatz und Wochentagen |
| `3-plan.png` | Aufgaben |
| `4-shield.png` | Schild mit Selbsttest-Ergebnis und Berechtigungen |
| `5-block.png` | **der Sperrbildschirm** — das Unterscheidungsmerkmal |
| `6-focus.png` | Fokus-Sitzung, Dauer und strenger Modus |

**Vor dem Upload neu aufnehmen.** In der Statusleiste stehen
Benachrichtigungssymbole — zulässig, aber schlampig. Mit eingeschaltetem
„Nicht stören" wird der Satz sauber.

---

## 5. Sonstiges beim ersten Upload

- **Inhaltseinstufung** — Fragebogen ausfüllen. Die App enthält keine
  nutzergenerierten öffentlichen Inhalte, keine Käufe, keine Werbung.
- **Zielgruppe** — nicht an Kinder gerichtet.
- **Kategorie** — „Gesundheit & Fitness" oder „Produktivität".
- **Geschlossener Test** — bei neuen Privatkonten verlangt Google zwölf
  Tester über vierzehn zusammenhängende Tage vor der Produktion. Das ist der
  Boden unter jedem Termin und lässt sich durch Arbeit nicht verkürzen.
- **`versionCode`** vor jedem weiteren Upload erhöhen.
