package com.auth.lewis.cloudclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
import se.simbio.encryption.Encryption;


public class MainActivity extends Activity {

    //Check to see if the device is connected to the internet
    public boolean isConnectedToInternet() {
        ConnectivityManager internetConn = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (internetConn != null) {
            NetworkInfo[] info = internetConn.getAllNetworkInfo();
            if (info != null) //If there is a connection present
                for (int i = 0; i < info.length; i++)
                    if (info[i].getState() == NetworkInfo.State.CONNECTED) {
                        return true; //return the state of the connection
                    }
        }
        return false; //otherwise, return nothing
    }

    //public declarations of user pref, token etc.
    public static final String SHAREDPREFFILE = "temp";
    public static final String USERIDPREF = "uid";
    public static final String TOKENPREF = "tkn";
    public static String accessToken;
    public static String authToken;
    private String accessResult;

    //declaration of  encryption instance of random numbers for byte array
    public byte[] arr = {1,5,10,7,3,12,3,7,18,21,23,29,32,34,56,72};

    //declaration of simbio encryption algorithm library - set key and salt
    public se.simbio.encryption.Encryption encryption = se.simbio.encryption.Encryption.getDefault("L3w154NDR3WY4T35", "YourSalt", arr);


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

        ImageView image;
        // Initialize the SDK before executing any other operations,
        // especially, if you're using Facebook UI elements.
        setContentView(R.layout.activity_main);
        image = (ImageView) findViewById(R.id.profilePicture);

        //10154073223068446

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


        } else {
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

    private void cacheUserToken(MobileServiceUser user) {
        SharedPreferences prefs = getSharedPreferences(SHAREDPREFFILE, Context.MODE_PRIVATE);
        Editor editor = prefs.edit();
        editor.putString(USERIDPREF, user.getUserId());
        String enc = encryption.encryptOrNull(user.getAuthenticationToken()); //token is encrypted
        editor.putString(TOKENPREF, enc);
        editor.apply();
    }

    private boolean loadUserTokenCache(MobileServiceClient client) {
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
        authToken = encryption.decryptOrNull(user.getAuthenticationToken()); //token is decrypted
        return true;
    }

    private void createTable() {

        // display = (TextView) findViewById(R.id.displayData);

        // using the MobileServiceTable object created earlier, create a reference to YOUR table
        mToDoTable = mClient.getTable(ToDoItem.class);
        new AsyncTaskParseJson().execute();
    }

    //aunthenticate method to allow for facebook aunthentication to start
    private void authenticate() {
        // We first try to load a token cache if one exists.
        if (loadUserTokenCache(mClient)) {
            createTable();
        }
        // If we failed to load a token cache, login and create a token cache
        else {
            // Login using the FB provider.
            ListenableFuture<MobileServiceUser> mLogin = mClient.login(MobileServiceAuthenticationProvider.Facebook);

            Futures.addCallback(mLogin, new FutureCallback<MobileServiceUser>() {
                @Override
                public void onFailure(Throwable exc) {
                    createAndShowDialog("You must log in. Login Required", "Error");
                }

                @Override
                public void onSuccess(MobileServiceUser user) {
                    createAndShowDialog(
                            "You Have Been Logged In!",
                            "Success!");
                    cacheUserToken(mClient.getCurrentUser());
                    authToken = user.getAuthenticationToken(); //get authentication token
                    createTable();
                }
            });
        }
    }

    private class getDisplayPicture extends AsyncTask<String, Void, Bitmap> {
        ImageView image; // Declare a variable to store the Imageview

        public getDisplayPicture(ImageView bmImage) {
            this.image = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap Icon = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                Icon = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return Icon;
        }

        protected void onPostExecute(Bitmap result) {
            image.setImageBitmap(result);
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

        private static final String API = "https://lewismcservice.azurewebsites.net/.auth/me";

        private static final String API_RESULT = "";

        String yourServiceUrl = (API);

        @Override
        protected void onPreExecute() {
        }

        @Override
        // this method is used for...................
        protected String doInBackground(String... arg0) {

            try {
                getAzure jParser = new getAzure();
                accessResult = jParser.getJSONFromUrl(MainActivity.this, API, authToken);
                accessToken = accessResult;
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("ERRORR", e.toString());
            }
            return null;
        }

        //List View is created and parsed JSON data form web service is appended to a new item of the list
        @Override
        protected void onPostExecute(String strFromDoInBg) {
            new AsyncTaskParseJson2().execute();
        }
    }

    public class AsyncTaskParseJson2 extends AsyncTask<String, String, String> {

        private final String FB_API = ("https://graph.facebook.com/me?fields=name,gender,email&access_token=" + accessToken);
        String yourServiceUrl = (FB_API);
        JSONObject FBJSON = new JSONObject();

        @Override
        protected void onPreExecute() {
        }

        @Override
        // this method is used for...................
        protected String doInBackground(String... arg0) {

            try {
                getFacebook jParser = new getFacebook();

                FBJSON = jParser.getJSONFromUrl(MainActivity.this, yourServiceUrl, authToken);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("ERROR", e.toString());
            }
            return null;
        }

        //List View is created and parsed JSON data form web service is appended to a new item of the list
        @Override
        protected void onPostExecute(String strFromDoInBg) {
            try {
                String[] results = new String[3];
                results[0] = FBJSON.getString("name");
                results[1] = FBJSON.getString("email");
                results[2] = FBJSON.getString("gender");
                String USERID = FBJSON.getString("id");
                ImageView image = (ImageView) findViewById(R.id.profilePicture);
                new getDisplayPicture(image).execute("https://graph.facebook.com/" + USERID + "/picture?type=large");
                ArrayList<String> items = new ArrayList<String>(Arrays.asList(results));
                ListView list = (ListView) findViewById(R.id.dataView);
                ArrayAdapter<String> facebookAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_expandable_list_item_1, items);
                list.setAdapter(facebookAdapter);
            }catch (Exception e)
            {

            }
        }
    }

    public class getAzure
    {
        final String TAG = "JsonParser.java";
        String json = "";

        public String getJSONFromUrl(Context context, String url, String Auth_Token){
            try{
                URL u = new URL(url);

                HttpURLConnection restConnection = (HttpURLConnection) u.openConnection();

                //request data from azure
                restConnection.setRequestMethod("GET");
                restConnection.setRequestProperty("X-ZUMO-AUTH", Auth_Token);
                restConnection.addRequestProperty("content-length", "0");
                restConnection.setUseCaches(false);
                restConnection.setAllowUserInteraction(false);
                restConnection.setConnectTimeout(10000);
                restConnection.setReadTimeout(10000);
                restConnection.connect();

                int status = restConnection.getResponseCode();

                switch (status) {
                    case 200:
                    case 201:
                        // live connection to  REST service is established here using getInputStream() method
                        BufferedReader br = new BufferedReader(new InputStreamReader(restConnection.getInputStream()));

                        // create a new string builder to store json data returned from the REST service
                        StringBuilder sb = new StringBuilder();
                        String line = "";

                        // loop through returned data line by line and append to stringbuffer 'sb' variable
                        while ((line = br.readLine()) != null) {
                            sb.append(line + "\n");
                        }
                        br.close();
                        //JSON returned as a JSONObject
                        try {
                            json  = sb.toString();
                            json = json.substring(json.indexOf(":") + 1);
                            json = json.substring(0, json.indexOf(","));
                            json = json.replace("\"", "");
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing data " + e.toString());

                            return null;
                        }

                        return json;
                }
                // HTTP 200 and 201 error handling from switch statement
            } catch (MalformedURLException ex) {
                Log.e(TAG, "Malformed URL ");
            } catch (IOException ex) {
                Log.e(TAG, "IO Exception ");
            }
            return json = "";
        }
    }

    public class getFacebook {
        final String TAG = "JsonParser.java";
        String json = "";
        JSONObject dataFb = new JSONObject();

        public JSONObject getJSONFromUrl(Context context, String url, String Auth_Token){
            try{
                URL u = new URL(url);

                HttpURLConnection restConnection = (HttpURLConnection) u.openConnection();

                //request data from azure
                restConnection.setRequestMethod("GET");
                restConnection.setRequestProperty("X-ZUMO-AUTH", Auth_Token);
                restConnection.addRequestProperty("content-length", "0");
                restConnection.setUseCaches(false);
                restConnection.setAllowUserInteraction(false);
                restConnection.setConnectTimeout(10000);
                restConnection.setReadTimeout(10000);
                restConnection.connect();

                int status = restConnection.getResponseCode();

                switch (status) {
                    case 200:
                    case 201:
                        // live connection to  REST service is established here using getInputStream() method
                        BufferedReader br = new BufferedReader(new InputStreamReader(restConnection.getInputStream()));

                        // create a new string builder to store json data returned from the REST service
                        StringBuilder sb = new StringBuilder();
                        String line = "";

                        // loop through returned data line by line and append to stringbuffer 'sb' variable
                        while ((line = br.readLine()) != null) {
                            sb.append(line + "\n");
                        }
                        br.close();
                        //JSON returned as a JSONObject
                        try {
                           dataFb = new JSONObject(sb.toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing data " + e.toString());

                            return null;
                        }

                        return dataFb;
                }
                // HTTP 200 and 201 error handling from switch statement
            } catch (MalformedURLException ex) {
                Log.e(TAG, "Malformed URL ");
            } catch (IOException ex) {
                Log.e(TAG, "IO Exception ");
            }
            return dataFb;
        }
    }
}
