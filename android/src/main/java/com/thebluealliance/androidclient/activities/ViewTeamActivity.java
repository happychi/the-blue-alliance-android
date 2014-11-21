package com.thebluealliance.androidclient.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.thebluealliance.androidclient.Constants;
import com.thebluealliance.androidclient.NfcUris;
import com.thebluealliance.androidclient.R;
import com.thebluealliance.androidclient.Utilities;
import com.thebluealliance.androidclient.adapters.ViewTeamFragmentPagerAdapter;
import com.thebluealliance.androidclient.background.team.MakeActionBarDropdownForTeam;
import com.thebluealliance.androidclient.datafeed.ConnectionDetector;
import com.thebluealliance.androidclient.eventbus.YearChangedEvent;
import com.thebluealliance.androidclient.views.SlidingTabs;

import java.util.Calendar;

import de.greenrobot.event.EventBus;

/**
 * File created by nathan on 4/21/14.
 */
public class ViewTeamActivity extends FABNotificationSettingsActivity implements ViewPager.OnPageChangeListener, AdapterView.OnItemSelectedListener, View.OnClickListener {

    public static final String TEAM_KEY = "team_key",
            TEAM_YEAR = "team_year",
            SELECTED_YEAR = "year",
            SELECTED_TAB = "tab";

    private TextView warningMessage;

    private int mCurrentSelectedYearPosition = -1,
            mSelectedTab = -1;

    private String[] yearsParticipated;

    // Should come in the format frc####
    private String mTeamKey;

    private int mYear;

    private ViewPager pager;

    private Toolbar toolbar;
    private Spinner toolbarSpinner;

    public static Intent newInstance(Context context, String teamKey) {
        System.out.println("making intent for " + teamKey);
        Intent intent = new Intent(context, ViewTeamActivity.class);
        intent.putExtra(TEAM_KEY, teamKey);
        return intent;
    }

    public static Intent newInstance(Context context, String teamKey, int year) {
        Intent intent = new Intent(context, ViewTeamActivity.class);
        intent.putExtra(TEAM_KEY, teamKey);
        intent.putExtra(TEAM_YEAR, year);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTeamKey = getIntent().getStringExtra(TEAM_KEY);
        if (mTeamKey == null) {
            throw new IllegalArgumentException("ViewTeamActivity must be created with a team key!");
        }

        setModelKey(mTeamKey);
        setContentView(R.layout.activity_view_team);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbarSpinner = (Spinner) findViewById(R.id.toolbar_spinner);

        warningMessage = (TextView) findViewById(R.id.warning_container);
        hideWarningMessage();

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SELECTED_TAB)) {
                mSelectedTab = savedInstanceState.getInt(SELECTED_TAB);
            }
            if (savedInstanceState.containsKey(SELECTED_YEAR)) {
                mYear = savedInstanceState.getInt(SELECTED_YEAR);
            }
        } else {
            if (getIntent() != null && getIntent().getExtras() != null && getIntent().getExtras().containsKey(TEAM_YEAR)) {
                mYear = getIntent().getIntExtra(TEAM_YEAR, Calendar.getInstance().get(Calendar.YEAR));
            } else {
                mYear = Calendar.getInstance().get(Calendar.YEAR);
            }
            mCurrentSelectedYearPosition = 0;
            mSelectedTab = 0;
        }

        pager = (ViewPager) findViewById(R.id.view_pager);
        pager.setOffscreenPageLimit(3);
        pager.setPageMargin(Utilities.getPixelsFromDp(this, 16));
        // We will notify the fragments of the year later
        pager.setAdapter(new ViewTeamFragmentPagerAdapter(getSupportFragmentManager(), mTeamKey));

        SlidingTabs tabs = (SlidingTabs) findViewById(R.id.tabs);
        tabs.setViewPager(pager);
        tabs.setOnPageChangeListener(this);

        if (!ConnectionDetector.isConnectedToInternet(this)) {
            showWarningMessage(getString(R.string.warning_unable_to_load));
        }

        new MakeActionBarDropdownForTeam(this).execute(mTeamKey);

        // We can call this even though the years particiapted haven't been loaded yet.
        // The years won't be shown yet; this just shows the team number in the toolbar.
        setupActionBar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSupportFragmentManager().getFragments().clear();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_YEAR, mYear);
        outState.putInt(SELECTED_TAB, mSelectedTab);
    }

    @Override
    public void onCreateNavigationDrawer() {
        useActionBarToggle(false);
        encourageLearning(false);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!isDrawerOpen()) {
            setupActionBar();
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void setupActionBar() {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            String teamNumber = mTeamKey.replace("frc", "");
            setActionBarTitle(String.format(getString(R.string.team_actionbar_title), teamNumber));
            // If we call this and the years participated haven't been loaded yet, don't try to use them
            if (yearsParticipated != null) {
                ArrayAdapter<String> actionBarAdapter = new ArrayAdapter<>(bar.getThemedContext(), R.layout.actionbar_spinner_team, R.id.year, yearsParticipated);
                actionBarAdapter.setDropDownViewResource(R.layout.actionbar_spinner_dropdown);
                toolbarSpinner.setVisibility(View.VISIBLE);
                toolbarSpinner.setAdapter(actionBarAdapter);
                toolbarSpinner.setOnItemSelectedListener(this);
                if (mCurrentSelectedYearPosition >= 0 && mCurrentSelectedYearPosition < yearsParticipated.length) {
                    toolbarSpinner.setSelection(mCurrentSelectedYearPosition);
                } else {
                    toolbarSpinner.setSelection(0);
                }
            }
        }
    }

    public void onYearsParticipatedLoaded(int[] years) {
        String[] dropdownItems = new String[years.length];
        int requestedYearIndex = 0;
        for (int i = 0; i < years.length; i++) {
            if (years[i] == mYear) {
                requestedYearIndex = i;
            }
            dropdownItems[i] = String.valueOf(years[i]);
        }
        yearsParticipated = dropdownItems;
        mCurrentSelectedYearPosition = requestedYearIndex;

        setupActionBar();

        // Notify anyone that cares that the year changed
        EventBus.getDefault().post(new YearChangedEvent(Integer.parseInt(yearsParticipated[mCurrentSelectedYearPosition])));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (isDrawerOpen()) {
                closeDrawer();
                return true;
            }

            // If this tasks exists in the back stack, it will be brought to the front and all other activities
            // will be destroyed. HomeActivity will be delivered this intent via onNewIntent().
            startActivity(HomeActivity.newInstance(this, R.id.nav_item_teams).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void showWarningMessage(String message) {
        warningMessage.setText(message);
        warningMessage.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideWarningMessage() {
        warningMessage.setVisibility(View.GONE);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        mSelectedTab = position;
        // hide the FAB if we aren't on the first page
        if (position != 0) {
            hideFab(true);
        } else {
            showFab(true);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    public int getCurrentSelectedYearPosition() {
        return mCurrentSelectedYearPosition;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == mCurrentSelectedYearPosition) {
            return;
        }
        Log.d(Constants.LOG_TAG, "year selected: " + Integer.parseInt(yearsParticipated[position]));

        mCurrentSelectedYearPosition = position;
        mYear = Integer.valueOf(yearsParticipated[mCurrentSelectedYearPosition]);

        EventBus.getDefault().post(new YearChangedEvent(mYear));

        setBeamUri(String.format(NfcUris.URI_TEAM_IN_YEAR, mTeamKey, mYear));
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
