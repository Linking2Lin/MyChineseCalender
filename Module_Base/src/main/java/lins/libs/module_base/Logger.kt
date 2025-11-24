package lins.libs.module_base

import com.elvishew.xlog.XLog

object Logger {
    fun d(tag:String,mess:String){
        XLog
            .tag(tag)
            .d(mess)
    }
}