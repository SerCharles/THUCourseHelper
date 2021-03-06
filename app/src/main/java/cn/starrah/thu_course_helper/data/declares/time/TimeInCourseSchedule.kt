package cn.starrah.thu_course_helper.data.declares.time

import androidx.room.TypeConverter
import cn.starrah.thu_course_helper.data.database.CREP
import cn.starrah.thu_course_helper.data.utils.OneBitNumToChineseTable
import cn.starrah.thu_course_helper.data.utils.Verifiable
import cn.starrah.thu_course_helper.data.utils.assertData
import cn.starrah.thu_course_helper.data.utils.chineseName
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.annotation.JSONField
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.round

/**
 * 描述一个按大节定义的时间格式。
 */
data class TimeInCourseSchedule(
    /** 周几。
     *
     * 当定期的情况下此字段必然可以直接获取；当类型为单次的情况下，则可通过[LocalDate.getDayOfWeek]
     * 获取某个日子对应的星期后填入即可。 */
    var dayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,

    /** 第几大节开始。注意第一大节为1。
     *
     * 例如一门课从第二大节一开始（第一小节）就开始上课，持续三小节，则*startBig*=2 */
    var startBig: Int = 0,

    /** 实际开始时间相对于上述大节的开始时间的偏移量。
     *
     * 例如一门课从第二大节一开始（第一小节）就开始上课，持续三小节，则*startOffsetSmall*=0.0f */
    var startOffsetSmall: Float = 0.0f,

    /** 持续的时间长度，以小节为单位
     *
     * 例如一门课从第二大节一开始（第一小节）就开始上课，持续三小节，则*lengthSmall*=3.0f */
    var lengthSmall: Float = 0.0f,

    /** 日期。此字段不一定含有（只在对应的类型为单次的情况下才含有） */
    var date: LocalDate? = null

): Verifiable {
    override fun assertValid() {
        assertData(lengthSmall > 0, "课程的小节长度必须大于0！")
        val (endBig, endOffsetSmall) = _calculateEnd()
        assertData(startBig in 1..CREP.timeRule.bigsCount &&
                endBig in 1..CREP.timeRule.bigsCount, "大节设置不合法！")
        val startBigSmallCount = CREP.timeRule.getBigByNumber(startBig).smallsCount
        assertData(startOffsetSmall >= 0 && startOffsetSmall < startBigSmallCount,
            "开始的小节设置不合法，第${startBig}大节仅有${startBigSmallCount}小节！")
        val endBigSmallCount = CREP.timeRule.getBigByNumber(endBig).smallsCount
        assertData(endOffsetSmall > 0 && endOffsetSmall <= endBigSmallCount,
            "开始的小节设置不合法，第${endBig}大节仅有${endBigSmallCount}小节！")
        date?.let { assertData(CREP.term.isDateInTerm(date!!), "设置的日期不在本学期内！") }
    }

    /** 返回该以大节形式描述的时间定义的汉字格式，前端可使用显示。*/
    val chineseName: String
        @JSONField(serialize = false)
        get() {
            if (endBig == startBig) {
                return "${dayOfWeek.chineseName}第${OneBitNumToChineseTable[startBig]}大节"
            } else {
                return "${dayOfWeek.chineseName}第${OneBitNumToChineseTable[startBig]}至${OneBitNumToChineseTable[endBig]}大节"
            }
        }

    /** 返回该以大节形式描述的时间定义的清华大学通用X-Y格式，前端可使用显示。
     *
     * 例如，周二第三大节显示为"2-3"。*/
    val THUGeneralName: String
        @JSONField(serialize = false)
        get() {
            return (startBig..endBig).map { "${dayOfWeek.value}-${it}" }.joinToString { "," }
        }

    private fun _calculateEnd(): Pair<Int, Float> {
        var remainLen = lengthSmall + startOffsetSmall
        for (i in startBig..CREP.timeRule.bigsCount) {
            val newLen = remainLen - CREP.timeRule.getBigByNumber(i).smallsCount
            if (newLen <= 0) return Pair(i, remainLen)
            remainLen = newLen
        }
        throw RuntimeException("不合法的大节格式时间定义：结束时间超限。")
    }

    /** 结束的时刻位于第几大节内。
     *
     * 例如一门课从第二大节一开始（第一小节）就开始上课，持续三小节，则*endBig*=2 */
    val endBig: Int
        @JSONField(serialize = false)
        get() = _calculateEnd().first

    /** 结束时刻相比于结束大节，发生了多少个小节的偏移。
     *
     * 例如一门课从第二大节一开始（第一小节）就开始上课，持续三小节，则*endOffsetSmall*=3.0f */
    val endOffsetSmall: Float
        @JSONField(serialize = false)
        get() = _calculateEnd().second

    fun toTimeInHour(): TimeInHour {
        val beginSmallClass =
            CREP.timeRule.getBigByNumber(startBig).smalls[startOffsetSmall.toInt()]
        val fractionalSmallBegin = startOffsetSmall - floor(startOffsetSmall)
        val startTime = if (fractionalSmallBegin == 0.0f) beginSmallClass.startTime
        else beginSmallClass.startTime +
                Duration.ofMinutes(round(beginSmallClass.lengthInMinutes * fractionalSmallBegin).toLong())

        val endTime: LocalTime
        val (endBig, endOffsetSmall) = _calculateEnd()
        val endBigClass = CREP.timeRule.getBigByNumber(endBig)
        if (endOffsetSmall >= endBigClass.smallsCount) endTime = endBigClass.endTime
        else {
            val fractionalSmallEnd = endOffsetSmall - floor(endOffsetSmall)
            if (fractionalSmallBegin == 0.0f){
                endTime = endBigClass.smalls[endOffsetSmall.toInt() - 1].endTime
            }
            else {
                val endSmallClass = endBigClass.smalls[endOffsetSmall.toInt()]
                endTime = endSmallClass.startTime +
                        Duration.ofMinutes(round(endSmallClass.lengthInMinutes * fractionalSmallEnd).toLong())
            }
        }

        return TimeInHour(startTime, endTime, dayOfWeek, date)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimeInCourseSchedule) return false

        if (dayOfWeek != other.dayOfWeek) return false
        if (startBig != other.startBig) return false
        if (startOffsetSmall != other.startOffsetSmall) return false
        if (lengthSmall != other.lengthSmall) return false
        if (date != other.date) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dayOfWeek.hashCode()
        result = 31 * result + startBig
        result = 31 * result + startOffsetSmall.hashCode()
        result = 31 * result + lengthSmall.hashCode()
        result = 31 * result + (date?.hashCode() ?: 0)
        return result
    }

    class TC {
        @TypeConverter
        fun toDBDataType(value: TimeInCourseSchedule?): String? {
            return value?.let { JSON.toJSONString(it) }
        }

        @TypeConverter
        fun fromDBDataType(value: String?): TimeInCourseSchedule? {
            return value?.let { JSON.parseObject(it, TimeInCourseSchedule::class.java) }
        }
    }

}