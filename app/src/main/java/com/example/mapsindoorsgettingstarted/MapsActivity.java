package com.example.mapsindoorsgettingstarted;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.textfield.TextInputEditText;
import com.mapsindoors.livesdk.LiveDataDomainTypes;
import com.mapsindoors.mapssdk.MPDirectionsConfig;
import com.mapsindoors.mapssdk.MPDirectionsRenderer;
import com.mapsindoors.mapssdk.MPDirectionsService;
import com.mapsindoors.mapssdk.MPFilter;
import com.mapsindoors.mapssdk.MPFilterBehavior;
import com.mapsindoors.mapssdk.MPLocation;
import com.mapsindoors.mapssdk.MPMapConfig;
import com.mapsindoors.mapssdk.MPPoint;
import com.mapsindoors.mapssdk.MPQuery;
import com.mapsindoors.mapssdk.MPRoute;
import com.mapsindoors.mapssdk.MPTravelMode;
import com.mapsindoors.mapssdk.MPVenue;
import com.mapsindoors.mapssdk.MapControl;
import com.mapsindoors.mapssdk.MapsIndoors;
import com.mapsindoors.mapssdk.OnRouteResultListener;
import com.mapsindoors.mapssdk.errors.MIError;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, OnRouteResultListener {

    private GoogleMap mMap;
    private MapControl mMapControl;
    private View mMapView;
    private TextInputEditText mSearchTxtField;
    private MPDirectionsService mpDirectionsService;
    private MPDirectionsRenderer mpDirectionsRenderer;
    private MPPoint mUserLocation = new MPPoint(38.897389429704695, -77.03740973527613, 0);
    private NavigationFragment mNavigationFragment;
    private SearchFragment mSearchFragment;
    private Fragment mCurrentFragment;
    private BottomSheetBehavior<FrameLayout> mBtmnSheetBehavior;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        //The local variable for the MapFragments view.
        mMapView = mapFragment.getView();

        //Initialize MapsIndoors and set the google api Key, we do not need a listener in this showcase
        MapsIndoors.initialize(getApplicationContext(), "d876ff0e60bb430b8fabb145", null);
        MapsIndoors.setGoogleAPIKey(getString(R.string.google_maps_key));

        ImageButton searchBtn = findViewById(R.id.search_btn);
        mSearchTxtField = findViewById(R.id.search_edit_txt);
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        //ClickListener to start a search, when the user clicks the search button
        searchBtn.setOnClickListener(view -> {
            if (mSearchTxtField.getText().length() != 0) {
                //There is text inside the search field. So lets do the search.
                search(mSearchTxtField.getText().toString());
                //Making sure keyboard is closed.
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        });
        //Listener for when the user searches through the keyboard
        mSearchTxtField.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE || i == EditorInfo.IME_ACTION_SEARCH) {
                if (textView.getText().length() != 0) {
                    //There is text inside the search field. So lets do the search.
                    search(textView.getText().toString());
                }
                //Making sure keyboard is closed.
                imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        FrameLayout bottomSheet = findViewById(R.id.standardBottomSheet);
        mBtmnSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        mBtmnSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    if (mCurrentFragment != null) {
                        if (mCurrentFragment instanceof NavigationFragment) {
                            //Clears the direction view if the navigation fragment is closed.
                            mpDirectionsRenderer.clear();
                        }
                        //Clears the filtered locations from map if any searches has been done.
                        mMapControl.clearFilter();
                        //Removes the current fragment from the BottomSheet.
                        removeFragmentFromBottomSheet(mCurrentFragment);
                    }
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });
    }

    /**
     * Public getter for the MapControl object
     *
     * @return MapControl object for this activity
     */
    public MapControl getMapControl() {
        return mMapControl;
    }

    /**
     * Public getter for the MPDirectionsRenderer object
     *
     * @return MPDirectionRenderer object for this activity
     */
    public MPDirectionsRenderer getMpDirectionsRenderer() {
        return mpDirectionsRenderer;
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (mMapView != null) {
            initMapControl(mMapView);
        }
    }

    /**
     * Inits MapControl with a basic configuration.
     *
     * @param view the view assigned to the google map.
     */
    void initMapControl(View view) {
        //Makes a basic configuration for MapControl
        MPMapConfig mapConfig = new MPMapConfig.Builder(this, mMap, view).build();

        //Creates a new instance of MapControl
        MapControl.create(mapConfig, this::onMapControlReady);

    }

    /**
     * Triggers when MapControl has initialized, here we assign it, and move the camera to the venue.
     *
     * @param mapControl the initialized MapControl
     * @param error      whether an error occured
     */
    public void onMapControlReady(MapControl mapControl, MIError error) {
        if (error == null) {
            // Sets the local MapControl variable so that it can be used later
            mMapControl = mapControl;
            //No errors so getting the first venue (in the white house solution the only one)
            MPVenue venue = MapsIndoors.getVenues().getCurrentVenue();
            runOnUiThread(() -> {
                if (venue != null) {
                    //Animates the camera to fit the new venue
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(venue.getLatLngBoundingBox(), 19));
                }
            });
            //Enable live data on the map
            enableLiveData();
        }
    }

    /**
     * Performs a search for locations with MapsIndoors and opens up a list of search results
     *
     * @param searchQuery String to search for
     */
    void search(String searchQuery) {
        //Query with a string to search on
        MPQuery mpQuery = new MPQuery.Builder().setQuery(searchQuery).build();
        //Filter for the search query, only taking 30 locations
        MPFilter mpFilter = new MPFilter.Builder().setTake(30).build();

        //Gets locations
        MapsIndoors.getLocationsAsync(mpQuery, mpFilter, (list, miError) -> {
            //Check if there is no error and the list is not empty
            if (miError == null && !list.isEmpty()) {
                //Create a new instance of the search fragment
                mSearchFragment = SearchFragment.newInstance(list, this);
                //Make a transaction to the bottomsheet
                addFragmentToBottomSheet(mSearchFragment);
                //Clear the search text, since we got a result
                mSearchTxtField.getText().clear();
                //Create a behavior for the map when filtering,
                // we want the map to move so that the filtered locations are in shot
                MPFilterBehavior behavior = new MPFilterBehavior.Builder()
                        .setMoveCamera(true)
                        .setAnimationDuration(500)
                        .build();
                //Calling setFilter results on the ui thread as camera movement is involved
                runOnUiThread(() -> {
                    mMapControl.setFilter(list, behavior);
                });
            } else {
                String alertDialogTitleTxt;
                String alertDialogTxt;
                if (list.isEmpty()) {
                    alertDialogTitleTxt = "No results found";
                    alertDialogTxt = "No results could be found for your search text. Try something else";
                } else {
                    if (miError != null) {
                        alertDialogTitleTxt = "Error: " + miError.code;
                        alertDialogTxt = miError.message;
                    } else {
                        alertDialogTitleTxt = "Unknown error";
                        alertDialogTxt = "Something went wrong, try another search text";
                    }
                }

                new AlertDialog.Builder(this)
                        .setTitle(alertDialogTitleTxt)
                        .setMessage(alertDialogTxt)
                        .show();
            }
        });
    }

    /**
     * Queries the MPRouting provider with a hardcoded user location and the location the user should be routed to
     *
     * @param mpLocation A MPLocation to navigate to
     */
    void createRoute(MPLocation mpLocation) {
        //If MPDirectionsService has not been instantiated create it here and assign the results call back to the activity.
        if (mpDirectionsService == null) {
            //Creating a configuration for the MPDirectionsService allows us to set a resultListener and a travelMode.
            MPDirectionsConfig config = new MPDirectionsConfig.Builder().setOnRouteResultListener(this).setTravelMode(MPTravelMode.WALKING).build();

            mpDirectionsService = new MPDirectionsService();
            mpDirectionsService.setConfig(config);
        }
        //Queries the MPDirectionsService for a route with the hardcoded user location and the point from a location.
        mpDirectionsService.query(mUserLocation, mpLocation.getPoint());
    }

    /**
     * The result callback from the route query. Starts the rendering of the route and opens up a new instance of the navigation fragment on the bottom sheet.
     *
     * @param route   the route model used to render a navigation view.
     * @param miError an MIError if anything goes wrong when generating a route
     */
    @Override
    public void onRouteResult(@Nullable MPRoute route, @Nullable MIError miError) {
        //Early return if either error is not null or the route is null
        if (miError != null || route == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Something went wrong")
                    .setMessage("Something went wrong when generating the route. Try again or change your destination/origin")
                    .show();
            return;
        }
        //Create the MPDirectionsRenderer if it has not been instantiated.
        if (mpDirectionsRenderer == null) {
            mpDirectionsRenderer = new MPDirectionsRenderer(this, mMap, mMapControl, i -> {
                //Listener callback for when the user changes route leg. (By default is only called when a user presses the RouteLegs end marker)
                mpDirectionsRenderer.setRouteLegIndex(i);
                mMapControl.selectFloor(mpDirectionsRenderer.getLegFloorIndex());
            });
        }
        //Set the route on the MPDirectionsRenderer
        mpDirectionsRenderer.setRoute(route);
        //Create a new instance of the navigation fragment
        mNavigationFragment = NavigationFragment.newInstance(route, this);
        //Add the fragment to the BottomSheet
        addFragmentToBottomSheet(mNavigationFragment);
        //As camera movement is involved run this on the UIThread
        runOnUiThread(() -> {
            //Starts drawing and adjusting the map according to the route
            mpDirectionsRenderer.renderOnMap();
        });
    }


    /**
     * Enables live data for the map.
     */
    void enableLiveData() {
        //Enabling live data for the three known live data domains that are enabled for this solution.
        mMapControl.enableLiveData(LiveDataDomainTypes.AVAILABILITY_DOMAIN);
        mMapControl.enableLiveData(LiveDataDomainTypes.OCCUPANCY_DOMAIN);
        mMapControl.enableLiveData(LiveDataDomainTypes.POSITION_DOMAIN);
    }

    void addFragmentToBottomSheet(Fragment newFragment) {
        if (mCurrentFragment != null) {
            getSupportFragmentManager().beginTransaction().remove(mCurrentFragment).commit();
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.standardBottomSheet, newFragment).commit();
        mCurrentFragment = newFragment;
        //Set the map padding to the height of the bottom sheets peek height. To not obfuscate the google logo.
        runOnUiThread(() -> {
            mMapControl.setMapPadding(0, 0, 0, mBtmnSheetBehavior.getPeekHeight());
            if (mBtmnSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                mBtmnSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
    }

    void removeFragmentFromBottomSheet(Fragment fragment) {
        if (mCurrentFragment.equals(fragment)) {
            mCurrentFragment = null;
        }
        getSupportFragmentManager().beginTransaction().remove(fragment).commit();
        runOnUiThread(() -> {
            mMapControl.setMapPadding(0, 0, 0, 0);
        });
    }
}