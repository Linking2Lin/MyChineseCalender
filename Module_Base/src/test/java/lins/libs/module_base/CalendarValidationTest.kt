package lins.libs.module_base

import java.time.LocalDate
import lins.libs.module_base.model.CHNDate
import lins.libs.module_base.model.CHNDateEntity
import lins.libs.module_base.network.NetworkJson
import org.junit.Assert.*
import org.junit.Test

/**
 * 网络解析与写库前校验的共同边界：允许扩字段，拒绝缺核心字段、错日及不存在的日期。
 */
class CalendarValidationTest {
    private val date = LocalDate.of(2026, 9, 6)

    /**
     * 响应新增字段但公历与农历完整时，验证可解析、可校验并可转换成按日缓存实体。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun additionalFieldsAreAcceptedWhenCoreDateIsValid() {
        val parsed = NetworkJson.decodeFromString<CHNDate>("""{"公历日期":"2026年9月6日 星期日","农历日期":"七月廿五","新增字段":"ignored"}""")
        assertTrue(parsed.isValidFor(date))
        assertEquals("2026-09-06", CHNDateEntity.fromModel(date, parsed).date)
    }

    /**
     * 分别检查缺公历、日期不匹配和农历空白，防止解析成功被误判为业务成功。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun partialPayloadAndWrongDayAreRejected() {
        assertFalse(NetworkJson.decodeFromString<CHNDate>("""{"宜":"测试"}""").isValidFor(date))
        assertFalse(CHNDate(year = "2026年9月5日", lunarDate = "七月廿四").isValidFor(date))
        assertFalse(CHNDate(year = "2026年9月6日", lunarDate = " ").isValidFor(date))
    }

    /**
     * 检查合法格式、闰日和错误前缀，验证解析器拒绝不存在的日期或非日期正文。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun dateParserRejectsImpossibleDatesAndAcceptsKnownFormats() {
        assertNull(CHNDate.parseGregorianDate("2026年2月29日"))
        assertNull(CHNDate.parseGregorianDate("错误:2026年9月6日"))
        assertNull(CHNDate.parseGregorianDate("2026-09-600"))
        assertEquals(date, CHNDate.parseGregorianDate("2026-09-06"))
        assertEquals(date, CHNDate.parseGregorianDate("2026/9/6 星期日"))
        assertEquals(LocalDate.of(2024, 2, 29), CHNDate.parseGregorianDate("2024年2月29日"))
    }
}
