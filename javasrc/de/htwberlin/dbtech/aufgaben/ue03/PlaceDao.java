package de.htwberlin.dbtech.aufgaben.ue03;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.htwberlin.dbtech.exceptions.DataException;

public class PlaceDao implements IPlaceDao {
    private final Connection connection;

    public PlaceDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Integer findSmallestFreePlaceNo(Integer trayId, Integer capacity) {
        boolean[] usedPlaces = new boolean[capacity + 1];
        String sql = "select PlaceNo from Place where TrayID = ?";
        try (PreparedStatement pStmt = connection.prepareStatement(sql)) {
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

    @Override
    public void insert(Integer trayId, Integer placeNo, Integer sampleId) {
        String sql = "insert into Place (TrayID, PlaceNo, SampleID) values (?, ?, ?)";
        try (PreparedStatement pStmt = connection.prepareStatement(sql)) {
            pStmt.setInt(1, trayId);
            pStmt.setInt(2, placeNo);
            pStmt.setInt(3, sampleId);
            pStmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }
}
