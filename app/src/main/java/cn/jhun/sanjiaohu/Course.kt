package cn.jhun.sanjiaohu

import java.util.TreeSet
import java.util.regex.Pattern

/**
 * Kotlin port of the original Course.java parser. No dependency on the network
 * or on Android, so the existing Java tests exercise it unchanged.
 *
 * Interop notes, because the rest of the project and the tests read these
 * members as plain fields:
 *   - @JvmField makes each property a real public field, so `c.name` and
 *     `c.weeks.contains(5)` keep compiling from Java. Without it Kotlin would
 *     emit getName()/setName() and every existing call site would break.
 *   - @JvmStatic keeps `Course.parse(...)`, `Course.parseWeeks(...)` and
 *     `Course.merge(...)` callable as Java statics instead of via Companion.
 *   - localId and term stay nullable, because Java compares them against null
 *     (CourseDetailSheet: `c.localId != null`) and against literals.
 *   - `require` throws IllegalArgumentException, matching the original.
 */
class Course {
    @JvmField var name: String = ""
    @JvmField var teacher: String = ""
    @JvmField var room: String = ""
    @JvmField var weekText: String = ""
    @JvmField var raw: String = ""
    @JvmField var localId: String? = null
    @JvmField var term: String? = null
    @JvmField var day: Int = 0
    @JvmField var start: Int = 0
    @JvmField var end: Int = 0
    @JvmField var weeks: MutableSet<Int> = TreeSet()

    companion object {
        private val LINE = Pattern.compile("^(.*?)\\s+([^\\s].*?)[(（]([0-9][0-9,，、\\-－~～单双周()（）\\s]*|-[0-9][0-9,\\-]*)\\s+([^()（）]+)[)）]$")

        @JvmStatic
        fun parse(raw: String, day: Int, period: Int, maxWeek: Int): Course {
            val m = LINE.matcher(raw.trim())
            // Matches the final parenthesis holding a week expression, so
            // parentheses inside the teacher group survive.
            require(m.matches()) { "无法识别课程：" + raw }
            val c = Course()
            c.raw = raw
            // android.jar annotates Matcher.group as nullable; matches() has
            // already succeeded, so all four groups participated in the match.
            c.name = m.group(1)!!.trim()
            c.teacher = m.group(2)!!.trim()
            c.weekText = m.group(3)!!.trim()
            c.room = m.group(4)!!.trim()
            // A course title can contain spaces (大学物理实验Ⅰ ②); the teacher
            // starts at the last whitespace before a teacher group or name.
            val prefix = c.name + " " + c.teacher
            val group = prefix.indexOf("物理组")
            val split = if (group > 0) group - 1 else prefix.lastIndexOf(' ')
            if (split > 0) {
                c.name = prefix.substring(0, split).trim()
                c.teacher = prefix.substring(split + 1).trim()
            }
            c.day = day
            c.start = period
            c.end = period
            c.weeks = parseWeeks(c.weekText, maxWeek)
            require(!(c.name.isEmpty() || c.weeks.isEmpty())) { "课程字段不完整" }
            return c
        }

        @JvmStatic
        fun parseWeeks(input: String, max: Int): MutableSet<Int> {
            val result = TreeSet<Int>()
            var s = input
                .replace('，', ',').replace('、', ',')
                .replace('－', '-').replace('～', '-').replace('~', '-')
                .replace("周", "").replace(Regex("\\s"), "")
            val globalOdd = s.endsWith("(单)") || s.endsWith("（单）")
            val globalEven = s.endsWith("(双)") || s.endsWith("（双）")
            if (globalOdd || globalEven) s = s.substring(0, s.length - 3)
            // Kotlin's split keeps empty parts, as Java's split(",", -1) does.
            for (raw in s.split(",")) {
                val odd = globalOdd || raw.contains("单")
                val even = globalEven || raw.contains("双")
                val part = raw.replace(Regex("[单双()（）]"), "")
                require(part.matches(Regex("\\d+|\\d*-\\d*"))) { "无法识别周次" }
                val a: Int
                val b: Int
                if (part.contains("-")) {
                    val ab = part.split("-")
                    a = if (ab[0].isEmpty()) 1 else ab[0].toInt()
                    b = if (ab[1].isEmpty()) max else ab[1].toInt()
                } else {
                    a = part.toInt()
                    b = a
                }
                require(!(a < 1 || b > max || a > b || (odd && even))) { "周次超出范围" }
                for (w in a..b) if ((!odd || w % 2 == 1) && (!even || w % 2 == 0)) result.add(w)
            }
            return result
        }

        @JvmStatic
        fun merge(input: List<Course>): MutableList<Course> {
            val sorted = ArrayList(input)
            sorted.sortWith(compareBy({ it.key() }, { it.start }))
            val out = ArrayList<Course>()
            for (c in sorted) {
                val prev = if (out.isEmpty()) null else out[out.size - 1]
                if (prev != null && prev.key() == c.key() && prev.end + 1 == c.start &&
                    c.start != 5 && c.start != 9
                ) prev.end = c.end
                else out.add(c)
            }
            out.sortWith(compareBy({ it.day }, { it.start }))
            return out
        }
    }

    fun key(): String = "$day|$name|$teacher|$room|$weeks"
}
