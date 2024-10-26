package com

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object FilePaths {
    const val projectDirPath = "app\\src\\main\\res"
    const val sourceFileDirPath = "/values/strings.xml"
    const val targerFileDirPath = "/values-de/strings.xml"
    const val attributeName = "name"
}

fun main() {
    val projectDir = File(FilePaths.projectDirPath)
    val sourceFile = File(projectDir, FilePaths.sourceFileDirPath)
    val targetFile = File(projectDir, FilePaths.targerFileDirPath)
    // Load and parse both XML files
    val stringDocument = parseXmlFile(sourceFile)
    val stringDeDocument = parseXmlFile(targetFile)

    // Add missing strings to de-string.xml
    val addedTranslatedAttributesCount = addMissingStrings(stringDocument, stringDeDocument)

    if (addedTranslatedAttributesCount > 0) {
        saveXmlFile(targetFile, stringDeDocument)
        executeGitCommand("git", "add", FilePaths.projectDirPath + FilePaths.targerFileDirPath)
    }
    println("Added strings count: $addedTranslatedAttributesCount")
}

fun getLastCommitAddedAttributes(filePath: String): Set<String> {
    val process = executeGitCommand("git", "diff", "HEAD~1", "HEAD", "--", filePath)
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()

    // Extract the names of added strings (lines prefixed with '+')
    return output.lineSequence()
        .filter { it.startsWith("+") && !it.startsWith("++") } // TODO handle "++" case
        .mapNotNull { line ->
            // Extract the "name" attribute from the added lines
            Regex("""name="([^"]+)"""").find(line)?.groups?.get(1)?.value
        }
        .toSet()
}

fun parseXmlFile(file: File): Document {
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    return builder.parse(file)
}

fun Document.toElements(): List<Element> {
    val nodeList = getElementsByTagName("string")
    return (0 until nodeList.length).map { index ->
        nodeList.item(index) as Element
    }
}

fun addMissingStrings(defaultLocaleDocument: Document, translatableLocaleDocument: Document): Int {
    val defaultLocaleElements = defaultLocaleDocument.toElements()
    val translatableLocaleElements = translatableLocaleDocument.toElements()
    val alreadyTranslatedAttributes =
        translatableLocaleElements.map { it.getAttribute(FilePaths.attributeName) }.toSet()

    val lastCommitAddedAttributes =
        getLastCommitAddedAttributes(FilePaths.projectDirPath + FilePaths.sourceFileDirPath)
    if (lastCommitAddedAttributes.isEmpty()) return 0

    var addedAttributesCount = 0
    // Iterate over source strings and add missing ones to the target
    defaultLocaleElements.forEach { item ->
        val itemAttribute = item.getAttribute(FilePaths.attributeName)

        if (itemAttribute !in alreadyTranslatedAttributes && itemAttribute in lastCommitAddedAttributes) {
            val newString = translatableLocaleDocument.importNode(item, true)
            translatableLocaleDocument.documentElement.appendChild(newString)
            addedAttributesCount++
        }
    }
    return addedAttributesCount
}

fun saveXmlFile(file: File, document: Document) {
    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    }
    val source = DOMSource(document)
    val result = StreamResult(file)
    transformer.transform(source, result)
}

fun executeGitCommand(vararg command: String): Process {
    val commands = command.toList()
    val process = ProcessBuilder(commands)
        .redirectErrorStream(true)
        .start()
    println("Command executed: $commands")
    return process
}