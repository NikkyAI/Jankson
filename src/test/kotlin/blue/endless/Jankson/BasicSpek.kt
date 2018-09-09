package blue.endless.Jankson

import blue.endless.jankson.*
import blue.endless.jankson.impl.Marshaller
import org.spekframework.spek2.Spek
import org.spekframework.spek2.dsl.TestBody
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import kotlin.test.*

object basicSpek : Spek({
    val jankson by memoized {
        Jankson.builder().build();
    }
    describe("Primitive equality") {
        val foo1 by memoized {
            JsonPrimitive("foo")
        }
        val foo2 by memoized {
            JsonPrimitive(java.lang.String("foo"))
        }
        testAssertEquals(foo1, foo2, "Equal objects should produce equal json primitives 1") //Ensure no interning
        testAssertNotSame(foo1, foo2, "Test Objects must not be the same instance")
        testAssertEquals(JsonPrimitive(java.lang.Double.valueOf(42.0)),
                JsonPrimitive(42.0),
                "Equal objects should produce equal json primitives"
        )
        testAssertNotEquals(JsonPrimitive("foo"),
                JsonPrimitive("bar"),
                "Non-Equal objects should produce non-equal json primitives"
        )
        testAssertNotEquals(JsonPrimitive(42.0),
                JsonPrimitive(42.1),
                "Non-Equal objects should produce non-equal json primitives"
        )
        testAssertNotEquals(JsonPrimitive(java.lang.Double.valueOf(42.0)),
                java.lang.Long.valueOf(42),
                "Intended quirk behavior: 42.0 != 42"
        )
    }

    describe("Basic Comprehension") {
        val before = """
            {
              'foo': 'bar',
              'baz':'bux'
            }""".trimIndent()
        val after by memoized { jankson.load(before) }
        testAssertEquals(2, after.keys.size, "Object should contain two keys")
        testAssertEquals(JsonPrimitive("bar"), after["foo"], "Object should contain mapping 'foo': 'bar'")
        testAssertEquals(JsonPrimitive("bux"), after["baz"], "Object should contain mapping 'baz': 'bux'")
        testAssertNull(after["bar"], "Object shouldn't contain keys that weren't defined")
    }

    describe("Object Content Categories") {
        val before = """
            {
              'a': 'hello',
              'b': 42,
              'c': 42.0,
              'd': {},
              'e': [],
              'f': true,
              'g': false,
              'h': null
            }
            """.trimIndent()
        val after by memoized { jankson.load(before) }
        testAssertEquals(8, after.keys.count(), "Object should contain 8 keys")
        testAssertEquals(JsonPrimitive("hello"), after["a"], "Object should contain mapping 'a': 'hello'")
        testAssertEquals(JsonPrimitive(java.lang.Long.valueOf(42)), after["b"], "Object should contain mapping 'b': 42")
        testAssertEquals(JsonPrimitive(java.lang.Double.valueOf(42.0)), after["c"], "Object should contain mapping 'c': 42.0")
        testAssertEquals(JsonObject(), after["d"], "Object should contain mapping 'd': {}")
        testAssertEquals(JsonArray(), after["e"], "Object should contain mapping 'e': []")
        testAssertEquals(JsonPrimitive(java.lang.Boolean.TRUE), after["f"], "Object should contain mapping 'f': true")
        testAssertEquals(JsonPrimitive(java.lang.Boolean.FALSE), after["g"], "Object should contain mapping 'g': false")
        testAssertEquals(JsonNull.INSTANCE, after["h"], "Object should contain mapping 'h': null")
    }

    describe("Array Content Categorize") {
        val before = """
            {
              'a': ['hello', 42, 42.0, {}, [], true, false, null]
            }""".trimIndent()
        val after by memoized { jankson.load(before) }
        testAssertEquals(1, after.keys.count(), "Object should contain one key")
        context("with array") {
            val array by memoized { after.recursiveGet(JsonArray::class.java, "a")!! }
            testAssertNotNull(array, "Recursive get of just 'a' should obtain an array.")
            testAssertEquals(8, array.count(), "Array should contain all declared elements and no more.")
            testAssertEquals(JsonPrimitive("hello"), array[0], "Array should contain 'hello' at position 0")
            testAssertEquals(JsonPrimitive(java.lang.Long.valueOf(42)), array[1], "Array should contain 42 at position 1")
            testAssertEquals(JsonPrimitive(java.lang.Double.valueOf(42.0)), array[2], "Array should contain 42.0 at position 2")
            testAssertEquals(JsonObject(), array[3], "Array should contain {} at position 3")
            testAssertEquals(JsonArray(), array[4], "Array should contain [] at position 4")
            testAssertEquals(JsonPrimitive(java.lang.Boolean.TRUE), array[5], "Array should contain true at position 5")
            testAssertEquals(JsonPrimitive(java.lang.Boolean.FALSE), array[6], "Array should contain false at position 6")
            testAssertEquals(JsonNull.INSTANCE, array[7], "Array should contain null at position 7")
        }
    }

    describe("Comment Attrution") {
        val data = mapOf(
                "Comment should be parsed and attributed to child 'foo'" to "{ /* Hello World */ 'foo': true }",
                "Comment should still be parsed and attributed to child 'foo'" to "{ /*Hello World */ 'foo': true }",
                "Single-line comment should be parsed and attributed to child 'foo'" to "{ //\tHello World \n 'foo': true }"
        )

        data.forEach { (description, before) ->
            val after by memoized { jankson.load(before) }
            it(description) {
                assertEquals("Hello World", after.getComment("foo"), description)
            }
        }
    }

    describe("Deep Nesting") {
        val subject = "{ a: { a: { a: { a: { a: { a: { a: { a: 'Hello' } } } } } } } }"
        val parsed by memoized { jankson.load(subject) }

        val prim by memoized { parsed.recursiveGet(JsonPrimitive::class.java, "a.a.a.a.a.a.a.a") }
        testAssertEquals(JsonPrimitive("Hello"), prim, "Get Prim")
        val longerKey by memoized { parsed.recursiveGet(JsonPrimitive::class.java, "a.a.a.a.a.a.a.a.a") }
        testAssertNull(longerKey, "Get Not Prim with longer key")
        val shorterkey by memoized { parsed.recursiveGet(JsonPrimitive::class.java, "a.a.a.a.a.a.a.a.a") }
        testAssertNull(shorterkey, "Get Not Prim with shorter key")
    }

    describe("Marshaller") {
        context("String") {
            val subject = "{ a: { a: { a: 'Hello' } } }"
            val stringResult by memoized { jankson.load(subject).recursiveGet(String::class.java, "a.a.a") }
            testAssertEquals("Hello", stringResult, "Should get the String 'Hello' back")
        }

        context("Numbers") {
            val subject by memoized { "{ a: { a: { a: 42 } } }" }
            val intResult by memoized { jankson.load(subject).recursiveGet(Int::class.java, "a.a.a") }
            testAssertEquals(java.lang.Integer.valueOf(42), intResult, "Should get the Integer 42 back")
            val doubleResult by memoized { jankson.load(subject).recursiveGet(Double::class.java, "a.a.a") }
            testAssertEquals(java.lang.Double.valueOf(42.0), doubleResult, "Should get the Double 42 back")
        }
    }

    describe("Test For Reuse Leaks") {
        val subjectOne = "{ a: 42 }"
        val subjectTwo = "{ b: 12 }"
        val parsedOne by memoized {
            jankson.load(subjectOne)
        }
        val parsedTwo by memoized {
            jankson.load(subjectTwo)
        }
        it("Ensure key 'a' is null and not reused") {
            assertNotNull(parsedOne) // to ensure the memoized block gets executed
            assertNull(parsedTwo["a"])
        }
    }

    describe("complex quirks") {
        val subject = "{ mods: [{name: 'alf' version:'1.12.2_v143.6'} {name:'bux', version:false}]}"
        val parsed by memoized { jankson.load(subject) }
        testAssertNotNull(parsed, "object is not null")
        testAssertNotNull(parsed.get("mods"), "mods are not null")
        testAssertTrue(parsed["mods"] is JsonArray, "mods is class JsonArray")
        val mods by memoized { parsed.recursiveGet(JsonArray::class.java, "mods")!! }
        testAssertEquals(2, mods.count(), "Mods is length 2")

        //TODO: Add more marshalling logic to arrays
        //JsonElement alfMod = mods.get(0);
    }

    describe("Array Serialization") {
        val intArray by memoized { intArrayOf(3, 2, 1) }
        val serializedIntArray by memoized { Marshaller.getFallback().serialize(intArray).toString() }
        testAssertEquals("[ 3, 2, 1 ]", serializedIntArray, "Test Int Array", null)

        val voidArray = arrayOf<Void?>(null, null)
        val serializedVoidArray by memoized { Marshaller.getFallback().serialize(voidArray).toString() }
        testAssertEquals("[ null, null ]", serializedVoidArray, "Test Black magic", null)

        val doubleArrayList by memoized {
            arrayListOf(
                    arrayOf(1.0, 2.0, 3.0),
                    arrayOf(4.0, 5.0)
            )
        }
        val serializedDoubleArrayList by memoized { Marshaller.getFallback().serialize(doubleArrayList).toString() }
        testAssertEquals("[ [ 1.0, 2.0, 3.0 ], [ 4.0, 5.0 ] ]", serializedDoubleArrayList, "Test List of Arrays", null)
    }

    describe("Map Serialization") {
        val intHashMap by memoized {
            hashMapOf(
                    "foo" to 1,
                    "bar" to 2
            )
        }
        val serialized by memoized { Marshaller.getFallback().serialize(intHashMap) }
        testAssertTrue(serialized is JsonObject, "Assume map is a JsonObject")
        val obj by memoized { serialized as JsonObject }
        testAssertEquals(JsonPrimitive(1L), obj["foo"], "'foo' should contain '1'", null)
        testAssertEquals(JsonPrimitive(2L), obj["bar"], "'bar' should contain '2'", null)
    }

    describe("serialize comments") {
        val commented by memoized { CommentedClass() }
        val serialized by memoized { Marshaller.getFallback().serialize(commented).toJson(true, false, 0) }
        testAssertEquals("{ /* This is a comment. */ \"foo\": \"what?\" }", serialized, "Test Comment Serialization", null)
    }

    describe("serialize enums") {
        val serialized by memoized { Marshaller.getFallback().serialize(ExampleEnum.CAT).toJson() }
        testAssertEquals("\"CAT\"", serialized, "Test Enum Serialization", null)
    }

    describe("deserialize enums") {
        val serialized = "{ aProperty: 'DAY' }"
        val deserialized by memoized { jankson.load(serialized) }
        val recovered by memoized { deserialized.get(ExampleEnum::class.java, "aProperty") }
        testAssertEquals(ExampleEnum.DAY, recovered, "Test equality with recovered enum value")
    }

    describe("diff against defaults") {
        /*
         * A number of specific behaviors are verified here:
         *  - 'a' is present as a default but not present in the base object. This key is ignored.
         *  - 'b' is not customized in the base object. This key is ignored.
         *  - 'c' is customized in the base object. Delta records the customization.
         *  - 'd' is itself an object, so we do a deep comparison:
         *    - 'd.e' is not customized. This key is ignored.
         *    - 'd.f' is customized. This key is recorded, and since the resulting object isn't empty, we know the
         *            outer key was also customized, so we record all of this.
         *  - 'g' is a list, and identical to the default. This key is ignored.
         *  - 'h' is a list, but its value has been customized. Even though the lists share some elements, the
         *        entire list is represented in the output. This test is effectively a promise to shallow-diff lists
         *  - 'i' is an object, so it receives a deep comparison. However, it is found to be identical, and so its key is ignored.
         */

        val defaultObj by memoized {
            jankson.load("{ a: 'a', b: 'b', c: 'c', d: { e: 'e', f: 'f' }, g: [1, 2], h: [1, 2], i: { j: 'j' } }")
        }
        val baseObj by memoized {
            jankson.load("{ b: 'b', c: 'test', d: { e: 'e', f: 'test' }, g: [1, 2], h: [2, 3], i: { j: 'j' } }")
        }
        val expected by memoized { "{ \"c\": \"test\", \"d\": { \"f\": \"test\" }, \"h\": [ 2, 3 ] }" }
        val actual by memoized { baseObj.getDelta(defaultObj).toJson() }
        testAssertEquals(expected, actual, "Test diff against defaults", null)
    }

    describe("Array Get") {
        val subject by memoized { jankson.load("{ a: [1, 2, 3, 4] }") }
        val maybe by memoized { subject.get(IntArray::class.java, "a")!! }
        testAssertNotNull(maybe, "Array must not be null", null)
        testAssertArrayEquals(intArrayOf(1, 2, 3, 4), maybe, "Test if Array matches", null)
    }

    describe("prevent mangled emoji") {
        val elements by memoized {
            arrayOf("\uD83C\uDF29", //lightningbolt
                    "\uD83D\uDD25", //:fire:
                    "\u2668", //:hotsprings:
                    "\uD83C\uDF0A", // :wave:
                    "\uD83D\uDC80", //starve
                    "\uD83C\uDF35", //cactus
                    "\u2BEF️", //fall
                    "\uD83D\uDCA8", //flyIntoWall
                    "\u2734", //*
                    "\uD83D\uDC7B", //???
                    "✨ ⚚", //magic
                    "\uD83D\uDD71", //wither
                    "\uD83D\uDC32", //dragonBreath
                    "\uD83C\uDF86", //fireworks

                    "\uD83D\uDC80", //mob
                    "\uD83D\uDDE1", //player
                    "\uD83C\uDFF9", //arrow
                    "彡°", //thrown
                    "\uD83C\uDF39", //thorns
                    "\uD83D\uDCA3 \uD83D\uDCA5", //explosion
                    "\uE120" //burger
            )
            val subject by memoized { JsonObject() }
            val serialized by memoized { subject.toJson() }
            val result by memoized { jankson.load(serialized) }
            testAssertEquals(subject, result, "Test empty json object ?")
        }
    }

    describe("recognize String Escaped") {
        val inputString = "The\nquick\tbrown\u000Cfox\bjumps\"over\\the\rlazy dog."
        val expected = "{ \"foo\": \"The\\nquick\\tbrown\\ffox\\bjumps\\\"over\\\\the\\rlazy dog.\" }"
        val subject by memoized {
            JsonObject().also {
                it["foo"] = JsonPrimitive(inputString)
            }
        }
        val actual by memoized { subject.toJson(false, false) }
        testAssertEquals(expected, actual, "Test handling String escapes", null)
    }

    /*
	@Test
	public void testBaseDeserialization() {
		try {
			JsonObject parsed = jankson.load("{x: 4, y: 4}");

			TestObject des = jankson.fromJson(parsed, TestObject.class);

			Assert.assertEquals(4, des.x);
			Assert.assertEquals("4", des.y);

		} catch (SyntaxError ex) {
			Assert.fail("Should not get a syntax error for a well-formed object: "+ex.getCompleteMessage());
		}
	}

	public static class TestContainer {
		TestObject object = new TestObject();
		private String foo = null;
	}

	@Test
	public void testNestedDeserialization() {
		try {
			JsonObject parsed = jankson.load("{object:{x: 4, y: 4}, foo:'bar'}");

			TestContainer des = jankson.fromJson(parsed, TestContainer.class);

			Assert.assertEquals(4, des.object.x);
			Assert.assertEquals("4", des.object.y);
			Assert.assertEquals("bar", des.foo);

		} catch (SyntaxError ex) {
			Assert.fail("Should not get a syntax error for a well-formed object: "+ex.getCompleteMessage());
		}
	}*/

    describe("negative numbers") {
        val subject = "{ 'foo': -1, 'bar': [ -1, -3 ] }"
        val parsed by memoized { jankson.load(subject) }
        testAssertEquals(java.lang.Integer.valueOf(-1), parsed.get(Int::class.java, "foo"), "Try Parsing '-1'", null)
        val array by memoized { parsed.get(IntArray::class.java, "bar")!! }
        testAssertArrayEquals(intArrayOf(-1, -3), array, "Test if Array of negative numbers matches")
    }

    describe("Avoid recursive get NPE") {
        val subject by memoized { JsonObject() }
        val value by memoized { subject.recursiveGetOrCreate(JsonArray::class.java, "some/random/path", JsonArray(), "This is a test") }
        testAssertEquals(JsonArray(), value, "value is not null", null)
    }

    describe("No inlined arrays") {
        val nested by memoized {
            JsonObject().also {
                it.put("foo", JsonPrimitive("foo"), "pitiable")
                it.put("bar", JsonPrimitive("bar"), "passable")
            }
        }
        val array by memoized {
            JsonArray().also {
                it += nested
            }
        }
        val subject by memoized {
            JsonObject().also {
                it["array"] = array
            }
        }
        val actual by memoized { subject.toJson(true, true, 0) }
        val expected by memoized { """
            {_
                "array": [_
                    {_
                        /* pitiable */_
                        "foo": "foo",
                        /* passable */_
                        "bar": "bar"
                    }
                ]
            }""".trimIndent()
                .replace('_', ' ')
                .replace("    ", "\t")
            // cause idea optimizes this away for me in src
        }

        testAssertEquals(expected, actual, "Assert noninlined arrays", null)
    }
})

inline fun Suite.test(description: String, crossinline body: TestBody.(String) -> Unit) =
        it(description) { body(description) }

fun <T> Suite.testAssertEquals(expected: T, actual: T, description: String, message: String? = description) =
        it(description) {
            assertEquals(expected, actual, message)
        }

fun <T> Suite.testAssertNotEquals(expected: T, actual: T, description: String, message: String? = description) =
        it(description) {
            assertNotEquals(expected, actual, message)
        }


fun <T> Suite.testAssertArrayEquals(expected: Array<T>, actual: Array<T>, description: String, message: String? = description) {
    it(description) {
        assertEquals(expected.count(), actual.count())
        expected.zip(actual).forEachIndexed { i, (expct, actl) ->
            assertEquals(expct, actl, "Element at index $i does not match")
        }
    }
}

fun Suite.testAssertArrayEquals(expected: IntArray, actual: IntArray, description: String, message: String? = description) {
    it(description) {
        assertEquals(expected.count(), actual.count())
        expected.zip(actual).forEachIndexed { i, (expct, actl) ->
            assertEquals(expct, actl, "Element at index $i does not match")
        }
    }
}

fun <T> Suite.testAssertSame(expected: T, actual: T, description: String, message: String? = description) =
        it(description) {
            assertSame(expected, actual, message)
        }

fun <T> Suite.testAssertNotSame(expected: T, actual: T, description: String, message: String? = description) =
        it(description) {
            assertNotSame(expected, actual, message)
        }

fun <T : Any> Suite.testAssertNotNull(actual: T?, description: String, message: String? = description) =
        it(description) {
            assertNotNull(actual, message)
        }

fun Suite.testAssertTrue(actual: Boolean, description: String, message: String? = description) = it(description) {
    assertTrue(actual, message)
}

fun <T : Any> Suite.testAssertNull(actual: T?, description: String, message: String? = description) = it(description) {
    assertNull(actual, message)
}

private class CommentedClass {
    @Comment("This is a comment.")
    private val foo = "what?"
}

private enum class ExampleEnum {
    ANT,
    BOX,
    CAT,
    DAY
};

class TestObject {
    private val x = 1
    private val y = "Hello"
}