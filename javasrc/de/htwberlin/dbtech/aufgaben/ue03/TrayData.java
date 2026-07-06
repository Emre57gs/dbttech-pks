package de.htwberlin.dbtech.aufgaben.ue03;

public class TrayData {
    private final Integer trayId;
    private final Integer capacity;

    public TrayData(Integer trayId, Integer capacity) {
        this.trayId = trayId;
        this.capacity = capacity;
    }

    public Integer getTrayId() {
        return trayId;
    }

    public Integer getCapacity() {
        return capacity;
    }
}
