package com.billlicht.progress

import java.time.Instant
import java.time.Duration

data class SleepInterval(val start:Instant,val end:Instant)
// Union, rather than addition, prevents overlapping writers from counting twice.
fun sleepHours(intervals:List<SleepInterval>):Double {
 val sorted=intervals.filter{it.start<it.end}.sortedBy{it.start}
 if(sorted.isEmpty())return 0.0
 var start=sorted[0].start;var end=sorted[0].end;var seconds=0L
 for(next in sorted.drop(1)){if(next.start<=end){if(next.end>end)end=next.end}else{seconds+=Duration.between(start,end).seconds;start=next.start;end=next.end}}
 return (seconds+Duration.between(start,end).seconds)/3600.0
}
