package jamk.l3326.excercise1;

import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private String downloadDocument(String address) {
        String doc = null;
        HttpURLConnection conn = null;

        try {
            URL url = new URL(address);
            conn = (HttpURLConnection)url.openConnection();

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) builder.append(line).append("\n");
            reader.close();

            doc = builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (conn != null)
                conn.disconnect();
        }

        return doc;
    }

    private class FetchMapDataTask extends AsyncTask<String, Void, JSONObject> {
        private JSONArray pubs;

        @Override
        protected JSONObject doInBackground(String... urls) {
            JSONObject json = null;

            try {
                json = new JSONObject(downloadDocument(urls[0]));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                // Wait for the map to be ready
                while (mMap == null)
                    wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }

            return json;
        }

        private class Pub {
            LatLng pos;
            String title;

            Pub(JSONObject obj) {
                try {
                    pos = new LatLng(obj.getDouble("lat"), obj.getDouble("lng"));
                    title = obj.getString("title");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        private void addMarker(JSONObject pub) {
            Pub p = new Pub(pub);
            mMap.addMarker(new MarkerOptions().position(p.pos).title(p.title));
        }

        @Override
        protected void onPostExecute(JSONObject json) {
            try {
                pubs = json.getJSONArray("pubs");

                for (int i = 0; i < pubs.length(); i++)
                    addMarker(pubs.getJSONObject(i));

                if (pubs.length() > 0) {
                    Pub firstPub = new Pub(pubs.getJSONObject(0));
                    Pub lastPub = new Pub(pubs.getJSONObject(pubs.length() - 1));

                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPub.pos, 15));

                    FindRouteTask findRouteTask = new FindRouteTask();
                    findRouteTask.execute(new LatLng[]{firstPub.pos, lastPub.pos});
                }
            } catch (JSONException e) {
                Log.e("JSON", e.getMessage());
            }
        }
    }


    private class FindRouteTask extends AsyncTask<LatLng, Void, List<List<HashMap<String,String>>>> {
        @Override
        protected List<List<HashMap<String,String>>> doInBackground(LatLng... latLngs) {
            LatLng from = latLngs[0];
            LatLng to = latLngs[1];

            String str_from = "origin=" + from.latitude + "," + from.longitude;
            String str_to = "destination=" + to.latitude + "," + to.longitude;
            String params = str_from + "&" + str_to + "&sensor=fale&mode=driving";
            String url = "https://maps.googleapis.com/maps/api/directions/json?" + params;

            JSONObject json = null;
            List<List<HashMap<String,String>>> routes = null;

            try {
                json = new JSONObject(downloadDocument(url));
                routes = new DirectionsJSONParser().parse(json);
            } catch (Exception e) {
                Log.e("FindRouteTask", e.getMessage());
            }

            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList points = null;
            PolylineOptions lineOptions = null;

            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList();
                lineOptions = new PolylineOptions();

                List<HashMap<String, String>> path = result.get(i);

                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                lineOptions.addAll(points);
                lineOptions.width(12);
                lineOptions.color(R.color.colorAccent);
                lineOptions.geodesic(true);

            }

            mMap.addPolyline(lineOptions);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        FetchMapDataTask dataTask = new FetchMapDataTask();
        dataTask.execute("https://jamkstudent-my.sharepoint.com/personal/l3326_student_jamk_fi/_layouts/15/guestaccess.aspx?docid=1e1c192ec959d4e519cf0e9875e599260&authkey=AbToZ_-r2rkwvmHRgOOlKYw");
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public synchronized void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        notifyAll();
    }
}
