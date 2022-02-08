/*
=============
Author: Lucas Josino
Github: https://github.com/LucJosin
Website: https://www.lucasjosino.com/
=============
Plugin/Id: on_audio_query#0
Homepage: https://github.com/LucJosin/on_audio_query
Pub: https://pub.dev/packages/on_audio_query
License: https://github.com/LucJosin/on_audio_query/blob/main/on_audio_query/LICENSE
Copyright: © 2021, Lucas Josino. All rights reserved.
=============
*/

package com.lucasjosino.on_audio_query

import android.app.Activity
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import androidx.annotation.NonNull
import com.lucasjosino.on_audio_query.controller.PermissionController
import com.lucasjosino.on_audio_query.controller.QueryController
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** OnAudioQueryPlugin Central */
class OnAudioQueryPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    companion object {
        // Get the current class name.
        private val TAG = this::class.java.name
    }

    // Dart <-> Kotlin communication
    private val channelName = "com.lucasjosino.on_audio_query"
    private lateinit var channel: MethodChannel

    // Main parameters
    private var activity: Activity? = null
    private var binding: ActivityPluginBinding? = null
    private var permissionController: PermissionController? = null

    //
    private lateinit var context: Context
    private lateinit var queryController: QueryController

    // Dart <-> Kotlin communication
    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        this.context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, channelName)
        channel.setMethodCallHandler(this)
    }

    // Methods will always follow the same route:
    // Receive method -> check permission -> controller -> do what's needed -> return to dart
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        // Setup the [QueryController].
        this.queryController = QueryController(context, call, result)

        // Both [activity] and [binding] are from [onAttachedToActivity].
        // If one of them are null. Something is really wrong.
        if (activity == null || binding == null) {
            result.error(
                "$TAG::onMethodCall",
                "The [activity] or [binding] parameter is null!",
                null
            )
        }

        // If user deny permission request a pop up will immediately show up
        // If [retryRequest] is null, the message will only show when call method again
        val retryRequest = call.argument<Boolean>("retryRequest") ?: false
        // Setup the [PermissionController]
        permissionController = PermissionController(retryRequest)

        //
        when (call.method) {
            // Permissions
            "permissionsStatus" -> result.success(permissionController!!.permissionStatus(context))
            "permissionsRequest" -> {
                binding!!.addRequestPermissionsResultListener(permissionController!!)
                permissionController!!.requestPermission(activity!!, result)
            }

            // Device information
            "queryDeviceInfo" -> {
                val deviceData: MutableMap<String, Any> = HashMap()
                deviceData["device_model"] = Build.MODEL
                deviceData["device_sys_version"] = Build.VERSION.SDK_INT
                deviceData["device_sys_type"] = "Android"
                result.success(deviceData)
            }

            // This method will scan the given path to update the 'state'.
            // When deleting a file using 'dart:io', call this method to update the file 'state'.
            "scan" -> {
                val sPath: String = call.argument<String>("path")!!

                // Check if the given file is empty.
                if (sPath.isEmpty()) result.success(false)

                // Scan and return
                MediaScannerConnection.scanFile(context, arrayOf(sPath), null) { _, _ ->
                    result.success(true)
                }
            }

            // All others methods
            else -> queryController.call()
        }
    }

    // Dart <-> Kotlin communication
    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    // Attach the activity and get the [activity] and [binding].
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
        this.binding = binding
    }

    //
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    //
    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    //
    override fun onDetachedFromActivity() {
        // Remove the permission listener
        if (binding != null && permissionController != null) {
            binding!!.removeRequestPermissionsResultListener(permissionController!!)
        }

        // Remove both [activity] and [binding].
        this.activity = null
        this.binding = null
    }
}
