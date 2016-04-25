package com.auth.lewis.cloudclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import com.facebook.FacebookSdk;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceAuthenticationProvider;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;
import com.microsoft.windowsazure.mobileservices.*;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.MalformedURLException;
import java.util.List;

public class MainActivity extends Activity {

    //Check to see if the device is connected to the internet
    public boolean isConnectedToInternet(){
        ConnectivityManager internetConn = (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (internetConn != null)
        {
            NetworkInfo[] info = internetConn.getAllNetworkInfo();
            if (info != null) //If there is a connection present
                for (int i = 0; i < info.length; i++)
                    if (info[i].getState() == NetworkInfo.State.CONNECTED)
                    {
                        return true; //return the state of the connection
                    }
        }
        return false; //otherwise, return nothing
    }

    public static final String SHAREDPREFFILE = "temp";

    public static final String USERIDPREF = "uid";

    public static final String TOKENPREF = "tkn";

    public static String authToken;

    private String accessResult;

    public static String currentToken = "";

    //Create an array that will hold the parsed weather data (String values)
    ArrayList<String> items = new ArrayList<String>();

    // Create an object to connect to your mobile app service
    private MobileServiceClient mClient;

    // Create an object for  a table on your mobile app service
    private MobileServiceTable<ToDoItem> mToDoTable;

    // global variable to update a TextView control text
    TextView display;

    // simple stringbulder to store textual data retrieved from mobile app service table
    StringBuilder sb = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
            FacebookSdk.sdkInitialize(this.getApplicationContext());

        // Initialize the SDK before executing any other operations,
        // especially, if you're using Facebook UI elements.
            setContentView(R.layout.activity_main);

       try {

           // using the MobileServiceClient global object, create a reference to YOUR service
           mClient = new MobileServiceClient(
                   "https://lewismcservice.azurewebsites.net/.auth/me",
                   this
           );

       } catch (MalformedURLException e) {
            e.printStackTrace();
       }
        //check internet connection
        if (isConnectedToInternet()) {
            authenticate();
            createTable();
            AsyncTaskParseJson taskJson = new AsyncTaskParseJson();
            taskJson.execute("");
        }

        else {
            //otherwise, display a dialog box that can direct the user to the settings page or ignore the warning
            final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle(("Connection Failed"));
            alertDialog.setIcon(R.mipmap.ic_alert); //include an alert icon
            alertDialog.setMessage("Please go to settings to check your connection status");
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Settings", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
                    startActivity(intent);
                }
            });
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Ignore", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    alertDialog.hide();
                }
            });
            alertDialog.show();
        }
    }

    private void cacheUserToken(MobileServiceUser user)
    {
        SharedPreferences prefs = getSharedPreferences(SHAREDPREFFILE, Context.MODE_PRIVATE);
        Editor editor = prefs.edit();
        editor.putString(USERIDPREF, user.getUserId());
        editor.putString(TOKENPREF, user.getAuthenticationToken());
        editor.apply();
    }

    private boolean loadUserTokenCache(MobileServiceClient client)
    {
        SharedPreferences prefs = getSharedPreferences(SHAREDPREFFILE, Context.MODE_PRIVATE);
        String userId = prefs.getString(USERIDPREF, "undefined");
        if (userId == "undefined")
            return false;
        String token = prefs.getString(TOKENPREF, "undefined");
        if (token == "undefined")
            return false;

        MobileServiceUser user = new MobileServiceUser(userId);
        user.setAuthenticationToken(token);
        client.setCurrentUser(user);
        saveData(user.getAuthenticationToken().toString());

        return true;
    }

    private void createTable(){

       // display = (TextView) findViewById(R.id.displayData);

        // using the MobileServiceTable object created earlier, create a reference to YOUR table
        mToDoTable = mClient.getTable(ToDoItem.class);

    }

    private void authenticate() {
        // We first try to load a token cache if one exists.
        if (loadUserTokenCache(mClient))
        {
            createTable();
        }
        // If we failed to load a token cache, login and create a token cache
        else
        {
            // Login using the FB provider.
            ListenableFuture<MobileServiceUser> mLogin = mClient.login(MobileServiceAuthenticationProvider.Facebook);

            Futures.addCallback(mLogin, new FutureCallback<MobileServiceUser>() {
                @Override
                public void onFailure(Throwable exc) {
                    createAndShowDialog("You must log in. Login Required", "Error");
                }

                @Override
                public void onSuccess(MobileServiceUser user) {
                    createAndShowDialog(String.format(
                            "Authentication Token Stored - %1$2s",
                            user.getUserId() + user.getAuthenticationToken()), "Success!");
                    cacheUserToken(mClient.getCurrentUser());
                    authToken = user.getAuthenticationToken();
                    createTable();
                    saveData(user.getAuthenticationToken().toString());
                }
            });
        }
    }

    // method to add data to mobile service table
    public void saveData(String Token) {

        // Create a new data item from the text input
        final ToDoItem item = new ToDoItem();
        item.authToken = Token;

        // This is an async task to call the mobile service and insert the data
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    //
                    final ToDoItem entity = mToDoTable.insert(item).get();  //addItemInTable(item);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            // code inserted here can update UI elements, if required
                        }
                    });
                } catch (Exception exception) {

                }
                return null;
            }
        }.execute();
    }

    private void createAndShowDialog(String message, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage(message);
        builder.setTitle(title);
        builder.create().show();
    }

    // method to add data to mobile service table
    public void addData(View view) {

        // create reference to TextView input widgets
       // TextView data1 = (TextView) findViewById(R.id.insertText1);
        // the below textview widget isn't used (yet!)
        //TextView data2 = (TextView) findViewById(R.id.insertText2);

        // Create a new data item from the text input
        //final ToDoItem item = new ToDoItem();
       // item.text = data1.getText().toString();

        // This is an async task to call the mobile service and insert the data
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    //
                  //  final ToDoItem entity = mToDoTable.insert(item).get();  //addItemInTable(item);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                        // code inserted here can update UI elements, if required

                        }
                    });
                } catch (Exception exception) {

                }
                return null;
            }
        }.execute();
    }

    // method to view data from mobile service table
    public void viewData(View view) {

            display.setText("Loading...");

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        final List<ToDoItem> result = mToDoTable.select("id", "text").execute().get();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // get all data from column 'text' only add add to the stringbuilder
                                for (ToDoItem item : result) {
                                    sb.append(item.text + " ");
                                }

                                // display stringbuilder text using scrolling method
                                display.setText(sb.toString());
                                display.setMovementMethod(new ScrollingMovementMethod());
                                sb.setLength(0);
                            }
                        });
                    } catch (Exception exception) {
                    }
                    return null;
                }
            }.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // class used to work with ToDoItem table in mobile service, this needs to be edited if you wish to use with another table
    public class ToDoItem {
        private String id;
        private String text;
        private String authToken;
        private String firstName;
        private String lastname;
        private Date dateAuthenticated;
        private String appID;
        private static final String TAG = "MyActivity";
    }

    public class AsyncTaskParseJson extends AsyncTask<String, String, String> {

        ArrayList<String> items = new ArrayList<String>();

        @Override
        protected void onPreExecute() {
        }

        @Override
        // this method is used for...................
        protected String doInBackground(String... arg0) {

            try {
                //the URL of the weather web service is called - passing in the latitude and longitude variables (to get current location)
                String Urlstring = "https://lewismcservice.azurewebsites.net/.auth/me";
                httpConnect jParser = new httpConnect();
                URL url = new URL("https://lewismcservice.azurewebsites.net/.auth/me");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                //get json string from service url
                String json = jParser.getJSONFromUrl(MainActivity.this, Urlstring);
                urlConnection = (HttpURLConnection) url.openConnection();

                urlConnection.setRequestMethod("GET");
                urlConnection.setRequestProperty("X-ZUMO-AUTH", authToken);
                urlConnection.addRequestProperty("content-length", "0");
                urlConnection.setUseCaches(false);
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);
                urlConnection.connect();
                int status = urlConnection.getResponseCode();

                //set.
                urlConnection.connect();

                JSONArray topary = new JSONArray(json);
                JSONObject topobj = topary.getJSONObject(2);
                JSONArray innary = topobj.getJSONArray("user_claims");
                JSONObject innobj = innary.getJSONObject(1);
                Object name = innobj.get("val");
                //Log.v("Thingy: ", imageJsonStr + " ");

                //JSON object(s) 'object0' and 'objectCountry' created, sys object is now accessible
                //JSONArray Array0 = new JSONArray(json);
                //JSONObject string = new JSONObject();
                //JSONObject main0 = new JSONObject().getJSONArray("array").getJSONObject(0);

                //String accesstoken = main0.getString("user_id");

                //items.add(accesstoken);
                //currentToken = accesstoken;

                //returns name of sys object along with country - which is then appended as an item to the listView of the Weather Activity.
                //e.g. Lincoln, GB
                //items.add(object0.getString("name") + ", " + objectCountry.getString("country"));

                //Temperature retrieved 'main' object - Math.round function rounds the Kelvin value to whole number,
                // -273.15 is the value you subtract from Kelvin to achieve °C.
         /*       JSONObject object = new JSONObject(json).getJSONObject("main");
                items.add("Temperature: " + Math.round(object.getDouble(("temp")) - 273.15) + "°C");

                //JSON Array 'weather' is accessed, object inside the Array is then accessed {0} (set as int i = 0), string(s) from the object then retrieved 'main' and 'description'.
                JSONArray weather = (new JSONObject(json)).getJSONArray("weather");
                int h = 0;
                items.add("Type: " + (weather.getJSONObject(h).getString("main") + ", " + (weather.getJSONObject(h).getString("description"))));

                JSONObject object2 = new JSONObject(json).getJSONObject("main");
                items.add("Humidity: " + object2.getInt("humidity") + "%");

                JSONObject object3 = new JSONObject(json).getJSONObject("wind");
                items.add("Wind Speed: " + object3.getDouble("speed") + " mps"); */

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("ERRORR", e.toString());
            }
            return null;
        }

        //List View is created and parsed JSON data form web service is appended to a new item of the list
        @Override
        protected void onPostExecute(String strFromDoInBg) {
            ListView list = (ListView) findViewById(R.id.dataView);
            ArrayAdapter<String> facebookAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_expandable_list_item_1, items);
            list.setAdapter(facebookAdapter);
        }
    }
}
