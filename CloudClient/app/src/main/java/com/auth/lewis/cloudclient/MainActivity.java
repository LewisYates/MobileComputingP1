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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.facebook.AccessToken;
import com.facebook.FacebookSdk;
import com.facebook.Profile;
import com.facebook.login.LoginManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceAuthenticationProvider;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;
import com.microsoft.windowsazure.mobileservices.*;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

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

    // Create an object to connect to your mobile app service
    private MobileServiceClient mClient;

    // Create an object for  a table on your mobile app service
    private MobileServiceTable<ToDoItem> mToDoTable;

    // global variable to update a TextView control text
    TextView display;

    // simple stringbulder to store textual data retrieved from mobile app service table
    StringBuilder sb = new StringBuilder();

    public void buttonOnClick(View view) {
        LoginManager.getInstance().logOut();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        FacebookSdk.sdkInitialize(getApplicationContext());
        // Initialize the SDK before executing any other operations,
        // especially, if you're using Facebook UI elements.
        setContentView(R.layout.activity_main);

       try {

           // using the MobileServiceClient global object, create a reference to YOUR service
           mClient = new MobileServiceClient(
                   "https://lewismcservice.azurewebsites.net",
                   this
           );

       } catch (MalformedURLException e) {
            e.printStackTrace();
       }
        //check internet connection
        if (isConnectedToInternet()) {

            authenticate();
            createTable();
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
        editor.commit();
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

        return true;
    }

    private void createTable(){

        display = (TextView) findViewById(R.id.displayData);

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
            // Login using the Google provider.
            ListenableFuture<MobileServiceUser> mLogin = mClient.login(MobileServiceAuthenticationProvider.Facebook);

            Futures.addCallback(mLogin, new FutureCallback<MobileServiceUser>() {
                @Override
                public void onFailure(Throwable exc) {
                    createAndShowDialog("You must log in. Login Required", "Error");
                }
                @Override
                public void onSuccess(MobileServiceUser user) {
                    createAndShowDialog(String.format(
                            "You are now logged in - %1$2s",
                            user.getUserId()), "Success");
                    cacheUserToken(mClient.getCurrentUser());
                    createTable();
                }
            });
        }
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
        TextView data1 = (TextView) findViewById(R.id.insertText1);
        // the below textview widget isn't used (yet!)
        TextView data2 = (TextView) findViewById(R.id.insertText2);

        // Create a new data item from the text input
        final ToDoItem item = new ToDoItem();
        item.text = data1.getText().toString();

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
    }




}
