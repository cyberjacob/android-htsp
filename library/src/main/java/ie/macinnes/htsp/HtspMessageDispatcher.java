/*
 * Copyright (c) 2017 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ie.macinnes.htsp;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.LongSparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HtspMessageDispatchers handle taking a message in, and figuring out who needs a copy of it. It's
 * here we'll implement filtering of messages (e.g. Muxpkt's should only go to 1 place, the
 * subscriber of that particular subscription ID etc).
 *
 * Subinterfaces of HtspMessage.Listener will be used to provide for some of this more advanced
 * dispatching functionality later on, as it becomes required.
 */
public class HtspMessageDispatcher implements HtspMessage.DispatcherInternal, HtspMessage.Listener, HtspConnection.Listener {
    private static final String TAG = HtspMessageDispatcher.class.getSimpleName();
    private static final AtomicInteger sSequence = new AtomicInteger();

    private final List<HtspMessage.Listener> mListeners = new ArrayList<>();
    private final Queue<HtspMessage> mQueue = new ConcurrentLinkedQueue<>();

    private static final LongSparseArray<String> sMessageResposeMethodsBySequence = new LongSparseArray<>();

    private HtspConnection mConnection;

    public HtspMessageDispatcher() {
    }

    // HtspMessage.DispatcherInternal Methods
    @Override
    public void addMessageListener(HtspMessage.Listener listener) {
        if (mListeners.contains(listener)) {
            Log.w(TAG, "Attempted to add duplicate message listener");
            return;
        }
        mListeners.add(listener);
    }

    @Override
    public void removeMessageListener(HtspMessage.Listener listener) {
        if (!mListeners.contains(listener)) {
            Log.w(TAG, "Attempted to remove non existing message listener");
            return;
        }
        mListeners.remove(listener);
    }

    @Override
    public void sendMessage(@NonNull HtspMessage message) {
        Log.v(TAG, "Queueing message for sending");

        // If necessary, inject a sequence number
        if (!message.containsKey("seq")) {
            message.put("seq", (long) sSequence.getAndIncrement());
        }

        // Record the Sequence Number and Method
        if (message.containsKey("method")) {
            sMessageResposeMethodsBySequence.append(message.getLong("seq"), message.getString("method"));
        }

        mQueue.add(message);

        if (mConnection != null) {
            mConnection.setWritePending();
        }
    }

    @Override
    public void onMessage(@NonNull final HtspMessage message) {
        if (message.containsKey("seq")) {
            long seq = message.getLong("seq");

            // Reply messages don't include a method, only the sequence supplied in the request, so
            // if we have this sequence in our lookup table, go ahead and add the method into the
            // message.
            if (sMessageResposeMethodsBySequence.indexOfKey(seq) >= 0) {
                if (!message.containsKey("method")) {
                    message.put("method", sMessageResposeMethodsBySequence.get(seq));
                }

                // Clear the sequence from our lookup table, it's no longer needed.
                sMessageResposeMethodsBySequence.remove(seq);
            }
        }

        for (final HtspMessage.Listener listener : mListeners) {
            Handler handler = listener.getHandler();

            if (handler == null) {
                listener.onMessage(message);
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onMessage(message);
                    }
                });
            }
        }
    }

    @Override
    public boolean hasPendingMessages() {
        return mQueue.size() > 0;
    }

    @NonNull
    @Override
    public HtspMessage getMessage() {
        Log.v(TAG, "Dequeueing message for sending");
        return mQueue.remove();
    }

    // HtspConnection.Listener Methods
    @Override
    public Handler getHandler() {
        return null;
    }

    @Override
    public void setConnection(@NonNull HtspConnection connection) {
        mConnection = connection;
    }

    @Override
    public void onConnectionStateChange(@NonNull HtspConnection.State state) {
        // Clear queued messages etc out as we close the connection
        if (state == HtspConnection.State.CLOSED) {
            Log.d(TAG, "Clearing out message queue as HTSP connection is closing");
            mQueue.clear();
        }
    }
}