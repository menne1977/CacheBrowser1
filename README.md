# Cache Browser (Testprojekt)

Bauen: Ordner in Android Studio öffnen -> Build -> Build APK(s).
(Android Studio erzeugt den Gradle-Wrapper beim Öffnen selbst.)

Bedienung: URL eingeben (z. B. bandcamp.com), Titel abspielen, oben "Cache" tippen.
- LIVE-PUFFER: welcher Titel, Position, wie viele Sekunden/Prozent gepuffert (aktualisiert jede Sekunde)
- MEDIEN-ANFRAGEN: Audio-/Range-Anfragen der Seite (nur URLs)
- HTTP-CACHE: Einträge im WebView-Cache mit Größe und URL; ♪ = sieht nach Audio aus
Hinweis: Streams mit Range-Anfragen landen oft nicht im HTTP-Cache; der Live-Puffer ist der verlässliche Teil.
Es werden nur Metadaten angezeigt, keine Audiodateien gespeichert oder exportiert.
