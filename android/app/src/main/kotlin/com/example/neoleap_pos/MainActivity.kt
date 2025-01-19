package com.example.neoleap_pos

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Xml
import android.widget.Toast
import com.mpos.mpossdk.api.MPOSService
import com.mpos.mpossdk.api.MPOSServiceCallback
import com.mpos.mpossdk.api.TerminalStatus
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FlutterActivity() {
    private val CHANNEL = "METHOD_CHANNEL"
    private lateinit var mChannel: MethodChannel
    private var serverIp: String? = null
    private val serverPort = 9999
    private var socket: Socket? = null
    private var output: PrintWriter? = null
    private var input: BufferedReader? = null
    private var amount: String? = null
    private var mposService: MPOSService? = null
    private val handler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = object : Runnable {
        override fun run() {
            if (serverIp != null && (socket == null || socket!!.isClosed)) {
                Thread(ConnectToDevice()).start()
            }
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mposService = MPOSService.getInstance(this)

        // Set the default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
        }
        handler.post(reconnectRunnable)
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        // Format the current date and time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())

        // Convert the stack trace to a string
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        val stackTraceString = stringWriter.toString()

        // Create the log string
        val logString = "Time: $currentTime\nThread: ${thread.name}\n${stackTraceString}\n"

        // Write the log to a file in external storage
        writeLogToFile(logString)

        // Terminate the app
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }

    private fun writeLogToFile(log: String) {
        try {
            // Define the filename and the path
            val fileName = "crash_log_${System.currentTimeMillis()}.txt"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS + "/logs"
                )
            }

            // Get the content resolver
            val resolver = applicationContext.contentResolver

            // Insert the file into MediaStore
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

            uri?.let {
                resolver.openOutputStream(it).use { outputStream ->
                    outputStream?.write(log.toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /// this method will setup the channel.
    /// 'connectDevice' will connect to the device using the IP address.
    /// 'startTransaction' will send the transaction data to the device.
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        mChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        mChannel.setMethodCallHandler { call, channelResult ->
            when (call.method) {
                "connectDevice" -> {
                    serverIp = call.arguments as String
                    Thread(ConnectToDevice()).start()
                    runOnUiThread {
                        channelResult.success("Device Connected")
                    }
                }

                "startTransaction" -> {
                    amount = call.arguments as String
                    var enteredAmount = amount.toString()
                    enteredAmount = getEnglishNumbers(enteredAmount)
                    enteredAmount = enteredAmount.replace(".", "")
                    enteredAmount = enteredAmount.replace(",", "")
                    enteredAmount = enteredAmount.replace("٫", "")
                    try {
                        if (enteredAmount.isEmpty() || enteredAmount.toFloat() <= 0) {
                            runOnUiThread {
                                Toast.makeText(activity, "Invalid Amount", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        } else {
                            while (enteredAmount.length != 12) {
                                enteredAmount = "0$enteredAmount"
                            }
                        }
                    } catch (r: java.lang.Exception) {
                    }

                    Thread(PostToDevice("\u000205018\u0002  $enteredAmount'\u0001��\u00032")).start()
                    runOnUiThread {
                        channelResult.success(true)
                    }
                }

                else -> {
                    runOnUiThread {
                        channelResult.notImplemented()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mposService!!.stop()
        closeResources()
        handler.removeCallbacks(reconnectRunnable)
        super.onDestroy()
    }

    /// establish connection with the device using socket.
    inner class ConnectToDevice : Runnable {
        override fun run() {
            try {
                closeResources()
                socket = Socket(serverIp, serverPort)
                socket!!.keepAlive = true
                socket!!.tcpNoDelay = true
                input = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                output = PrintWriter(socket!!.getOutputStream(), true)
                Thread(ListenToDevice()).start()
            } catch (e: Exception) {
                closeResources()
                e.printStackTrace()
            }
        }
    }

    /// this class will listen to messages from the device.
    /// and send that back to the Flutter.
    inner class ListenToDevice : Runnable {
        override fun run() {
            while (true) {
                try {
                    var message: String? = null
                    val dataInputStream = DataInputStream(socket!!.getInputStream())
                    if (dataInputStream.read() != -1) {
                        message = readStream(dataInputStream)
                        if (message != null && message != "AAAAAA\u0003") {
                            val msglen = message.length
                            Log.d("message", message.substring(7, msglen))
                            message = message.substring(7, msglen)
                            //message=message.replace("1U00854","");
                            val data = showTransactionResult(message)
                            runOnUiThread {
                                mChannel.invokeMethod("transactionResult", data)
                            }
                        }
                    } else {
                        closeResources()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    runOnUiThread {
                        closeResources()
                    }
                }
            }
        }
    }

    /// this class will receive the transaction data from Flutter.
    /// and send that to the device.
    /// unused. Using startTransaction method from MPOSService.
    inner class PostToDevice(private val message: String) : Runnable {
        override fun run() {
            try {
                output?.println(message)
                output?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
                closeResources()
            }
        }
    }

    private fun getEnglishNumbers(Numbers: String): String {
        var ArabicNumber = Numbers
        try {
            ArabicNumber = ArabicNumber.replace('٠', '0')
            ArabicNumber = ArabicNumber.replace('١', '1')
            ArabicNumber = ArabicNumber.replace('٢', '2')
            ArabicNumber = ArabicNumber.replace('٣', '3')
            ArabicNumber = ArabicNumber.replace('٤', '4')
            ArabicNumber = ArabicNumber.replace('٥', '5')
            ArabicNumber = ArabicNumber.replace('٦', '6')
            ArabicNumber = ArabicNumber.replace('٧', '7')
            ArabicNumber = ArabicNumber.replace('٨', '8')
            ArabicNumber = ArabicNumber.replace('٩', '9')
        } catch (e: java.lang.Exception) {
        }

        return ArabicNumber
    }

    fun readStream(stream: InputStream): String? {
        try {
            val len = stream.available()
            val bo = ByteArrayOutputStream()
            var i: Int
            for (x in 0 until len) {
                i = stream.read()
                bo.write(i)
            }
            return bo.toString()
        } catch (ex: java.lang.Exception) {
            val error = ex.message
            return error
        }
    }

    private fun showTransactionResult(transactionResponse: String?): String {
        val result = MPOSService.getInstance(this.applicationContext)
            .parseTransactionResponse(transactionResponse)
        val json = JSONObject(result as Map<*, *>?)
        return json.toString()
    }

    private fun closeResources() {
        try {
            input?.close()
            output?.close()
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
