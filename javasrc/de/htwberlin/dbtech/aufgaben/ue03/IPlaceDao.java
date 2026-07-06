package de.htwberlin.dbtech.aufgaben.ue03;

public interface IPlaceDao {
    Integer findSmallestFreePlaceNo(Integer trayId, Integer capacity);

    void insert(Integer trayId, Integer placeNo, Integer sampleId);
}
