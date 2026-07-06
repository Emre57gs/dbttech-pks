package de.htwberlin.dbtech.aufgaben.ue03;

import java.sql.Date;

public interface ISampleDao {
    Date findExpirationDateById(Integer sampleId);
}
