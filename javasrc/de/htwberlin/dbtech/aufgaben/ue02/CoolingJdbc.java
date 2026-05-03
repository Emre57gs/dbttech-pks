package de.htwberlin.dbtech.aufgaben.ue02;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    }

    @Override
    public void clearTray(Integer trayId) {
        L.info("clearTray: trayId: " + trayId);
        // TODO Auto-generated method stub

    }
    public boolean chickID (String table, Integer id) { // eventuell hilfsmethode bauen
        return true;
    }
}
