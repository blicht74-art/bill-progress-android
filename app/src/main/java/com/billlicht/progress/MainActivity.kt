package com.billlicht.progress

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.widget.*
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class RationaleActivity:Activity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(TextView(this).apply{setPadding(32,48,32,32);textSize=18f;text="Bill’s Progress reads only the health categories you approve and sends them over HTTPS to your private dashboard while you are signed in. It does not write to Health Connect. Background sync uses your dashboard session and may require signing in again. Revoke access in Android Settings → Health Connect to stop reading. Uninstall to remove this app’s local session. Your dashboard retains imported readings. Weight and body composition use pounds, distance uses miles, and blood pressure uses mmHg. Historical import covers up to 90 days when allowed. No advertising or analytics are included."})}}

class MainActivity:ComponentActivity(){
 private lateinit var status:TextView
 private lateinit var web:WebView
 private lateinit var syncButton:Button
 private lateinit var historyButton:Button
 private val hc by lazy {HealthConnectClient.getOrCreate(this)}
 private val permissionLauncher=registerForActivityResult(PermissionController.createRequestPermissionResultContract()){permissions->
  status.text="Allowed ${permissions.intersect(readPermissions).size} measurement categories. Sign in below, then tap Sync now."
  if(permissions.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND))schedule()
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(243,246,251));setPadding(16,20,16,0)}
  root.addView(TextView(this).apply{text="Bill’s Progress Importer v2";textSize=22f;setTextColor(Color.rgb(23,35,61));setPadding(0,0,0,12)})
  status=TextView(this).apply{text="Production dashboard. 1. Allow Health Connect access. 2. Sign in below with your dashboard’s ChatGPT account. 3. Sync your readings.";textSize=14f;setPadding(0,0,0,12)};root.addView(status)
  val controls=LinearLayout(this)
  controls.addView(Button(this).apply{text="Allow access";setOnClickListener{allowAccess()}},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
  syncButton=Button(this).apply{text="Sync now";setOnClickListener{runSync(false)}};controls.addView(syncButton,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(controls)
  val extra=LinearLayout(this)
  historyButton=Button(this).apply{text="Import 90-day history";setOnClickListener{runSync(true)}};extra.addView(historyButton,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
  extra.addView(Button(this).apply{text="Stop auto sync";setOnClickListener{WorkManager.getInstance(this@MainActivity).cancelUniqueWork("health-sync");status.text="Automatic sync stopped. Allow access again to restart.";getSharedPreferences("sync",0).edit().putString("status",status.text.toString()).apply()}},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(extra)
  web=WebView(this).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;webViewClient=WebViewClient()}
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
  root.addView(web,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));setContentView(root);root.setOnApplyWindowInsetsListener{view,insets->val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());view.setPadding(16,20+bars.top,16,bars.bottom);insets};root.requestApplyInsets();web.loadUrl(DASHBOARD)
  if(HealthConnectClient.getSdkStatus(this)!=HealthConnectClient.SDK_AVAILABLE)status.text="Health Connect is unavailable on this device. Update it in Android Settings."
 }
 private fun allowAccess(){lifecycleScope.launch{try{val permissions=readPermissions.toMutableSet();if(hc.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)==HealthConnectFeatures.FEATURE_STATUS_AVAILABLE)permissions.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND);if(hc.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)==HealthConnectFeatures.FEATURE_STATUS_AVAILABLE)permissions.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY);permissionLauncher.launch(permissions)}catch(e:Exception){status.text="Could not open Health Connect access. Check Android Settings."}}}
 private fun schedule(){val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();val work=PeriodicWorkRequestBuilder<SyncWorker>(1,TimeUnit.HOURS).setConstraints(constraints).build();WorkManager.getInstance(this).enqueueUniquePeriodicWork("health-sync",ExistingPeriodicWorkPolicy.UPDATE,work)}
 private fun runSync(history:Boolean){syncButton.isEnabled=false;historyButton.isEnabled=false;status.text="Reading Health Connect and importing…";CookieManager.getInstance().flush();lifecycleScope.launch{try{status.text=sync(this@MainActivity,history);web.loadUrl(DASHBOARD)}catch(e:SecurityException){status.text=e.message?:"Sign in and allow access first."}catch(e:Exception){status.text="Import did not complete. ${e.message?:"Check your connection and try again."}"}finally{syncButton.isEnabled=true;historyButton.isEnabled=true}}}
 override fun onResume(){super.onResume();if(::status.isInitialized){val saved=getSharedPreferences("sync",0).getString("status",null);if(saved!=null)status.text=saved}}
}
