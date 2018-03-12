package com.example.maliy.cars;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Formatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.util.FloatMath;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements IBaseGpsListener, SensorEventListener {
    private LocationManager locationManager;
    private boolean startDriving;
    double G = 9.8; // 9.8m/s^2
    float[] history = new float[2];
    String[] direction = {"NONE", "NONE"};
    final SmsManager sms = SmsManager.getDefault();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SensorManager manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = manager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        //manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        manager.registerListener(this, accelerometer, 1000000);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        startDriving = false;
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            statusCheck(locationManager);
            this.updateSpeed(null);

            final Button button = (Button) findViewById(R.id.buttonLimit);
            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String limitSpeed = null;
                    try {
                        limitSpeed = req();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //limitSpeed = limitSpeed + " " + "KM/H";
                    Toast toast = Toast.makeText(getApplicationContext(), limitSpeed, Toast.LENGTH_SHORT);
                    toast.show();
                    //button.setText(limitSpeed + " " + "KM/H");

                }
            });

           Button recButton = findViewById(R.id.buttonRec);
            recButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(MainActivity.this, DataRecord.class);
                    //i.putExtra("userId", userId);
                    startActivity(i);
                }
            });

        }

    }
    public void statusCheck(LocationManager manager) {
        //final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();

        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void startDriving(Location location) {
        if (location != null && location.getSpeed() > 0) {

            Toast toast = new Toast(getApplicationContext());
            ImageView view = new ImageView(getApplicationContext());
            view.setImageResource(R.drawable.seatbelt);
            toast.setView(view);
            toast.show();
        }

    }

    //get the max speed by the current location, use streetmap api.
    private String req() throws IOException {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "no permission";
        }

        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null)
            return "null";

       /* double latitude = 31.266582;
        double longitude = 34.7824569;*/
       double latitude = location.getLatitude();
       double longitude = location.getLongitude();
        String res = "";
        double vicinityRange = 0.005;
        DecimalFormat format = new DecimalFormat("##0.0000000", DecimalFormatSymbols.getInstance(Locale.ENGLISH)); //$NON-NLS-1$
        String left = format.format(latitude - vicinityRange);
        String bottom = format.format(longitude - vicinityRange);
        String right = format.format(latitude + vicinityRange);
        String top = format.format(longitude + vicinityRange);

        String urlString = "http://www.overpass-api.de/api/xapi?*[maxspeed=*][bbox=" + left + "," + bottom + "," + right + "," + top + "]";
        URL url = new URL(urlString);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                final Pattern pattern = Pattern.compile("<way (.+?)</way>");
                final Matcher matcher = pattern.matcher(response);
                if (!matcher.find())
                    return "Not available";
                String m = matcher.group(1);
                final Pattern way = Pattern.compile("<tag k=\"maxspeed\" v=(.+?)/>");
                final Matcher matcher2 = way.matcher(m);
                if (!matcher2.find())
                    return "Not available";
                res = matcher2.group(1);
                //System.out.println("Response:" + m);
                //System.out.println( matcher2.group(1));

                in.close();
            } else {
                Toast toast = Toast.makeText(getApplicationContext(), "POST request not worked", Toast.LENGTH_SHORT);
                toast.show();
                //System.out.println("POST request not worked");

            }
        } finally {
            urlConnection.disconnect();
        }


        return res;
    }

    public void finish() {
        super.finish();
        System.exit(0);
    }

    private void updateSpeed(CLocation location) {
        // TODO Auto-generated method stub
        float nCurrentSpeed = 0;

        if (location != null) {
            location.setUseMetricunits(true);
            nCurrentSpeed = location.getSpeed();
        }

        Formatter fmt = new Formatter(new StringBuilder());
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed);
        String strCurrentSpeed = fmt.toString();
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0');

        String strUnits = "KM/H";

        Button speedView = this.findViewById(R.id.speedView);
        speedView.setText(strCurrentSpeed + " " + strUnits);

    }


    @Override
    public void onLocationChanged(Location location) {
        // TODO Auto-generated method stub
        if (location != null) {
            CLocation myLocation = new CLocation(location, true);
            this.updateSpeed(myLocation);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onGpsStatusChanged(int event) {
        // TODO Auto-generated method stub

    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if(!startDriving){
            startDriving(location);
            startDriving = true;
        }

        double latitude = 0;
        double longitude = 0;
        float speed = 0;

        //calculate G value
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float gX = x / 9.8f;
        float gY = y / 9.8f;
        float gZ = z / 9.8f;

        float gForce = (float)Math.sqrt(gX * gX + gY * gY + gZ * gZ);

        //calculate current speed
        if(location!= null ){
            speed = location.getSpeed();
            latitude = location.getLatitude();
            longitude = location.getLongitude();
        }

        //accident detection
        if(gForce > 4*G && speed > 24){
            Toast toast = Toast.makeText(getApplicationContext(), "Accident", Toast.LENGTH_SHORT);
            toast.show();

            //send message to emergency helper
            sms.sendTextMessage("1111", null, "Help! I've met with an accident at http://maps.google.com/?q="+String.valueOf(latitude)+","+String.valueOf(longitude), null, null);
            sms.sendTextMessage("1111", null, "Nearby Hospitals http://maps.google.com/maps?q=hospital&mrt=yp&sll="+String.valueOf(latitude)+","+String.valueOf(longitude)+"&output=kml", null, null);

            //voice alert
            try {
                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        //detect Deviation from the road
        String pos = Float.toString(event.values[0]);

        float xChange = history[0] - event.values[0];
        float yChange = history[1] - event.values[1];

        history[0] = event.values[0];
        history[1] = event.values[1];

        if (xChange > 2){
            direction[0] = "LEFT";/*
            Toast toast = new Toast(getApplicationContext());
            ImageView view = new ImageView(getApplicationContext());
            view.setImageResource(R.drawable.leftt);
            toast.setView(view);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.show();
            Toast toast = Toast.makeText(getApplicationContext(), direction[0], Toast.LENGTH_SHORT);
            toast.show();*/
        }
        else if (xChange < -2){
            direction[0] = "RIGHT";/*
            Toast toast = new Toast(getApplicationContext());
            ImageView view = new ImageView(getApplicationContext());
            view.setImageResource(R.drawable.right);
            toast.setView(view);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.show();
            Toast toast2 = Toast.makeText(getApplicationContext(), direction[0], Toast.LENGTH_SHORT);
            toast2.show();*/
        }



    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
