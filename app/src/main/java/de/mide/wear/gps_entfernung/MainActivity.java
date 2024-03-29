package de.mide.wear.gps_entfernung;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;


/**
  * Activity einer WearOS-App, mit der man die Entfernung zwischen zwei Orten über
  * die GPS-Ortung berechnen kann.
  * <br><br>
  *
  * This project is licensed under the terms of the BSD 3-Clause License.
  */
public class MainActivity extends WearableActivity
                          implements View.OnClickListener, LocationListener {

    /** Tag für Log-Messages von dieser Activity. */
    private static final String TAG4LOGGING = "GPS-Entfernung";

    /** API-Level um zu erkennen, ob Runtime Permissions unterstützt werden. */
    public static final int API_LEVEL = android.os.Build.VERSION.SDK_INT;

    /** Key für geographische Länge in SharedPreference. */
    private static final String PREFKEY_GEOLAENGE = "geo_laenge";

    /** Key für geographische Breite in SharedPreference. */
    private static final String PREFKEY_GEOBREITE = "geo_breite";

    /** Dateiname für SharedPreferences-Datei zum Abspeichern der Koordinaten der letzten Ortung. */
    private static final String PREF_DATEINAME = "koordinaten_prefs";

    /** Formatierer für Entfernungen in (Kilo-)Meter, fügt ggf. 1.000er-Punkt ein. */
    private static DecimalFormat sZahlFormatierer = null;

    /** Preferences-Objekt zum Abspeichern der Koordinaten der letzten Ortung. */
    private SharedPreferences _sharedPreferences = null;

    /** Fortschrittsanzeige, wird während der Ortungsabfrage auf sichtbar geschaltet. */
    private ProgressBar _progressBar = null;


    /**
     * Lifecycle-Methode: Lädt Layout-Datei, holt Referenz auf Button & ProgressBar,
     * lädt auch SharedPreferences-Datei.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = findViewById(R.id.ortungsButton);
        button.setOnClickListener(this);

        _progressBar = findViewById(R.id.fortschrittsanzeige);

        _sharedPreferences = getSharedPreferences(PREF_DATEINAME, Context.MODE_PRIVATE );

        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        sZahlFormatierer = new DecimalFormat("###,###", dfs);

        Log.i(TAG4LOGGING, "API-Level=" + API_LEVEL);

        setAmbientEnabled(); // Enables Always-on
    }


    /**
     * Methode zum Speichern eines Ortungsobjekts mit einem SharedPreferences-Objekt;
     * wenn schon ein Ortungsobjekt gespeichert ist, dann wird es überschrieben.
     *
     * @param location  Ortungs-Objekt, das in SharedPreferences-Objekt gespeichert
     *                  werden soll; es werden geographische Länge und Breite
     *                  gespeichert.
     */
    protected void speicherLocation(Location location) {

        float breite = (float) location.getLatitude();
        float laenge = (float) location.getLongitude();

        SharedPreferences.Editor editor = _sharedPreferences.edit();
        editor.putFloat(PREFKEY_GEOBREITE, breite);
        editor.putFloat(PREFKEY_GEOLAENGE, laenge);
        editor.commit();

        Log.i(TAG4LOGGING, "Ortungs-Objekt gespeichert: " + location);
    }


    /**
     * Methode zum Auslesen des in den SharedPreferences gespeicherten Location-Objekts.
     *
     * @return  Aus SharedPreferences ausgelesener Location-Objekt; ist {@code null},
     *          wenn noch keine Koordinaten abgespeichert wurden!
     */
    protected Location holeLocation() {

        boolean geoBreiteGespeichert = _sharedPreferences.contains(PREFKEY_GEOBREITE);
        boolean geoLaengeGespeichert = _sharedPreferences.contains(PREFKEY_GEOLAENGE);

        if (!geoBreiteGespeichert || !geoLaengeGespeichert) {

            Log.i(TAG4LOGGING, "Noch keine Koordinate in SharedPrefs abgespeichert.");
            return null;
        }

        // Default-Wert wird für Aufruf Methode SharedPreferences::getFloat() als Argument
        // benötigt, aber da wir zuvor mit SharedPreferences::contains() geprüft haben,
        // dass die beiden Koordinatenwerte in den SharedPreferences vorhanden sind
        // wird dieser Wert nie tatsächlich zurückgegeben werden.
        final float defaultWert = 0.0f;

        double breite = _sharedPreferences.getFloat(PREFKEY_GEOBREITE, defaultWert);
        double laenge = _sharedPreferences.getFloat(PREFKEY_GEOLAENGE, defaultWert);

        Location ergebnisLocation = new Location("dummy");

        ergebnisLocation.setLatitude(breite);
        ergebnisLocation.setLongitude(laenge);

        return ergebnisLocation;
    }


     /**
      * Event-Handler-Methode für Button zur Abfrage der Ortung. Wenn die App auf einem
      * Gerät mit API-Level 23 oder größer läuft, dann
      *
      * @param view  Button-Objekt
      */
     @Override
     public void onClick(View view) {

         if (API_LEVEL < 23) {

             ortungAnfordern();

         } else {

             if ( checkSelfPermission( Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED ) {

                 // App hat schon die Permission, wir können also gleich die Ortung anfordern
                 ortungAnfordern();

             } else {

                 String[] permissionArray = { Manifest.permission.ACCESS_FINE_LOCATION };
                 requestPermissions( permissionArray, 1234 );
                 // Callback-Methode: onRequestPermissionsResult
             }
         }
     }


     /**
      * Callback-Methode für Erhalt Ergebnis der Berechtigungsanfrage.
      *
      * @param requestCode  Request-Code zur Unterscheidung verschiedener Requests.
      *
      * @param permissionsArray  Array angeforderter Runtime-Permissions.
      *
      * @param grantResultsArry  Ergebnis für angeforderte Runtime-Permissions.
      */
     @Override
     public void onRequestPermissionsResult(int      requestCode,
                                            String[] permissionsArray,
                                            int[]    grantResultsArry) {

         Log.i(TAG4LOGGING, "Anzahl Einträge in Permission-Array: " + permissionsArray.length);

         if (permissionsArray.length != 1) {

             zeigeDialog( "INTERNER FEHLER: Permission-Array hat nicht genau ein Element.", true );
             return;
         }


         Log.i(TAG4LOGGING, "Permission: " + permissionsArray[0]);

         if ( ! permissionsArray[0].equals(Manifest.permission.ACCESS_FINE_LOCATION) ) {

             zeigeDialog( "INTERNER FEHLER: Unerwartetes Element in Permission-Array.", true );
             return;
         }


         Log.i(TAG4LOGGING, "Grant-Result: " + grantResultsArry[0] );

         if ( grantResultsArry[0] == PackageManager.PERMISSION_GRANTED ) {

             ortungAnfordern();

         } else {

             zeigeDialog( "Berechtigung verweigert, kann Ortung nicht vornehmen.", true );
         }
     }


     /**
      * Methode startet Abfrage der aktuellen (GPS-)Ortung -- asynchron, Callback-Methode
      * ist {@link #onLocationChanged(Location)}.
      * <br><br>
      *
      * <b>Bevor diese Methode aufgerufen wird muss sichergestellt sein, dass die App die
      * Runtime-Berechtigung {@code android.permission.ACCESS_FINE_LOCATION} hat!</b>
      */
     @SuppressLint("MissingPermission")
     protected void ortungAnfordern() {

         LocationManager locationManager = null;

         locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
         if (locationManager == null) {

             zeigeDialog( "LocationManager nicht gefunden.", true );
             return;
         }

         locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
         // letztes Argument looper=null (hiermit kann Thread angegeben werden, in dem die Callback-
         // Methode onLocationChanged() ausgeführt werden soll.

         _progressBar.setVisibility( View.VISIBLE );

         Log.i(TAG4LOGGING, "Ortung angefordert.");
     }


     /**
      * Methode aus Interface {@link LocationListener}; wird aufgerufen, wenn neuer
      * Ortungswert vorliegt. Da wir nur eine einmalige Ortung angefordert haben
      * müssen wir die Ortung auch nicht "abschalten".
      *
      * @param location  Neue Ortung; wird in SharedPrefs gespeichert.
      */
     @Override
     public void onLocationChanged(Location location) {

         _progressBar.setVisibility( View.INVISIBLE );


         Location locationAlt = holeLocation();
         if (locationAlt == null) {

             zeigeDialog("Erste Ortung gespeichert.");

         } else {

             int entfernungMeter = (int) location.distanceTo(locationAlt);

             String entfernungStr = meter2string(entfernungMeter);

             zeigeDialog("Entfernung zu letzter Ortung: " + entfernungStr);
         }

         // Erste Ortung speichern oder bisherige Ortung überschreiben.
         speicherLocation(location);
     }


     /**
      * Methode zur Formatierung der gemessenen Entfernung zwischen zwei GPS-Koordinaten
      * für die Anzeige; bis einschl. 10.000m wird in Meter angezeigt, danach nur noch
      * die vollen Kilometer.
      *
      * @param entfernungMeter  Gemessene Entfernung in Metern die angezeigt werden soll.
      *
      * @return  String mit Entfernung in Meter oder Kilometern zur Anzeige, z.B.
      *          "1234m" oder "1234km".
      */
     protected String meter2string(int entfernungMeter) {

         String einheit   = "m";
         int    kilometer = -1;

         if (entfernungMeter > 10000) {

             kilometer = entfernungMeter / 1000;

             return sZahlFormatierer.format( kilometer ) + " km";

         } else {

             return sZahlFormatierer.format( entfernungMeter  ) + " m";
         }
     }


     /**
      * Methode aus Interface {@link LocationListener}.
      *
      * @param providerName  Name des Location-Providers, der einen neuen Status hat.
      *
      * @param status  Neuer Status
      *
      * @param bundle  Zusätzliche Infos als Key-Value-Paare.
      */
     @Override
     public void onStatusChanged(String providerName, int status, Bundle bundle) {

         // absichtlich leer gelassen
     }


     /**
      * Methode aus Interface {@link LocationListener}.
      *
      * @param providerName  Name des Location-Providers der eingeschaltet wurde.
      */
     @Override
     public void onProviderEnabled(String providerName) {

        // absichtlich leer gelassen
     }


     /**
      * Methode aus Interface {@link LocationListener}.
      *
      * @param providerName  Name des Location-Providers der abgeschaltet wurde.
      */
     @Override
     public void onProviderDisabled(String providerName) {

        // absichtlich leer gelassen
     }


     /**
      * Convenience-Methode um Fehlermeldung in Dialog anzuzeigen.
      *
      * @param nachricht  Anzuzeigende Nachricht, die keine Fehlermeldung ist.
      */
     protected void zeigeDialog(String nachricht) {

        zeigeDialog(nachricht, false); // false: kein Fehler
     }


    /**
     * Methode um Text in Dialog anzuzeigen (normaler Text oder Fehlermeldung).
     *
     * @param nachricht  Anzuzeigende Nachricht.
     *
     * @param istFehler  {@code true} gdw. die darzustellende Nachricht eine Fehlermeldung ist
     *                   (wird für Titel des Dialogs benötigt).
     */
    protected void zeigeDialog(String nachricht, boolean istFehler) {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        if (istFehler) {

            dialogBuilder.setTitle( "Fehler" );

        } else {

            dialogBuilder.setTitle( "Ergebnis" );
        }

        dialogBuilder.setMessage(nachricht);
        dialogBuilder.setPositiveButton( "Ok", null);

        AlertDialog dialog = dialogBuilder.create();
        dialog.show();
    }

}
