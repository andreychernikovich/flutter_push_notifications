package com.rescomms.flutter_push_notifications

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.RemoteMessage
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar


/** FlutterPushNotificationsPlugin */
class FlutterPushNotificationsPlugin : FlutterPlugin, MethodCallHandler, BroadcastReceiver(), PluginRegistry.NewIntentListener, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null
    private val notificationUtils = NotificationUtils()

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "flutter_push_notifications")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        FirebaseApp.initializeApp(context)
        initBroadcast()
        initFbToken()
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "flutter_push_notifications")
            channel.setMethodCallHandler(FlutterPushNotificationsPlugin())
        }
    }


    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        if (call.method == "getPlatformVersion") {
            result.success("Android ${Build.VERSION.RELEASE}")
        } else {
            result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMOTE_MESSAGE -> intent.extras?.let { notificationUtils.createNotification(it[EXTRA_MESSAGE] as RemoteMessage) }
            ACTION_NEW_FB_TOKEN -> intent.extras?.let { Log.e("AAAA", it[EXTRA_TOKEN] as String) }
        }
    }

    override fun onNewIntent(intent: Intent?): Boolean {
        return false
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        binding.addOnNewIntentListener(this)
        this.activity = binding.activity
        notificationUtils.createNotificationManager(binding.activity)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        binding.addOnNewIntentListener(this)
        this.activity = binding.activity
        notificationUtils.createNotificationManager(binding.activity)

    }

    override fun onDetachedFromActivity() {
        this.activity = null
    }

    private fun initBroadcast() {
        IntentFilter().apply {
            this.addAction(ACTION_NEW_FB_TOKEN)
            this.addAction(ACTION_REMOTE_MESSAGE)
            LocalBroadcastManager.getInstance(context).registerReceiver(this@FlutterPushNotificationsPlugin, this)
        }
    }

    private fun initFbToken() {
        FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                return@addOnCompleteListener
            }
            task.result?.let {
                Intent(ACTION_NEW_FB_TOKEN).apply {
                    this.putExtra(EXTRA_TOKEN, it.token)
                    LocalBroadcastManager.getInstance(context).sendBroadcast(this)
                }
            }
        }
    }
}