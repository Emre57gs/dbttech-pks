package de.htwberlin.dbtech.aufgaben.ue03;

import java.sql.Date;

public interface ITrayDao {
    TrayData findSuitableTray(Integer diameterInCM, Date sampleExpirationDate);

    TrayData findEmptyTray(Integer diameterInCM);

    void updateExpirationDate(Integer trayId, Date expirationDate);
}
