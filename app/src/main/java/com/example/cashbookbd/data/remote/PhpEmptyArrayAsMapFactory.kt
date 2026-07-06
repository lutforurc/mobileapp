package com.example.cashbookbd.data.remote

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * PHP encodes an empty associative array as a JSON array `[]` instead of an
 * object `{}`. Laravel's `receivedDetails` (keyed by branch id) hits exactly
 * this case: it's an object when populated but `[]` when there are no rows.
 *
 * Without this, Gson throws "Expected BEGIN_OBJECT but was BEGIN_ARRAY" while
 * deserializing any [Map] field. This factory tolerates the quirk by mapping an
 * empty `[]` to an empty map and delegating everything else to the default
 * adapter.
 */
class PhpEmptyArrayAsMapFactory : TypeAdapterFactory {
    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!Map::class.java.isAssignableFrom(type.rawType)) return null

        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) = delegate.write(out, value)

            override fun read(reader: JsonReader): T? {
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    // Empty PHP array — consume it and hand back an empty map.
                    reader.beginArray()
                    reader.endArray()
                    @Suppress("UNCHECKED_CAST")
                    return LinkedHashMap<Any, Any>() as T
                }
                return delegate.read(reader)
            }
        }
    }
}
