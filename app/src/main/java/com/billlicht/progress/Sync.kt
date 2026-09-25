package com.billlicht.progress

import android.content.Context
import android.webkit.CookieManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

const val DASHBOARD="https://bill-health-progress.blicht74.chatgpt.site"
val DASHBOARD_ZONE:ZoneId=ZoneId.of("America/New_York")
val recordTypes=setOf(WeightRecord::class,BodyFatRecord::class,LeanBodyMassRecord::class,BodyWaterMassRecord::class,BloodPressureRecord::class,HeartRateRecord::class,StepsRecord::class,DistanceRecord::class,SleepSessionRecord::class,ExerciseSessionRecord::class)
val readPermissions=recordTypes.map{HealthPermission.getReadPermission(it)}.toSet()

class SyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result=try{sync(applicationContext,false,true);Result.success()}catch(e:SecurityException){applicationContext.getSharedPreferences("sync",0).edit().putString("status","Open importer: access or sign-in needs attention.").apply();Result.failure()}catch(e:Exception){applicationContext.getSharedPreferences("sync",0).edit().putString("status","Sync delayed. Open the importer to check your connection.").apply();Result.retry()}
}

suspend fun sync(context:Context,history:Boolean=false,background:Boolean=false,exportOnly:Boolean=false):String=withContext(Dispatchers.IO){
 val hc=HealthConnectClient.getOrCreate(context)
 val granted=hc.permissionController.getGrantedPermissions()
 if(background&&!granted.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND))throw SecurityException("Background access is not granted")
 if(granted.intersect(readPermissions).isEmpty())throw SecurityException("Allow Health Connect access first")
 val prefs=context.getSharedPreferences("sync",0)
 val now=Instant.now();val hasHistory=granted.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
 val last=if(exportOnly||prefs.getInt("sleepFormat",0)<3)0L else prefs.getLong("checkpoint",0)
 val defaultStart=now.minus(29,ChronoUnit.DAYS)
 val start=if(history&&hasHistory)now.minus(90,ChronoUnit.DAYS) else if(last>0&&!history){val since=Instant.ofEpochMilli(last).minus(7,ChronoUnit.DAYS);if(hasHistory||since>defaultStart)since else defaultStart}else defaultStart
 val filter=TimeRangeFilter.between(start,now)
 var processed=0
 val exported=JSONArray()
 var exportedReport:JSONObject?=null
 val counts=mutableMapOf<KClass<out Record>,Int>()
 fun upload(items:JSONArray,report:JSONObject?=null){if(items.length()==0&&report==null)return
 if(items.length()>500){for(offset in 0 until items.length() step 500){val part=JSONArray();for(i in offset until minOf(offset+500,items.length()))part.put(items.getJSONObject(i));upload(part)};return}
  if(exportOnly){for(i in 0 until items.length())exported.put(items.getJSONObject(i));if(report!=null)exportedReport=report;return}
  val cookies=CookieManager.getInstance().getCookie(DASHBOARD) ?: throw SecurityException("Sign in to your dashboard in this app first")
  val conn=URL("$DASHBOARD/api/readings").openConnection() as HttpURLConnection
  try{conn.requestMethod="POST";conn.instanceFollowRedirects=false;conn.connectTimeout=20000;conn.readTimeout=30000;conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Cookie",cookies);conn.outputStream.use{it.write(JSONObject().put("records",items).apply{if(report!=null){put("diagnostics",report);put("replaceCalendarSleep",granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class)))}}.toString().toByteArray())}
   val code=conn.responseCode
   if(code==401||code==403||code in 300..399)throw SecurityException("Sign in again inside the importer")
   if(code !in 200..299)throw IllegalStateException("The dashboard could not accept this batch ($code)")
   conn.inputStream.use{it.readBytes()};processed+=items.length()
  }finally{conn.disconnect()}
 }
 fun item(id:String,metric:String,time:Instant,value:Double,source:String,value2:Double?=null,startTime:Instant?=null,endTime:Instant?=null)=JSONObject().put("id",id).put("metric",metric).put("time",time.toString()).put("value",value).put("source",source).apply{if(value2!=null)put("value2",value2);if(startTime!=null)put("start_time",startTime.toString());if(endTime!=null)put("end_time",endTime.toString())}
 suspend fun <T:Record> raw(type:KClass<T>,convert:(T)->JSONObject?){if(!granted.contains(HealthPermission.getReadPermission(type)))return
  var token:String?=null
  do{val result=hc.readRecords(ReadRecordsRequest(recordType=type,timeRangeFilter=filter,pageSize=500,pageToken=token));val batch=JSONArray();counts[type]=(counts[type]?:0)+result.records.size;result.records.forEach{convert(it)?.let(batch::put)};upload(batch);token=result.pageToken}while(token!=null)
 }
 fun source(r:Record)=r.metadata.dataOrigin.packageName
 fun id(r:Record)= "hc:${source(r)}:${r.metadata.id}"
 raw(WeightRecord::class){item(id(it),"weight",it.time,it.weight.inPounds,source(it))}
 raw(BodyFatRecord::class){item(id(it),"bodyfat",it.time,it.percentage.value,source(it))}
 raw(LeanBodyMassRecord::class){item(id(it),"leanmass",it.time,it.mass.inPounds,source(it))}
 raw(BodyWaterMassRecord::class){item(id(it),"bodywater",it.time,it.mass.inPounds,source(it))}
 raw(BloodPressureRecord::class){item(id(it),"bp",it.time,it.systolic.inMillimetersOfMercury,source(it),it.diastolic.inMillimetersOfMercury)}
 raw(ExerciseSessionRecord::class){item(id(it),"workout",it.endTime,java.time.Duration.between(it.startTime,it.endTime).seconds/60.0,source(it))}
 // Keep complete sleep sessions together and union overlapping sleep intervals.
 val sleepSessions=mutableListOf<SleepSessionRecord>()
 raw(SleepSessionRecord::class){sleepSessions.add(it);null}
 val sleepDays=sleepSessions.groupBy{it.endTime.atZone(DASHBOARD_ZONE).toLocalDate()}
 val sleeps=JSONArray()
 for((wakeDate,sessions) in sleepDays){
  // Prefer staged records where an unstaged duplicate overlaps the same session.
  val selected=sessions.filter{r->r.stages.isNotEmpty()||sessions.none{other->other.stages.isNotEmpty()&&other.startTime<r.endTime&&other.endTime>r.startTime}}
  val intervals=selected.flatMap{r->if(r.stages.isEmpty())listOf(SleepInterval(r.startTime,r.endTime)) else r.stages.filter{it.stage in setOf(SleepSessionRecord.STAGE_TYPE_SLEEPING,SleepSessionRecord.STAGE_TYPE_LIGHT,SleepSessionRecord.STAGE_TYPE_DEEP,SleepSessionRecord.STAGE_TYPE_REM)}.map{SleepInterval(it.startTime,it.endTime)}}
  val hours=sleepHours(intervals)
  if(hours>0){
   val origins=selected.map(::source).distinct().joinToString(", ")
   val note=if(selected.any{it.stages.isEmpty()})" (includes session duration without stages)" else ""
   val wake=sessions.maxOf{it.endTime}
   val bedtime=sessions.minOf{it.startTime}
   sleeps.put(item("hc:sleep-day:$wakeDate","sleep",wake,hours,(origins+note).take(150),startTime=bedtime,endTime=wake))
   fun stageHours(stage:Int)=sleepHours(selected.flatMap{r->r.stages.filter{it.stage==stage}.map{SleepInterval(it.startTime,it.endTime)}})
   val light=stageHours(SleepSessionRecord.STAGE_TYPE_LIGHT)
   val deep=stageHours(SleepSessionRecord.STAGE_TYPE_DEEP)
   val rem=stageHours(SleepSessionRecord.STAGE_TYPE_REM)
   val awake=stageHours(SleepSessionRecord.STAGE_TYPE_AWAKE)+stageHours(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)
   if(light>0)sleeps.put(item("hc:sleep-light-day:$wakeDate","sleep_light",wake,light,origins.take(150)))
   if(deep>0)sleeps.put(item("hc:sleep-deep-day:$wakeDate","sleep_deep",wake,deep,origins.take(150)))
   if(rem>0)sleeps.put(item("hc:sleep-rem-day:$wakeDate","sleep_rem",wake,rem,origins.take(150)))
   if(awake>0)sleeps.put(item("hc:sleep-awake-day:$wakeDate","sleep_awake",wake,awake,origins.take(150)))
   val inBed=sleepHours(selected.map{SleepInterval(it.startTime,it.endTime)})
   if(inBed>0){
    sleeps.put(item("hc:sleep-inbed-day:$wakeDate","sleep_in_bed",wake,inBed,origins.take(150)))
    sleeps.put(item("hc:sleep-efficiency-day:$wakeDate","sleep_efficiency",wake,(hours/inBed*100.0).coerceIn(0.0,100.0),origins.take(150)))
   }
  }
 }
 upload(sleeps)
 // Keep Health Connect's deduplication for activity totals.
 val zone=DASHBOARD_ZONE;var day=start.atZone(zone).toLocalDate();val today=LocalDate.now(zone)
 // Never upload a partial historical day under a full-day ID. That would replace
 // a previously complete total with only the tail of the day on later syncs.
 if(!hasHistory&&day.atStartOfDay(zone).toInstant()<defaultStart)day=day.plusDays(1)
 val aggregateBatch=JSONArray()
 while(!day.isAfter(today)){
  val from=day.atStartOfDay(zone).toInstant();val next=day.plusDays(1).atStartOfDay(zone).toInstant();val to=if(next>now)now else next
  if(from<to){val range=TimeRangeFilter.between(from,to);val batch=JSONArray()
   if(granted.contains(HealthPermission.getReadPermission(StepsRecord::class))){val a=hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL),range));a[StepsRecord.COUNT_TOTAL]?.let{batch.put(item("hc:aggregate:steps:$day","steps",to.minusSeconds(1),it.toDouble(),"HealthConnect.aggregate"))}}
   if(granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))){val a=hc.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL),range));a[DistanceRecord.DISTANCE_TOTAL]?.let{batch.put(item("hc:aggregate:distance:$day","distance",to.minusSeconds(1),it.inMeters/1609.344,"HealthConnect.aggregate"))}}
   if(granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))){val a=hc.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG),range));a[HeartRateRecord.BPM_AVG]?.let{batch.put(item("hc:aggregate:pulse:$day","pulse",to.minusSeconds(1),it.toDouble(),"HealthConnect.aggregate"))}}
   for(i in 0 until batch.length())aggregateBatch.put(batch.getJSONObject(i))
  };day=day.plusDays(1)
 }
 upload(aggregateBatch)
 fun diagnostic(type:KClass<out Record>)=JSONObject().put("granted",granted.contains(HealthPermission.getReadPermission(type))).put("count",counts[type]?:0)
 val report=JSONObject().put("start",start.toString()).put("end",now.toString()).put("leanmass",diagnostic(LeanBodyMassRecord::class)).put("bodywater",diagnostic(BodyWaterMassRecord::class)).put("sleep",diagnostic(SleepSessionRecord::class))
 upload(JSONArray(),report)
 if(exportOnly){
  if(exported.length()==0)throw IllegalStateException("Health Connect returned no readings in this period")
  val document=JSONObject().put("version",1).put("records",exported).put("diagnostics",exportedReport)
  val serialized=document.toString()
  if(serialized.toByteArray(Charsets.UTF_8).size>9_000_000)throw IllegalStateException("Export is over 9 MB. Prepare the 30-day file instead")
  return@withContext serialized
 }
 fun summary(type:KClass<out Record>)=if(!granted.contains(HealthPermission.getReadPermission(type)))"access not granted" else "${counts[type]?:0} source records"
 val result="Synced $processed readings at ${java.time.ZonedDateTime.now().toLocalTime().truncatedTo(ChronoUnit.MINUTES)}.\nLean mass: ${summary(LeanBodyMassRecord::class)}; body water: ${summary(BodyWaterMassRecord::class)}; sleep: ${summary(SleepSessionRecord::class)}."
 prefs.edit().putInt("sleepFormat",3).putLong("checkpoint",now.toEpochMilli()).putString("status",result).apply();result
}
