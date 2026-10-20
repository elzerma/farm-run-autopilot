package com.farmrunautopilot.settings;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Settings saved by a newer version of the plugin still load: a dev build, or another computer that updated first,
 * saves patch types, places or teleports this version hasn't heard of. Gson reads each unknown map key as null and
 * fails the whole read on the second one ("duplicate key: null"), so every run setting went back to defaults (the
 * released plugin reading a dev build's allotments, flowers and hops, elzerma's client log, 2026-10-19). A map that
 * fails is read again entry by entry, keeping the entries this version can read. Unknown values anywhere else
 * already read as null, which {@link RunConfig#sanitise()} and {@link AccountSettings#sanitise()} drop.
 */
final class NewerSaves implements TypeAdapterFactory
{
	/** The plugin's Gson, reading what a newer version saved as far as it can. */
	static Gson tolerant(Gson gson)
	{
		return gson.newBuilder().registerTypeAdapterFactory(new NewerSaves()).create();
	}

	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
	{
		if (!Map.class.isAssignableFrom(type.getRawType()))
		{
			return null;
		}
		final TypeAdapter<T> map = gson.getDelegateAdapter(this, type);
		final TypeAdapter<JsonElement> tree = gson.getAdapter(JsonElement.class);
		return new TypeAdapter<T>()
		{
			@Override
			public void write(JsonWriter out, T value) throws IOException
			{
				map.write(out, value);
			}

			@Override
			public T read(JsonReader in) throws IOException
			{
				final JsonElement json = tree.read(in);
				try
				{
					return map.fromJsonTree(json);
				}
				catch (JsonParseException | NullPointerException e)
				{
					if (!json.isJsonObject())
					{
						throw e;
					}
					final JsonObject readable = new JsonObject();
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet())
					{
						if (readable(entry))
						{
							readable.add(entry.getKey(), entry.getValue());
						}
					}
					return map.fromJsonTree(readable);
				}
			}

			/** Whether this version reads the entry: its key isn't one it doesn't know, which reads as null. */
			private boolean readable(Map.Entry<String, JsonElement> entry)
			{
				final JsonObject alone = new JsonObject();
				alone.add(entry.getKey(), entry.getValue());
				try
				{
					final Map<?, ?> read = (Map<?, ?>) map.fromJsonTree(alone);
					return read != null && read.keySet().stream().noneMatch(Objects::isNull);
				}
				catch (JsonParseException | NullPointerException e)
				{
					return false;
				}
			}
		};
	}
}
