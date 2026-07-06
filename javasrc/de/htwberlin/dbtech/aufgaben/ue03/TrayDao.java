package de.htwberlin.dbtech.aufgaben.ue03;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.htwberlin.dbtech.exceptions.DataException;

public class TrayDao implements ITrayDao {
    private final Connection connection;

    public TrayDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public TrayData findSuitableTray(Integer diameterInCM, Date sampleExpirationDate) {
        String sql = "select t.TrayID, t.Capacity "
                + "from Tray t "
                + "where t.DiameterInCM = ? "
                + "and t.ExpirationDate > ? "
                + "and (select count(*) from Place p where p.TrayID = t.TrayID) < t.Capacity "
                + "order by t.ExpirationDate, t.TrayID";
        try (PreparedStatement pStmt = connection.prepareStatement(sql)) {
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

    @Override
    public TrayData findEmptyTray(Integer diameterInCM) {
        String sql = "select t.TrayID, t.Capacity "
                + "from Tray t "
                + "where t.DiameterInCM = ? "
                + "and not exists (select 1 from Place p where p.TrayID = t.TrayID) "
                + "order by t.TrayID";
        try (PreparedStatement pStmt = connection.prepareStatement(sql)) {
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

    @Override
    public void updateExpirationDate(Integer trayId, Date expirationDate) {
        String sql = "update Tray set ExpirationDate = ? where TrayID = ?";
        try (PreparedStatement pStmt = connection.prepareStatement(sql)) {
            pStmt.setDate(1, expirationDate);
            pStmt.setInt(2, trayId);
            pStmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }
}
