package lins.libs.module_base

import org.junit.Test

import org.junit.Assert.*

/** 本地 JUnit 环境的模板基础用例；日历、图片和网络行为由各业务测试类覆盖。 */
class ExampleUnitTest {
    /**
     * 检查本地 JUnit 断言能否正常执行；这是模板基础用例，不覆盖业务行为。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}