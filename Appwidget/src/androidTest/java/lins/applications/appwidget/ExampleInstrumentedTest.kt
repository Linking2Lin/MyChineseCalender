package lins.applications.appwidget

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/** Android 仪器测试的模板基础用例，仅核对目标 Context 包名，不验证页面或数据库。 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    /**
     * 检查仪器测试取得的目标应用 Context 与该模块预期包名一致；需在 Android 设备运行。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test
    fun useAppContext() {
        // 使用被测目标的 Context，不使用测试 APK 自身的 Context。
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("lins.applications.appwidget.test", appContext.packageName)
    }
}