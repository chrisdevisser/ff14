import sub.missingRecipes
import sub.parseCraftingList
import sub.parseRecipes
import java.io.File
import java.lang.Integer.max
import java.lang.Integer.min

fun main() {
    val ff14Folder = File("F:/documents/ff14/crafting")
    val listFile = File(ff14Folder, "crafting.txt")
    val recipeFile = File(ff14Folder, "recipes.txt")
    val sublistFiles = listOf(File(ff14Folder, "hw/HWa1-hq.csv"))

    val recipes = parseRecipes(recipeFile)
    val entries = parseCraftingList(listFile).toMutableList()
    val noEntries = mutableListOf<Pair<String, List<String>>>()
    val noRecipes = mutableListOf<String>()

    sublistFiles.forEach { sublistFile ->
        val sublist = createCraftingList(listOf(sublistFile).asSequence()).first()
        assert(sublist.hq)
        val sublistEntries = sublist.entries

        sublistEntries.forEach inner@ { sub ->
            val mainEntry = entries.find { it.name == sub.item.toLowerCase() }
            if (mainEntry == null) {
                noEntries += sub.item.toLowerCase() to emptyList()
                return@inner
            }

            lowerQuality(sub, mainEntry, entries)
        }
    }

    println("\nMissing entries:")
    noEntries.distinct().forEach {
        println("${it.first}: ${it.second.joinToString("->")}")
    }

    val output = entries.joinToString("\n") {
        with(it) {
            "$name (${if (total != 0) "$current/$total" else "ISH"})${if (source.any()) " ($source)" else ""} $tags"
        }
    }
//    listFile.writeText(output)
}

fun lowerQuality(
    subEntry: Entry,
    mainEntry: sub.Entry,
    entries: MutableList<sub.Entry>
) {
    val quantity = subEntry.amount
    var nqEntry = entries.find { it.name == mainEntry.name.substringBeforeLast(' ') }
    if (nqEntry == null) {
        println("Adding NQ entry for ${mainEntry.name} | $quantity")
        nqEntry = sub.Entry(mainEntry.name.substringBeforeLast(' '), 0, 0, mainEntry.isCraft, mainEntry.source, null, mainEntry.tags)
        entries.add(0, nqEntry)
    }

    val prevCurrent = mainEntry.current
    val prevTotal = mainEntry.total
    val prevNqCurrent = nqEntry.current
    val prevNqTotal = nqEntry.total

    assert(quantity <= mainEntry.total)

    val leftover = max(quantity - mainEntry.total + mainEntry.current, 0)
    mainEntry.total -= quantity
    mainEntry.current = min(mainEntry.current, mainEntry.total)
    if (mainEntry.current == mainEntry.total) {
        println("Removing entry ${mainEntry.name}")
        entries -= mainEntry
    }

    nqEntry.total += quantity

    if (nqEntry.isCraft && nqEntry.current + leftover >= nqEntry.total) {
        println("Maxed ${nqEntry.name}")
        nqEntry.current = nqEntry.total - 1
    } else {
        nqEntry.current += leftover
    }

    println("Updated entry ${mainEntry.name} | $quantity ($prevCurrent/$prevTotal) -> (${mainEntry.current}/${mainEntry.total})")
    println("Updated entry ${nqEntry.name} | $quantity ($prevNqCurrent/$prevNqTotal) -> (${nqEntry.current}/${nqEntry.total})")
}