package com.ccextractor.ultimate_alarm_clock

import kotlinx.coroutines.*
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.google.android.gms.wearable.MessageClient
import java.nio.charset.StandardCharsets

class WearOSCommunicator(private val context: Context) :
    MessageClient.OnMessageReceivedListener,
    OnDataChangedListener {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val APP_OPEN_WEARABLE_PAYLOAD_PATH = "/APP_OPEN_WEARABLE_PAYLOAD"
    private val wearableAppCheckPayload = "AppOpenWearable"
    private val wearableAppCheckPayloadReturnACK = "AppOpenWearableACK"
    private var currentAckFromWearForAppOpenCheck: String? = null

    init {
        Wearable.getMessageClient(context).addListener(this)
        Wearable.getDataClient(context).addListener(this)
    }

    fun sendAcknowledgment() {
        coroutineScope.launch {
            val nodes = getConnectedNodes()
            if (nodes.isNotEmpty()) {
                val nodeId = nodes.first()
                val payload: ByteArray = wearableAppCheckPayload.toByteArray(StandardCharsets.UTF_8)
                val sendMessageTask = Wearable.getMessageClient(context)
                    .sendMessage(nodeId, APP_OPEN_WEARABLE_PAYLOAD_PATH, payload)
                try {
                    Tasks.await(sendMessageTask)
                    Log.d("WearOSCommunicator", "Acknowledgment message sent successfully")
                } catch (e: Exception) {
                    Log.e("WearOSCommunicator", "Failed to send acknowledgment", e)
                }
            } else {
                Log.e("WearOSCommunicator", "No connected wearable devices found")
            }
        }
    }

    fun sendAlarmIntervalToWatch(interval: Long) {
        val dataClient = Wearable.getDataClient(context)
        val putDataReq = PutDataMapRequest.create("/alarm_interval").run {
            dataMap.putLong("interval", interval)
            asPutDataRequest()
        }
        dataClient.putDataItem(putDataReq).addOnSuccessListener {
            Log.d("WearOSCommunicator", "Interval sent to watch successfully: $interval")
        }.addOnFailureListener {
            Log.e("WearOSCommunicator", "Failed to send interval to watch")
        }
    }
    
    // Handle incoming data changes from the Wear OS device
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == "/interval_from_watch") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val interval = dataMap.getLong("interval", -1L)
                    Log.d("WearOSCommunicator", "Interval received from watch: $interval")
                }
            }
        }
    }

    // Handle incoming messages from the Wear OS device
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val receivedMessage = String(messageEvent.data, StandardCharsets.UTF_8)
        Log.d("WearOSCommunicator", "Received message: $receivedMessage")

        if (messageEvent.path == APP_OPEN_WEARABLE_PAYLOAD_PATH) {
            if (receivedMessage == wearableAppCheckPayloadReturnACK) {
                currentAckFromWearForAppOpenCheck = receivedMessage
                Log.d("WearOSCommunicator", "✅ Acknowledgment received from Wear OS device")
            }
        }
    }

    // Get the list of connected nodes (wearable devices)
    private fun getConnectedNodes(): List<String> {
        val nodeListTask = Wearable.getNodeClient(context).connectedNodes
        return try {
            val nodes = Tasks.await(nodeListTask)
            nodes.map { it.id }
        } catch (e: Exception) {
            Log.e("WearOSCommunicator", "Failed to get connected nodes", e)
            emptyList()
        }
    }

    fun cleanup() {
        Wearable.getMessageClient(context).removeListener(this)
        Wearable.getDataClient(context).removeListener(this)
        coroutineScope.coroutineContext.cancel()
    }
}
