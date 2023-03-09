import sub.parseRecipes
import java.io.File
import kotlin.system.exitProcess
import kotlin.test.assertFalse
import kotlin.test.fail

data class Entry(
    val item: String,
    var amount: Int,
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
    return LetteredListSubId(
        pieces.groupValues[1],
        pieces.groupValues[2].let { if (it.isEmpty()) null else it.toInt() })
}

data class ListIdOrder(
    val expansion: Int,
    val subIdNumber: Int,
    val subIdLetters: String,
    val subIdPartNumber: Int,
    val genericId: String
) : Comparable<ListIdOrder> {
    override fun compareTo(other: ListIdOrder) =
        compareBy<ListIdOrder>(
            { it.expansion },
            { it.subIdNumber },
            { it.subIdLetters },
            { it.subIdPartNumber },
            { it.genericId }).compare(this, other)
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
    val recipeFile = File(ffFolder, "recipes.txt")
    val weirdYieldsFile = File(ffFolder, "weird-yields.txt")

    val lists = createCraftingList(listFiles)

    val entries = lists
        .onEach { println("Processing ${it.id} (HQ=${it.hq})") }
        .flatMap { list -> list.entries.asSequence() }
        .toList()

    println()

    val output = mutableListOf<String>()

    val recipes = parseRecipes(recipeFile)
    val weirdYields = weirdYieldsFile.readLines()

    // When a craftable item has a yield of >1 and is used across multiple lists, the total count can be inaccurate.
    // For example, an item with a yield of 3 could have 5 needed for one list and 4 for another.
    // Each list is independent, so both would need 6, 12 in total. However, only 9 are needed in total.
    // This item would be combined correctly, but the combined list would have enough extra ingredients to make another.
    //
    // To combat this, the plan is to keep a recipe entry for each applicable item. Knowing the yield and the ingredients, quantities can be adjusted automatically.
    // Because CUL and ALC are so dense with these items, they'll be checked if the item is used in multiple lists.
    // WVR will also be checked to catch the threads, but will give some false positives.
    val suspiciousItemsMissingRecipe = mutableListOf<String>()
    val autofixedItems = mutableListOf<String>()
    val missingRecipes = mutableSetOf<String>()
    val fixes = mutableMapOf<String, Int>()

    entries
        .groupBy { it.item }
        .forEach { (item, itemEntries) ->
            val isHq = itemEntries.first().hq
            val firstCraftable = itemEntries.find { it.type == "craft" }
            val firstGatherable = itemEntries.find { it.type == "node" }
            val firstNonCraftable = itemEntries.find { it.type != "craft" }
            val (type, source) =
                if (isHq) {
                    (firstCraftable ?: firstGatherable ?: itemEntries.first()).let { listOf(it.type, it.source) }
                } else {
                    (firstNonCraftable ?: itemEntries.first()).let { listOf(it.type, it.source) }
                }

            if (type != "craft") return@forEach
            val recipe = recipes.firstOrNull { it.name == item }

            if (recipe == null && itemEntries.size > 1 && ("ALC" in source || "CUL" in source || "WVR" in source || item in weirdYields)) {
                suspiciousItemsMissingRecipe += "$item: ${itemEntries.map { it.listId }.joinToString("") { "[$it]" }}"
            } else if (recipe != null) {
                if (recipe.yield == 1) return@forEach

                val totalExtra = itemEntries.sumBy { (recipe.yield - it.amount % recipe.yield) % recipe.yield }
                if (totalExtra < recipe.yield) return@forEach

                val totalExtraCrafts = totalExtra / recipe.yield
                autofixedItems += "$item: $totalExtraCrafts@${recipe.yield} extra -> " + lowerIngredients(recipe, totalExtraCrafts, entries, recipes, missingRecipes, fixes).joinToString(", ") + " " + itemEntries.joinToString("") { "[${it.listId}/${it.amount}]" }
            }
        }

    entries
        .groupBy { it.item }
        .forEach { (item, entries) ->
            val totalAmount = entries.sumBy { it.amount } + (fixes[item] ?: 0)
            val isHq = entries.first().hq
            val firstCraftable = entries.find { it.type == "craft" }
            val firstGatherable = entries.find { it.type == "node" }
            val firstNonCraftable = entries.find { it.type != "craft" }
            val (type, source) =
                if (isHq) {
                    (firstCraftable ?: firstGatherable ?: entries.first()).let { listOf(it.type, it.source) }
                } else {
                    (firstNonCraftable ?: entries.first()).let { listOf(it.type, it.source) }
                }

            output += "$item (0/$totalAmount) ($type: $source) ${entries.joinToString("") { "[${it.listId}/${it.amount}]" }}"
        }

    if (missingRecipes.any()) {
        println("Missing recipes:\n${missingRecipes.joinToString("\n")}")
        return
    }

    if (suspiciousItemsMissingRecipe.any()) {
        println("Suspicious items missing recipes:\n${suspiciousItemsMissingRecipe.joinToString("\n")}")
        return
    }

    println(output.joinToString("\n"))
    println()

    if (autofixedItems.any()) {
        println("Autofixed extras:\n${autofixedItems.joinToString("\n")}\n")
    }
}

fun lowerIngredients(recipe: Recipe, by: Int, entries: List<Entry>, recipes: List<Recipe>, missingRecipes: MutableSet<String>, fixes: MutableMap<String, Int>): List<String> {
    return recipe.ingredients.flatMap { ing ->
        val ingEntry = entries.find { it.item == ing.name }!!
        val ingDiff = by * ing.quantity
        val originalFix = fixes[ing.name] ?: 0
        fixes[ing.name] = originalFix - ingDiff

        if (ingEntry.type != "craft") return@flatMap listOf("-$ingDiff ${ing.name}")

        val ingRecipe = recipes.find { it.name == ing.name }
        if (ingRecipe == null) {
            missingRecipes += ing.name
            listOf("-$ingDiff ${ing.name}")
        } else {
            // Harder example: item is lowered by 1, so all ingredients are lowered by 1x.
            // One ingredient is a craft with a yield of 3. Now we have to make sure its ingredients are touched only when it falls to or past a multiple of 3.

            val start = ingEntry.amount + originalFix
            val startExcess = start % ingRecipe.yield
            if (ingDiff >= startExcess) {
                listOf("-$ingDiff ${ing.name}") + lowerIngredients(ingRecipe, ingDiff / ingRecipe.yield, entries, recipes, missingRecipes, fixes)
            } else {
                listOf("-$ingDiff ${ing.name}")
            }
        }
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
                    approxCols[0].trim('"').let { if (isHq) "$it HQ" else it }.toLowerCase(),
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