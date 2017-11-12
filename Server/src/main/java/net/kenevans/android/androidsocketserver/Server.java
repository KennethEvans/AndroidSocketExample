package net.kenevans.android.androidsocketserver;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server extends Activity {
    private static final String TAG = "Server";
    private ServerSocket mServerSocket;
    private Handler mUpdateLogHandler;
    private Thread mServerThread;
    private TextView mText;
    private Handler mHandler;
    private static boolean LONG_STATUS_INTERVAL_ONLY = true;
    private static final long STATUS_INTERVAL = 1000;
    private static final long STATUS_INTERVAL_TOO_LONG =
            11 * STATUS_INTERVAL / 10;
//    private static final long STATUS_INTERVAL_TOO_LONG = 0;

    private static final String sdCardFileNameTemplate =
            "AndroidSocketExample.%s" +
                    ".txt";

    private static final int SERVERPORT = 6000;
    private static final int MAX_TEXT_LENGTH = 50000;
    private static final int ADJ_TEXT_LENGTH = 9 * MAX_TEXT_LENGTH / 10;
    private static final String TIME_FORMAT = "HH:mm:ss.SSS";
    private static final String TIME_FORMAT_PATTERN = "(\\d{2}:\\d{2}:\\d{2}" +
            ".\\d{3})";
    private static final SimpleDateFormat mFormatter = new SimpleDateFormat
            (TIME_FORMAT, Locale.US);

    private ArrayList<ClientThread> mClientList = new ArrayList<>();
    private int mNClients;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        // Make it stay on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mText = findViewById(R.id.text2);
        mHandler = new Handler();
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
        shutdown();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.shutdown:
                shutdown();
                return true;
            case R.id.restart:
                restart();
                return true;
            case R.id.save:
                save();
                return true;
            case R.id.exit:
                //shutdown();
                finish();
                return true;
        }
        return false;
    }

    /**
     * Shuts down the server then restarts it.
     */
    void restart() {
        shutdown();
        // Start the server thread
        this.mServerThread = new Thread(new ServerThread());
        this.mServerThread.start();
    }

    /**
     * Shuts down the server, closing all resources
     */
    void shutdown() {
        mHandler.removeCallbacksAndMessages(null);
        try {
            Socket clientSocket;
            for (ClientThread thread : mClientList) {
                clientSocket = thread.getClientSocket();
                BufferedReader in = thread.getmClientIn();
                PrintWriter out = thread.getmClientOut();
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            }
            mServerSocket.close();
        } catch (IOException ex) {
            addMsg("Exception", "Error closing sockets", ex);
            Log.d(TAG, "Error closing sockets", ex);
        }
    }

    /**
     * Adds a message of the form "prefix: msg" to the output.
     *
     * @param prefix The prefix.
     * @param msg    The message.
     */
    void addMsg(final String prefix, final String msg) {
        Date currentTime = new Date();
        final String timeString = mFormatter.format(currentTime);
        runOnUiThread(new Runnable() {
            public void run() {
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

    /**
     * Adds t.getMsg() to the message and calls addMsg.
     *
     * @param prefix The prefix.
     * @param msg    The message.
     * @param t      The Throwable (super class of Exception).
     * @see #addMsg(String, String)
     */
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
     * Saves the output to the SD card
     */
    private void save() {
        BufferedWriter out = null;
        try {
            File sdCardRoot = Environment.getExternalStorageDirectory();
            if (sdCardRoot.canWrite()) {
                String format = "yyyy-MM-dd-HHmmss";
                SimpleDateFormat formatter = new SimpleDateFormat(format,
                        Locale.US);
                Date now = new Date();
                String fileName = String.format(sdCardFileNameTemplate,
                        formatter.format(now));
                File file = new File(sdCardRoot, fileName);
                FileWriter writer = new FileWriter(file);
                out = new BufferedWriter(writer);
                CharSequence charSeq = mText.getText();
                out.write(charSeq.toString());
                if (charSeq.length() == 0) {
                    addMsg("save", "The file written is empty");
                }
                addMsg("Save", "Wrote " + fileName);
            } else {
                addMsg("Save", "Cannot write to SD card");
            }
        } catch (Exception ex) {
            addMsg("Save", "Error saving to SD card", ex);
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ex) {
                // Do nothing
            }
        }
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
                    if (mServerSocket == null || mServerSocket.isClosed()) {
                        break;
                    }
                } catch (Exception ex) {
                    addMsg("Server Exception", "Unexpected Error", ex);
                    if (mServerSocket == null || mServerSocket.isClosed()) {
                        break;
                    }
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
        private Runnable mCheckStatus;
        private long mCurTime;
        private long mPrevTime;
        private int mId;

        ClientThread(Socket clientSocket, int id) {
            this.mClientSocket = clientSocket;
            mId = id;

            // Start the timer
            mCurTime = mPrevTime = new Date().getTime();
            mCheckStatus = new Runnable() {
                @Override
                public void run() {
                    try {
                        updateClientStatus();
                    } finally {
                        // 100% guarantee that this always happens, even if
                        // your update method throws an exception
                        mHandler.postDelayed(mCheckStatus, STATUS_INTERVAL);
                    }
                }
            };
        }

        void updateClientStatus() {
            if (mClientSocket == null) return;
            String info = "";
            // Check if interval is too long
            mPrevTime = mCurTime;
            mCurTime = new Date().getTime();
            if (mPrevTime != 0) {
                boolean connected = mClientSocket.isConnected();
                boolean closed = mClientSocket.isClosed();
                info += (connected ? "conn" : "unconn") + ",";
                info += (closed ? "closed" : "open");
                long deltaTime = mCurTime - mPrevTime;
                if (LONG_STATUS_INTERVAL_ONLY) {
                    if (deltaTime > STATUS_INTERVAL_TOO_LONG) {
                        info += " !!! " + deltaTime + " ms";
                        addMsg("Status: Client" + " [" + mId + "]", info);
                    }
                } else {
                    addMsg("Status: Client" + " [" + mId + "]", info);
                }
            }
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
                    // If the lines starts with a timestamp, calculate the delay
                    Pattern p = Pattern.compile(TIME_FORMAT_PATTERN);
                    Matcher m = p.matcher(inputLine);
                    if (m.find()) {
                        String sentString = inputLine.substring(0, TIME_FORMAT
                                .length());
                        try {
                            // We need to account for the fact our
                            // timestamps don't have dates in them, thus
                            // represent time since the start time, which is
                            // Date(0) but is UTC time.  00:00:00.000 is not
                            // Date(0) but differences should be ok.
                            Date currentTime = new Date();
                            final String nowString = mFormatter.format
                                    (currentTime);
                            Date nowDate = mFormatter.parse(nowString);
                            Date sentDate = mFormatter.parse(sentString);
                            long deltaTime = nowDate.getTime() - sentDate
                                    .getTime();
                            Log.d(TAG, "currentTime=" + currentTime
                                    + "\n nowString=" + nowString
                                    + "\n sentString=" + sentString
                                    + "\n deltaTime=" + deltaTime);
//                            Log.d(TAG, "currentTime=" + currentTime
//                                    + "\n nowDate=" + nowDate
//                                    + "\n sentDate=" + sentDate
//                                    + "\n deltaTime=" + deltaTime);
                            addMsg("Client" + " [" + mId + "]", inputLine
                                    + " " + deltaTime + " ms");
                            mClientOut.println("Echo: " + inputLine
                                    + " " + deltaTime + " ms");
                            continue;
                        } catch (Exception ex) {
                            addMsg("Client Exception" + " [" + mId + "]",
                                    "Error parsing timestamp; " + sentString);
                        }
                    }
                    addMsg("Client" + " [" + mId + "]", inputLine);
                    if (inputLine.equals("?")) {
                        mClientOut.println("Echo: " + "\"Bye.\" ends Client, " +
                                "\"End Server.\" ends Server");
                    } else if (inputLine.equals("Bye.")) {
                        addMsg("Client" + " [" + mId + "]",
                                "Closing client per remote request");
                        // Note: break causes it to finish the loop and close
                        // the socket
                        break;
                    } else if (inputLine.equals("End Server.")) {
                        addMsg("Client" + " [" + mId + "]",
                                "Closing server per remote request");
                        shutdown();
                        break;
                    } else {
                        // Echo it
                        mClientOut.println("Echo: " + inputLine);
                    }
                }
            } catch (
                    IOException ex)

            {
                addMsg("Client Exception" + " [" + mId + "]", "Error in " +
                        "run()", ex);
            } finally

            {
                try {
                    mClientOut.close();
                    mClientIn.close();
                    mClientSocket.close();
                    addMsg("Client" + " [" + mId + "]",
                            "Socket closed");
                } catch (Exception ex) {
                    addMsg("Client Exception" + " [" + mId + "]",
                            "Error closing socket");
                }
                stopTimer();
            }
        }

        public Socket getClientSocket() {
            return mClientSocket;
        }

        public BufferedReader getmClientIn() {
            return mClientIn;
        }

        public PrintWriter getmClientOut() {
            return mClientOut;
        }

        public int getId() {
            return mId;
        }
    }
}