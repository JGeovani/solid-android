package com.btellez.solidandroid.test.activity;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.test.ActivityUnitTestCase;
import android.util.Log;

import com.btellez.solidandroid.module.DependencyInjector;
import com.google.gson.JsonObject;
import com.squareup.otto.Bus;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.inject.Inject;

import dagger.Module;
import dagger.Provides;
import com.btellez.solidandroid.activity.MainUserActivity;
import com.btellez.solidandroid.fragment.SimpleFragment;
import com.btellez.solidandroid.network.SimpleApi;
import com.btellez.solidandroid.test.MockApplicationInjectable;
import com.btellez.solidandroid.test.module.SingletonTestModule;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MainUserActivityTest extends ActivityUnitTestCase<MainUserActivity> {

    Activity activity;
    private Application mApplication;
    private Context mContext;

    @Inject SimpleApi simpleApi;
    @Inject Bus eventBus;

    //region start-module-def
    /*
     * This region is used to define modules that are needed for the classes tested
     * in this test.
     *
     */

    @Module(injects = MainUserActivityTest.class, includes = SingletonTestModule.class)
    public class SelfInjectorModule {}

    @Module(injects = MainUserActivity.class, includes = SingletonTestModule.class)
    public class MainUserActivityTestModule {
        // Sample Mocke String Injected...
        @Provides String provideStringValue() { return "Mock String"; }
    }

    @Module(injects = {SimpleApi.class, SimpleFragment.class}, includes = SingletonTestModule.class)
    public class SimpleFragmentTestModule {}
    //endregion


    public MainUserActivityTest() {
        super(MainUserActivity.class);
    }


    @Override
    public void setUp() throws Exception {
        super.setUp();
        // See: http://stackoverflow.com/questions/12267572/mockito-dexmaker-on-android
        System.setProperty("dexmaker.dexcache",getInstrumentation().getTargetContext().getCacheDir().getPath());
        mContext = new ContextWrapper(getInstrumentation().getTargetContext()) {
            @Override
            public Context getApplicationContext() {
                return mApplication;
            }
        };

        // Complete the implementation of the Application class
        // with the modules required for this test in particular.
        mApplication = new MockApplicationInjectable(mContext){
            @Override
            public Object[] getModules() {
                return new Object[]{
                        new SelfInjectorModule(),
                        new MainUserActivityTestModule(),
                        new SimpleFragmentTestModule(),
                        new MockApplicationContextModule(this)
                };
            }
        };

        ((DependencyInjector) mApplication).inject(this); // Inject self dependencies

        setApplication(mApplication); // Specify modules and initialize object graph
        setActivityContext(mContext);
        startActivity(new Intent(mContext, MainUserActivity.class), null, null);
        activity = getActivity();

    }


    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        activity.finish();
    }


    public void testContext() {
        assertTrue(activity.getApplicationContext() instanceof MockApplicationInjectable);
    }


    public void testMockObject() throws Exception {
        getInstrumentation().callActivityOnResume(activity);
        getInstrumentation().callActivityOnPause(activity);
        Log.e("TEST", "Event Bus Object" + eventBus.toString() + eventBus.hashCode());
        verify(eventBus, times(3)).register(anyObject());
        verify(eventBus, atLeastOnce()).post(anyObject());
    }


    public void testSimpleAPITestSuccess() throws Exception {
        // Set up stubbing for the api calls
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                eventBus.post(new SimpleApi.Success(new JsonObject()));
                return null;
            }
        }).when(simpleApi).getAllData();

        getInstrumentation().callActivityOnResume(activity);
        getInstrumentation().callActivityOnPause(activity);

        // Verify API was called
        verify(simpleApi, times(1)).getAllData();
        verify(eventBus, times(1)).post(isA(SimpleApi.Success.class));
    }


    public void testSimpleAPITestFailure() throws Exception {
        // Set up stubbing for the api calls
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                eventBus.post(new SimpleApi.Failure(new Exception()));
                return null;
            }
        }).when(simpleApi).getAllData();

        getInstrumentation().callActivityOnResume(activity);
        getInstrumentation().callActivityOnPause(activity);

        // Verify API was called
        verify(simpleApi, times(1)).getAllData();
        verify(eventBus, times(1)).post(isA(SimpleApi.Failure.class));

        // Unable to do verification for the direct method call, since the system interacts
        // directly with the activity, not the spy, so Mockito can not pick up on any interactions
        // that are going on with the activity.
        //
        // verify(activitySpy, times(1)).onNetworkFailure(any(SimpleApi.Failure.class));
        // TODO: Verify correct method calls were made and that the events were received as expected.
    }
}
