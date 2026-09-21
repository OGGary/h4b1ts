# H4b1ts — Entwicklung

Das Arbeitsprotokoll zum Projekt: warum die Dinge so gebaut sind, wie sie
gebaut sind, und was unterwegs kaputtging. Für Leute, die am Code arbeiten.

Wer die App benutzen will, ist im [README](README.md) besser aufgehoben.

---

## Marke

Quelldateien liegen in `branding/`:

| Datei | Zweck |
|---|---|
| `h4b1ts-icon.svg` | Kurzmarke `H4`, 1024 × 1024, schwarzer Grund — Basis des Launcher-Icons |
| `h4b1ts-wordmark.svg` | Volle Wortmarke `H4b1TS`, transparent — Splashscreen, Kopfzeilen, Marketing |
| `playstore-icon-512.png` | 512 × 512, 32-Bit RGBA — Pflichtformat für den Play-Store-Eintrag |
| `playstore-feature-1024x500.png` | Feature-Grafik des Store-Eintrags, aus Icon und Pixelbasel gesetzt |

Im Projekt: `drawable/ic_launcher_foreground.xml` (Kurzmarke, Adaptive-Icon-Vordergrund
inklusive Monochrom-Ebene für themed icons ab Android 13) und `drawable/ic_wordmark.xml`
(volle Wortmarke, tint-fähig).

### Schrift

**Pixelbasel** (SIL OFL, © GGBotNet) in `res/font/pixelbasel.ttf`, gesetzt über
`H4Typography` in `ui/theme/Type.kt`. Zwei Eigenheiten treiben das Setup:

Die **gleiche Datei ist für alle vier Schnitte registriert**. Eine Pixelschrift hat
keinen fetten Schnitt; fordert man einen an, verschmiert der Renderer die Glyphen
um Bruchteile eines Pixels — genau das, was eine Pixelschrift nicht darf. So löst
`FontWeight.Bold` auf einen echten Schnitt auf. Hierarchie trägt deshalb nicht mehr
das Gewicht, sondern Größe, Farbe und Laufweite.

**Alle Größen sind um 2sp gewachsen.** Die Versalhöhe liegt bei 0,5625 em gegen
Robotos 0,71 — gleiche Punktgröße wirkt also rund ein Fünftel kleiner. Die
Zeilenhöhen sind großzügiger als bei Material: Blockige Glyphen mit flachen Ober-
und Unterkanten bringen keine optische Luft mit, die muss der Durchschuss liefern.

### Icons

38 Stück 1-Bit-Pixelart, 16 × 16, aus `Documents/Used Icons` importiert nach
**`res/drawable-nodpi/`** — nicht nach `drawable/`. Ein 16-px-Bild in einem
Dichte-Ordner gilt als mdpi und wird schon beim Dekodieren bilinear hochskaliert;
so wird aus Pixelart Matsch. `nodpi` unterbindet das Resampling.

Zugriff über das `H4Icon`-Enum in `ui/components/PixelIcon.kt`, damit kein Screen
direkt auf `R.drawable.*` zeigt.

Die Wortmarke ist bewusst **nicht** das Launcher-Icon: sechs Zeichen sind bei 24 bis 48
Pixeln nicht mehr lesbar. Launcher bekommt `H4`, alles Größere die volle Marke.

## Farbsystem

`ui/theme/Palette.kt` und `ui/theme/Theme.kt`. Die App ist **nur dunkel** — keine
helle Neonfarbe erreicht auf Weiß 4,5:1, das ist keine Designentscheidung, sondern
Physik.

Zwei Regeln tragen das Ganze:

1. **Farbe ist die Belohnung, Monochrom ist die Sperre.** Der Akzent markiert nur
   Verdientes. Der Sperrbildschirm bekommt gar keinen — ein gesperrter Zustand darf
   nichts anzubieten haben.
2. **`Void` (`#000000`) ist semantisch, nicht dekorativ.** Reines Schwarz heißt
   „gesperrt" und wird nirgends sonst benutzt; auf OLED sind diese Pixel dann
   tatsächlich aus. Die dunkle Oberfläche der App ist `#0E0E14`, nicht Schwarz.

Zwei Akzentmodi, über `AccentMode` wählbar und in den Prefs gemerkt:

| Modus | Farbe | Kontrast auf `Ink` |
|---|---|---|
| Volt | `#C6FF00` | 16,2 : 1 |
| Pulse | `#FF2BD6` | 6,0 : 1 |

Sie unterscheiden sich **nur** im Akzent; alles Monochrome bleibt identisch. Das Paar
ist zugleich das robusteste gemessene Neonpaar: 33,5 Helligkeitsabstand, damit auch
bei Deuteranopie, Protanopie und in Graustufen unterscheidbar. Grün gegen Rot wurde
gestrichen — es verschmilzt bei Deuteranopie zu demselben Oliv.

Farbe trägt nie allein eine Aussage: Statuspunkte wiederholen, was der Text schon
sagt (WCAG 1.4.1).

Gotcha: Material3 färbt `secondaryContainer` und `tertiary` standardmäßig lila. Ohne
explizites Überschreiben schleppen die Tonal-Buttons einen Fremdton in den sonst
monochromen Screen.

## Milestone 1 — Sperr-Spike (dieser Stand)

Ziel: beweisen, dass der Sperrmechanismus auf echter Hardware trägt, bevor
irgendetwas anderes gebaut wird.

Enthalten:

- `block/H4b1tsAccessibilityService.kt` — hört `TYPE_WINDOW_STATE_CHANGED`, gleicht
  gegen die Blockliste ab, schickt nach Hause und legt den Sperrbildschirm darüber.
- `ui/BlockActivity.kt` — Vollbild-Sperre mit 5-Sekunden-Reibung und
  1-Minuten-Ausnahme.
- `ui/MainActivity.kt` — Setup-Status (Bedienungshilfe, Overlay) + App-Auswahl.
- `data/BlockRepository.kt` — Blockliste in einem `@Volatile`-Feld, damit der
  Service ohne I/O entscheiden kann. Ausnahmen laufen über `elapsedRealtime`, nicht
  über die Systemuhr.

Bewusst noch **nicht** enthalten: Room, Hilt, Zeitpläne, Habits, Umgehungsschutz.

## Bauen

Voraussetzung: JDK 21 und Android SDK (compileSdk 36).

> **Wichtig — Gradle JDK.** Android Studio 2026.1 bündelt JBR 25. Gradle 8.13
> unterstützt JDK 25 nicht und bricht den Sync mit „Unsupported Java version" ab.
> Fix: *Settings → Build, Execution, Deployment → Build Tools → Gradle →
> Gradle JDK* auf ein JDK **21** stellen (über „Download JDK…", Vendor Temurin).
> Die Einstellung liegt in `.idea/` und ist damit lokal, nicht im Repo.

```bash
gradlew assembleDebug
```

Das APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

## Testen auf dem Gerät

1. APK installieren, App öffnen.
2. „Jetzt aktivieren" → in den Systemeinstellungen H4b1ts unter Bedienungshilfen
   einschalten.
3. Zurück in die App, eine Test-App (z. B. den Browser) auf gesperrt stellen.
4. Test-App über den Launcher öffnen → der Sperrbildschirm muss binnen ~100 ms
   erscheinen.

Worauf zu achten ist:

- **Latenz** — wie lange ist die gesperrte App sichtbar?
- **Samsung / Xiaomi / Oppo** killen Hintergrunddienste aggressiv. Prüfen, ob die
  Sperre nach 30 Minuten Standby und nach einem Neustart noch greift.
- **Recents** — führt der Weg über den App-Umschalter statt über den Launcher
  ebenfalls zur Sperre?
- **Rückkehr** — die Sperre darf nicht in einer Endlosschleife feuern.
- Logcat filtern auf Tag `H4b1ts`.

## Milestone 2 — Robustheit

Milestone 1 hat auf dem S25 funktioniert. Diese Stufe sorgt dafür, dass das auch
auf Geräten gilt, die nie jemand von uns in der Hand hatte.

**Redundanz statt eines Pfades.** Jeder der beiden kritischen Mechanismen hat jetzt
einen zweiten Weg:

| | Primär | Fallback |
|---|---|---|
| Erkennung | `H4b1tsAccessibilityService` (Fensterwechsel, ~10 ms) | `UsageFallbackService` (Nutzungsstatistik, ~900 ms) |
| Anzeige | `OverlayBlocker` (`TYPE_APPLICATION_OVERLAY`) | `BlockActivity` |

**Messergebnis Galaxy S25 / One UI (13.08.2026):** Der Background-Start von
`BlockActivity` wird kommentarlos verworfen. `startActivity` kehrt normal zurück,
im Fensterstapel entsteht nie ein Fenster — per `dumpsys window` unabhängig
bestätigt. Die erste Fassung probierte die Activity zuerst und schaltete erst nach
1,5 Sekunden Prüfzeit um; so lange blieb die gesperrte App sichtbar.

Deshalb ist die Reihenfolge umgedreht: Liegt die Overlay-Berechtigung vor, wird
direkt das Overlay gezeigt — es zeichnet auf jedem ROM in wenigen Millisekunden.
Die Activity bleibt der Weg für Nutzer ohne diese Berechtigung und wird dort per
Polling geprüft, weil ein stiller Fehlschlag anders nicht erkennbar ist.

Folge fürs Onboarding: **`SYSTEM_ALERT_WINDOW` ist keine Option, sondern
Voraussetzung.** Der Setup-Screen sagt das jetzt auch so.

**Gemeinsame Schleuse.** Beide Detektoren laufen im selben Prozess und sehen
denselben Fensterwechsel — im ersten Test 9 ms auseinander, was den Sperrbildschirm
zweimal aufbaute und den Countdown neu starten ließ. `BlockGate` entprellt jetzt
detektorübergreifend; zusätzlich hält sich `UsageFallbackService` heraus, solange
die Bedienungshilfe nachweislich Events liefert (`ServiceHealth`, 60-Sekunden-Fenster).
Ein Fehler, der erst sichtbar wird, wenn der zweite Pfad eingeschaltet ist.

**Selbstdiagnose statt Testmatrix.** `SelfTest` öffnet eine der gesperrten Apps und
misst über `ServiceHealth`, wie viele Millisekunden bis zur Sperre vergehen. Ab
150 ms sieht der Nutzer seinen Feed, deshalb wird darüber gewarnt. Der Nutzer sieht
selbst, ob es tut; du bekommst statt „geht nicht" eine Zahl.

**Herstellerhürden.** `OemSetup` liefert je nach `Build.MANUFACTURER` die passenden
Schritte samt Intent — Samsung schlafende Apps, Xiaomi Autostart und Hintergrund-
Popups, Oppo Startup-Liste, Huawei Startverwaltung. Jeder Intent wird vorher
aufgelöst und im `runCatching` gestartet, weil diese Activities zwischen
ROM-Versionen verschwinden.

Neu im Manifest: `PACKAGE_USAGE_STATS`, `FOREGROUND_SERVICE_SPECIAL_USE`,
`POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` plus `BootReceiver`, der den
Fallback nach einem Neustart wieder anwirft.

## Milestone 3 — Gewohnheiten und UI-Umbau

Die andere Hälfte der App. `habits/` enthält die Domäne, `ui/screens/` die drei
Bildschirme.

**Identität statt Ziel.** `Habit.identity` ist das Feld, das H4b1ts von einer
To-do-Liste trennt. Ein Ziel („5 km laufen") endet, wenn es erreicht ist; eine
Identität („Ich bin jemand, der sich täglich bewegt") wird durch jede Erledigung
gewählt und ist nie fertig. Die übrigen Felder bedienen je ein Gesetz: `cue`
(offensichtlich), `gateway` (einfach, Zwei-Minuten-Regel), `days` (Zeitplan).

**Nie zweimal aussetzen.** `Streaks` bricht die Serie **nicht** bei einem verpassten
Tag, sondern erst bei zwei geplanten Tagen in Folge. Der Tag nach einem Fehltag ist
der, an dem sich alles entscheidet — deshalb ist er nicht bloß eine Zahl, sondern
ein eigener Zustand (`HabitStatus.atRisk`) mit eigener Sektion oben im Today-Screen
und dem Angebot der Zwei-Minuten-Variante. Ein heute noch offener Tag zählt nicht
als Fehltag.

**Persistenz ohne Room.** `HabitStore` schreibt eine JSON-Datei über `org.json` und
hält den Zustand in Compose-Snapshot-Collections — kein ViewModel, kein Flow
dazwischen. Ein paar Dutzend Gewohnheiten mit je einem Datum pro Erledigung sind
kein Datenbankproblem; die Schnittstelle ist eng genug, dass ein späterer Wechsel
auf Room nichts darüber berührt.

**UI-Umbau.** Aus der einen Debug-Liste sind drei Tabs geworden:

| Tab | Inhalt |
|---|---|
| Today | nach Dringlichkeit sortiert: gefährdet, offen, erledigt |
| Habits | Liste, Editor, 30-Tage-Konsistenz |
| Shield | Selbsttest, Berechtigungen, Herstellerschritte, App-Liste |

Wiederverwendbare Bausteine in `ui/components/Common.kt`. Der Akzentumschalter
sitzt neben der Wortmarke im Header, weil beides Identität ist und nicht Navigation.

## Milestone 4 — Heartbeat, Kopplung, Tests

**Heartbeat.** `HealthWatchdog` prüft alle zwei Stunden per `setInexactRepeating`
sowie bei jedem App-Start, ob noch gesperrt wird, und meldet sich sonst per
Benachrichtigung mit direktem Sprung in die Einstellungen. Anlass war eine
Beobachtung am Gerät: Nach einer Neuinstallation war die Bedienungshilfe aus —
ohne Fehler, ohne Hinweis. Beim Play-Store-Update passiert dem Nutzer dasselbe.
Vier Zustände: `DISARMED` (nichts gesperrt, also keine Warnung), `SERVICE_OFF`,
`STALE`, `OK`. Kein WorkManager — ein ungenauer Wecker reicht dafür.

**Kopplung.** `BlockRepository.gates` ordnet einem Package eine Gewohnheit zu;
`isBlocked()` fragt `HabitStore.isDoneToday()`. Damit öffnet sich eine gesperrte
App von selbst, sobald die Gewohnheit erledigt ist — der eigentliche Grund, warum
Tracker und Blocker eine App sind. Die Auswahl sitzt direkt an der App im
Shield-Tab, nicht in einem Einstellungsmenü, und der Today-Screen nennt den Lohn
vor der Arbeit („unlocks 2 apps").

Zwei Details, die sonst zu Ärger führen: Eine Gewohnheit, die heute nicht geplant
ist, gilt als erledigt — sonst bliebe eine App das ganze Wochenende gesperrt, weil
der Nutzer seinen eigenen Plan befolgt. Und beim Löschen einer Gewohnheit werden
ihre Gates entfernt, sonst wäre die App für immer zu.

**Tests.** `StreaksTest`: Serie, vergebener Fehltag, zwei Fehltage, offener
heutiger Tag, Wochenpläne, Erstellungsdatum, `atRisk`, Konsistenz, archiviert.
Reine Datumslogik ist die Stelle, an der ein Fehler nicht abstürzt, sondern
monatelang leise falsche Zahlen zeigt — und die Serie ist das, woran Nutzer
emotional hängen.

Dazu kamen später `HardDeadlineTest` (Uhr verstellen, Neustart),
`RepeatingTaskTest`, `DayStatsTest`, `ReminderSchedulerTest` und
`JsonFileTest` (abgeschnittene Datei, Rückgriff auf die Sicherung).

```bash
gradlew testDebugUnitTest
```

## Milestone 5 — Hellmodus, Einstellungen, Produktivität

**Hellmodus.** `ThemeMode` (System / Hell / Dunkel), umgesetzt über zwei
`ColorScheme`s. Alle Bildschirme lesen jetzt `MaterialTheme.colorScheme` statt
fester Werte. Neon funktioniert auf Weiß nicht — Lime kommt dort auf 1,2 : 1 —,
deshalb hat jeder Akzent ein abgedunkeltes Geschwister (`VoltInk` 6,2 : 1,
`PulseInk` 7,6 : 1). **Der Sperrbildschirm bleibt in beiden Themes reines
Schwarz**, weil `Void` ein Zustand ist und keine Oberfläche.

**Einstellungen** liegen hinter dem Zahnrad oben rechts, nicht in der Tab-Leiste:
Aussehen stellt man einmal ein und es soll nicht mit den fünf Dingen konkurrieren,
für die die App da ist.

**Today neu gebaut**, angelehnt an Meditations-Apps: großer Tagestitel, eine
gefüllte organische Form statt eines Fortschrittsbalkens, und die Gewohnheiten als
verbundene Timeline statt als Kartenstapel. Übernommen ist die Struktur, nicht die
Stimmung — jene Apps verkaufen Ruhe, H4b1ts verkauft Zurückhaltung.

**Produktivität.** `productivity/` mit `JsonStore` als gemeinsamer Basis:

| Store | Inhalt |
|---|---|
| `TaskStore` | einmalige Aufgaben mit Fälligkeit — bewusst getrennt von `Habit` |
| `NoteStore` | Notizen |
| `AttachmentStore` | die Galerie, nach Besitzer verschlüsselt |

`Attachment` hängt an `HABIT`, `NOTE`, `TASK` oder `DAY` (ISO-Datum). Ein Store
statt drei Bildfeatures — deshalb ist `GalleryStrip` überall derselbe Baustein.
Bilder werden in den App-Speicher **kopiert**: eine URI-Freigabe des Fotopickers
überlebt keinen Neustart, eine darauf gebaute Galerie wird still zur Wand kaputter
Vorschaubilder.

Der Kalender speichert nichts eigenes, er liest: erledigte Gewohnheiten, fällige
Aufgaben, Notizen, Bilder. Nur die Bilder gehören dem Tag selbst.

**Sperren überleben die Deinstallation.** Deinstallieren ist der einfachste Weg
aus einer Sperre, also bleibt das Package auf der Liste und greift bei einer
Neuinstallation sofort wieder. `BlockRepository` merkt sich dafür den Anzeigenamen
**im Moment des Sperrens** — nach der Deinstallation kann der PackageManager ihn
nicht mehr auflösen, und der Nutzer stünde vor nackten Package-Namen. Der
Shield-Tab führt sie unter „Blocked, not installed" mit einem „Forget"-Ausgang.

**Geo-Tracking wurde gestrichen** (14.08.2026). Hintergrund-Standort wäre die
dritte heikle Play-Deklaration neben Bedienungshilfe und Nutzungszugriff gewesen
und hätte das Argument „Daten verlassen das Gerät nie" gekostet.

## Milestone 6 — Entflechtung

**`SettingsRepository` aus `BlockRepository` gelöst.** Akzent, Theme und der
Fallback-Schalter lagen in einer Klasse, deren Aufgabe es ist zu entscheiden, ob
eine App geöffnet werden darf. `BlockRepository.themeModeName` war das deutlichste
denkbare Zeichen, dass zwei Zuständigkeiten zusammengewachsen waren. Gleiche
Prefs-Datei, also keine Migration.

**Identität entwirrt.** `Habit.identity` speichert jetzt nur noch die
*Fortsetzung*; `identityStatement` setzt den Satz zum Anzeigen zusammen. Der Stamm
„I am someone who…" steht als unveränderliche Zeile über dem Eingabefeld — nicht
als `prefix` des Textfelds, denn Material blendet den bei leerem, unfokussiertem
Feld aus, also genau dann, wenn die Satzstruktur sichtbar sein müsste.
`normaliseIdentity()` heilt Altbestand beim Laden.

**Aufgaben auf Today.** Heute fällige Aufgaben stehen jetzt auf dem Bildschirm,
der „was jetzt?" beantwortet — aber in einem **eigenen Block** unter den
Gewohnheiten. Eine Aufgabe ist einmal erledigt und weg, eine Gewohnheit
wiederholt sich und wählt eine Identität. Beide in eine Liste zu mischen ist der
Grund, warum Serien in anderen Apps nichts bedeuten.

**Galerie-Übersicht.** Der Notes-Tab ist in „Notes | Photos" geteilt; `Photos`
zeigt alle Anhänge als Raster und benennt beim Antippen den Besitzer im Klartext
(„Habit: Walk", „Day: 2026-08-14"). Die Streifen dienen dem Hinzufügen im Kontext,
diese Ansicht beantwortet „wo sind meine Bilder" — was Streifen allein nie können.

**`Note.date` ist jetzt optional.** Eine Notiz muss zu keinem Tag gehören; nur
datierte erscheinen im Kalender. Vorher war das Feld gesetzt, unveränderbar und
wurde trotzdem gelesen — halb verdrahtet.

**`SegmentedRow` und `GroupLabel`** liegen in `ui/components`, damit Plan und
Notes sich gleich teilen statt jeweils eigene Umschalter zu erfinden.

## Milestone 7 — Positive und negative Gewohnheiten

`Polarity` mit `POSITIVE` („I am someone who") und `NEGATIVE` („I am not someone
who"). Das zugrunde liegende Modell benennt vier Bedingungen, unter denen
eine Gewohnheit entsteht, und kehrt jede für schlechte Gewohnheiten um — es
braucht also beide Richtungen.

Der Unterschied ist nicht kosmetisch: Bei einer negativen Gewohnheit heißt ein
Haken **„heute ferngeblieben"**, nicht „heute getan". Deshalb wird der Name im
Today-Screen bei Negativen **nicht durchgestrichen** — Durchstreichen läse sich
als „erledigt", also als das Gegenteil der Wahrheit. Stattdessen erscheint eine
Pille mit `polarity.doneVerb` (`done` / `avoided`).

Die Serienlogik bleibt unverändert: Ein ferngebliebener Tag ist genauso eine
Stimme wie ein erledigter. `normaliseIdentity()` erkennt beide Präfixe.

Negative Gewohnheiten sind zugleich die natürlichen Kandidaten für die
Sperr-Kopplung: „Ich bin nicht jemand, der in Instagram scrollt" ist genau die
Gewohnheit, die Instagram freischalten soll.

## Milestone 8 — Scorecard und Gliederung

**Habit Scorecard als Erstkontakt.** `OnboardingScreen` fragt nach dem, was man
ohnehin schon tut, und lässt jede Zeile mit `+ / = / −` bewerten. Die
Bestandsaufnahme steht vor jeder Änderung, weil man keine Gewohnheit verbessern
kann, die man nicht bemerkt hat.

Beim Abschluss werden die Plus-Zeilen zu `POSITIVE`-Gewohnheiten, die Minus-Zeilen
zu `NEGATIVE`. **Neutrale Zeilen werden bewusst verworfen** — sie zu bemerken und
dann in Ruhe zu lassen ist der Sinn der Markierung. Die Minus-Zeilen sind zugleich
die Kandidaten, hinter die eine App gesperrt gehört; darauf weist der Screen hin,
sobald mindestens eine existiert.

Sichtbar wird der Screen beim ersten Start (kein Häkchen **und** keine
Gewohnheiten — eine Neuinstallation über Bestand hinweg zwingt niemanden erneut
hindurch) und jederzeit über *Einstellungen → Habit scorecard → Run it again*.

Gotcha aus dem Gerätetest: Der Onboarding-Zweig kehrt vor dem `Scaffold` zurück
und hatte deshalb keinen Statusleisten-Abstand — die Kopfzeile klebte an der Uhr.
Er bringt jetzt sein eigenes `Scaffold` mit.

**Shield gegliedert** in „Does it work / Permissions / Hersteller / Apps" über
`GroupLabel`, statt einer durchgehenden Liste aus vier Themen.

## Milestone 9 — Die App bekommt Zähne

Bis hierher konnte man den Focus Mode jederzeit folgenlos beenden und jede Sperre
in einem Tap lösen. Damit wirkte die App nur, solange man wollte, dass sie wirkt —
also genau dann nicht, wenn man sie braucht. Die Reibung saß am Sperrbildschirm,
während die **Regeln dahinter** ungeschützt lagen; ein Umweg ist sinnlos, wenn das
Ändern der Regel billiger ist als der Umweg.

**Das Prinzip: Verschärfen wirkt sofort, Lockern kostet Zeit.** Sperren setzen,
Session starten, Wartezeit erhöhen — augenblicklich. Entsperren, Session vorzeitig
beenden, Wartezeit senken — erst nach der Abkühlphase, standardmäßig 10 Minuten
(5 bis 60 einstellbar). Eine Anfrage zurückzunehmen ist wieder ein Verschärfen und
damit kostenlos.

Die Selbstbezüglichkeit ist der Teil, der sonst vergessen wird: **Das Senken der
Abkühlphase wartet die aktuelle Abkühlphase ab.** Ohne das wäre alles einen Tap
tief — Wartezeit auf null, dann alles entsperren.

**Strikte Session.** Beim Start wählbar, ohne jeden vorzeitigen Ausgang. Der
Ulysses-Vertrag: Man bindet sich, solange man klar denkt. Weil es ausdrücklich
gewählt wird und nie der Normalfall ist, sperrt es niemanden ein, der das nicht
wollte. Der Bildschirm bietet dann gar keinen Knopf an — einer, der ablehnt, lädt
nur zu der Diskussion ein, die er verhindern soll.

**`HardDeadline` gegen die Uhr.** Focus rechnete bisher nur mit der Wanduhr, also
war Systemzeit vorstellen ein Ausgang. Jetzt werden Wanduhr **und**
`elapsedRealtime` mit Boot-Zähler gespeichert und innerhalb eines Boots die
*längere* Restzeit genommen: Vorstellen hilft nicht (die verstrichene Zeit läuft
weiter), Zurückstellen auch nicht (die Wartezeit wird länger). Über einen Neustart
bleibt nur die Wanduhr — aber ein Neustart kostet echte Zeit. Bewusst frei von
Android-Typen, deshalb direkt testbar (`HardDeadlineTest`, 6 Fälle).

Abgelaufene Wartezeiten greifen ohne offene App: `isBlocked()` und
`FocusMode.isActive` lösen sie beim Lesen ein, der Zwei-Stunden-Alarm des
Watchdogs holt den Rest nach. Eine Anfrage, die still nie landet, wäre schlimmer
als gar keine.

**Was das nicht schließt:** Deinstallieren, App-Daten löschen, Safe Mode,
Ausschalten. Das Ziel ist kein Gefängnis, sondern dass der Ausweg länger dauert
als das Verlangen.

## Milestone 10 — Rückschau

Der Verlauf der Gewohnheiten lag längst vor (Heatmap), aber die Zahlen, die
tatsächlich etwas Neues sagen, wurden nirgends aufgezeichnet: wie oft der Schild
hochkam, wie oft man trotzdem durchging, wie viel Zeit in Sessions steckte, wie
oft man die Regel selbst aufweichen wollte. `StatsStore` zählt das jetzt mit,
`ReflectView` zeigt es als dritten Abschnitt im Plan-Tab.

**Zählstände, kein Ereignisprotokoll.** Eine Liste „du hast Instagram um 23:41
geöffnet" wäre ein Überwachungsprotokoll, das der Nutzer über sich selbst führt —
das einzige wirklich heikle Datum in einer App, die sonst Absichten speichert, und
es lädt zum Grübeln statt zum Nachdenken ein. Tagesaggregate liefern dieselbe
Einsicht und lassen sich nicht als Tagebuch rücklesen. Aufbewahrt werden 400 Tage.

**Kein Analytics-SDK.** Handgeschrieben, weil jede dieser Bibliotheken dafür
existiert, Daten irgendwohin zu schicken. Die App hat kein `INTERNET` — dass die
Zahlen auf dem Gerät bleiben, ist damit eine Eigenschaft des Builds und nicht eine
Behauptung in einer Datenschutzerklärung.

**Wenige Zahlen, auch die unangenehmen.** Die regelmäßige Rückschau gehört
dazu — und mit ihr die Gefahr, dass eine Kennzahl selbst zum Ziel wird. Eine Wand
aus Diagrammen würde eine App über Zurückhaltung in genau das verwandeln, was sie
verhindern soll: noch etwas, das man für einen Reiz öffnet. Deshalb kein Score,
keine Level — und ausdrücklich die Zahl, wie oft der Schild übergangen wurde. Ein
Bildschirm, der nur Erfolge meldet, ist eine Schmeichelmaschine.

`heldPercent` gibt bei null Sperrungen `null` zurück statt 100 Prozent: Eine Woche,
in der man nach nichts gegriffen hat, ist keine Woche, in der man widerstanden hat.

## Zwei Varianten: full und play

Dieselbe App, ein Unterschied — die Bedienungshilfe.

| | `full` | `play` |
|---|---|---|
| Bedienungshilfe-Dienst | ja | **nein** |
| Erkennung | Fensterwechsel, zweistellige Millisekunden | Nutzungsstatistik, ~900 ms |
| Neustart nach ROM-Kill | durch das System | nicht automatisch |
| Vertrieb | Sideload, GitHub | Play Store |

```
gradlew assembleFullDebug     # zum Sideloaden
gradlew bundlePlayRelease     # für die Play Console
```

**Warum überhaupt zwei.** Google reserviert die Accessibility-API für Werkzeuge,
die Menschen mit Behinderung helfen; ein App-Blocker fällt ausdrücklich nicht
darunter. Seit Android 17 entzieht der Advanced Protection Mode die Berechtigung
sogar Apps, denen der Nutzer sie bereits erteilt hat. Ein Build, der sie nie
anfragt, kann sie nicht verlieren — und muss im Review deutlich weniger erklären.

**Was das kostet.** Ohne den Bedienungshilfe-Dienst ist der Schild etwa eine
Sekunde langsam statt sofort, und der Vordergrunddienst wird von aggressiven ROMs
gekillt, ohne dass ihn jemand neu startet. `full` ist das bessere Produkt, `play`
das veröffentlichbare.

**Im Code** entscheidet `BuildConfig.USES_ACCESSIBILITY`. Der Dienst steht nur im
Manifest von `src/full`; `UsageFallbackService` hört auf, sich zurückzuhalten, und
`fallbackEnabled` ist in `play` standardmäßig an — dort ist es kein Fallback,
sondern der einzige Detektor.

Beide Varianten teilen sich die `applicationId`: es sind zwei Builds einer App,
nicht zwei Apps. Nur eine kann gleichzeitig installiert sein.

### Der Detektor überlebt ein Update nicht von allein

Auf dem Gerät gefunden: `usage_fallback_enabled` stand auf `true`, der Dienst lief
trotzdem nicht. Gestartet wurde er nur beim Reboot oder von Hand im Shield-Tab —
und `START_STICKY` bringt einen Dienst nicht zurück, wenn der Prozess durch eine
Installation ersetzt wurde. Nach jedem Update sagte die Einstellung „an", während
nichts lief.

Im `full`-Build kostet das die Reserve. Im `play`-Build ist es fatal: Dort ist der
Fallback der einzige Detektor, das Blockieren hätte nach jedem Store-Update
stillschweigend aufgehört.

Zwei Ergänzungen: `UsageFallbackService.isRunning` sagt, ob der Dienst tatsächlich
läuft — der gespeicherte Schalter beantwortet das nicht. Und `HealthWatchdog`
**repariert statt zu melden**: Läuft der Dienst nicht, obwohl er soll, startet er
ihn neu. Anders als bei der Bedienungshilfe, die nur der Nutzer in den
Systemeinstellungen einschalten kann, ist dieser Dienst unserer. Scheitert der
Start (Android 12 verbietet die meisten Vordergrunddienst-Starts aus dem
Hintergrund), bleibt es bei der Benachrichtigung.

### Akku: der Poll ist an den Bildschirm gekoppelt

Der Poll lief bedingungslos alle 900 ms, also auch nachts — im `play`-Build
dauerhaft. Jetzt hält er an, sobald der Bildschirm ausgeht, und läuft beim
Einschalten weiter; hinter dem Sperrbildschirm und bei leerer Blockliste
entfällt die Abfrage ebenfalls. Nichts kann in den Vordergrund kommen, solange
das Display aus ist, also war die Arbeit dort vollständig umsonst.

Das **Intervall** bleibt dagegen konstant. Es nach einer Weile ohne
App-Wechsel zu strecken würde wenig sparen und genau den Fall verlängern, auf den
es ankommt: jemand sitzt lange in einer App und greift dann zur gesperrten.

## Milestone 11 — Härtung

Ein Review über den Stand, nicht über einen einzelnen Umbau. Die Befunde teilen
sich in zwei Sorten: Dinge, die nur unter einem Prozessabbruch oder auf einem
fremden ROM sichtbar werden, und Dinge, die auf genau einem der beiden Builds
falsch sind.

**Schreiben, das ein Kill übersteht.** `File.writeText` kürzt die Datei auf null
und füllt sie dann. Stirbt der Prozess dazwischen — und genau das tun die
Hersteller-Killer, gegen die die halbe App gebaut ist —, bleibt eine Datei
zurück, die sich nicht parsen lässt. Der Store las das als „keine Gewohnheiten",
und der nächste `save()` machte es wahr. Kein Absturz, keine Meldung, und mit
`allowBackup="false"` auch kein Netz darunter. `JsonFile` schreibt jetzt in eine
temporäre Datei, erzwingt sie auf die Platte und ersetzt das Original per
Rename; die verdrängte Fassung bleibt eine Generation lang als `.bak` liegen,
weil das einzige Fenster, das offen bleibt, ein Abbruch zwischen den beiden
Renames ist — und genau dann ist eine alte Fassung mehr wert als keine.

**Der Schild wurde von seinem eigenen Heimweg abgeräumt.** Sperren heißt: erst
nach Hause schicken, dann das Overlay zeigen. Der Launcher, der daraufhin
hochkommt, ist ein Fensterwechsel wie jeder andere, und der Handler nahm jeden
davon als Anlass, das Overlay zu entfernen. `hideIfSettled` wartet deshalb eine
Weile, bevor der Schild fremden Fenstern weicht — lange genug für den Übergang,
den er selbst ausgelöst hat, kurz genug, dass ein eingehender Anruf den
Bildschirm trotzdem bekommt. Dieselbe Meldung macht jetzt auch der
Nutzungsdetektor: vorher räumte nur die Bedienungshilfe je auf, also verhielt
sich derselbe Bildschirm in den beiden Builds unterschiedlich.

**Warnungen, die im `play`-Build ins Leere zeigten.** `evaluate()` unterschied
längst nach Flavor, die Benachrichtigung nicht: Sie nannte den
Bedienungshilfe-Dienst, den dieser Build gar nicht deklariert, und tippen führte
in eine Liste, in der H4b1ts nicht steht. Text und Ziel richten sich jetzt nach
dem, was tatsächlich fehlt — Nutzungszugriff oder der Detektor selbst.

**Neustart ohne Boot-Zähler.** `HardDeadline` verließ sich auf `boot_count`.
Liefert ein ROM den nicht, war der Wert auf jedem Boot derselbe, der veraltete
Uptime-Messwert galt weiter, und aus zehn Minuten Abkühlphase wurde die bisherige
Laufzeit des Geräts. Die Deadline merkt sich deshalb ihre Dauer und rechnet den
Uptime-Stand zum Zeitpunkt des Setzens zurück: Uptime kann innerhalb eines Boots
nicht kleiner werden, ein niedrigerer Stand beweist also den Neustart — ganz ohne
Zähler.

**Die Abkühlphase war eine Anfrage tief.** Das Ziel einer laufenden Anfrage ließ
sich nachschärfen, ohne die Uhr neu zu starten: 55 beantragen, kurz vor Ablauf
auf 5 ändern. Ein *tieferer* Schnitt startet jetzt seine eigene Wartezeit; ihn
anzuheben bleibt gratis, weil das ein Verschärfen ist.

**Löschen reicht jetzt so weit wie das Ding.** Eine gelöschte Gewohnheit nahm ihre
Gates mit, aber nicht ihre Erinnerung und nicht ihre Bilder — die blieben
unerreichbar im Photos-Raster stehen, beschriftet mit etwas, das es nicht mehr
gibt. Und eine entfernte Erinnerungszeile ließ ihren Wecker scharf. Beides
erledigen jetzt die Stores statt der Bildschirme, die zufällig davor standen.

Kleiner, aber aus demselben Review: Die Ausnahmeliste des Focus Mode hatte kein
Verfallsdatum, also war eine mitten in der Session installierte Tastatur nicht
darin und wurde gesperrt — womit sie in jeder noch erlaubten App fehlte, die
Telefon-App eingeschlossen. Der Bypass blendete das Overlay aus, bevor er die App
startete, und nahm sich damit genau das sichtbare Fenster, das den Start erst
erlaubt. Der Nutzungsdetektor entprellte ein zweites Mal neben `BlockGate` und
verschluckte damit echte Rückkehr; sein Abfragefenster blieb außerdem stehen,
wenn ein Tick übersprungen wurde. `consistency()` zählte den noch offenen
heutigen Tag als Fehltag, anders als die Serienlogik direkt darüber. Und Bilder
wurden im Kompositionsthread dekodiert.

## Milestone 12 — Focus, das tatsächlich sperrt

**Der Fehler: Focus lief nur auf dem Bildschirm.** `isBlocked()` beantwortet eine
Session, *bevor* es die Blockliste überhaupt ansieht — eine Session sperrt das
ganze Telefon, ohne dass etwas auf der Liste steht. Jeder billige Ausstieg
drumherum fragte aber genau diese Liste: `UsageFallbackService` übersprang jeden
Poll bei leerer Liste, `HealthWatchdog` meldete `DISARMED` und startete den
Detektor nicht nach. Ergebnis: Session läuft, Countdown zählt, und kein einziger
App-Wechsel wird angesehen. Im `play`-Build, wo der Nutzungsdetektor der einzige
ist, war Focus damit vollständig wirkungslos.

`BlockRepository.isArmed` ist jetzt die eine Definition von „es gibt etwas
durchzusetzen" — Liste nicht leer **oder** Session aktiv — und alle drei Stellen
fragen sie statt die Liste.

**Und: Eine Session wird nicht mehr angeboten, wenn niemand hinsieht.**
`HealthWatchdog.armNow()` repariert, was zu reparieren ist, und sagt dann, ob
überhaupt ein Detektor läuft. Tut er das nicht, startet Focus gar nicht erst,
sondern erklärt es und führt in den Shield-Tab. Eine Session ist ein
Versprechen, dass das Telefon zu ist; der Nutzer hört auf zu prüfen — das ist
ihr Sinn — und wenn dann nichts sperrt, ist das schlimmer, als sie nie
angeboten zu haben. Der Watchdog schaltet dabei nichts eigenmächtig ein: ein
abgeschalteter Detektor kostet Akku und bleibt die Entscheidung des Nutzers.

## Milestone 13 — Ein Rang, kein Punktestand

**Rang statt Punktestand.** Milestone 10 hat einen Score abgelehnt, und das gilt
weiter für die Sorte, die dort gemeint war: eine Zahl, die fürs Öffnen der App
steigt und sich farmen lässt. Ein Rang ist die andere Sorte. Er ist keine
Währung, sondern ein Name für das, was der Verlauf sagt — derselbe Satz, den die
Gewohnheiten ohnehin tragen („Ich bin jemand, der …"), einmal für alle zusammen
ausgesprochen. Unten ist er deshalb bewusst unschmeichelhaft: Für drei Haken
gratuliert niemand, und eine Leiter, die mit Lob beginnt, hat nach oben keinen
ehrlichen Platz mehr.

Acht Stufen von `Larva` über `Child`, `Teenager`, `Student`, `Apprentice`,
`Adult` und `Functioning Adult` bis `Someone Who Shows Up`.

**Ein Punkt pro Erledigung, sonst nichts.** Kein Serien-Multiplikator, kein
Wochenbonus — die machen die Zahl zu einem Rätsel, das man optimiert, und wer
herausfindet, wie man sie farmt, tut es auch. Der Preis der flachen Regel: Zehn
Gewohnheiten steigen schneller als zwei. Das ist in Ordnung, es ist auch mehr.

**Abgeleitet, nicht mitgezählt.** `Rank.standing()` rechnet aus
`HabitStore.totalCompletions()`, so wie der Kalender nichts Eigenes speichert,
sondern liest. Einen Tag abhaken zu widerrufen nimmt den Punkt mit — er war nie
verdient. Eine Gewohnheit zu löschen nimmt ihren Verlauf mit; wer eine Gewohnheit
beenden will, ohne das zu verlieren, archiviert sie.

Zu sehen im Plan-Tab unter „Rückschau", direkt über den Wochenzahlen: Die Woche
sagt, wie es läuft, der Rang, was daraus geworden ist. `RankTest` deckt die
Leiter ab, inklusive der Ränder — oberste Stufe ohne Nachfolger, Balken ohne
Division durch null.

## Milestone 14 — Zweiter Review-Durchgang

**Shield stürzte auf Android 8 und 9 ab.** `hasUsagePermission` rief
`unsafeCheckOpNoThrow` ohne Versionsprüfung — die Methode kam erst mit Android 10,
`minSdk` ist 26. Auf 8 und 9 war das keine falsche Antwort, sondern ein
`NoSuchMethodError` beim Öffnen des Tabs. Die ältere Schreibweise
`checkOpNoThrow` ist veraltet, aber vorhanden, und tut dasselbe.

**Die Ein-Minuten-Ausnahme konnte ewig gelten.** Der Nutzungsdetektor
beantwortete nur neu eingetroffene Ereignisse. Wer die Ausnahme nahm und
schlicht in der App blieb, erzeugte keine weiteren — also wurde nie wieder
gefragt, und die Minute lief nicht ab. Ausnahmen, Abkühlphasen und
Habit-Kopplungen laufen aber an einer Uhr ab, nicht an einem App-Wechsel.
Der Detektor merkt sich deshalb, was vorn ist, und prüft das bei jedem Tick.

Gemeldet wird ein Vordergrundwechsel weiterhin nur, wenn wirklich etwas
gewechselt hat: Der Schild misst seine Standzeit ab dem Einblenden, und eine
Meldung im Sekundentakt hätte daraus „verschwinde, sobald die Karenz vorbei
ist" gemacht.

**`armNow()` konnte die erste Session nicht retten.** Der Reparaturpfad stieg
aus, wenn nichts gesperrt und keine Session aktiv war — also genau vor der
ersten Session. Der `force`-Pfad ist für den Aufrufer, der gerade im Begriff
ist scharfzustellen; die Armed-Prüfung gehört zum periodischen Durchlauf.

**Verschobene Aufgaben nahmen ihren Wecker nicht mit.** `TaskStore.update()`
speicherte die Aufgabe, ohne den Alarm neu zu stellen: Eine Aufgabe von morgen
auf heute zu ziehen verschluckte die heutige Erinnerung, sie zu verschieben ließ
sie am alten Tag trotzdem klingeln. Abhaken zählt genauso, weil eine erledigte
Aufgabe keine nächste Auslösung mehr hat. Auf der Habit-Seite ist Archivieren
dieselbe Art Eingabe und wird jetzt genauso behandelt.

**Die Tab-Leiste schloss die Einstellungen nicht.** Einstellungen liegen über den
Tabs, nicht neben ihnen. Ein Tipp auf die Leiste verschob die Markierung auf ein
Ziel, das hinter den Einstellungen verborgen blieb — die App sagte „Habits" und
zeigte das Zahnrad, und der einzige Ausweg war wieder das Zahnrad.

## Milestone 15 — Auf API 36

Play verlangt seit dem 31.08.2026 für neue Apps `targetSdk = 36`. Die 35 war
kein Versäumnis, sondern der Stand von vorher — jetzt ist sie schlicht nicht
mehr hochladbar.

**Der Bump selbst war unauffällig.** AGP lädt die fehlende Plattform von allein
nach, und die Verhaltensänderungen von Android 16 treffen diese App kaum: Der
Randlos-Zwang galt für `targetSdk = 35` bereits, eine Abmeldung davon gab es
hier nie, und die Bildschirme hängen ohnehin an `Scaffold`, das die
Systemleisten-Einrückung mitbringt. Eine Orientierungssperre, die auf großen
Displays ab 36 ignoriert würde, steht nicht im Manifest. `onBackPressedDispatcher`
ist die API, die Predictive Back erwartet.

**Was daran Arbeit war, war das Werkzeug.** AGP 8.7.3 baut gegen 36, warnt aber,
dass es nie dagegen getestet wurde. Für einen Store-Build ist das die falsche
Sorte Unbekanntes: Nicht der Compiler bricht, sondern Manifest-Merge und
Ressourcen-Verarbeitung tun etwas Ungeprüftes. Also AGP auf 8.11.1, das API 36
offiziell kennt. Gradle 8.13 bleibt — genau die Mindestversion dafür.

`lintVitalPlayRelease` und `lintVitalFullRelease` laufen durch, der volle
Lint-Bericht meldet null Fehler und nur Warnungen der kosmetischen Sorte.

**Nicht gelöst, nur gesehen:** `FocusActivity` und `BlockActivity` setzen eine
feste Einrückung von 48 dp nach oben statt der echten Systemleisten-Einrückung.
Auf gewöhnlichen Geräten reicht das, auf einem großen Ausschnitt wird es eng.
Unverändert gegenüber vorher und deshalb kein Blocker — aber ein Punkt für den
Gerätetest.

## Milestone 16 — R8, und was er fast kaputtgemacht hätte

`isMinifyEnabled` steht jetzt auf `true`. Das AAB fällt von 6,23 MB auf
3,49 MB, also um 44 Prozent.

**Der gefährliche Teil waren die Enums.** Fünf Stück tragen ihren Namen in die
Persistenz: `Polarity` in `habits.json`, `AttachmentOwner` in
`attachments.json`, `ReminderOwner` in `reminders.json`, dazu `AccentMode` und
`ThemeMode` in den Einstellungen. Geschrieben wird mit `.name`, gelesen mit
`valueOf()` beziehungsweise `it.name == value`.

R8 benennt Felder um, und Enum-Konstanten sind Felder. Das hätte weder den Build
gebrochen noch die App zum Absturz gebracht — sie hätte nach dem Update
schlicht nicht mehr erkannt, was sie vorher selbst geschrieben hatte, wäre auf
ihren Standardwert zurückgefallen und hätte die Polarität jeder Gewohnheit
stillschweigend verloren. Ein Fehler, der sich als Vergesslichkeit tarnt, ist
der schlechteste, den man ausliefern kann.

Die Regel in `proguard-rules.pro` hält deshalb alle Enum-Member der App fest.
Nachgewiesen wird das nicht durch Zusehen, sondern an `mapping.txt`: Alle 13
Konstanten stehen dort als `POSITIVE -> POSITIVE` — Name unverändert. Die
Klassen drumherum sind umbenannt und dürfen es sein, die stehen nirgends in
einer Datei.

**Die zweite Falle blieb aus.** Eine umbenannte Aktivität oder ein umbenannter
Receiver lässt das Manifest auf eine Klasse zeigen, die es nicht mehr gibt.
AGP erzeugt dafür eigene Keep-Regeln; geprüft ist es trotzdem: Alle sieben
Komponenten aus dem Manifest stehen unverändert in `mapping.txt`.

**Was R8 nicht beweist.** Die Unit-Tests laufen gegen den unminifizierten Build
— sie sagen über den ausgelieferten Code nichts. `mapping.txt` deckt die beiden
bekannten Fallen ab, nicht jede. Der minifizierte Build gehört vor dem Upload
auf ein Gerät.

`isShrinkResources` bleibt aus. Es ist ein eigener Schalter mit eigenem
Fehlerbild, und zwei Unbekannte gleichzeitig einzuschalten macht den
Gegentest unmöglich.

## Milestone 17 — Der Paketname, solange er noch änderbar war

Das Paket heißt jetzt `de.h4b1ts.app`. Der vorherige Name enthielt den Vornamen
des Autors — in 64 Dateien, im Manifest, in der Intent-Action der Erinnerungen
und vor allem in der `applicationId`.

**Der Zeitpunkt war der ganze Punkt.** Die `applicationId` ist nach dem ersten
Play-Upload unveränderlich. Sie ist dauerhaft die Kennung der App im Store und
auf jedem Gerät, sie lässt sich nicht migrieren und nicht zurückziehen. Ein Tag
später wäre daraus eine Entscheidung auf Lebenszeit geworden.

305 Vorkommen in 64 Dateien, dazu die beiden Quellverzeichnisse. Ein
mechanischer Umbau — riskant ist daran nur, etwas zu übersehen, und genau das
beantwortet der Build:

- `testPlayDebugUnitTest` und `testFullDebugUnitTest` grün
- `bundlePlayRelease` und `assembleFullRelease` bauen
- `output-metadata.json` meldet `"applicationId": "de.h4b1ts.app"`
- im Manifest des AAB steht der alte Name nirgends mehr, auch nicht in
  `taskAffinity` oder der generierten Receiver-Berechtigung
- die R8-Keep-Regel zeigte auf das alte Paket und wurde mitgezogen: alle 13
  Enum-Konstanten stehen unverändert in `mapping.txt`

**Mit umgezogen ist `ACTION_FIRE`**, die Intent-Action der Erinnerungen. Auf
einem Gerät mit alter Installation würden bereits gestellte Wecker damit ins
Leere laufen — folgenlos, weil `rescheduleAll()` bei jedem Start ohnehin alles
neu stellt, und weil es diese Installationen außerhalb der Testgeräte nicht
gibt.

### Warum die Historie neu beginnt

Dieses Repository hat genau einen Commit. Der alte Paketname stand in 61
Dateipfaden und in jeder Paketzeile jedes Commits, die Commit-Metadaten trugen
eine private E-Mail-Adresse. Beides lässt sich mit `filter-branch` ersetzen —
aber ein öffentlich gewordener Commit bleibt bei GitHub noch eine Weile über
seinen alten Hash erreichbar, und Forks und Caches erreicht ein Rewrite gar
nicht.

Ein Repository ohne Vorgeschichte hat dieses Problem nicht. Die sechzehn
Milestones sind als Text ohnehin hier; was verloren geht, ist die Aufteilung in
Commits, und die wog weniger.

## Milestone 18 — Was der Selbsttest wirklich misst

Erster Lauf des Schilds auf echter Hardware unter R8: Galaxy S25, Android 16,
signierter Release-Build. Der Selbsttest meldete

> Blocked after 232 ms via overlay — slow enough to notice.

in Orange, weil `GOOD_LATENCY_MS` bei 150 liegt. Zwei Schlüsse lagen nahe und
waren beide falsch.

**„Der Overlay ist der Fallback“ — nein, er ist der Hauptpfad.**
`BlockPresenter` probiert die Aktivität gar nicht erst: Auf genau diesem Gerät
verwirft One UI den Aktivitätsstart aus dem Hintergrund stillschweigend,
`startActivity` kehrt normal zurück und es entsteht nie ein Fenster. Das zuerst
zu versuchen kostete anderthalb Sekunden, in denen die gesperrte App sichtbar
blieb. Ein Overlay-Fenster zeichnet dagegen auf jedem ROM in wenigen
Millisekunden. „via overlay“ ist also die gute Nachricht, nicht die schlechte.

**„232 ms sind die Erkennungslatenz“ — nein, es ist die Summe.** `SelfTest`
misst von `startActivity` der *gesperrten* App bis zum Feuern der Sperre. Darin
steckt der Kaltstart der Zielanwendung, den H4b1ts nicht beeinflussen kann.
Nachgemessen mit `am start -W`:

| | |
|---|---|
| Selbsttest, Ende zu Ende | 232 ms |
| Kaltstart von Adobe Scan allein | 141 ms (`WaitTime`) |
| Rest für Erkennung, `goHome()` und Overlay | **rund 91 ms** |

Die Mechanik liegt damit deutlich unter der eigenen Schwelle. Gewarnt hat die
App über eine Zahl, die zu großen Teilen einer fremden App gehört — und Adobe
Scan ist schwer. Bei einer leichten App würde derselbe Schild als schnell
gelten.

**Was daraus folgt, ist eine Entscheidung über die Messung, nicht über den
Schild.** Drei Möglichkeiten, keine davon umgesetzt:

- Die Schwelle anheben, weil sie faktisch gegen Ende-zu-Ende-Zeit prüft.
- Ab dem erkannten Vordergrundwechsel messen statt ab `startActivity`. Das
  misst, was die App steuert — verliert aber genau das, was der Nutzer spürt.
- So lassen und die Meldung umformulieren: Nicht „zu langsam“, sondern „so
  lange war die App sichtbar“. Ehrlicher, und es erklärt, warum eine schwere
  App schlechter abschneidet.

Der Messwert ist indikativ, nicht exakt: Beim Nachmessen feuerte der Schild
während des Starts mit, was `WaitTime` beeinflusst haben kann.

**Was der Lauf sonst bewies.** Das System löste
`de.h4b1ts.app.block.H4b1tsAccessibilityService` auf und band den Dienst —
hätte R8 die Klasse umbenannt, wäre genau das fehlgeschlagen. Sperrbildschirm,
Fünf-Sekunden-Reibung, Ein-Minuten-Ausnahme und die Zehn-Minuten-Abkühlphase
beim Entsperren liefen alle wie vorgesehen.

## Signieren und Veröffentlichen

Der Upload-Schlüssel wird mit `keytool` erzeugt, außerhalb des Repos. Die
Schritte stehen hier bewusst nicht: Der Befehl trägt einen Dateipfad, und der
verrät den Benutzernamen dessen, der ihn getippt hat.

Der Schlüssel gehört **nicht** ins Repo — `keystore.properties`, `*.jks` und
`*.keystore` stehen in `.gitignore`. Wer Datei und Passwort hat, kann ein
Update im Namen dieser App veröffentlichen.

Bei Play App Signing ist das der *Upload*-Schlüssel; den eigentlichen
Signaturschlüssel hält Google. Geht der Upload-Schlüssel verloren, lässt er
sich über die Play Console zurücksetzen — der Signaturschlüssel nicht.

### Zugangsdaten hinterlegen

`keystore.properties.template` nach `keystore.properties` kopieren und ausfüllen.
Fehlt die Datei, bleibt der Release-Build **unsigniert** statt zu scheitern — so
kann jeder das Projekt bauen, ohne den Schlüssel zu besitzen. Ein Release, das
still mit dem Debug-Key signiert wird, wäre der schlechtere Ausgang: er
installiert sich anstandslos und lässt sich bei Play nie wieder aktualisieren.

Alternativ liest der Build dieselben Werte aus der Umgebung
(`H4B1TS_STORE_FILE`, `H4B1TS_STORE_PASSWORD`, `H4B1TS_KEY_ALIAS`,
`H4B1TS_KEY_PASSWORD`), damit ein CI-Runner ohne Datei auskommt.

### Bauen

Play nimmt für neue Apps ein App Bundle, keine APK:

```
gradlew bundleRelease
```

Ergebnis: `app/build/outputs/bundle/release/app-release.aab`.

Für Freunde ohne Play-Umweg reicht die signierte APK:

```
gradlew assembleRelease
```

`versionCode` in `app/build.gradle.kts` muss **vor jedem Upload** steigen; Play
lehnt eine bereits benutzte Nummer ab. `versionName` ist frei und nur für Menschen.

### Was vor dem ersten Upload noch fehlt

- ~~**Bedienungshilfe-Deklaration.**~~ Erledigt durch die Flavor-Trennung: Der
  `play`-Build führt keinen `AccessibilityService`, das gemergte
  Release-Manifest enthält weder den Dienst noch `BIND_ACCESSIBILITY_SERVICE`.
  Damit entfällt die Begründung im Formular „Berechtigungen für vertrauliche
  Daten" — und mit ihr die Hürde, an der App-Blocker wiederholt gescheitert
  sind. Gilt nur für `play`; `full` wird ohnehin nie hochgeladen.
- **Nutzungszugriff** (`PACKAGE_USAGE_STATS`) und **Special-Use-Foreground-Service**
  brauchen je eine eigene Erklärung. Der Subtyp steht bereits im Manifest, die
  Begründungen ausformuliert in `store/play-eintrag.md`.
- **Datenschutzerklärung** — `docs/datenschutz.html`, fertig bis auf den
  Verantwortlichen. Play verlangt dafür eine öffentlich erreichbare URL; die
  Seite ist so gebaut, dass GitHub Pages aus `docs/` genügt.
- **Store-Eintrag** — Titel, Kurz- und Vollbeschreibung, Data-Safety-Antworten
  und die Grafik-Checkliste liegen in `store/play-eintrag.md`. Offen sind nur
  die Screenshots, die vom Gerät kommen müssen.
- **Signaturschlüssel** — existiert noch nicht. Ohne ihn baut `bundlePlayRelease`
  ein unsigniertes AAB, wie vorgesehen; hochladbar ist es damit nicht.
- `QUERY_ALL_PACKAGES` wird bewusst vermieden — es werden nur Apps mit
  Launcher-Eintrag abgefragt. Eine Deklaration weniger.
- ~~**R8 ist aus.**~~ Eingeschaltet, siehe Milestone 16. Der minifizierte Build
  gehört noch auf ein Gerät, bevor er hochgeladen wird.

## Nächste Schritte

9. Umgehungsschutz: Device Admin gegen Deinstallation.
10. Play Billing, Freemium-Grenzen.
11. Play Console: Deklaration für den Nutzungszugriff, geschlossener Test mit
    zwölf Testern über vierzehn Tage.
