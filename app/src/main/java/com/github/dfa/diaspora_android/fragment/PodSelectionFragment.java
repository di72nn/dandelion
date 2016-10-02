package com.github.dfa.diaspora_android.fragment;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import com.github.dfa.diaspora_android.App;
import com.github.dfa.diaspora_android.R;
import com.github.dfa.diaspora_android.activity.MainActivity;
import com.github.dfa.diaspora_android.data.AppSettings;
import com.github.dfa.diaspora_android.task.GetPodsService;
import com.github.dfa.diaspora_android.util.AppLog;
import com.github.dfa.diaspora_android.util.DiasporaUrlHelper;
import com.github.dfa.diaspora_android.util.WebHelper;

import java.util.ArrayList;

/**
 * Fragment that lets the user choose a Pod
 * Created by vanitas on 01.10.16.
 */

public class PodSelectionFragment extends CustomFragment {
    public static final String TAG = "com.github.dfa.diaspora_android.PodSelectionFragment";

    protected EditText editFilter;
    protected ListView listPods;
    protected ImageView selectPodButton;

    protected App app;
    protected AppSettings appSettings;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        AppLog.d(this, "onCreateView()");
        return inflater.inflate(R.layout.podselection__fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.app = (App) getActivity().getApplication();
        this.appSettings = app.getSettings();

        this.editFilter = (EditText) view.findViewById(R.id.podselection__edit_filter);
        this.listPods = (ListView) view.findViewById(R.id.podselection__listpods);
        this.selectPodButton = (ImageView) view.findViewById(R.id.podselection__button_select_pod);

        listPods.setTextFilterEnabled(true);
        listPods.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                showPodConfirmationDialog((String) listPods.getAdapter().getItem(i));
            }
        });
        setListedPods(appSettings.getPreviousPodlist());
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(podListReceiver, new IntentFilter(GetPodsService.MESSAGE_PODS_RECEIVED));
        if (!WebHelper.isOnline(getContext())) {
            Snackbar.make(listPods, R.string.no_internet, Snackbar.LENGTH_LONG).show();
        }
        selectPodButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (editFilter.getText().length() > 4 && editFilter.getText().toString().contains("")) {
                    showPodConfirmationDialog(editFilter.getText().toString());
                } else {
                    Snackbar.make(listPods, R.string.valid_pod, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public String getFragmentTag() {
        return TAG;
    }

    @Override
    public void onCreateBottomOptionsMenu(Menu menu, MenuInflater inflater) {
        /* Nothing to do */
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    private final BroadcastReceiver podListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("pods")) {
                Bundle extras = intent.getExtras();
                String[] pods = extras.getStringArray("pods");
                if (pods != null && pods.length > 0) {
                    app.getSettings().setPreviousPodlist(pods);
                    setListedPods(pods);
                } else {
                    setListedPods(app.getSettings().getPreviousPodlist());
                    Snackbar.make(listPods, R.string.podlist_error, Snackbar.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        Intent i = new Intent(getContext(), GetPodsService.class);
        getContext().startService(i);
    }


    private void setListedPods(String[] listedPodsArr) {
        final ArrayList<String> listedPodsList = new ArrayList<>();
        for (String pod : listedPodsArr) {
            listedPodsList.add(pod.toLowerCase());
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_list_item_1,
                listedPodsList);

        // save index and top position
        int index = listPods.getFirstVisiblePosition();
        View v = listPods.getChildAt(0);
        int top = (v == null) ? 0 : (v.getTop() - listPods.getPaddingTop());
        listPods.setAdapter(adapter);
        listPods.setSelectionFromTop(index, top);

        adapter.getFilter().filter(editFilter.getText());
        editFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                (adapter).getFilter().filter(s.toString());
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void showPodConfirmationDialog(final String selectedPod) {
        // Make a clickable link
        final SpannableString dialogMessage = new SpannableString(getString(R.string.confirm_pod, selectedPod));
        Linkify.addLinks(dialogMessage, Linkify.ALL);

        // Check if online
        if (!WebHelper.isOnline(getContext())) {
            Snackbar.make(listPods, R.string.no_internet, Snackbar.LENGTH_LONG).show();
            return;
        }

        // Show dialog
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.confirmation))
                .setMessage(dialogMessage)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                onPodSelectionConfirmed(selectedPod);
                            }
                        })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void onPodSelectionConfirmed(String selectedPod) {
        app.getSettings().setPodDomain(selectedPod);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().removeSessionCookies(null);
            } else {
                //noinspection deprecation
                CookieManager.getInstance().removeAllCookie();
                //noinspection deprecation
                CookieManager.getInstance().removeSessionCookie();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ((MainActivity)getActivity()).openDiasporaUrl(new DiasporaUrlHelper(appSettings).getPodUrl());
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(podListReceiver);
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.podselection__menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_reload: {
                if (WebHelper.isOnline(getContext())) {
                    Intent i = new Intent(getContext(), GetPodsService.class);
                    getContext().startService(i);
                    return true;
                } else {
                    Snackbar.make(listPods, R.string.no_internet, Snackbar.LENGTH_LONG).show();
                    return false;
                }
            }
        }
        return super.onOptionsItemSelected(item);
    }
}