package net.kenevans.android.androidsocketserver;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

public class Server extends Activity {
    private ServerSocket mServerSocket;
    private Socket mClientSocket;
    private Handler mUpdateLogHandler;
    private Thread mServerThread;
    private TextView mText;
    private static final long STATUS_INTERVAL = 1000;
    private static final long STATUS_INTERVAL_TOO_LONG =
            11 * STATUS_INTERVAL / 10;
    private Handler mHandler;
    private Runnable mCheckStatus;

    private static final int SERVERPORT = 6000;
    private static final int MAX_TEXT_LENGTH = 50000;
    private static final int ADJ_TEXT_LENGTH = 9 * MAX_TEXT_LENGTH / 10;
    private static final String TIME_FORMAT = "HH:mm:ss.SSS";
    private static final SimpleDateFormat mFormatter = new SimpleDateFormat
            (TIME_FORMAT, Locale.US);

    private static long mCurTime;
    private static long mPrevTime;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        // Make it stay on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mText = (TextView) findViewById(R.id.text2);
        mUpdateLogHandler = new Handler();
        // Initialize the log
        mUpdateLogHandler.post(new PostLogMsgThread
                ("Starting", "Local IP Address=" + getLocalIpAddress()));

        // Start the server thread
        this.mServerThread = new Thread(new ServerThread());
        this.mServerThread.start();

        // Start the timer
        mHandler = new Handler();
        mCheckStatus = new Runnable() {
            @Override
            public void run() {
                try {
                    updateSocketStatus();
                } finally {
                    // 100% guarantee that this always happens, even if
                    // your update method throws an exception
                    mHandler.postDelayed(mCheckStatus, STATUS_INTERVAL);
                }
            }
        };
        startTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            stopTimer();
            mServerSocket.close();
        } catch (IOException ex) {
            mUpdateLogHandler.post(new PostLogMsgThread("Exception",
                    ex.getMessage()));
        }
    }

    void updateSocketStatus() {
        if (mClientSocket == null) return;
        String info = "";
        boolean connected = mClientSocket.isConnected();
        boolean closed = mClientSocket.isClosed();
        info += (connected ? "connected" : "unconnected") + " ";
        info += (closed ? "closed" : "open");
        // Check if interval is too long
        mPrevTime = mCurTime;
        mCurTime = new Date().getTime();
        if (mPrevTime != 0) {
            long deltaTime = mCurTime - mPrevTime;
            if (deltaTime > STATUS_INTERVAL_TOO_LONG) {
                info += " !!! " + deltaTime + " ms";
            }
        }
        mUpdateLogHandler.post(new PostLogMsgThread
                ("Status", info));
    }

    void startTimer() {
        if (mCheckStatus != null) {
            mCheckStatus.run();
        }
    }

    void stopTimer() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mCheckStatus);
        }
    }

    /**
     * Gets the local IP address
     *
     * @return The local IP address.
     */
    public String getLocalIpAddress() {
        String ip;
        try {
            WifiManager wm = (WifiManager) getApplicationContext()
                    .getSystemService(WIFI_SERVICE);
            ip = Formatter.formatIpAddress(wm.getConnectionInfo()
                    .getIpAddress());
        } catch (Exception ex) {
            ip = null;
            mUpdateLogHandler.post(new PostLogMsgThread
                    ("Exception", ex.getMessage()));
        }
        return ip;
    }

    /**
     * Handles the server socket.
     */
    class ServerThread implements Runnable {
        public void run() {
            Socket mClientSocket = null;
            try {
                mServerSocket = new ServerSocket(SERVERPORT);
                String info = mServerSocket.getInetAddress().getHostAddress();
                info += " " + mServerSocket.getLocalPort();
                mUpdateLogHandler.post(new PostLogMsgThread
                        ("Server Socket", info));
            } catch (IOException ex) {
                mUpdateLogHandler.post(new PostLogMsgThread
                        ("Exception", ex.getMessage()));
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    mClientSocket = mServerSocket.accept();
                    CommunicationThread commThread = new CommunicationThread
                            (mClientSocket);
                    new Thread(commThread).start();
                    String info = mClientSocket.getRemoteSocketAddress() +
                            "to" + mClientSocket.getLocalSocketAddress();
                    mUpdateLogHandler.post(new PostLogMsgThread
                            ("Client Socket", info));
                } catch (IOException ex) {
                    mUpdateLogHandler.post(new PostLogMsgThread
                            ("Exception", ex.getMessage()));
                }
            }
        }
    }

    /**
     * Handles communication form the client.
     */
    class CommunicationThread implements Runnable {
        private BufferedReader mBufferedReader;

        CommunicationThread(Socket clientSocket) {
            try {
                this.mBufferedReader = new BufferedReader(new
                        InputStreamReader(mClientSocket.getInputStream()));
            } catch (IOException ex) {
                mUpdateLogHandler.post(new PostLogMsgThread
                        ("Exception", ex.getMessage()));
            }
        }

        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = mBufferedReader.readLine();
                    mUpdateLogHandler.post(new PostLogMsgThread(read));
                } catch (IOException ex) {
                    mUpdateLogHandler.post(new PostLogMsgThread
                            ("Exception", ex.getMessage()));
                }
            }
        }
    }

    /**
     * Sends a message to be added to the log.
     */
    class PostLogMsgThread implements Runnable {
        private String mMsg;
        private String mPrefix = "Client Says";

        PostLogMsgThread(String str) {
            this.mMsg = str;
        }

        PostLogMsgThread(String prefix, String str) {
            this.mPrefix = prefix;
            this.mMsg = str;
        }

        @Override
        public void run() {
            Date currentTime = new Date();
            String timeString = mFormatter.format(currentTime);
            CharSequence text = mText.getText();
            int len = text.length();
            // Don't let it get too long
            if (len > MAX_TEXT_LENGTH) {
                text = text.subSequence(0, ADJ_TEXT_LENGTH);
                mText.setText(timeString + " " + " Adjust: Text truncated to "
                        + ADJ_TEXT_LENGTH + " characters\n");
            }
            mText.setText(timeString + " " + mPrefix + ": " + mMsg + "\n" +
                    text.toString());
        }
    }
}