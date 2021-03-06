package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2019 by Marcel Bokhorst (M66B)
*/

import android.app.AlertDialog;
import android.Manifest;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
//import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.acl.Owner;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import static android.provider.ContactsContract.CommonDataKinds.Website.URL;


public class FragmentSetup extends FragmentBase {
    private ViewGroup view;

    private Button btnQuick;

    private TextView tvAccountDone;
    private Button btnAccount;
    private TextView tvNoPrimaryDrafts;

    private TextView tvIdentityDone;
    private Button btnIdentity;

    private TextView tvPermissionsDone;
    private Button btnPermissions;

    private TextView tvDozeDone;
    private Button btnDoze;

    private Button btnData;

    private Button btnOptions;
    private Button btnInbox;

    private int textColorPrimary;
    private int colorWarning;
    private Drawable check;

    private String sURL_Password = "http://guestmsg.tgho.net:8080/api/v1/guestMsg/pcheck/";//"http://jsonplaceholder.typicode.com/posts/1";

    private static final String[] permissions = new String[]{
            Manifest.permission.READ_CONTACTS
    };

    public void checkPassword(final Context context, final String eventName, final String pwd) {
        // Instantiate the cache
        Cache cache = new DiskBasedCache(getActivity().getCacheDir(), 1024 * 1024); // 1MB cap
        // Set up the network to use HttpURLConnection as the HTTP client.
        Network network = new BasicNetwork(new HurlStack());
        RequestQueue rqInstance = new RequestQueue(cache, network);
        rqInstance.start();

        HashMap<String, String> params = new HashMap<String, String>();
        params.put("pcheck", pwd);
        JSONObject jsoPost = new JSONObject(params);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, sURL_Password, jsoPost, new Response.Listener<JSONObject>() {
             @Override
             public void onResponse(JSONObject response) {
                 Boolean bPwdCorrect = false;
                 String sStatus = "Unknown";
                 try{
                     bPwdCorrect = response.getBoolean("success");
                     sStatus = response.getString("msg");
                 }catch (JSONException ex){
                     bPwdCorrect = false;
                 }
                 if (!bPwdCorrect) {
                     Toast.makeText(context, sStatus, Toast.LENGTH_LONG).show();
                     return;
                 }
                 loadUI(eventName);
             }
         }, new Response.ErrorListener() {
             @Override
             public void onErrorResponse(VolleyError error) {
                 String sErrMsg = "Error (C): ";
                 AlertDialog.Builder odl = new AlertDialog.Builder(context);
                 odl.setTitle("Error (C)!");
                 if (error.networkResponse == null){
                     sErrMsg += " Unknown (No Resp.)";
                 } else {
                    sErrMsg += Integer.toString(error.networkResponse.statusCode);
                 }
                 odl.setMessage(sErrMsg );
                 odl.setPositiveButton(android.R.string.ok,null);
                 AlertDialog ad1 = odl.create();
                 ad1.show();
             }
         });
        rqInstance.add(request);
    };

    public void getPassword(Context context, final String eventName) {
        View dview = LayoutInflater.from(context).inflate(R.layout.dialog_password, null);
        final TextInputLayout etPassword1 = dview.findViewById(R.id.tilPassword1);
        final TextInputLayout etPassword2 = dview.findViewById(R.id.tilPassword2);
        TextView tvImportHint = dview.findViewById(R.id.tvImporthint);

        etPassword2.setVisibility(View.GONE);
        tvImportHint.setVisibility(View.GONE);

        AlertDialog.Builder odl = new AlertDialog.Builder(context);
        odl.setView(dview);
        odl.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String sSubmittedPwd = etPassword1.getEditText().getText().toString();
                //String password2 = etPassword2.getEditText().getText().toString();

                if (!BuildConfig.DEBUG && TextUtils.isEmpty(sSubmittedPwd))
                    Snackbar.make(view, R.string.title_setup_password_missing, Snackbar.LENGTH_LONG).show();
                else {
                    Dialog diaCurrent  = (Dialog) dialog;
                    checkPassword(diaCurrent.getContext(), eventName, sSubmittedPwd);
                }
            }

            ;
        });
        AlertDialog ad1 = odl.create();
        ad1.show();
    }

    ;


    public void loadUI(String eventName) {
        FragmentTransaction fragmentTransaction;
        switch (eventName) {
            case "quick":
                fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, new FragmentQuickSetup()).addToBackStack(eventName);
                fragmentTransaction.commit();
            case "accounts":
                fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, new FragmentAccounts()).addToBackStack(eventName);
                fragmentTransaction.commit();
                break;
            case "identities":
                fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, new FragmentIdentities()).addToBackStack(eventName);
                fragmentTransaction.commit();
                break;
            case "options":
                fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, new FragmentOptions()).addToBackStack(eventName);
                fragmentTransaction.commit();
                break;
            case "permissions":
                btnPermissions.setEnabled(false);
                requestPermissions(permissions, ActivitySetup.REQUEST_PERMISSION);
                break;
            case "battery":
                new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                        .setMessage(R.string.title_setup_doze_instructions)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                                } catch (Throwable ex) {
                                    Log.e(ex);
                                }
                            }
                        })
                        .create()
                        .show();
                break;
            case "data":
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                            Uri.parse("package:" + BuildConfig.APPLICATION_ID)));
                } catch (Throwable ex) {
                    Log.e(ex);
                }
                break;
            default:
                return;
        }
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setSubtitle(R.string.title_setup);
        setHasOptionsMenu(true);

        textColorPrimary = Helper.resolveColor(getContext(), android.R.attr.textColorPrimary);
        colorWarning = Helper.resolveColor(getContext(), R.attr.colorWarning);
        check = getResources().getDrawable(R.drawable.baseline_check_24, getContext().getTheme());

        view = (ViewGroup) inflater.inflate(R.layout.fragment_setup, container, false);

        // Get controls
        btnQuick = view.findViewById(R.id.btnQuick);

        tvAccountDone = view.findViewById(R.id.tvAccountDone);
        btnAccount = view.findViewById(R.id.btnAccount);
        tvNoPrimaryDrafts = view.findViewById(R.id.tvNoPrimaryDrafts);

        tvIdentityDone = view.findViewById(R.id.tvIdentityDone);
        btnIdentity = view.findViewById(R.id.btnIdentity);

        tvPermissionsDone = view.findViewById(R.id.tvPermissionsDone);
        btnPermissions = view.findViewById(R.id.btnPermissions);

        tvDozeDone = view.findViewById(R.id.tvDozeDone);
        btnDoze = view.findViewById(R.id.btnDoze);

        btnData = view.findViewById(R.id.btnData);

        btnOptions = view.findViewById(R.id.btnOptions);
        btnInbox = view.findViewById(R.id.btnInbox);


        // Wire controls

        btnQuick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context thisContext = btnQuick.getContext();
                getPassword(thisContext, "quick");
                // Moved to askPassword
                //FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                //fragmentTransaction.replace(R.id.content_frame, new FragmentQuickSetup()).addToBackStack("quick");
                //fragmentTransaction.commit();
            }
        });

        btnAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPassword(view.getContext(), "accounts");
                // Moved to askPassword
                //FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                //fragmentTransaction.replace(R.id.content_frame, new FragmentAccounts()).addToBackStack("accounts");
                //fragmentTransaction.commit();
            }
        });

        btnIdentity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPassword(view.getContext(), "identities");
                // Moved to askPassword
//                        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
//                        fragmentTransaction.replace(R.id.content_frame, new FragmentIdentities()).addToBackStack("identities");
//                        fragmentTransaction.commit();
            }
        });

        btnPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPassword(view.getContext(), "permissions");
                // Moved to askPassword
//                btnPermissions.setEnabled(false);
//                requestPermissions(permissions, ActivitySetup.REQUEST_PERMISSION);
            }
        });

        btnDoze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPassword(view.getContext(), "battery");
                // Moved to askPassword
//                new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
//                        .setMessage(R.string.title_setup_doze_instructions)
//                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                try {
//                                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
//                                } catch (Throwable ex) {
//                                    Log.e(ex);
//                                }
//                            }
//                        })
//                        .create()
//                        .show();
            }
        });

        btnData.setOnClickListener(new View.OnClickListener() {
            @Override
            @TargetApi(Build.VERSION_CODES.N)
            public void onClick(View v) {
                getPassword(view.getContext(), "data");
                // Moved to askPassword
//                try {
//                    startActivity(new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
//                            Uri.parse("package:" + BuildConfig.APPLICATION_ID)));
//                } catch (Throwable ex) {
//                    Log.e(ex);
//                }
            }
        });

        btnOptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPassword(view.getContext(), "options");
                // Moved to askPassword
//                FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
//                fragmentTransaction.replace(R.id.content_frame, new FragmentOptions()).addToBackStack("options");
//                fragmentTransaction.commit();
            }
        });

        btnInbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Initialize
        tvAccountDone.setText(null);
        tvAccountDone.setCompoundDrawables(null, null, null, null);
        tvNoPrimaryDrafts.setVisibility(View.GONE);

        tvIdentityDone.setText(null);
        tvIdentityDone.setCompoundDrawables(null, null, null, null);
        btnIdentity.setEnabled(false);

        tvPermissionsDone.setText(null);
        tvPermissionsDone.setCompoundDrawables(null, null, null, null);

        tvDozeDone.setText(null);
        tvDozeDone.setCompoundDrawables(null, null, null, null);
        btnDoze.setEnabled(false);

        btnData.setVisibility(View.GONE);

        btnInbox.setEnabled(false);

        int[] grantResults = new int[permissions.length];
        for (int i = 0; i < permissions.length; i++)
            grantResults[i] = ContextCompat.checkSelfPermission(getActivity(), permissions[i]);

        checkPermissions(permissions, grantResults, true);

        // Create outbox
        new SimpleTask<Void>() {
            @Override
            protected Void onExecute(Context context, Bundle args) {
                DB db = DB.getInstance(context);
                try {
                    db.beginTransaction();

                    EntityFolder outbox = db.folder().getOutbox();
                    if (outbox == null) {
                        outbox = new EntityFolder();
                        outbox.name = "OUTBOX";
                        outbox.type = EntityFolder.OUTBOX;
                        outbox.level = 0;
                        outbox.synchronize = false;
                        outbox.sync_days = 0;
                        outbox.keep_days = 0;
                        outbox.id = db.folder().insertFolder(outbox);
                    }

                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

                return null;
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
            }
        }.execute(this, new Bundle(), "outbox:create");

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final DB db = DB.getInstance(getContext());

        db.account().liveSynchronizingAccounts().observe(getViewLifecycleOwner(), new Observer<List<EntityAccount>>() {
            private boolean done = false;

            @Override
            public void onChanged(@Nullable List<EntityAccount> accounts) {
                done = (accounts != null && accounts.size() > 0);

                getActivity().invalidateOptionsMenu();

                tvAccountDone.setText(done ? R.string.title_setup_done : R.string.title_setup_to_do);
                tvAccountDone.setTextColor(done ? textColorPrimary : colorWarning);
                tvAccountDone.setCompoundDrawablesWithIntrinsicBounds(done ? check : null, null, null, null);

                btnIdentity.setEnabled(done);
                btnInbox.setEnabled(done);
            }
        });

        db.folder().livePrimaryDrafts().observe(getViewLifecycleOwner(), new Observer<EntityFolder>() {
            @Override
            public void onChanged(EntityFolder draft) {
                tvNoPrimaryDrafts.setVisibility(draft == null ? View.VISIBLE : View.GONE);
            }
        });

        db.identity().liveIdentities(true).observe(getViewLifecycleOwner(), new Observer<List<TupleIdentityEx>>() {
            @Override
            public void onChanged(@Nullable List<TupleIdentityEx> identities) {
                boolean done = (identities != null && identities.size() > 0);
                tvIdentityDone.setText(done ? R.string.title_setup_done : R.string.title_setup_to_do);
                tvIdentityDone.setTextColor(done ? textColorPrimary : colorWarning);
                tvIdentityDone.setCompoundDrawablesWithIntrinsicBounds(done ? check : null, null, null, null);
            }
        });

        // Backward compatibility
        PackageManager pm = getContext().getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(getContext(), ActivitySearch.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean ignoring = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                ignoring = pm.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID);
            }
        }
        btnDoze.setEnabled(!ignoring);
        tvDozeDone.setText(ignoring ? R.string.title_setup_done : R.string.title_setup_to_do);
        tvDozeDone.setTextColor(ignoring ? textColorPrimary : colorWarning);
        tvDozeDone.setCompoundDrawablesWithIntrinsicBounds(ignoring ? check : null, null, null, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean saving = (cm.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED);
            btnData.setVisibility(saving || BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_setup, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        PackageManager pm = getContext().getPackageManager();
        menu.findItem(R.id.menu_advanced).setVisible(BuildConfig.DEBUG);
        menu.findItem(R.id.menu_help).setVisible(Helper.getIntentSetupHelp().resolveActivity(pm) != null);
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_advanced:
                onMenuAdvanced();
                return true;
            case R.id.menu_help:
                onMenuHelp();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onMenuAdvanced() {
        //TODO: TMC Change (03/28/2019): Commented out code below to prevent Advanced UI from Activating
//        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
//        fragmentTransaction.replace(R.id.content_frame, new FragmentOptions()).addToBackStack("options");
//        fragmentTransaction.commit();
    }

    private void onMenuHelp() {
        //TODO: TMC Change (03/28/2019): Commented out code below to prevent Help UI from Activating
//        startActivity(Helper.getIntentSetupHelp());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == ActivitySetup.REQUEST_PERMISSION)
            checkPermissions(permissions, grantResults, false);
    }

    private void checkPermissions(String[] permissions, @NonNull int[] grantResults, boolean init) {
        boolean has = (grantResults.length > 0);
        for (int result : grantResults)
            if (result != PackageManager.PERMISSION_GRANTED) {
                has = false;
                break;
            }

        tvPermissionsDone.setText(has ? R.string.title_setup_done : R.string.title_setup_to_do);
        tvPermissionsDone.setTextColor(has ? textColorPrimary : colorWarning);
        tvPermissionsDone.setCompoundDrawablesWithIntrinsicBounds(has ? check : null, null, null, null);
        btnPermissions.setEnabled(!has);
    }
}
