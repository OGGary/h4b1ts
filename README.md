# H4b1ts

**Gewohnheiten aufbauen. Ablenkung aussperren.** Ein Habit-Tracker für Android,
der auch sperren kann — weil beides dieselbe Aufgabe von zwei Seiten ist.

Kein Konto, keine Werbung, keine Internet-Berechtigung.

---

## Die Idee

Die meisten Habit-Apps zählen Ihre Serie. Die meisten App-Blocker sperren Ihr
Handy. H4b1ts macht beides, weil das eine ohne das andere selten funktioniert:
Wer laufen gehen will, scheitert nicht am Vorsatz, sondern am Feed, der gerade
interessanter ist.

Eine Gewohnheit entsteht leichter, wenn sie **auffällt**, wenn sie **reizvoll**
ist, wenn sie **wenig Aufwand** kostet und wenn sie sich **lohnt**. Sie
verschwindet leichter, wenn man genau das umdreht. H4b1ts baut beide Richtungen
in dieselbe App.

| Damit eine gute Gewohnheit entsteht | Damit eine schlechte verschwindet |
|---|---|
| Sie hängt an einem festen Auslöser: „Nach dem Kaffee mache ich …", mit Zeit und Ort | Die App verschwindet aus dem Zugriff |
| Die Belohnung kommt danach, nicht vorher: der Feed erst nach dem Lauf | Der Sperrbildschirm zeigt, was der Griff gerade kostet |
| Jede Gewohnheit hat eine Zwei-Minuten-Variante für schlechte Tage | Reibung statt Mauer: Countdown, Tippaufgabe, eine Ausnahme von einer Minute |
| Erledigtes ist sichtbar — Heatmap, Serie, Rang | Ein Rückfall bleibt im Verlauf stehen |

### Identität statt Punktestand

Jede Gewohnheit hängt an einem Satz: *„Ich bin jemand, der …"* Jede Erledigung
ist eine Stimme dafür. Nicht die Zahl ist das Ziel, sondern der Mensch, der sie
erreicht.

### Nie zweimal auslassen

Ein verpasster Tag bricht Ihre Serie **nicht**. Zwei hintereinander schon. Ein
schlechter Tag ist ein Unfall; zwei sind der Anfang einer neuen Gewohnheit — und
genau davor warnt die App, statt Sie für den ersten Ausrutscher zu bestrafen.

### Die Zwei-Minuten-Variante

Jede Gewohnheit speichert eine Mini-Version. Nicht „eine Stunde laufen", sondern
„Laufschuhe anziehen". An Tagen, an denen nichts geht, geht das.

### Die Bestandsaufnahme am Anfang

Beim ersten Start listen Sie auf, was Sie ohnehin schon täglich tun, und bewerten
jede Zeile mit `+`, `=` oder `−`. Die Minus-Einträge werden direkt zu
Sperrkandidaten. So füttert der Tracker den Blocker, statt danebenzustehen.

---

## Was die App kann

- **Gewohnheiten** mit Wochentagen, Identitätssatz, Zwei-Minuten-Variante und
  Serie nach der „nie zweimal"-Regel
- **Sperre** für selbst gewählte Apps — einzeln oder in Gruppen
- **Kopplung**: eine App erst freigeben, wenn eine Gewohnheit erledigt ist
- **Fokus-Sitzungen**: für eine feste Dauer ist nur da, was Sie vorher erlaubt
  haben. Die Sitzung zeigt Ihre Gewohnheiten und Aufgaben, statt Sie nur
  auszusperren
- **Aufgaben** mit Wiederholungen und Erinnerungen
- **Notizen** mit Bildern
- **Rückschau**: Heatmap, Tageszähler über rund ein Jahr, ein Rang statt einer
  Punktzahl
- Hell- und Dunkelmodus, zwei Akzentfarben, Pixelart-Oberfläche

---

## Zwei Fassungen

Dieselbe App, ein Unterschied — wie schnell sie merkt, dass Sie eine gesperrte
App geöffnet haben.

| | Sideload-Fassung | Play-Store-Fassung |
|---|---|---|
| Reaktion | sofort | knapp eine Sekunde |
| Nach einem Abschuss durch das System | kommt von allein zurück | muss unter Umständen von Hand gestartet werden |
| Bezug | als APK, direkt installiert | Google Play |

Die Sideload-Fassung nutzt dafür einen Bedienungshilfe-Dienst. Google behält
diese Schnittstelle Werkzeugen für Menschen mit Behinderung vor — ein
App-Blocker zählt ausdrücklich nicht dazu, und ab Android 17 entzieht der
erweiterte Schutzmodus die Berechtigung sogar dann, wenn Sie sie selbst erteilt
haben. Die Play-Fassung fragt sie deshalb gar nicht erst an.

Beide tragen dieselbe Kennung: Es sind zwei Fassungen einer App, nicht zwei
Apps. Nur eine kann gleichzeitig installiert sein.

---

## Installation

Die App ist **noch nicht im Play Store**. Bis dahin: die APK aus den
[Releases](../../releases) laden und installieren. Android fragt dabei nach, ob
es Apps aus dieser Quelle installieren darf.

Voraussetzung ist Android 8.0 oder neuer.

Beim ersten Start führt die App durch die Berechtigungen, die sie braucht.

---

## Welche Berechtigungen die App braucht — und wofür

| Berechtigung | Wofür |
|---|---|
| **Zugriff auf Nutzungsdaten** | Erkennt, welche App gerade vorn ist, damit eine gesperrte App blockiert werden kann. Ausgewertet wird nur der Name der sichtbaren App, nur im Arbeitsspeicher. Kein Nutzungsverlauf, keine Nutzungsdauer. |
| **Über anderen Apps anzeigen** | Blendet den Sperrbildschirm ein, wenn das Herstellersystem den normalen Weg blockiert. |
| **Vordergrunddienst** | Hält die Erkennung am Laufen, solange der Bildschirm an ist. Bei ausgeschaltetem Bildschirm pausiert sie — nichts kann dann in den Vordergrund kommen. |
| **Benachrichtigungen** | Ihre Erinnerungen, und die vorgeschriebene Anzeige des laufenden Dienstes. |
| **Start nach Neustart** | Stellt Sperre und Erinnerungen nach einem Geräteneustart wieder her. |

Zur Auswahl der zu sperrenden Apps fragt H4b1ts nur Apps mit einem Startsymbol
ab. Die weitreichende Berechtigung, *alle* installierten Pakete zu sehen, wird
bewusst nicht verwendet.

---

## Ihre Daten

**H4b1ts hat keine Internet-Berechtigung.** Ohne sie kann eine Android-App
technisch keine Netzwerkverbindung aufbauen. Das heißt:

- Kein Konto, keine Anmeldung, keine Registrierung
- Keine Analyse-Bibliotheken, keine Werbung, keine Absturzberichte
- Nichts wird übertragen — nicht, weil wir es versprechen, sondern weil das
  Programm es nicht kann

Alles liegt im app-privaten Speicher Ihres Geräts, für andere Apps nicht lesbar,
von Cloud-Sicherungen ausgenommen. Es verschwindet rückstandslos, wenn Sie die
App deinstallieren oder unter *Einstellungen → Apps → H4b1ts → Speicher* die
App-Daten löschen.

Vollständig: [Datenschutzerklärung](https://oggary.github.io/h4b1ts/datenschutz.html)

---

## Schrift

Die Oberfläche ist in **Pixelbasel** gesetzt (© GGBotNet), lizenziert unter der
[SIL Open Font License 1.1](https://openfontlicense.org/).

---

## Mitentwickeln

Der Quellcode liegt vollständig in diesem Repository — Kotlin und Jetpack
Compose, ohne Fremdbibliotheken über AndroidX hinaus.

Wie das Projekt gebaut ist, warum die Entscheidungen so gefallen sind und was
unterwegs kaputtging, steht in **[ENTWICKLUNG.md](ENTWICKLUNG.md)**.
