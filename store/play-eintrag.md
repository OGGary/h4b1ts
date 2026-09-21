# Play-Eintrag — Vorlagen und Formularantworten

Alles, was die Play Console beim ersten Upload abfragt, hier vorformuliert.
Kopiervorlagen stehen in Codeblöcken, damit Zeichenzahlen nicht durch
Formatierung verfälscht werden.

Namen und Formulierungen aus der Ratgeberliteratur, an der sich die Mechanik
orientiert, bleiben draußen — Methoden sind frei, Buchtitel und ihr Wortlaut
nicht.

---

## 1. Eintragstexte

### App-Name (max. 30 Zeichen)

```
H4b1ts: Gewohnheiten & Fokus
```

28 Zeichen.

### Kurzbeschreibung (max. 80 Zeichen)

```
Gewohnheiten aufbauen, ablenkende Apps sperren. Ohne Konto, ohne Datenabfluss.
```

78 Zeichen.

### Vollbeschreibung (max. 4000 Zeichen)

```
H4b1ts ist ein Gewohnheits-Tracker, der auch sperren kann. Beides gehört
zusammen: Eine gute Gewohnheit aufzubauen und eine schlechte loszuwerden sind
dieselbe Aufgabe von zwei Seiten.

IDENTITÄT STATT PUNKTESTAND

Jede Gewohnheit hängt an einem Satz: "Ich bin jemand, der ..." Jede Erledigung
ist eine Stimme dafür. Nicht die Serie ist das Ziel, sondern der Mensch, der
sie führt.

NIE ZWEIMAL AUSLASSEN

Ein verpasster Tag bricht Ihre Serie nicht. Zwei hintereinander schon. Ein
schlechter Tag ist ein Unfall, zwei sind der Anfang einer neuen Gewohnheit —
und genau davor warnt die App, statt Sie für den ersten Ausrutscher zu
bestrafen.

DIE ZWEI-MINUTEN-VARIANTE

Jede Gewohnheit speichert eine Mini-Version für schlechte Tage. Nicht
"eine Stunde laufen", sondern "Laufschuhe anziehen". An den Tagen, an denen
nichts geht, geht das.

DIE SPERRE

Wählen Sie Apps, die Sie zu oft öffnen. H4b1ts legt einen Sperrbildschirm
darüber, der zeigt, was der Griff gerade kostet. Kein Schloss, sondern
Reibung: ein Countdown, eine Tippaufgabe, eine Ausnahme von einer Minute,
wenn Sie sie wirklich brauchen.

Sie können eine App auch an eine Gewohnheit koppeln — erst der Lauf, dann der
Feed.

FOKUS-SITZUNGEN

Eine Sitzung starten, und für ihre Dauer ist nur da, was Sie vorher erlaubt
haben. Die Sitzung zeigt Ihre Gewohnheiten und Aufgaben des Tages, statt Sie
nur auszusperren.

AUSSERDEM

- Aufgaben mit Wiederholungen und Erinnerungen
- Notizen mit Bildern
- Rückschau: Heatmap über Ihre Gewohnheiten, Tageszähler über rund ein Jahr
- Hell- und Dunkelmodus, zwei Akzentfarben, kontraststarkes Design
- Pixelart-Oberfläche

IHRE DATEN BLEIBEN BEI IHNEN

H4b1ts hat keine Internet-Berechtigung. Das heißt: Die App kann technisch
keine Verbindung ins Netz aufbauen. Kein Konto, keine Anmeldung, keine
Analyse-Bibliotheken, keine Werbung, keine Übertragung — nicht weil wir es
versprechen, sondern weil das Programm es nicht kann.

Alles liegt im app-privaten Speicher Ihres Geräts und verschwindet
rückstandslos, wenn Sie die App deinstallieren.

BERECHTIGUNGEN

Für die Sperre braucht die App den Zugriff auf Nutzungsdaten, um zu erkennen,
welche App gerade vorn ist. Ausgewertet wird nur der Name der sichtbaren App,
nur im Arbeitsspeicher. Es wird kein Nutzungsverlauf und keine Nutzungsdauer
gespeichert.
```

Rund 2.300 Zeichen — reichlich Luft unter dem Limit.

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
| Screenshots Telefon | mind. 2, max. 8; kurze Seite ≥ 320 px | fehlt |
| Screenshots Tablet | optional | — |

Screenshots müssen vom Gerät kommen. Sinnvolle Auswahl: Today, Habits mit
Heatmap, Sperrbildschirm, Fokus-Sitzung, Shield-Tab.

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
