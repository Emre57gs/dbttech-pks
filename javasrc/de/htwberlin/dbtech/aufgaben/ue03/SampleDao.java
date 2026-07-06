package de.htwberlin.dbtech.aufgaben.ue03;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.htwberlin.dbtech.exceptions.DataException;

public class SampleDao implements ISampleDao {
    private final Connection connection;

    public SampleDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Date findExpirationDateById(Integer sampleId) {
        String sql = "select ExpirationDate from Sample where SampleID = ?";
        try (PreparedStatement pStmt = connection.prepareStatement(sql)) {
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
}
