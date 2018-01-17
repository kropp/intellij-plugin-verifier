package org.jetbrains.plugins.verifier.service.database

import com.jetbrains.pluginverifier.misc.createDir
import org.mapdb.DBMaker
import java.nio.file.Path

/**
 * Database implementation which uses the [MapDB library](https://github.com/jankotek/mapdb)
 * for storing the data.
 */
class MapDbServerDatabase(applicationHomeDir: Path) : ServerDatabase {
  private val serverDBFile = applicationHomeDir
      .resolve("database").createDir()
      .resolve("serverDB").toFile()

  private val serverDB = DBMaker
      .fileDB(serverDBFile)
      .checksumHeaderBypass()
      .closeOnJvmShutdown()
      .make()

  override fun <T> openOrCreateSet(setName: String, elementType: ValueType<T>): MutableSet<T> =
      serverDB
          .hashSet(setName, elementType.serializer)
          .createOrOpen()

  override fun <K, V> openOrCreateMap(mapName: String, keyType: ValueType<K>, valueType: ValueType<V>): MutableMap<K, V> =
      serverDB
          .hashMap(mapName, keyType.serializer, valueType.serializer)
          .createOrOpen()

  override fun close() = serverDB.close()
}
