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

    @Override
    public void transferSample(Integer sampleId, Integer diameterInCM) {
        L.info("transferSample: sampleId: " + sampleId + ", diameterInCM: " + diameterInCM);

        // 1. Probe suchen, wenn es sie nicht gibt, ist die Aufgabe nicht lösbar
        Date sampleExpirationDate = findSampleExpirationDate(sampleId);
        if (sampleExpirationDate == null) {
            throw new CoolingSystemException();
        }

        // 2. Erst ein passendes, schon benutzbares Tablett suchen
        TrayData tray = findTrayForSample(diameterInCM, sampleExpirationDate);

        // 3. Wenn keines passt, ein leeres Tablett nehmen und Ablaufdatum setzen
        if (tray == null) {
            tray = findEmptyTray(diameterInCM);
            if (tray == null) {
                throw new CoolingSystemException();
            }
            updateTrayExpirationDate(tray.trayId, Date.valueOf(sampleExpirationDate.toLocalDate().plusDays(30)));
        }

        // 4. kleinsten freien Platz finden und Probe dort eintragen
        Integer placeNo = findFreePlaceNo(tray.trayId, tray.capacity);
        if (placeNo == null) {
            throw new CoolingSystemException();
        }

        insertPlace(tray.trayId, placeNo, sampleId);
    }

    private Date findSampleExpirationDate(Integer sampleId) {
        // null als Rückgabe bedeutet: "Probe existiert nicht"
        String sql = "select ExpirationDate from Sample where SampleID = ?";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            pStmt.setInt(1, sampleId);
            try (ResultSet rs = pStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDate("ExpirationDate");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    private TrayData findTrayForSample(Integer diameterInCM, Date sampleExpirationDate) {
        // Passend heisst: Durchmesser passt, Ablaufdatum ist später, Kapazität ist frei
        String sql = "select t.TrayID, t.Capacity "
                + "from Tray t "
                + "where t.DiameterInCM = ? "
                + "and t.ExpirationDate > ? "
                + "and (select count(*) from Place p where p.TrayID = t.TrayID) < t.Capacity "
                + "order by t.ExpirationDate, t.TrayID";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            pStmt.setInt(1, diameterInCM);
            pStmt.setDate(2, sampleExpirationDate);
            try (ResultSet rs = pStmt.executeQuery()) {
                if (rs.next()) {
                    return new TrayData(rs.getInt("TrayID"), rs.getInt("Capacity"));
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    private TrayData findEmptyTray(Integer diameterInCM) {
        // Leeres Tablett: passender Durchmesser und kein Eintrag in Place
        String sql = "select t.TrayID, t.Capacity "
                + "from Tray t "
                + "where t.DiameterInCM = ? "
                + "and not exists (select 1 from Place p where p.TrayID = t.TrayID) "
                + "order by t.TrayID";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            pStmt.setInt(1, diameterInCM);
            try (ResultSet rs = pStmt.executeQuery()) {
                if (rs.next()) {
                    return new TrayData(rs.getInt("TrayID"), rs.getInt("Capacity"));
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    private Integer findFreePlaceNo(Integer trayId, Integer capacity) {
        // Erst belegte Plätze merken, danach von 1 bis capacity die erste Lücke nehmen.
        boolean[] usedPlaces = new boolean[capacity + 1];
        String sql = "select PlaceNo from Place where TrayID = ?";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            pStmt.setInt(1, trayId);
            try (ResultSet rs = pStmt.executeQuery()) {
                while (rs.next()) {
                    int placeNo = rs.getInt("PlaceNo");
                    if (placeNo >= 1 && placeNo <= capacity) {
                        usedPlaces[placeNo] = true;
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        for (int placeNo = 1; placeNo <= capacity; placeNo++) {
            if (!usedPlaces[placeNo]) {
                return placeNo;
            }
        }
        return null;
    }

    private void updateTrayExpirationDate(Integer trayId, Date expirationDate) {
        String sql = "update Tray set ExpirationDate = ? where TrayID = ?";
        try (PreparedStatement pStmt = useConnection().prepareStatement(sql)) {
            pStmt.setDate(1, expirationDate);
            pStmt.setInt(2, trayId);
            pStmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

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
    // Hilfsklasse + Hilfsmethode
    private static class TrayData {
        private final Integer trayId;
        private final Integer capacity;

        private TrayData(Integer trayId, Integer capacity) {
            this.trayId = trayId;
            this.capacity = capacity;
        }
    }

}
