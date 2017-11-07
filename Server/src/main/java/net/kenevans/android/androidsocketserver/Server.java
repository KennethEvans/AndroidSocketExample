package net.kenevans.android.androidsocketserver;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Server extends Activity {
    private static final String TAG = "Server";
    private ServerSocket mServerSocket;
    private Handler mUpdateLogHandler;
    private Thread mServerThread;
    private TextView mText;
    private static final long STATUS_INTERVAL = 1000;
    //    private static final long STATUS_INTERVAL_TOO_LONG =
//            11 * STATUS_INTERVAL / 10;
    private static final long STATUS_INTERVAL_TOO_LONG = 0;

    private static final int SERVERPORT = 6000;
    private static final int MAX_TEXT_LENGTH = 50000;
    private static final int ADJ_TEXT_LENGTH = 9 * MAX_TEXT_LENGTH / 10;
    private static final String TIME_FORMAT = "HH:mm:ss.SSS";
    private static final SimpleDateFormat mFormatter = new SimpleDateFormat
            (TIME_FORMAT, Locale.US);

    private ArrayList<ClientThread> mClientList = new ArrayList<>();
    private int mNClients;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        // Make it stay on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mText = (TextView) findViewById(R.id.text2);
        mUpdateLogHandler = new Handler();
        // Initialize the addMsg
        addMsg("Starting", "Local IP Address=" + getLocalIpAddress());

        // Start the server thread
        this.mServerThread = new Thread(new ServerThread());
        this.mServerThread.start();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onDestroy");
        super.onDestroy();
        try {
            mServerSocket.close();
            Socket clientSocket;
            for (ClientThread thread : mClientList) {
                clientSocket = thread.getClientSocket();
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            }
        } catch (IOException ex) {
            addMsg("Exception", "Error closing sockets", ex);
            Log.d(TAG, "Error closing sockets", ex);
        }
    }

    void addMsg(final String prefix, final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Date currentTime = new Date();
                String timeString = mFormatter.format(currentTime);
                CharSequence text = mText.getText();
                int len = text.length();
                // Don't let the text get too long
                if (len > MAX_TEXT_LENGTH) {
                    text = text.subSequence(0, ADJ_TEXT_LENGTH);
                    mText.setText(timeString + " " + " Adjust: Text truncated" +
                            " to "
                            + ADJ_TEXT_LENGTH + " characters\n");
                }
                mText.setText(timeString + " " + prefix + ": " + msg + "\n" +
                        text.toString());
            }
        });
    }

    void addMsg(final String prefix, final String msg, final Throwable t) {
        addMsg(prefix, msg + ": " + "" + t.getMessage());
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
            addMsg("Exception", "Error getting local IP address", ex);
        }
        return ip;
    }

    /**
     * Handles the server socket.
     */
    class ServerThread implements Runnable {
        public void run() {
            Socket socket;
            try {
                mServerSocket = new ServerSocket(SERVERPORT);
                String info = mServerSocket.getInetAddress().getHostAddress();
                info += " " + mServerSocket.getLocalPort();
                addMsg("Server Socket", info);
            } catch (IOException ex) {
                addMsg("Server Exception", "Error setting up server", ex);
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = mServerSocket.accept();
                    // Creates a new thread
                    String info = socket.getRemoteSocketAddress() +
                            " to " + socket.getLocalSocketAddress();
                    addMsg("Starting Client", info);
                    ClientThread clientThread = new ClientThread(socket,
                            mNClients++);
                    mClientList.add(clientThread);
                    // Runs the thread (calls its run method)
                    new Thread(clientThread).start();
                } catch (IOException ex) {
                    addMsg("Server Exception", "IO Error", ex);
                } catch (Exception ex) {
                    addMsg("Server Exception", "Unexpected Error", ex);
                }
            }
        }
    }

    /**
     * Handles a client.
     */
    class ClientThread implements Runnable {
        private Socket mClientSocket;
        private BufferedReader mClientIn;
        private PrintWriter mClientOut;
        private Handler mHandler;
        private Runnable mCheckStatus;
        private long mCurTime;
        private long mPrevTime;
        private int mId;

        ClientThread(Socket clientSocket, int id) {
            this.mClientSocket = clientSocket;
            mId = id;

            // Necessary to run handler inside thread
            Looper.prepare();

            // Start the timer
            mCurTime = mPrevTime = new Date().getTime();
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
            addMsg("Status: Client" + " [" + mId + "]", info);
        }

        void startTimer() {
            if (mCheckStatus != null && mCheckStatus != null) {
                mCheckStatus.run();
            }
        }

        void stopTimer() {
            if (mHandler != null && mCheckStatus != null) {
                mHandler.removeCallbacks(mCheckStatus);
            }
        }

        public void run() {
            startTimer();
            try {
                this.mClientIn = new BufferedReader(new
                        InputStreamReader(mClientSocket.getInputStream()));
                this.mClientOut = new PrintWriter(mClientSocket
                        .getOutputStream(),
                        true);
                String inputLine;
                while ((inputLine = mClientIn.readLine()) != null) {
                    addMsg("Client" + " [" + mId + "]", inputLine);
                    if (inputLine.equals("?"))
                        mClientOut.println("Echo: " + "\"Bye.\" ends Client, " +
                                "\"End Server.\" ends Server");
                    if (inputLine.equals("Bye.")) {
                        addMsg("Client" + " [" + mId + "]",
                                "Closing per remote request");
                        break;
                    }
                    if (inputLine.equals("End Server.")) {
//                        serverContinue = false;
                    }
                }
                mClientOut.close();
                mClientIn.close();
                mClientSocket.close();
            } catch (IOException ex) {
                addMsg("Client Exception" + " [" + mId + "]", "Error in " +
                        "run()", ex);
            }
        }

        public Socket getClientSocket() {
            return mClientSocket;
        }

        public int getId() {
            return mId;
        }
    }
}