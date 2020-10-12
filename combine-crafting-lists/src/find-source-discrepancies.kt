import java.io.File

//fun main() {
//    val leveCsv = "F:/Documents/ff14/leve-crafting.csv"
//    val leveEntries = File(leveCsv).readLines().filter {it.contains(Regex(",\\d+,"))}.map { line ->
//        val approxCols = line.split(',')
//        Entry(approxCols[0].trim('"'), approxCols[1].toInt(), approxCols[2], approxCols.drop(3).joinToString())
//    }
//
//    val nqCsv = "F:/Documents/ff14/nq-crafting.csv"
//    val nqEntries = File(nqCsv).readLines().filter {it.contains(Regex(",\\d+,"))}.map { line ->
//        val approxCols = line.split(',')
//        Entry(approxCols[0].trim('"'), approxCols[1].toInt(), approxCols[2], approxCols.drop(3).joinToString())
//    }
//
//    val leveSources = leveEntries.associateBy({it.item}, {it.source})
//
//    nqEntries
//        .filter { leveSources[it.item] != it.source && leveSources[it.item] != null }
//        .forEach {
//            println("${it.item} (${leveEntries.find { l -> l.item == it.item}?.amount}): ${it.source} vs. ${leveSources[it.item]}")
//        }
//}