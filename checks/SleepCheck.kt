package com.billlicht.progress
import java.time.Instant
fun main(){
 fun i(a:String,b:String)=SleepInterval(Instant.parse(a),Instant.parse(b))
 fun checkHours(expected:Double,vararg intervals:SleepInterval){check(kotlin.math.abs(sleepHours(intervals.toList())-expected)<0.000001)}
 val night=i("2026-09-14T23:00:00Z","2026-09-15T07:00:00Z")
 checkHours(8.0,night) // midnight does not split the session
 checkHours(8.0,night,night) // duplicate writers
 checkHours(9.0,night,i("2026-09-15T06:00:00Z","2026-09-15T08:00:00Z"))
 checkHours(7.5,i("2026-09-14T23:00:00Z","2026-09-15T03:00:00Z"),i("2026-09-15T03:30:00Z","2026-09-15T07:00:00Z")) // awake gap excluded
 checkHours(9.0,night,i("2026-09-15T14:00:00Z","2026-09-15T15:00:00Z")) // nap included
 checkHours(0.0)
 checkHours(8.0,i("2026-09-15T02:00:00Z","2026-09-15T04:00:00Z"),night) // sorting, contained interval
 println("7 sleep regression cases passed")
}
