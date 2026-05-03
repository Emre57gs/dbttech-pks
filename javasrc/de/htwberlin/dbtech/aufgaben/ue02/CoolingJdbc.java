package de.htwberlin.dbtech.aufgaben.ue02;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.htwberlin.dbtech.exceptions.CoolingSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.htwberlin.dbtech.exceptions.DataException;

public class CoolingJdbc implements ICoolingJdbc {

    private static final Logger L = LoggerFactory.getLogger(CoolingJdbc.class);
    private Connection connection;

    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("unused")
    private Connection useConnection() {
        if (connection == null) {
            throw new DataException("Connection not set");
        }
        return connection;
    }
    // 20.04.26 in der Übung gemacht
    @Override
    public List<String> getSampleKinds() {
        L.info("getSampleKinds: start");
        // TODO Auto-generated method stub
        List<String> sampleKind = null;
        String sql = "select * from  samplekind order by text asc"; // SQL, kein Semikolon
        PreparedStatement p = null; // Connection
        ResultSet rs = null;

        try {
            sampleKind = new LinkedList<String>(); // Liste initialisiert
            p = useConnection().prepareStatement(sql);
            rs = p.executeQuery();

            while (rs.next()) {
                sampleKind.add(rs.getString("text"));
            }
        } catch (SQLException e){
            throw new DataException(e);

        }

        return sampleKind;
    }

    @Override
    public Sample findSampleById(Integer sampleId) {
        L.info("findSampleById: sampleId: " + sampleId);
        // TODO Auto-generated method stub
        String sql = "SELECT * FROM Sample WHERE SampleID = ?"; // ? Platzhalter für p
        PreparedStatement p = null;
        ResultSet rs = null;

        try {
            p = useConnection().prepareStatement(sql);
            p.setInt(1, sampleId);
            rs = p.executeQuery();
            // Test 1 baut Objekt
            if (rs.next()) {
                Sample s = new Sample();
                s.setSampleId(rs.getInt("SampleID"));
                s.setSampleKindId(rs.getInt("SampleKindID"));
                s.setExpirationDate(rs.getDate("ExpirationDate").toLocalDate());
                return s;
                // Test 2 wirft Exception
            } else {
                throw new CoolingSystemException("Sample not found");
            }

        } catch (SQLException e) {
            throw new CoolingSystemException(e);
        }
    }

    @Override
    public void createSample(Integer sampleId, Integer sampleKindId) {
        L.info("createSample: sampleId: " + sampleId + ", sampleKindId: " + sampleKindId);
        // TODO Auto-generated method stub
        // SQL-Statements vorbereiten
        String checkSampleSql = "SELECT * FROM Sample WHERE SampleID = ?";
        String checkKindSql = "SELECT * FROM SampleKind WHERE SampleKindID = ?";
        String insertSql = "INSERT INTO Sample (SampleID, SampleKindID, ExpirationDate) VALUES (?, ?, ?)";

        try {
            // 1. Prüfen: Existiert die SampleID schon?
            PreparedStatement p = useConnection().prepareStatement(checkSampleSql);

            // Setzt den Wert für das ? (Platzhalter)
            p.setInt(1, sampleId);

            ResultSet rs = p.executeQuery();

            // Wenn ein Datensatz gefunden wird → Sample existiert schon
            if (rs.next()) {
                throw new CoolingSystemException("Sample already exists");
            }

            // 2. Prüfen: Existiert die SampleKindID?
            p = useConnection().prepareStatement(checkKindSql);
            p.setInt(1, sampleKindId);
            rs = p.executeQuery();

            // Wenn KEIN Datensatz gefunden wird → SampleKind existiert nicht
            if (!rs.next()) {
                throw new CoolingSystemException("SampleKind does not exist");
            }

            // 3. ValidNoOfDays auslesen und Ablaufdatum berechnen
            // Beispiel: Blut → 4 Tage gültig
            int validNoOfDays = rs.getInt("ValidNoOfDays");
            // Ablaufdatum = heute + ValidNoOfDays
            LocalDate expirationDate = LocalDate.now().plusDays(validNoOfDays);

            // 4. Neuen Datensatz in Sample einfügen
            p = useConnection().prepareStatement(insertSql);
            // Werte in die INSERT-Anweisung einsetzen
            p.setInt(1, sampleId);                  // SampleID
            p.setInt(2, sampleKindId);              // SampleKindID
            p.setDate(3, java.sql.Date.valueOf(expirationDate)); // Datum umwandeln

            // INSERT ausführen
            p.executeUpdate();

        } catch (SQLException e) {
            // SQL-Fehler in unsere eigene Exception umwandeln
            throw new CoolingSystemException(e);
        }

    }

    @Override
    public void clearTray(Integer trayId) {
        L.info("clearTray: trayId: " + trayId);
        // TODO Auto-generated method stub
        String checkTraySql = "SELECT * FROM Tray WHERE TrayID = ?";
        String selectSamplesSql = "SELECT SampleID FROM Place WHERE TrayID = ?";
        String deletePlacesSql = "DELETE FROM Place WHERE TrayID = ?";
        String deleteSampleSql = "DELETE FROM Sample WHERE SampleID = ?";

        try {
            // Prüfen, ob das Tray existiert
            PreparedStatement p = useConnection().prepareStatement(checkTraySql);
            p.setInt(1, trayId);
            ResultSet rs = p.executeQuery();

            // Wenn kein Tray gefunden wurde → Fehler
            if (!rs.next()) {
                throw new CoolingSystemException("Tray does not exist");
            }

            // Alle SampleIDs holen, die auf diesem Tray liegen (über Place)
            p = useConnection().prepareStatement(selectSamplesSql);
            p.setInt(1, trayId);
            rs = p.executeQuery();

            // Liste speichern, weil wir sie nachher noch brauchen
            List<Integer> sampleIds = new ArrayList<Integer>();

            while (rs.next()) {
                sampleIds.add(rs.getInt("SampleID"));
            }

            // Zuerst alle Plätze des Tabletts löschen
            // (sonst verhindern Fremdschlüssel das Löschen der Samples)
            p = useConnection().prepareStatement(deletePlacesSql);
            p.setInt(1, trayId);
            p.executeUpdate();

            // Danach alle zugehörigen Samples löschen
            for (Integer sampleId : sampleIds) {
                p = useConnection().prepareStatement(deleteSampleSql);
                p.setInt(1, sampleId);
                p.executeUpdate();
            }

        } catch (SQLException e) {
            // SQL-Fehler in unsere eigene Exception umwandeln
            throw new CoolingSystemException(e);
        }

    }
    public boolean chickID (String table, Integer id) { // eventuell hilfsmethode bauen
        return true;
    }
}
