package it.ariaspa.mypay.mypaycore.api.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public enum LogMarker {
    //max 10 characters
    MONITORING("MON_GEN"),
    REST("MON_REST"),
    SOAP_SERVER("MON_WSS"),
    SOAP_CLIENT("MON_WSC"),
    METHOD("MON_METH"),
    DB_STATEMENT("MON_DBS"),
    DB_CONNECTION_POOL("MON_CONN");


    public final Marker marker;

    private LogMarker(String markerName) {
        this.marker = MarkerFactory.getMarker(markerName);
    }
}