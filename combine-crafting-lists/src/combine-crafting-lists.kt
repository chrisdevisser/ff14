import java.io.File
import kotlin.system.exitProcess
import kotlin.test.assertFalse
import kotlin.test.fail

data class Entry(
    val item: String,
    val amount: Int,
    val type: String,
    val source: String,
    val listId: ListId,
    val hq: Boolean
)

enum class Expansion {
    ARR,
    HW,
    SB,
    ShB
}

fun parseExpansion(s: String) =
    when (s) {
        "ARR" -> Expansion.ARR
        "HW" -> Expansion.HW
        "SB" -> Expansion.SB
        "ShB" -> Expansion.ShB
        else -> fail("Invalid expansion name")
    }

sealed class ListSubId
data class NumberedListSubId(val number: Int) : ListSubId() {
    override fun toString(): String {
        return number.toString()
    }
}

data class LetteredListSubId(
    val letters: String,
    val partNumber: Int?
) : ListSubId() {
    override fun toString(): String {
        return letters + (partNumber?.toString() ?: "");
    }
}

fun parseListSubId(s: String): ListSubId {
    if (s.all { it.isDigit() }) {
        return NumberedListSubId(s.toInt())
    }

    val pieces = Regex("""([A-Za-z]+)(\d*)""").matchEntire(s)!!
    return LetteredListSubId(pieces.groupValues[1], pieces.groupValues[2].let { if (it.isEmpty()) null else it.toInt() })
}

data class ListIdOrder(
    val expansion: Int,
    val subIdNumber: Int,
    val subIdLetters: String,
    val subIdPartNumber: Int,
    val genericId: String
) : Comparable<ListIdOrder> {
    override fun compareTo(other: ListIdOrder) =
        compareBy<ListIdOrder>({it.expansion}, {it.subIdNumber}, {it.subIdLetters}, {it.subIdPartNumber}, {it.genericId}).compare(this, other)
}

sealed class ListId {
    fun ordered() =
        when (this) {
            is GenericListId -> ListIdOrder(Int.MAX_VALUE, Int.MAX_VALUE, Char.MAX_VALUE.toString(), Int.MAX_VALUE, id)
            is ExpansionListId -> ListIdOrder(
                expansion.ordinal,
                (subId as? NumberedListSubId)?.number ?: Int.MAX_VALUE,
                (subId as? LetteredListSubId)?.letters ?: Char.MAX_VALUE.toString(),
                (subId as? LetteredListSubId)?.partNumber ?: Int.MAX_VALUE,
                Char.MAX_VALUE.toString()
            )
        }
}

data class ExpansionListId(
    val expansion: Expansion,
    val subId: ListSubId
) : ListId() {
    override fun toString(): String {
        return expansion.toString() + subId.toString()
    }
}

data class GenericListId(val id: String) : ListId() {
    override fun toString(): String {
        return id
    }
}

fun parseListId(s: String): ListId {
    return when (val pieces = Regex("""(${Expansion.values().joinToString("|")})(\d+|[A-Za-z]+\d*)""").find(s)) {
        null -> GenericListId(s)
        else -> ExpansionListId(parseExpansion(pieces.groupValues[1]), parseListSubId(pieces.groupValues[2]))
    }
}

data class CraftingList(
    val hq: Boolean,
    val id: ListId,
    val entries: List<Entry>
)

fun main() {
    val ffFolder = File("F:/documents/ff14/crafting")
    val listFiles = ffFolder.walkTopDown().filter { it.extension == "csv" }

    val lists = createCraftingList(listFiles)

    val entries = lists
        .onEach { println("Processing ${it.id} (HQ=${it.hq})") }
        .flatMap { list -> list.entries.asSequence() }

    entries
        .groupBy { it.item }
        .forEach { (item, entries) ->
            val totalAmount = entries.sumBy { it.amount }
            val isHq = entries.first().hq
            val firstCraftable = entries.find {it.type == "craft"}
            val firstGatherable = entries.find {it.type == "node"}
            val firstNonCraftable = entries.find {it.type != "craft"}
            val (type, source) =
                if (isHq) {
                    (firstCraftable ?: firstGatherable ?: entries.first()).let {listOf(it.type, it.source)}
                } else {
                    (firstNonCraftable ?: entries.first()).let {listOf(it.type, it.source)}
                }

            println("$item (0/$totalAmount) ($type: $source) ${entries.joinToString("") { "[${it.listId}/${it.amount}]" }}")
        }
}

fun createCraftingList(listFiles: Sequence<File>): Sequence<CraftingList> {
    val lists = listFiles
        .map { file ->
            val isHq = file.nameWithoutExtension.endsWith("-hq")
            val rawListId = file.nameWithoutExtension.let { if (isHq) it.dropLast("-hq".length) else it }
            val listId = parseListId(rawListId)

            val entries = file.readLines().filter { it.contains(Regex(",\\d+,")) }.map { line ->
                val approxCols = line.split(',')
                Entry(
                    approxCols[0].trim('"').let { if (isHq) "$it HQ" else it },
                    approxCols[1].toInt(),
                    approxCols[2],
                    approxCols.drop(3).joinToString().trim('"'),
                    listId,
                    isHq
                )
            }

            CraftingList(isHq, listId, entries)
        }.sortedBy { it.id.ordered() }
    return lists
}