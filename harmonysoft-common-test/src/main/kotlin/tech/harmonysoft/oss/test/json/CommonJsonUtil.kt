package tech.harmonysoft.oss.test.json

import tech.harmonysoft.oss.test.binding.DynamicBindingContext
import tech.harmonysoft.oss.test.binding.DynamicBindingKey
import tech.harmonysoft.oss.test.binding.DynamicBindingUtil.TO_BIND_REGEX
import tech.harmonysoft.oss.test.util.TestUtil.fail

object CommonJsonUtil {

    private const val DYNAMIC_VALUE_PREFIX = "dynamic-value-"
    private const val NOT_SET_MARKER = "<not-set>"

    /**
     * Normally when we want to capture any dynamic value from json, we define it as-is, without any quotes:
     *
     * ```
     * {
     *   "key1": <bind:key1>,
     *   "key2": key2-value
     * }
     * ```
     *
     * This is easy to read, but that is malformed json. That's why we normalize it by replacing such dynamic
     * value markers by well-formed json strings starting from [DYNAMIC_VALUE_PREFIX].
     *
     * Example above is transformed to this:
     *
     * ```
     * {
     *   "key1": "dynamic-value-key1",
     *   "key2": key2-value
     * }
     * ```
     */
    fun prepareDynamicMarkers(json: String): String {
        val normalized = StringBuilder()
        var start = 0
        while (true) {
            TO_BIND_REGEX.find(json, start)?.let {
                normalized
                    .append(json.substring(start, it.range.first))
                    .append("\"")
                    .append(DYNAMIC_VALUE_PREFIX)
                    .append(it.groupValues[1])
                    .append("\"")
                start = it.range.last + 1
            } ?: break
        }
        if (start < json.length) {
            normalized.append(json.substring(start))
        }
        return normalized.toString()
    }

    /**
     * There is a common use-case when we want to compare json content. It's also possible that we want to extract
     * dynamic value mappings from it. For example, we might receive the following expected content:
     *
     * ```
     * {
     *   "key1": <bind:key1>,
     *   "key2": key2-value
     * }
     * ```
     *
     * and the following actual content:
     *
     * ```
     * {
     *   "key1": 123,
     *   "key2": key2-value
     * }
     * ```
     *
     * Here we want to do the following:
     * 1. Store dynamic value mapping between `key1` and `123`
     * 2. Return empty comparison errors collection
     *
     * If expected content is like below instead:
     *
     * ```
     * {
     *   "key1": 123,
     *   "key2": other-key2-value
     * }
     * ```
     *
     * we want to return a collection of a single error about non-dynamic value mismatch (`key2-value`
     * vs `other-key2-value`).
     *
     * This method allows to do that
     *
     * @param strict    defines if we should ensure that all data from actual json is present in expected json.
     *                  For example, we can receive a big json response from server but want just to verify that
     *                  particular path has particular value
     * @return collection of data comparison errors (if any); empty collection as an indication of successful comparison
     */
    fun compareAndBind(
        expected: Any,
        actual: Any,
        path: String,
        context: DynamicBindingContext,
        strict: Boolean = true
    ): Collection<String> {
        if (expected::class != actual::class) {
            fail("expected an instance of ${expected::class.qualifiedName} ($expected) at path '$path' " +
                 "but got and instance of ${actual::class.qualifiedName} ($actual")
        }
        return when {
            expected is Map<*, *> -> {
                val actualMap = actual as Map<*, *>
                if (strict) {
                    val missingKeys = actualMap.keys.toSet() - expected.keys
                    if (missingKeys.isNotEmpty()) {
                        fail("unexpected data is found at paths ${missingKeys.joinToString { "$path.$it" }}"
                             + missingKeys.joinToString { "$it: ${actual[it]}" })
                    }
                }
                expected.entries.flatMap { (key, value) ->
                    if (value == NOT_SET_MARKER) {
                        actualMap[key]?.let {
                            fail("expected that no value is set at path $path.$key but there is a value of "
                                 + "type ${it::class.simpleName}: $it")
                        }
                        emptyList()
                    } else {
                        actualMap[key]?.let {
                            compareAndBind(value as Any, it, "$path.$key", context, strict)
                        } ?: fail(
                            "mismatch at path '$path.$key' - expected to find a ${value?.javaClass?.name} "
                            + "value but got null")
                    }
                }
            }

            expected is List<*> -> {
                val actualList = actual as List<*>
                if (expected.size != actualList.size) {
                    fail(
                        "unexpected entry(-ies) found at path '$path' - expected ${expected.size} elements but "
                        + "got ${actual.size} ($expected VS $actual)")
                }
                expected.flatMapIndexed { i: Int, expectedValue: Any? ->
                    expectedValue ?: fail("I can't happen, path: $path, index: $i")
                    actual[i]?.let {
                        compareAndBind(expectedValue, it, "$path[$i]", context, strict)
                    } ?: fail(
                        "mismatch at path '$path[$i]' - expected to find a "
                        + "${expectedValue::class.qualifiedName} '$expectedValue' but got null")
                }
            }

            expected is String && expected.startsWith(DYNAMIC_VALUE_PREFIX) -> {
                context.storeBinding(
                    key = DynamicBindingKey(expected.substring(DYNAMIC_VALUE_PREFIX.length)),
                    value = actual
                )
                emptyList()
            }

            else -> if (expected == actual) {
                emptyList()
            } else {
                listOf("mismatch at path '$path' - expected a ${expected::class.qualifiedName} '$expected' but got "
                       + "${actual::class.qualifiedName} '$actual'")
            }
        }
    }
}