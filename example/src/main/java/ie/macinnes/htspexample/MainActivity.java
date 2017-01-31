package ie.macinnes.htspexample;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import ie.macinnes.htsp.HtspConnection;
import ie.macinnes.htsp.HtspDataHandler;
import ie.macinnes.htsp.HtspMessageDispatcher;
import ie.macinnes.htsp.HtspMessageSerializer;
import ie.macinnes.htsp.SimpleHtspConnection;
import ie.macinnes.htsp.tasks.Authenticator;

public class MainActivity extends AppCompatActivity {
    private static final String NEWLINE = System.getProperty("line.separator");

    private HtspMessageSerializer mHtspMessageSerializer;
    private HtspMessageDispatcher mHtspMessageDispatcher;
    private HtspDataHandler mHtspDataHandler;
    private HtspConnection.ConnectionDetails mConnectionDetails;
    private Authenticator mAuthenticator;

    private HtspConnection mHtspConnection;
    private Thread mHtspConnectionThread;

    private Handler mMainHandler = new Handler(Looper.getMainLooper());

    private SimpleHtspConnection mSimpleHtspConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConnectionDetails = new HtspConnection.ConnectionDetails(
                "tvheadend.macinnes.ie", 9983, "dev", "dev", "android-htsp example",
                BuildConfig.VERSION_NAME);

        mSimpleHtspConnection = new SimpleHtspConnection(mConnectionDetails);

        mSimpleHtspConnection.addConnectionListener(new HtspConnection.Listener() {
            @Override
            @NonNull
            public Handler getHandler() {
                return mMainHandler;
            }

            @Override
            public void setConnection(@NonNull HtspConnection connection) {

            }

            @Override
            public void onConnectionStateChange(@NonNull HtspConnection.State state) {
                TextView v = (TextView) findViewById(R.id.debugOutput);
                v.append("Connection State Changed: " + state + NEWLINE);

                ScrollView sv = (ScrollView) findViewById(R.id.scrollView);
                sv.scrollTo(0, sv.getBottom());
                sv.fullScroll(View.FOCUS_DOWN);
            }
        });

        mSimpleHtspConnection.addAuthenticationListener(new Authenticator.Listener() {
            @Override
            @NonNull
            public Handler getHandler() {
                return mMainHandler;
            }

            @Override
            public void onAuthenticationStateChange(@NonNull Authenticator.State state) {
                TextView v = (TextView) findViewById(R.id.debugOutput);
                v.append("Authentication State Changed: " + state + NEWLINE);

                ScrollView sv = (ScrollView) findViewById(R.id.scrollView);
                sv.scrollTo(0, sv.getBottom());
                sv.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    public void connect(View view) {
        if (mSimpleHtspConnection != null && !mSimpleHtspConnection.isClosed()) {
            // We're not disconnected, running the code below will dupe the thread and leave multiple
            // connections open.
            return;
        }

        mSimpleHtspConnection.start();
    }

    public void authenticate(View view) {
        // mSimpleHtspConnection.startAuthentication();
    }

    public void disconnect(View view) {
        mSimpleHtspConnection.closeConnection();
    }
}