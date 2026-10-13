package com.kellvyn.assistant.workspace

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

/**
 * Writes the small Android binary XML document required by an APK that has no
 * XML resources or resource table. Values and resource IDs follow the Android
 * ResXML/ResStringPool chunk layout.
 */
internal object BinaryAndroidManifest {
    private const val NO_INDEX = -1
    private const val RES_XML = 0x0003
    private const val RES_STRING_POOL = 0x0001
    private const val RES_XML_RESOURCE_MAP = 0x0180
    private const val RES_XML_START_NAMESPACE = 0x0100
    private const val RES_XML_END_NAMESPACE = 0x0101
    private const val RES_XML_START_ELEMENT = 0x0102
    private const val RES_XML_END_ELEMENT = 0x0103
    private const val UTF8_FLAG = 0x00000100
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

    private data class Attribute(
        val name: String,
        val value: String? = null,
        val type: Int = TYPE_STRING,
        val data: Int = 0,
        val androidNamespace: Boolean = true
    )

    fun create(
        packageName: String,
        activityName: String,
        appLabel: String,
        minSdk: Int,
        targetSdk: Int
    ): ByteArray {
        require(validPackage(packageName)) { "applicationId inválido para o manifesto." }
        require(activityName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\\\.[A-Za-z_][A-Za-z0-9_]*)+"))) {
            "Nome de Activity inválido para o manifesto."
        }
        require(appLabel.isNotBlank() && appLabel.length <= 100) { "Rótulo do aplicativo inválido." }
        require(minSdk in 21..100 && targetSdk >= minSdk && targetSdk <= 100) { "Níveis SDK inválidos." }

        val strings = LinkedHashMap<String, Int>()
        fun string(value: String): Int = strings.getOrPut(value) { strings.size }
        fun attr(name: String, value: String? = null, type: Int = TYPE_STRING, data: Int = 0, android: Boolean = true) =
            Attribute(name, value, type, data, android)

        val manifestAttrs = listOf(
            attr("package", packageName, android = false),
            attr("versionCode", type = TYPE_INT_DEC, data = 1),
            attr("versionName", "1.0")
        )
        val usesSdkAttrs = listOf(
            attr("minSdkVersion", type = TYPE_INT_DEC, data = minSdk),
            attr("targetSdkVersion", type = TYPE_INT_DEC, data = targetSdk)
        )
        val appAttrs = listOf(attr("label", appLabel))
        val activityAttrs = listOf(
            attr("name", activityName),
            attr("exported", "true", TYPE_INT_BOOLEAN, 1)
        )
        val actionAttrs = listOf(attr("name", "android.intent.action.MAIN"))
        val categoryAttrs = listOf(attr("name", "android.intent.category.LAUNCHER"))

        listOf(
            "android", ANDROID_URI,
            "manifest", "uses-sdk", "application", "activity", "intent-filter", "action", "category",
            "package", packageName, "versionCode", "versionName", "1.0",
            "minSdkVersion", "targetSdkVersion", minSdk.toString(), targetSdk.toString(),
            "label", appLabel, "name", activityName, "exported", "true",
            "android.intent.action.MAIN", "android.intent.category.LAUNCHER"
        ).forEach { string(it) }
        val resourceIds = mapOf(
            "label" to 0x01010001,
            "name" to 0x01010003,
            "exported" to 0x01010010,
            "versionCode" to 0x0101021b,
            "versionName" to 0x0101021c,
            "minSdkVersion" to 0x0101020c,
            "targetSdkVersion" to 0x01010270
        )

        val chunks = ByteArrayOutputStream()
        chunks.write(stringPoolChunk(strings))
        chunks.write(resourceMapChunk(strings, resourceIds))
        chunks.write(namespaceChunk(RES_XML_START_NAMESPACE, strings.getValue("android"), strings.getValue(ANDROID_URI)))
        chunks.write(startElement(strings, "manifest", manifestAttrs))
        chunks.write(startElement(strings, "uses-sdk", usesSdkAttrs))
        chunks.write(endElement(strings, "uses-sdk"))
        chunks.write(startElement(strings, "application", appAttrs))
        chunks.write(startElement(strings, "activity", activityAttrs))
        chunks.write(startElement(strings, "intent-filter", emptyList()))
        chunks.write(startElement(strings, "action", actionAttrs))
        chunks.write(endElement(strings, "action"))
        chunks.write(startElement(strings, "category", categoryAttrs))
        chunks.write(endElement(strings, "category"))
        chunks.write(endElement(strings, "intent-filter"))
        chunks.write(endElement(strings, "activity"))
        chunks.write(endElement(strings, "application"))
        chunks.write(endElement(strings, "manifest"))
        chunks.write(namespaceChunk(RES_XML_END_NAMESPACE, strings.getValue("android"), strings.getValue(ANDROID_URI)))

        val body = chunks.toByteArray()
        val result = ByteArrayOutputStream(body.size + 8)
        writeU16(result, RES_XML)
        writeU16(result, 8)
        writeU32(result, body.size + 8)
        result.write(body)
        return result.toByteArray()
    }

    private fun stringPoolChunk(strings: LinkedHashMap<String, Int>): ByteArray {
        val values = strings.keys.toList()
        val data = ByteArrayOutputStream()
        val offsets = ArrayList<Int>(values.size)
        values.forEach { value ->
            offsets += data.size()
            val utf8 = value.toByteArray(StandardCharsets.UTF_8)
            writeLength8(data, value.length)
            writeLength8(data, utf8.size)
            data.write(utf8)
            data.write(0)
        }
        align4(data)
        val stringsStart = 28 + values.size * 4
        val size = stringsStart + data.size()
        val out = ByteArrayOutputStream(size)
        writeU16(out, RES_STRING_POOL)
        writeU16(out, 28)
        writeU32(out, size)
        writeU32(out, values.size)
        writeU32(out, 0)
        writeU32(out, UTF8_FLAG)
        writeU32(out, stringsStart)
        writeU32(out, 0)
        offsets.forEach { writeU32(out, it) }
        out.write(data.toByteArray())
        return out.toByteArray()
    }

    private fun resourceMapChunk(
        strings: LinkedHashMap<String, Int>,
        ids: Map<String, Int>
    ): ByteArray {
        val size = 8 + strings.size * 4
        val out = ByteArrayOutputStream(size)
        writeU16(out, RES_XML_RESOURCE_MAP)
        writeU16(out, 8)
        writeU32(out, size)
        strings.keys.forEach { writeU32(out, ids[it] ?: 0) }
        return out.toByteArray()
    }

    private fun namespaceChunk(type: Int, prefix: Int, uri: Int): ByteArray {
        val out = ByteArrayOutputStream(24)
        writeU16(out, type)
        writeU16(out, 16)
        writeU32(out, 24)
        writeU32(out, 1)
        writeU32(out, NO_INDEX)
        writeU32(out, prefix)
        writeU32(out, uri)
        return out.toByteArray()
    }

    private fun startElement(
        strings: LinkedHashMap<String, Int>,
        name: String,
        attributes: List<Attribute>
    ): ByteArray {
        val size = 36 + attributes.size * 20
        val out = ByteArrayOutputStream(size)
        writeU16(out, RES_XML_START_ELEMENT)
        writeU16(out, 16)
        writeU32(out, size)
        writeU32(out, 1)
        writeU32(out, NO_INDEX)
        writeU32(out, NO_INDEX)
        writeU32(out, strings.getValue(name))
        writeU16(out, 20)
        writeU16(out, 20)
        writeU16(out, attributes.size)
        writeU16(out, 0)
        writeU16(out, 0)
        writeU16(out, 0)
        attributes.forEach { attribute ->
            writeU32(out, if (attribute.androidNamespace) strings.getValue(ANDROID_URI) else NO_INDEX)
            writeU32(out, strings.getValue(attribute.name))
            writeU32(out, attribute.value?.let(strings::getValue) ?: NO_INDEX)
            writeU16(out, 8)
            out.write(0)
            out.write(attribute.type)
            writeU32(out, if (attribute.type == TYPE_STRING) {
                strings.getValue(attribute.value ?: "")
            } else attribute.data)
        }
        return out.toByteArray()
    }

    private fun endElement(strings: LinkedHashMap<String, Int>, name: String): ByteArray {
        val out = ByteArrayOutputStream(24)
        writeU16(out, RES_XML_END_ELEMENT)
        writeU16(out, 16)
        writeU32(out, 24)
        writeU32(out, 1)
        writeU32(out, NO_INDEX)
        writeU32(out, NO_INDEX)
        writeU32(out, strings.getValue(name))
        return out.toByteArray()
    }

    private fun writeLength8(out: ByteArrayOutputStream, length: Int) {
        if (length > 0x7f) {
            out.write((length shr 8) or 0x80)
            out.write(length and 0xff)
        } else out.write(length)
    }

    private fun align4(out: ByteArrayOutputStream) {
        while (out.size() % 4 != 0) out.write(0)
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
        out.write((value ushr 16) and 0xff)
        out.write((value ushr 24) and 0xff)
    }

    private fun validPackage(value: String): Boolean =
        value.length <= 240 && value.split('.').size >= 2 &&
            value.split('.').all { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }
}
