@file:Suppress("UNUSED_PARAMETER")

package cn.starrah.thu_course_helper.activity

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.starrah.thu_course_helper.R
import cn.starrah.thu_course_helper.data.database.CREP
import cn.starrah.thu_course_helper.data.declares.calendarEntity.CalendarItemDataWithTimes
import cn.starrah.thu_course_helper.data.declares.calendarEntity.CalendarTimeData
import cn.starrah.thu_course_helper.data.declares.calendarEnum.CalendarTimeType
import cn.starrah.thu_course_helper.data.declares.time.TimeInCourseSchedule
import cn.starrah.thu_course_helper.data.utils.getNotNullValue
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime


/**
 * 描述：编辑/新建日程活动
 */
class ItemEditActivity : AppCompatActivity(){

    //当前的元素
    private var currentItem: CalendarItemDataWithTimes? = null

    //当前元素的id，如果没有就-1
    private var currentID:Int = -1

    private var mRecyclerView: RecyclerView? = null
    private var mAdapter: ItemEditAdapter? = null


    companion object {
        /**
         * 描述：将时间转换成 08:00 这种形式
         */
        fun getTimeString(time:LocalTime):String {
            var hour = "" + time.hour
            var minute = "" + time.minute
            if(hour.length != 2) {
                hour = "0" + hour
            }
            if(minute.length != 2) {
                minute = "0" + minute
            }
            return hour + ":" + minute
        }

        /**
         * 描述：将周列表转换成字符串
         * @param ：int类型列表, 代表周列表
         * @return ：显示的字符串
         * @see ：全周，前半学期，后半学期，单周，双周，考试周，第1,2,3,4,6周，etc
         */
        fun getWeeksString(week_list:MutableList<Int>):String {
            //先map映射，去重+排序
            val week_map:MutableMap<Int, Boolean> = mutableMapOf()
            for(item in week_list) {
                week_map[item] = true
            }
            val total_weeks = CREP.term.totalWeekCount
            val normal_weeks = CREP.term.normalWeekCount

            //一个filter，用于筛选全周，前八周，后八周，单，双周，考试周
            //有考试周--非考试周的全false，有正常周的，考试周false
            var i = 1
            val week_list2:MutableList<Int> = mutableListOf()
            var whether_really_full = true
            var whether_full = true
            var whether_first_eight = true
            var whether_last_eight = true
            var whether_single = true
            var whether_double = true
            var whether_exam = true
            while(i <= total_weeks) {
                val result:Boolean? = week_map[i]

                if(result == true) {
                    if(i <= normal_weeks) {
                        whether_exam = false
                        if(i <= normal_weeks / 2) {
                            whether_last_eight = false
                        }
                        else {
                            whether_first_eight = false
                        }
                        if(i % 2 == 1) {
                            whether_double = false
                        }
                        else {
                            whether_single = false
                        }
                    }
                    else {
                        whether_full = false
                        whether_first_eight = false
                        whether_last_eight = false
                        whether_single = false
                        whether_double = false
                    }
                    week_list2.add(i)
                }
                else {
                    whether_really_full = false
                    if(i <= normal_weeks) {
                        whether_full = false
                    }
                    if(i <= normal_weeks && i <= normal_weeks / 2) {
                        whether_first_eight = false
                    }
                    if(i <= normal_weeks && i > normal_weeks / 2) {
                        whether_last_eight = false
                    }
                    if(i <= normal_weeks && i % 2 == 1) {
                        whether_single = false
                    }
                    if(i <= normal_weeks && i % 2 == 0) {
                        whether_double = false
                    }
                    if(i > normal_weeks) {
                        whether_exam = false
                    }
                }
                i ++
            }

            //判断是否是几种特殊情况
            var result: String
            if(whether_really_full) {
                result = "全学期（含考试周）"
            }
            else if(whether_full) {
                result = "全学期（不含考试周）"
            }
            else if(whether_first_eight) {
                result = "前半学期"
            }
            else if(whether_last_eight) {
                result = "后半学期"
            }
            else if(whether_single) {
                result = "单周"
            }
            else if(whether_double) {
                result = "双周"
            }
            else if(whether_exam) {
                result = "考试周"
            }
            else if(week_list2.size <= 0) {
                result = "空"
            }
            else {
                result = "第"
                for(i in week_list2.indices) {
                    result = result + week_list2[i]
                    if(i != week_list2.size - 1) {
                        result = result + ","
                    }
                }
                result = result + "周"
            }
            return result
        }

        /**
         *描述：隐藏控件
         *参数：自己
         *返回：无
         */
        fun HideItem(item:LinearLayout) {
            val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT , 0)
            item.layoutParams = params
        }

        /**
         *描述：显示控件
         *参数：自己
         *返回：无
         */
        fun ShowItem(item:LinearLayout) {
            //和style一致
            val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT , 120)
            item.layoutParams = params
        }


        /**
         *描述：显示控件(edittext)
         *参数：自己
         *返回：无
         */
        fun ShowEdit(item:LinearLayout) {
            //和style一致
            val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT , ViewGroup.LayoutParams.WRAP_CONTENT)
            item.layoutParams = params
        }

        /**
         * 描述：将一个5min-1天的duration转换成中文字符串
         * 参数：时间
         * 返回：中文字符串
         */
        fun getAheadTimeString(time: Duration) :String {
            val remind_minutes = time.toMinutes().toInt()
            var remind_string: String
            if(remind_minutes >= 1440) {
                remind_string = "一天"
            }
            else if(remind_minutes < 60) {
                remind_string = "" + remind_minutes + "分钟"
            }
            else {
                val remind_hour = remind_minutes / 60
                val remind_minute:Int = remind_minutes % 60
                remind_string = "" + remind_hour + "小时"
                if(remind_minute != 0) {
                    remind_string = remind_string + "" + remind_minute + "分钟"
                }
            }
            return remind_string
        }
    }

    /**
     * 描述：初始化
     * @param savedInstanceState 存储的data，其实只有待编辑活动的ID，如果找不到intent就是新建，弄一个新的
     */
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.calendar_item_edit)

        val intent = intent
        val message = intent.getIntExtra(ItemShowActivity.EXTRA_MESSAGE, -1)
        currentID = message


        lifecycleScope.launch {
            //数据获取
            if(currentID < 0) {
                getInitData()
            }
            else {
                getData()
            }

            mRecyclerView = findViewById(R.id.recycler_view)
            mAdapter = ItemEditAdapter(currentItem!!, this@ItemEditActivity)
            mRecyclerView!!.setAdapter(mAdapter)
            mRecyclerView!!.setLayoutManager(LinearLayoutManager(this@ItemEditActivity))
        }

    }

    /**
     * 描述：新建日程的时候，建立一个新id
     * 参数：无
     * 返回：无
     */
    suspend fun getInitData() {
        currentItem = CalendarItemDataWithTimes()
    }

    /**
     * 描述：根据id从数据库获取数据，id是类变量showID，读取的数据存在showItem里
     * 参数：无
     * 返回：无
     */
    suspend fun getData() {
        val list: List<Int> = listOf(currentID)
        val the_item = CREP.findItemsByIds(list)
        val size = the_item.getNotNullValue().size
        if(size <= 0 || size > 1) {
            Toast.makeText(this, "未找到数据!", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        currentItem = the_item.getNotNullValue()[0]
        if(currentItem == null) {
            Toast.makeText(this, "未找到数据!", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }





    /**
     * 描述：用于保存时，用于判断当前currentItem是否符合要求
     * 参数：无
     * 返回：符合true，不符合false,并且报错
     */
    fun integrityCheck() :Boolean{
        if(currentItem == null) {
            Toast.makeText(this, "未找到数据!", Toast.LENGTH_LONG).show()
            return false
        }
        if(currentItem?.times == null || currentItem!!.times.size < 1) {
            Toast.makeText(this, "当前日程没有任何时间段!", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    /**
     * 描述：保存修改
     * 参数：无
     * 返回：无
     */
    suspend fun saveItem() {
        if(currentID < 0) {
            currentItem!!.id = 0
        }
        CREP.updateItemAndTimes(currentItem!!)
    }


    //对话框显示函数
    /**
     * 描述：返回时的对话框，如果确定，就不保存就返回，否则继续
     * 参数：无
     * 返回：无
     */
    private fun showDialogReturn() {
        val dialog: AlertDialog.Builder =
            object : AlertDialog.Builder(this@ItemEditActivity) {
                override fun create(): AlertDialog {
                    return super.create()
                }

                override fun show(): AlertDialog {
                    return super.show()
                }
            }
        dialog.setOnCancelListener { }
        dialog.setOnDismissListener { }
        dialog.setIcon(R.drawable.logo)
            .setTitle("返回详情")
            .setMessage("您的编辑未保存，确定要不保存直接退出吗？")
            .setCancelable(true)
            .setPositiveButton("确定"
            ) { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish() }
            .setNegativeButton("取消"
            ) { _, _ ->  }
        dialog.show()
    }

    /**
     * 描述：保存时的对话框，如果确定，就保存然后返回，否则取消
     * 参数：无
     * 返回：无
     */
    private fun showDialogSave() {
        val dialog: AlertDialog.Builder =
            object : AlertDialog.Builder(this@ItemEditActivity) {
                override fun create(): AlertDialog {
                    return super.create()
                }

                override fun show(): AlertDialog {
                    return super.show()
                }
            }
        dialog.setOnCancelListener { }
        dialog.setOnDismissListener { }
        dialog.setIcon(R.drawable.logo)
            .setTitle("保存日程")
            .setMessage("确定要保存日程吗")
            .setCancelable(true)
            .setPositiveButton("确定",
                { _, _ ->
                    lifecycleScope.launch{
                        try {
                            saveItem()
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                        catch (e: Exception) {
                            Toast.makeText(this@ItemEditActivity, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                })
            .setNegativeButton("取消",
                DialogInterface.OnClickListener { _, _ ->  })
        dialog.show()
    }

    //按钮绑定函数
    /**
     * 描述：处理返回按钮的事件--返回
     * 参数：无
     * 返回：无
     */
    fun handleReturn(view: View) {
        showDialogReturn()
    }

    /**
     * 描述：处理添加按钮的事件--添加
     * 参数：无
     * 返回：无
     */
    fun handleAdd(view: View) {
        val new_time_data:TimeInCourseSchedule = TimeInCourseSchedule(dayOfWeek = LocalDate.now().dayOfWeek, date = LocalDate.now(), startBig = 1)
        val newTime:CalendarTimeData = CalendarTimeData(item_id = currentItem!!.id, type = CalendarTimeType.SINGLE_COURSE, timeInCourseSchedule = new_time_data, timeInHour = null)
        currentItem!!.times.add(newTime)
        mAdapter!!.notifyDataSetChanged()
        mRecyclerView!!.scrollToPosition(mAdapter!!.itemCount - 1)
    }

    /**
     * 描述：处理保存按钮的事件--保存并且返回
     * 参数：无
     * 返回：无
     */
    fun handleSave(view: View) {
        //判断是否合法
        if(integrityCheck()) {
            //对话框，保存
            showDialogSave()
        }
    }



}