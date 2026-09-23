# Bomberman Viewer

Darstellungsschicht und Moderationskonsole für die Bomberman-Arena.

Die Anwendung enthält **keine Spiellogik**. Sie zeigt, was der Server sagt, und schickt
die Befehle der Moderation zurück.

```
Server ──WebSocket /ws/admin──▶  Viewer  (Lobby, Spielfeld, Ergebnis)
       ◀───── JSON-Befehle ─────  Moderation (start, pause, kick, …)
```

Gegenstelle ist der Arena-Server: <https://github.com/jegollub-btc/bomberman>
(`VISUALIZER_GUIDE.md` und `MODERATION_API.md` dort sind die maßgebliche Quelle).

> **Der UDP-Port 47800 des Servers gehört den Bots.** Diese Anwendung fasst ihn nicht an —
> sie spricht ausschließlich WebSocket auf dem Webport.

---

## Starten

```bash
mvn javafx:run
```

Verbindet sich standardmäßig mit `ws://127.0.0.1:8080/ws/admin`. Läuft der echte Server
noch nicht, gibt es den mitgelieferten Ersatz:

```bash
mvn exec:java -Dexec.mainClass=com.btc.hackathon.viewer.tools.FakeServer
```

Der Fake-Server spricht dasselbe Protokoll und simuliert Lobby, Countdown, Match, Pause,
Abbruch und Ergebnis — damit ist die ganze Kette ohne den Rust-Server vorführbar. Beenden
mit Ctrl+C.

Einzelne Befehle lassen sich auch ohne Oberfläche absetzen — praktisch, um den
Steuerkanal eines echten Servers zu prüfen:

```bash
mvn exec:java -Dexec.mainClass=com.btc.hackathon.viewer.tools.SendCommand -Dexec.args="start"
mvn exec:java -Dexec.mainClass=com.btc.hackathon.viewer.tools.SendCommand -Dexec.args="kick 2 10.0.0.5 8080"
```

### Tastenkürzel

| Taste | Wirkung |
|---|---|
| `F2` | Moderationsleiste ein-/ausblenden (für die Beamer-Ansicht) |
| `F3` | Technische Anzeige: FPS, Zustände/s, Lesefehler |
| `F5` | Kartenvorschau wegräumen |
| `F11` | Vollbild |

### Serveradresse

**Im laufenden Betrieb:** oben in der Moderationsleiste stehen Adresse und Port mit einem
Knopf „Verbinden". Der Wechsel bricht die bestehende Verbindung ab und baut sofort die
neue auf — kein Neustart, auch dann nicht, wenn das Fenster schon auf dem Beamer liegt.
Die alte Anzeige wird dabei geleert, damit keine Lobby des vorigen Servers stehen bleibt
und eine Verbindung vortäuscht.

**Beim Start** geht alles über **`-Djavafx.args`**, als eine zusammenhängende
Zeichenkette:

```bash
mvn javafx:run -Djavafx.args="--host=10.0.0.5 --port=8080"
```

| Argument | Vorgabe | Wirkung |
|---|---|---|
| `--host=` | `127.0.0.1` | Adresse des Servers, IP oder Name |
| `--port=` | `8080` | `web_bind` des Servers, **nicht** der Bot-Port 47800 |
| `--assets=` | `assets` | Grafikordner |
| `--no-panel` | aus | startet ohne Moderationsleiste |
| `--spectate-only` | aus | nutzt `/ws/spectate`, keine Moderation möglich |

> **`-Dviewer.host=…` wirkt bei `mvn javafx:run` nicht.** Das Plugin startet eine eigene
> JVM und reicht Mavens System-Properties nicht weiter; der Viewer fällt dann stumm auf
> `127.0.0.1` zurück. Die System-Properties greifen nur, wenn die Anwendung direkt in
> derselben JVM läuft — also aus der IDE heraus. Beim Start über Maven ist
> `--host=`/`--port=` der einzige Weg.

Ob die Adresse angekommen ist, steht in der ersten Protokollzeile und in der Titelzeile
des Fensters:

```
INFORMATION: Konfiguration: Server=ws://10.0.0.5:8080/ws/admin, …
```

Der Fake-Server nimmt `-Dfake.port` und `-Dfake.joinSeconds` (Abstand zwischen
eintreffenden Bots, `0` = alle sofort) — dort wirken die Properties, weil `exec:java`
in derselben JVM läuft.

### Ohne Maven: das eigenständige JAR

`mvn package` legt neben dem gewöhnlichen JAR ein startbares mit allen Abhängigkeiten ab:

```bash
mvn package
java -jar target/bomberman-viewer-1.0-SNAPSHOT-viewer.jar --host=10.0.0.5 --port=8080
```

Hier zählen die Argumente **und** die `-Dviewer.*`-Properties, weil alles in einer JVM
läuft. Auf dem Zielrechner braucht es nur ein JDK 25 — kein Maven, kein Projektbaum.

Zwei Dinge gehören dazugesagt:

- Das JAR enthält die JavaFX-Bibliotheken der **Bauplattform**. Unter Windows gebaut,
  läuft es unter Windows. Für Linux oder macOS muss dort gebaut werden.
- Der Grafikordner wird relativ zum **Arbeitsverzeichnis** gesucht. Liegt das JAR
  woanders, gehört `assets/` daneben oder `--assets=…` dazu.

---

## Was der Viewer vom Protokoll wissen muss

Das Format ist vollständig im `VISUALIZER_GUIDE.md` des Servers beschrieben. Vier Punkte
bestimmen hier den Aufbau — sie sind die Stellen, an denen es sonst schiefgeht:

**1. Das Spielfeld kommt genau einmal.** `match_init` liefert `tiles`, danach nur noch
`tile_changes`. Der `ViewStateStore` schreibt sie auf ein `Board` fort. Wer das ausließe,
sähe zerstörte Kisten nach kurzer Zeit wieder auftauchen.

**2. `x`/`y` einer laufenden Figur sind bereits die Zielzelle.** Der Schritt gilt ab dem
ersten Tick als festgelegt. Gezeichnet wird deshalb **rückwärts** vom Ziel über
`move_progress`/`move_total` (`PlayerState.cellX/cellY`). Direkt gezeichnet würden Figuren
eine Zelle vorausspringen.

**3. `paused` ist ein eigenes Flag, keine Phase.** Der Zustand ist eines von `open`,
`locked`, `countdown`, `running`, `match_over` — und `paused` steht orthogonal daneben.
Eine Pause blockiert auch den Start, also muss sie sich in jeder Phase aufheben lassen.

**4. `preview_map` wird nicht mit `ack` beantwortet**, sondern mit der `map_preview`-
Nachricht selbst. Der `ModerationChannel` behandelt das als Sonderfall; ohne ihn liefe der
Befehl in die Zeitüberschreitung, obwohl er ausgeführt wurde.

Dazu: `can_start` liefert der Server mit. Die Knopfleiste benutzt dieses Flag, statt die
Regel nachzubauen — sonst driften Anzeige und tatsächliches Verhalten auseinander.

### Befehle

`start`, `pause`, `resume`, `end`, `reset`, `lock`, `unlock`, `kick`, `rename`,
`configure_map`, `preview_map`. Zugeordnet werden die Antworten über den **Befehlsnamen**
(`{"type":"ack","cmd":"start"}`) — das Protokoll kennt keine laufende Nummer, also führt
`ModerationChannel` eine Warteschlange in Absendereihenfolge.

---

## Grafiken

Im Einsatz ist das 64×64-Pixel-Art-Set mit 121 Sprites unter `assets/`. Die Dateien werden
zur **Laufzeit** gelesen — eine Grafik austauschen kostet keinen Build, nur einen Neustart.

Der Sprite-Ordner wird **gesucht**, nicht fest verdrahtet: der Viewer nimmt den ersten
Ordner unterhalb von `assets/`, der `floor_0.png` enthält. Fehlt ein Sprite, zeichnet er
einen eingefärbten Platzhalter.

| Element | Sprites |
|---|---|
| Boden | `floor_0`, `floor_1` |
| Feste Wand / Kiste | `wall_solid`, `crate` |
| Kiste zerfällt | `crate_break_0…3`, ausgelöst durch `block_destroyed` |
| Spielfigur | `player_{blue,red,yellow,purple}_{down,up,left,right}_{0…3}` |
| Bombe | `bomb_0…3`, nach `fuse` und `bomb_fuse_ticks` |
| Flamme | `expl_{center,arm_h,arm_v,tip_*}_{0…4}` |
| Power-up | `item_{bomb_up,fire_up,speed_up}_{0,1}` |

Drei Dinge, die der Renderer aus dem Set herausholt:

- **Die Laufanimation hängt an `move_progress`**, nicht an der Uhr — so bleibt sie an die
  Simulation gekoppelt und eine stehende Figur bleibt stehen.
- **Die Form des Feuerbalkens leitet `ExplosionShape` aus der Nachbarschaft ab.** Das
  `explosion`-Ereignis nennt zwar die Armlängen, gilt aber nur für den Tick der Detonation,
  während die Flamme ~30 Ticks brennt. Die Flammenliste bleibt auch dann richtig, wenn
  einzelne Zellen schon erlöschen.
- **Skaliert wird mit `NEAREST`** (`setImageSmoothing(false)`), sonst verwischt die
  Pixel-Art.

Der Server-Leitfaden beschreibt ein älteres 73-Frame-Set mit *einer* Spielfigur und rät,
die vier Spieler selbst einzufärben. Unser Set bringt vier fertige Farbvarianten mit — die
Farben in `Palette` sind exakt deren Anzugfarben, in derselben Reihenfolge wie die
Spieler-IDs 0–3. `item_kick` und `item_remote` bleiben ungenutzt: die Simulation kennt nur
drei Power-ups.

---

## Aufbau

```
viewer/
  ViewerLauncher    main(); erbt NICHT von Application (nötig fürs Fat-JAR)
  ViewerApp         verdrahtet Verbindung, Zustand und Oberfläche
  ViewerConfig      Startparameter

  net/              ServerConnection (java.net.http.WebSocket, Auto-Reconnect)
                    ServerMessageCodec — JSON → Modell
  control/          ModerationChannel, CommandType, CommandAvailability
  model/            unveränderliche Records, ServerMessage als sealed interface
  state/            ViewStateStore — die Übergabe Netz ↔ JavaFX, Board-Fortschreibung
  render/           Canvas-Zeichnen: Game, Lobby, Ergebnis, Debug
                    Sprites/Animations/ExplosionShape — reine Logik, ohne JavaFX
  ui/               Moderationsleiste: Adressfeld, Tabelle, Knöpfe, Protokoll
  tools/FakeServer  simulierter Server, spricht dasselbe Protokoll
  tools/MiniWebSocketServer  minimaler RFC-6455-Server, nur für den Fake-Server
  tools/SendCommand einzelner Moderationsbefehl von der Kommandozeile
```

Einzige Laufzeitabhängigkeit neben JavaFX ist **Jackson**; der WebSocket-Client kommt aus
dem JDK.

### Threading

Der WebSocket-Thread schreibt ausschließlich in eine `AtomicReference` im
`ViewStateStore`, der JavaFX-Thread liest sie im Bildtakt. Bewusst **kein
`Platform.runLater` je Nachricht** — dessen Warteschlange ist unbegrenzt, und bei 60
Zuständen je Sekunde läuft die Anzeige nach jedem Ruckler hinterher. `runLater` wird nur
für einzelne Ereignisse benutzt: Antwort auf einen Befehl, Wechsel des
Verbindungszustands.

Zwischen zwei Zuständen bewegen sich die Figuren weiter: `ViewStateStore.subTickProgress`
liefert den Bruchteil des laufenden Ticks. Ohne das ruckelt es auf jedem Schirm, der
schneller als 60 Hz zeichnet.

---

## Tests

```bash
mvn test
```

Vier Tests tragen die meiste Last:

- **`ServerMessageCodecTest`** liest die JSON-Beispiele, die wörtlich aus dem
  `VISUALIZER_GUIDE.md` des Servers stammen. Ändert sich das Protokoll, schlägt das hier
  an — und nicht erst die Anzeige auf dem Beamer.
- **`PlayerStateTest`** sichert die Rückwärts-Interpolation ab (Punkt 2 oben).
- **`ViewStateStoreTest`** sichert die Fortschreibung des Spielfelds ab (Punkt 1).
- **`ViewerIntegrationTest`** fährt den Fake-Server hoch und spielt die vollständige
  Moderationsstrecke über eine echte WebSocket-Verbindung durch.

Dazu prüft **`SpritesTest`** jeden Sprite-Namen, den der Renderer erzeugen kann, gegen die
vorliegenden Dateien — ein Tippfehler fiele sonst nicht auf, sondern ließe den Renderer
still auf den Platzhalter zurückfallen.
