package com.billlicht.progress

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.time.LocalDate

class RationaleActivity:Activity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(TextView(this).apply{setPadding(32,48,32,32);textSize=18f;text="Bill’s Progress reads only the Health Connect categories you approve. It prepares a local JSON file when you tap a button; you choose where to save it and upload it to your private dashboard in Chrome using your passkey. This version does not send data automatically or use an API key. Revoke access in Android Settings → Health Connect. The file contains private health readings; store and delete it carefully."})}}

class MainActivity:ComponentActivity(){
 private lateinit var status:TextView
 private lateinit var prepareButton:Button
 private lateinit var historyButton:Button
 private var pendingExport:String?=null
 private val hc by lazy {HealthConnectClient.getOrCreate(this)}
 private val permissionLauncher=registerForActivityResult(PermissionController.createRequestPermissionResultContract()){permissions->
  status.text="Allowed ${permissions.intersect(readPermissions).size} measurement categories. Tap Prepare 30-day file."
 }
 private val saveLauncher=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri:Uri?->
  val data=pendingExport;pendingExport=null
  if(uri==null||data==null){status.text="File was not saved. Tap Prepare again when ready.";return@registerForActivityResult}
  try{
   contentResolver.openOutputStream(uri)?.use{it.write(data.toByteArray(Charsets.UTF_8))}
    ?:throw IllegalStateException("Could not open the selected file")
   status.text="File saved. In Chrome, open Connections → Import readings JSON and select the file you just saved."
   val intent=Intent(Intent.ACTION_VIEW,Uri.parse(DASHBOARD)).apply{addCategory(Intent.CATEGORY_BROWSABLE);setPackage("com.android.chrome")}
   try{startActivity(intent)}catch(_:Exception){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(DASHBOARD)).addCategory(Intent.CATEGORY_BROWSABLE))}
  }catch(e:Exception){status.text="Could not save the file: ${e.message?:"choose another location"}"}
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  WorkManager.getInstance(this).cancelUniqueWork("health-sync")
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(243,246,251));setPadding(24,48,24,24)}
  root.addView(TextView(this).apply{text="Bill’s Progress Importer v2.1";textSize=24f;setTextColor(Color.rgb(23,35,61));setPadding(0,0,0,24)})
  status=TextView(this).apply{text="Your passkey works in Chrome. Allow Health Connect access, prepare a file, then upload that file in the dashboard’s Connections tab.";textSize=17f;setPadding(0,0,0,24)};root.addView(status)
  root.addView(Button(this).apply{text="Allow Health Connect access";setOnClickListener{allowAccess()}},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
  prepareButton=Button(this).apply{text="Prepare 30-day file";setOnClickListener{prepare(false)}};root.addView(prepareButton)
  historyButton=Button(this).apply{text="Prepare 90-day history";setOnClickListener{prepare(true)}};root.addView(historyButton)
  root.addView(TextView(this).apply{text="After saving, Chrome opens the production dashboard. Tap Connections → Import readings JSON, choose the saved file, and confirm the import. No TEST data or API key is used.";textSize=16f;setPadding(0,32,0,0)})
  setContentView(root)
  if(HealthConnectClient.getSdkStatus(this)!=HealthConnectClient.SDK_AVAILABLE)status.text="Health Connect is unavailable. Update it in Android Settings."
 }
 private fun allowAccess(){lifecycleScope.launch{try{val permissions=readPermissions.toMutableSet();if(hc.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)==HealthConnectFeatures.FEATURE_STATUS_AVAILABLE)permissions.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY);permissionLauncher.launch(permissions)}catch(e:Exception){status.text="Could not open Health Connect access. Check Android Settings."}}}
 private fun prepare(history:Boolean){
  prepareButton.isEnabled=false;historyButton.isEnabled=false;status.text="Reading Health Connect and preparing your private file…"
  lifecycleScope.launch{try{
   val data=sync(this@MainActivity,history=history,exportOnly=true)
   pendingExport=data
   saveLauncher.launch("bill-progress-health-${if(history)"90-day" else "30-day"}-${LocalDate.now()}.json")
  }catch(e:SecurityException){status.text=e.message?:"Allow Health Connect access first."}
   catch(e:Exception){status.text="Could not prepare file: ${e.message?:"try again"}"}
   finally{prepareButton.isEnabled=true;historyButton.isEnabled=true}}
 }
}
