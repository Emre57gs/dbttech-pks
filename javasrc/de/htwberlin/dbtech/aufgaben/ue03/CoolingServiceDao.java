package de.htwberlin.dbtech.aufgaben.ue03;

import java.sql.Connection;
import java.sql.Date;

import de.htwberlin.dbtech.exceptions.CoolingSystemException;
import de.htwberlin.dbtech.exceptions.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoolingServiceDao implements ICoolingService {
    private static final Logger L = LoggerFactory.getLogger(CoolingServiceDao.class);
    private Connection connection;
    private ISampleDao sampleDao;
    private ITrayDao trayDao;
    private IPlaceDao placeDao;

    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
        this.sampleDao = new SampleDao(connection);
        this.trayDao = new TrayDao(connection);
        this.placeDao = new PlaceDao(connection);
    }

    private Connection useConnection() {
        if (connection == null) {
            throw new DataException("Connection not set");
        }
        return connection;
    }

    @Override
    public void transferSample(Integer sampleId, Integer diameterInCM) {
        L.info("transferSample: sampleId: " + sampleId + ", diameterInCM: " + diameterInCM);
        useConnection();

        // 1. Probe suchen. Wenn es sie nicht gibt, ist die Aufgabe nicht loesbar.
        Date sampleExpirationDate = sampleDao.findExpirationDateById(sampleId);
        if (sampleExpirationDate == null) {
            throw new CoolingSystemException();
        }

        // 2. Erst ein passendes Tablett mit freiem Platz suchen.
        TrayData tray = trayDao.findSuitableTray(diameterInCM, sampleExpirationDate);

        // 3. Wenn keines passt, ein leeres Tablett nehmen und Ablaufdatum setzen.
        if (tray == null) {
            tray = trayDao.findEmptyTray(diameterInCM);
            if (tray == null) {
                throw new CoolingSystemException();
            }
            Date trayExpirationDate = Date.valueOf(sampleExpirationDate.toLocalDate().plusDays(30));
            trayDao.updateExpirationDate(tray.getTrayId(), trayExpirationDate);
        }

        // 4. Kleinsten freien Platz finden und die Probe dort eintragen.
        Integer placeNo = placeDao.findSmallestFreePlaceNo(tray.getTrayId(), tray.getCapacity());
        if (placeNo == null) {
            throw new CoolingSystemException();
        }

        placeDao.insert(tray.getTrayId(), placeNo, sampleId);
    }
}
