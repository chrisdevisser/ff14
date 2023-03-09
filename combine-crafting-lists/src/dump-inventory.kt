package sub

import Ingredient
import java.io.File
import Recipe
import java.lang.Integer.min
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class Entry(
    val name: String,
    var current: Int,
    var total: Int,
    val isCraft: Boolean,
    val source: String,
    val recipe: String?,
    val tags: String
)

fun parseRecipes(recipeList: File): List<Recipe> = recipeList
    .readLines()
    .map { Regex("""(.*?) \((\d+)\): (.*)""").matchEntire(it) }
    .map { match ->
        match!!
        val name = match.groupValues[1].toLowerCase()
        val yield = match.groupValues[2].toInt()
        val ingredientPieces = match.groupValues[3].takeIf { it.any() }?.split(',')?.map { it.trim().toLowerCase() } ?: emptyList()

        val ingredients = ingredientPieces.map {
            Ingredient(it.substringAfter(' '), it.substringBefore(' ').toInt())
        }

        Recipe(name, `yield`, ingredients)
    }

fun parseCraftingList(craftingList: File): List<Entry> {
    return craftingList
        .readLines()
        .map { it to Regex("""(.*?) \(((\d+)/(\d+)(?:\+ISH)?|ISH)\)( \(((.*?): .*?)\))?( -- ?(.*?) ?--)? (.*)""").matchEntire(it) }
        .map { (s,match) ->
            match!!
            val name = match.groupValues[1].toLowerCase()
            val current = match.groupValues[3].toInt()
            val total = match.groupValues[4].toInt()
            val isCraft = match.groupValues[7] == "craft"
            val source = match.groupValues[6]
            val recipe = match.groups[9]?.value
            val tags = match.groupValues[10]

            Entry(name, current, total, isCraft, source, recipe, tags)
        }
}

fun parseInventory(inventoryFile: File): List<Ingredient> {
    return inventoryFile
        .readLines()
        .map {it.trim()}
        .map {Ingredient(it.substringAfter(' ').toLowerCase(), it.substringBefore(' ').toInt())}
}

fun main() {
    val ff14Folder = File("F:/documents/ff14/crafting")
    val listFile = File(ff14Folder, "crafting.txt")
    val recipeFile = File(ff14Folder, "recipes.txt")
    val inventoryFile = File(ff14Folder, "inventory.txt")

    val recipes = parseRecipes(recipeFile)
    val entries = parseCraftingList(listFile).toMutableList()
    val origEntries = entries.toList()
    val inventory = parseInventory(inventoryFile)

    val noEntries = mutableListOf<Pair<String, List<String>>>()
    val noRecipes = mutableListOf<String>()
    val addedQuanities = AddedQuanities(mutableMapOf(), mutableMapOf())
    val prevQuantities = entries.associateBy({it.name}, {Pair(it.current, it.total)})

    inventory.forEach {inv ->
        val entry = entries.find { it.name == inv.name }
        if (entry == null) {
            noEntries += inv.name to emptyList()
            return@forEach
        }

        val missingRecipes = missingRecipes(entry, origEntries, recipes, noEntries, emptyList())
        noRecipes += missingRecipes
    }

    if (noRecipes.any()) {
        println("\nMissing recipes:")
        noRecipes.distinct().forEach {
            println("https://ffxiv.gamerescape.com/wiki/Special:Search/${URLEncoder.encode(it, StandardCharsets.UTF_8.toString()).replace("+", "%20")}")
        }
        return
    }

    inventory.forEach {inv ->
        val entry = entries.find { it.name == inv.name }
        if (entry == null) {
            addedQuanities.total[inv.name] = (addedQuanities.total[inv.name] ?: 0) + inv.quantity
            addedQuanities.sourced[Pair(inv.name, null)] = (addedQuanities.sourced[Pair(inv.name, null)] ?: 0) + inv.quantity
            return@forEach
        }

        addQuantity(inv.quantity, entry, entries, recipes, emptyList(), addedQuanities)
    }

    println("\nMissing entries:")
    noEntries.distinct().forEach {
        println("${it.first}: ${it.second.joinToString("->")}")
    }

    println("\nLeftovers:")
    addedQuanities.total.filter { (name, inc) -> !entries.any { it.name == name } }.forEach {(name, totalIncrease) ->
        val sourced = addedQuanities.sourced.entries.filter { it.key.first == name }

        val (prevCurrent, prevTotal) = prevQuantities[name] ?: Pair(0, 0)
        if (prevCurrent + totalIncrease == prevTotal) return@forEach

        println("$name ($prevCurrent/$prevTotal -> ${prevCurrent + totalIncrease}/$prevTotal, ${prevCurrent + totalIncrease - prevTotal} excess): ${
            sourced.sortedWith(compareBy({it.key.second != null}, {-it.value})).joinToString(", ") { "${it.value} [${it.key.second?.let { "$it" } ?: "direct" }]"}
        }")
    }

    val output = entries.sortedBy { "FSH" in it.tags }.joinToString("\n", transform = Entry::format)
//    listFile.writeText(output)
}

fun Entry.format() =
    "$name (${if (total != 0) "$current/$total" else "ISH"})${if (source.any()) " ($source)" else ""} $tags"

fun missingRecipes(entry: Entry, entries: List<Entry>, recipes: List<Recipe>, missingEntries: MutableList<Pair<String, List<String>>>, chain: List<String>): List<String> {
    if (!entry.isCraft) return emptyList()

    val recipe = recipes.find { it.name == entry.name }
    recipe ?: return listOf(entry.name)
    return recipe.ingredients.flatMap { ing ->
        val ingEntry = entries.find { it.name == ing.name }
        if (ingEntry == null) {
            missingEntries += ing.name to chain
            return@flatMap emptyList<String>()
        }

        missingRecipes(ingEntry, entries, recipes, missingEntries, chain + ing.name)
    }
}

data class AddedQuanities(val sourced: MutableMap<Pair<String, String?>, Int>, val total: MutableMap<String, Int>)

private fun addQuantity(increase: Int, entry: Entry, entries: MutableList<Entry>, recipes: List<Recipe>, chain: List<String>, added: AddedQuanities) {
    added.total[entry.name] = (added.total[entry.name] ?: 0) + increase
    added.sourced[Pair(entry.name, chain.lastOrNull())] = (added.sourced[Pair(entry.name, chain.lastOrNull())] ?: 0) + increase
    if (entry.isCraft && entry.current == entry.total/* - 1*/) {
        println("Skipped updating ${entry.name}: ${recipes.find{it.name == entry.name}!!.ingredients.joinToString(", ") {"${it.quantity} ${it.name}"}}")
        return
    }

    val cappedIncrease = if (entry.isCraft) min(increase, entry.total - entry.current/* - 1*/) else increase

    val prevCurrent = entry.current
    val prevTotal = entry.total
    val newQuantity = entry.current + cappedIncrease
    if (newQuantity >= entry.total) {
        entry.current = entry.total

//        if (entry.isCraft) {
//            entry.current = entry.total - 1
//        }

//        if (entry.tags.contains("ISH")) {
//            entry.isIshOnly = true
//        } else /*if (!entry.isCraft)*/ {
            entries.remove(entry)
            println("Removing entry ${entry.name}")
//        }
    } else {
        entry.current = newQuantity
    }

    var newCurrent = entry.current
    var newTotal = entry.total

    if (entry.isCraft) {
        val recipe = recipes.find { it.name == entry.name }
        val yield = recipe!!.yield
        val origExcess = prevCurrent % `yield`

        // The ingredients should be deducted when product quantity (mod yield) = product total (mod yield)
        // This is because once you get the 1, 2, etc. extra, you're done the extras. Deducting too late means one fewer craft than what should happen and leftover ingredients.
        // Assuming a yield of 3:
        // prev=17/20, current=18/20 -> don't touch ingredients
        // prev=17-18/20, current=19/20 -> don't touch ingredients
        // prev=1/20, current=2/20 -> add 1 craft to ingredients
        // prev=2/20, current=3/20 -> don't touch ingredients
        // prev=0/20, current=5/20 -> add 2 crafts to ingredients
        // Overall, the answer needs to be in (mod yield), but % is a remainder not a modulus hence the `( + yield) % yield` dance.
        val recipeIncrease = ((origExcess + `yield` - entry.total % `yield`) % `yield` + cappedIncrease) / `yield`

        if (recipeIncrease != 0) {
            recipe.ingredients.forEach { ing ->
                val ingIncrease = ing.quantity * recipeIncrease

                val ingEntry = entries.find { it.name == ing.name } ?: run {
                    added.total[ing.name] = (added.total[ing.name] ?: 0) + ingIncrease
                    added.sourced[Pair(ing.name, entry.name)] = (added.sourced[Pair(ing.name, entry.name)] ?: 0) + ingIncrease
                    return@forEach
                }

                addQuantity(ingIncrease, ingEntry, entries, recipes, chain + entry.name, added)
            }
        }
    }

//    if (entry.isIshOnly) {
//        entry.current = 0
//        entry.total = 0
//        newCurrent = 0
//        newTotal = 0
//    }

    println("Updated entry ${entry.name} ($prevCurrent/$prevTotal) -> ($newCurrent/$newTotal). ${chain.joinToString("->")}")
}