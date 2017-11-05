package net.kenevans.android.androidsocketserver;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Calendar;

public class Server extends Activity {
    private ServerSocket mServerSocket;
    Handler mUpdateConversationHandler;
    Thread mServerThread = null;
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
        mUpdateConversationHandler = new Handler();
        this.mServerThread = new Thread(new ServerThread());
        this.mServerThread.start();

        // Start the timer
        mHandler = new Handler();
        mCheckStatus = new Runnable() {
            @Override
            public void run() {
                try {
                    updateStatus();
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
            mUpdateConversationHandler.post(new UpdateUIThread("Exception",
                    ex.getMessage()));
        }
    }

    void updateStatus() {
        if (mServerSocket == null) return;
        String info = "";
        boolean bound = mServerSocket.isBound();
        boolean closed = mServerSocket.isClosed();
        info += (bound ? "bound" : "unbound") + " ";
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
        mUpdateConversationHandler.post(new UpdateUIThread
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

    class ServerThread implements Runnable {
        public void run() {
            Socket socket = null;
            try {
                mServerSocket = new ServerSocket(SERVERPORT);
            } catch (IOException ex) {
                mUpdateConversationHandler.post(new UpdateUIThread
                        ("Exception", ex.getMessage()));
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = mServerSocket.accept();
                    CommunicationThread commThread = new CommunicationThread
                            (socket);
                    new Thread(commThread).start();
                } catch (IOException ex) {
                    mUpdateConversationHandler.post(new UpdateUIThread
                            ("Exception", ex.getMessage()));
                }
            }
        }
    }

    class CommunicationThread implements Runnable {
        private Socket clientSocket;
        private BufferedReader input;

        CommunicationThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {
                this.input = new BufferedReader(new InputStreamReader(this
                        .clientSocket.getInputStream()));
            } catch (IOException ex) {
                mUpdateConversationHandler.post(new UpdateUIThread
                        ("Exception", ex.getMessage()));
            }
        }

        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = input.readLine();
                    mUpdateConversationHandler.post(new UpdateUIThread(read));
                } catch (IOException ex) {
                    mUpdateConversationHandler.post(new UpdateUIThread
                            ("Exception", ex.getMessage()));
                }
            }
        }
    }

    class UpdateUIThread implements Runnable {
        private String msg;
        private String prefix = "Client Says";

        UpdateUIThread(String str) {
            this.msg = str;
        }

        UpdateUIThread(String prefix, String str) {
            this.prefix = prefix;
            this.msg = str;
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
            mText.setText(timeString + " " + prefix + ": " + msg + "\n" +
                    text.toString());
        }
    }
}