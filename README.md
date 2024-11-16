# Temi robot RoboGuide demo project

## Programm für den Temi Roboter zur interaktiven Führung durch das Computermuseum der FH-Kiel

## Programm ablauf:

Der User muss einen Place auf dem ersten screen auswählen. Die Places werden aus der Datenbank abgefragt.
Danach gibt es verschiedene möglichkeiten eine Tour zu Planen.
Auf dem Angezeigen Screen gibt es aus wahlmöglichkeiten für eine Ausführliche oder kurze Tour und eine Tour mit langen oder kurzen Texten.
Außerdem hat der User noch die Möglichkeit eine Individuelle Tour zu planen.
Wenn die gewünschte Tour ausgeählt wurde, kann auf Start gedrückt werden und die Tour wird automatisch ausgeführt. 
Während der Tour kann der User einzelene Austellungsstücke überspringen oder die Tour Paussieren.
Wenn die Führung beendet ist wird ein bewertungsscreen angezeigt, die Bewertungen werden dann local auf dem Temi gespeichter.
Wenn eine Beweertung abgegeben wurde, fährt der Temi zu der ersten Location zurück und es kann erneut eine Tour gestartet werden.

---


## Klassen
### Activities
### PlaceSelectionActivity
Hier werden alle Places von der DB abgefragt und von dem ausgewählten Place wird die ID weiter an die MainActivity gegeben damit dort alle locations für den ausgewählten place abgerufen werden kann

### MainActivity
Hier muss der User die art der Tour auswählen anhand welche tour ausgewählt wurde, wird eine andere Funktion im TourService ausgewählt

### IndividualGuide
Hier wird eine Liste mit allen Locations des Place angezeigt die ausgewählten Locations werden dann an den TourService gegeben

### ExcecutionActivity
Durch ein BroadcastListener werden der Text,das Bild/Video der Titel und der Progress der Tour empfangen und dargestellt
Falls neue Informationen geschickt werden werden die alten überschreiben und somit bleibt diese View für den ganzen Teil der Tour offen 

### Raiting Activity
Es können 0-5 Sterne für die Tour gegeben werden die Bewertung wird mit dem Format YYYY-MM-DD an der Location  /storage/emulated/0/Android/data/de.fhkiel.temi.robogguide/files/Documents/
gespeichert Über Shell zugriff auf dem Roboter können die Datein eingesehen werden

### PopUpError
Ein einfaches errorHandeling wo sich der User zwischen Erneut versuchen/ überspringen und Tour beenden entscheiden kann, falls eine Location nicht erreicht werden kann.

---

### TourService
Der Tourservice verwaltet den TourHelper er ist als Service im Android Manifest regestriert. 
Er dient dazu dass wir einen einhaltlichen zugriff auf den TourHelper haben

### TourHelper
Im TourHelper wird die Tour geplant und ausgeführt heir befidnen sich auch die Movent und Tts Listener
Er ordent außerdem die Tour in die richtiege Reihenfolge findet den Anfang der Tour heraus und dient generell als Logic des Programms
In der Route werden eine geordnete liste der anzufahrenden Locations gespeichert die Location werden mit der Location-ID dem Namen und der dazugehöriegen Transfer-ID gespeichert
Der TourHelper kümmert sich auch ums Error handeling hier wird falls ein error code beim MovmentListener recieved wird ein Error PopUp gestartet

### DatenbankHelper
Der DatenbankHelper ist zum größtenTeil gleichgeblieben es sind nur ein paar funktionen dazu gekommen die gezielt Texte und Bilder aus der Datenbank abfragen
Wichtig beim erstellen der DatanbankHelper Klasse muss der Name der aktuellen Datenbank übergeben werden damit diese richtig geöffnet werden kann  
