import sub.format
import sub.parseCraftingList
import java.io.File

fun main() {
    val ff14Folder = File("F:/documents/ff14/crafting")
    val listFile = File(ff14Folder, "crafting.txt")

    val entries = parseCraftingList(listFile)
    val newEntries = mutableListOf<sub.Entry>()
    entries.forEach { entry ->
        if (newEntries.contains(entry)) return@forEach

        if (entry.name.endsWith(" hq")) {
            entries.find { it.name + " hq" == entry.name }?.let { newEntries.add(it) }
            newEntries.add(entry)
        } else {
            newEntries.add(entry)
            entries.find { it.name == entry.name + " hq" }?.let { newEntries.add(it) }
        }
    }

    val output = newEntries.joinToString("\n", transform = {it.format()})
    listFile.writeText(output)
}