package de.htwberlin.dbtech.aufgaben.ue03;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.htwberlin.dbtech.exceptions.CoolingSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.htwberlin.dbtech.exceptions.DataException;

public class CoolingService implements ICoolingService {
    private static final Logger L = LoggerFactory.getLogger(CoolingService.class);

    // Diese Verbindung wird von den Tests gesetzt und für alle sql Befehle benutzt
    private Connection connection;

    /**
     * Speichert die Datenbankverbindung in der Klasse
     * ohne diese Verbindung können die späteren Methoden keine sql Abfragen ausführen.
     */
    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    /**
     * Gibt die gespeicherte Datenbankverbindung zurück.
     * Falls vorher keine Verbindung gesetzt wurde, ist das ein technischer Fehler.
     */
    @SuppressWarnings("unused")
    private Connection useConnection() {
        if (connection == null) {
            throw new DataException("Connection not set");
        }
        return connection;
    }

    /**
     * Hauptmethode der Aufgabe
     *
     * Ziel: Die Probe mit sampleId soll auf ein passendes Tablett gelegt werden
     * der Parameter diameterInCM (Probenröhrchen Durchmesser) kommt laut Aufgabe vom Sensor und bestimmt,
     * welcher Tablett-Durchmesser erlaubt ist
     */
    @Override
    public void transferSample(Integer sampleId, Integer diameterInCM) {
        L.info("transferSample: sampleId: " + sampleId + ", diameterInCM: " + diameterInCM);

        // Schritt 1: Ablaufdatum der Probe suchen
        // wenn null zurückkommt, gibt es keine Probe mit dieser sampleId
        Date sampleExpirationDate = findSampleExpirationDate(sampleId);
        if (sampleExpirationDate == null) {
            // Fachlicher Fehler: Die Einlagerung ist nicht möglich.
            throw new CoolingSystemException();
        }

        // Schritt 2: Erst versuchen, ein bereits verwendbares Tablett zu finden
        // Verwendbar heißt: Durchmesser passt, Ablaufdatum ist später als bei der Probe,
        // und es ist noch mindestens ein Platz frei
        TrayData tray = findTrayForSample(diameterInCM, sampleExpirationDate);

        // Schritt 3: Wenn kein passendes Tablett gefunden wurde, ein leeres Tablett nehmen
        // das ist laut Interface der Ersatzfall
        if (tray == null) {
            tray = findEmptyTray(diameterInCM);
            if (tray == null) {
                // Es gibt weder ein passendes benutztes noch ein leeres passendes Tablett
                throw new CoolingSystemException();
            }

            // Bei einem leeren Tablett wird das Ablaufdatum neu gesetzt:
            // Ablaufdatum der Probe plus 30 Tage
            updateTrayExpirationDate(tray.trayId, Date.valueOf(sampleExpirationDate.toLocalDate().plusDays(30)));
        }

        // Schritt 4: Auf dem ausgewählten Tablett den kleinsten freien Platz finden
        // dadurch werden Lücken zuerst aufgefüllt
        Integer placeNo = findFreePlaceNo(tray.trayId, tray.capacity);
        if (placeNo == null) {
            // Sollte normalerweise nicht passieren, weil vorher auf freie Kapazität geprüft wird
            throw new CoolingSystemException();
        }

        // Schritt 5: Probe einlagern
        // Technisch bedeutet das: neuer Datensatz in der Tabelle Place
        insertPlace(tray.trayId, placeNo, sampleId);
    }

    /**
     * Sucht das Ablaufdatum einer Probe.
     *
     * Rückgabe:
     * - Datum, wenn die Probe existiert.
     * - null, wenn keine Probe mit dieser sampleId gefunden wurde.
     */
    private Date findSampleExpirationDate(Integer sampleId) {
        // Es wird nur das Ablaufdatum gebraucht, deshalb wird auch nur diese Spalte gelesen
        String sql = "select ExpirationDate from Sample where SampleID = ?";

        // try-with-resources schliesst PreparedStatement und ResultSet automatisch
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            // Das erste Fragezeichen im SQL wird durch die sampleId ersetzt
            pStmt.setInt(1, sampleId);
            try (ResultSet rs = pStmt.executeQuery()) {
                // rs.next() ist true, wenn die Datenbank mindestens eine passende Zeile gefunden hat
                if (rs.next()) {
                    return rs.getDate("ExpirationDate");
                }

                // Keine Zeile gefunden: Probe existiert nicht
                return null;
            }
        } catch (SQLException e) {
            // SQLException ist ein technischer Datenbankfehler und wird als DataException weitergegeben
            throw new DataException(e);
        }
    }

    /**
     * Sucht ein bereits nutzbares Tablett für die Probe
     *
     * Ein Tablett ist passend, wenn:
     * 1. der Durchmesser passt,
     * 2. das Tablett-Ablaufdatum größer ist als das Probe-Ablaufdatum,
     * 3. das Tablett noch nicht voll ist
     */
    private TrayData findTrayForSample(Integer diameterInCM, Date sampleExpirationDate) {
        // Die Sortierung ist wichtig: Das Tablett mit dem kleinsten passenden Ablaufdatum kommt zuerst
        String sql = "select t.TrayID, t.Capacity "
                + "from Tray t "
                + "where t.DiameterInCM = ? "
                + "and t.ExpirationDate > ? "
                + "and (select count(*) from Place p where p.TrayID = t.TrayID) < t.Capacity "
                + "order by t.ExpirationDate, t.TrayID";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            // ? Nummer 1: passender Durchmesser aus dem Sensorwert
            pStmt.setInt(1, diameterInCM);

            // ? Nummer 2: Ablaufdatum der Probe; das Tablett muss später ablaufen
            pStmt.setDate(2, sampleExpirationDate);
            try (ResultSet rs = pStmt.executeQuery()) {
                // Wegen order by ist die erste gefundene Zeile direkt das beste Tablett
                if (rs.next()) {
                    return new TrayData(rs.getInt("TrayID"), rs.getInt("Capacity"));
                }

                // Kein vorhandenes Tablett erfüllt alle Bedingungen
                return null;
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /**
     * Sucht ein komplett leeres Tablett mit passendem Durchmesser
     *
     * Diese Methode wird nur aufgerufen, wenn kein bereits nutzbares Tablett gefunden wurde
     * Leer bedeutet hier: In der Tabelle Place gibt es noch keinen Eintrag für dieses Tablett
     */
    private TrayData findEmptyTray(Integer diameterInCM) {
        String sql = "select t.TrayID, t.Capacity "
                + "from Tray t "
                + "where t.DiameterInCM = ? "
                + "and not exists (select 1 from Place p where p.TrayID = t.TrayID) "
                + "order by t.TrayID";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            pStmt.setInt(1, diameterInCM);
            try (ResultSet rs = pStmt.executeQuery()) {
                // Das erste leere Tablett mit passendem Durchmesser wird verwendet
                if (rs.next()) {
                    return new TrayData(rs.getInt("TrayID"), rs.getInt("Capacity"));
                }

                // Kein leeres Tablett mit passendem Durchmesser vorhanden
                return null;
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /**
     * Ermittelt die kleinste freie Platznummer auf einem Tablett
     *
     * Beispiel: Wenn Platz 1 und 3 belegt sind, wird Platz 2 zurückgegeben
     * genau dadurch werden Lücken zuerst gefüllt
     */
    private Integer findFreePlaceNo(Integer trayId, Integer capacity) {
        // Index 0 wird nicht benutzt, weil PlaceNo bei 1 beginnt
        boolean[] usedPlaces = new boolean[capacity + 1];

        // Alle belegten Plätze dieses Tabletts aus der Datenbank lesen
        String sql = "select PlaceNo from Place where TrayID = ?";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            pStmt.setInt(1, trayId);
            try (ResultSet rs = pStmt.executeQuery()) {
                while (rs.next()) {
                    int placeNo = rs.getInt("PlaceNo");

                    // Nur gültige Platznummern innerhalb der Kapazität markieren
                    if (placeNo >= 1 && placeNo <= capacity) {
                        usedPlaces[placeNo] = true;
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        // Von vorne suchen, damit der kleinste freie Platz genommen wird
        for (int placeNo = 1; placeNo <= capacity; placeNo++) {
            if (!usedPlaces[placeNo]) {
                return placeNo;
            }
        }

        // Kein freier Platz vorhanden
        return null;
    }

    /**
     * Setzt das Ablaufdatum eines Tabletts
     *
     * Das wird nur im Ersatzfall gebraucht, wenn ein leeres Tablett genommen wurde
     */
    private void updateTrayExpirationDate(Integer trayId, Date expirationDate) {
        String sql = "update Tray set ExpirationDate = ? where TrayID = ?";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            // Neues Ablaufdatum eintragen
            pStmt.setDate(1, expirationDate);

            // Bestimmen, welches Tablett geändert wird
            pStmt.setInt(2, trayId);
            pStmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /**
     * Lagert die Probe technisch ein
     *
     * Dafür wird ein Datensatz in Place erzeugt:
     * auf welchem Tablett, auf welchem Platz, welche Probe
     */
    private void insertPlace(Integer trayId, Integer placeNo, Integer sampleId) {
        String sql = "insert into Place (TrayID, PlaceNo, SampleID) values (?, ?, ?)";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            pStmt.setInt(1, trayId);
            pStmt.setInt(2, placeNo);
            pStmt.setInt(3, sampleId);
            pStmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /**
     * Kleine Hilfsklasse, um TrayID und Capacity gemeinsam zurückzugeben
     *
     * Begründung Java-Methoden können nur einen Wert zurückgeben
     * deshalb werden die zwei
     * zusammengehörenden Werte in dieses Objekt gepackt
     */
    private static class TrayData {
        private final Integer trayId;
        private final Integer capacity;

        private TrayData(Integer trayId, Integer capacity) {
            this.trayId = trayId;
            this.capacity = capacity;
        }
    }

}
