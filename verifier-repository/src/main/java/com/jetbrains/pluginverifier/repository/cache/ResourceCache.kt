package com.jetbrains.pluginverifier.repository.cache

import com.jetbrains.pluginverifier.misc.closeOnException
import com.jetbrains.pluginverifier.repository.cleanup.SizeEvictionPolicy
import com.jetbrains.pluginverifier.repository.cleanup.SizeWeight
import com.jetbrains.pluginverifier.repository.provider.ResourceProvider
import com.jetbrains.pluginverifier.repository.resources.ResourceRepositoryImpl
import com.jetbrains.pluginverifier.repository.resources.ResourceRepositoryResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Resource cache is intended to cache any resources which
 * fetching may be expensive.
 *
 * Initially, the cache is empty.
 * The resources are fetched and cached on demand: if a resource
 * requested by a [key] [K] is not available in the cache,
 * the [resourceProvider] provides the corresponding resource. It works
 * concurrently, meaning that in case several threads request a resource
 * by the same key, only one of them will actually provide the resource,
 * while others will wait for the first to complete.
 *
 * The resources are [returned] [getResourceCacheEntry] wrapped in [ResourceCacheEntry]
 * that protect them from eviction from the cache while the resources
 * are used by requesting threads. Only once all the [cache entries] [ResourceCacheEntry]
 * of a resource by a specific [key] [K] get [closed] [ResourceCacheEntry.close],
 * the resource _may be_ [disposed] [disposer]. Note that it is not necessarily happens
 * immediately as the same resource may be requested once again shortly.
 *
 * todo: provide more guarantees on this. don't add the resource to cache if it leads to exceeding the size limit.
 * The cache is limited in size of [cacheSize], though it is possible
 * to exceed this limit if all the requested resources are locked.
 * While there are available "slots" in the cache, the resources are not disposed.
 * All the unreleased resources will be [disposed] [disposer] once the cache is [closed] [close].
 */
class ResourceCache<R, in K>(
    /**
     * The maximum number of resources held by this cache at a moment.
     *
     * The [cleanup] [SizeEvictionPolicy] procedure will be
     * carried out once the cache size reaches this value.
     */
    private val cacheSize: Long,
    /**
     * The resource [provider] [ResourceProvider] that
     * provides the requested resources by [keys] [K].
     */
    private val resourceProvider: ResourceProvider<K, R>,
    /**
     * The disposer used to close the resources.
     *
     * The resources are closed either when the [cleanup] [SizeEvictionPolicy] procedure
     * determines to evict the corresponding resources,
     * or when the resources are removed from the [resourceRepository].
     * On [close], all the resources are removed and closed.
     */
    private val disposer: (R) -> Unit,
    /**
     * The cache name that can be used for logging and debug purposes
     */
    private val presentableName: String
) : Closeable {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(ResourceCache::class.java)
  }

  /**
   * Resource [repository] [com.jetbrains.pluginverifier.repository.resources.ResourceRepository]
   * of the allocated [resources] [R].
   *
   * Initially, the repository is empty, meaning that there
   * are no resources opened.
   *
   * The repository is limited in size of [cacheSize].
   *
   * When the repository is full and a new resource is requested,
   * the unused resources are [disposed] [disposer].
   */
  private val resourceRepository = ResourceRepositoryImpl(
      SizeEvictionPolicy(cacheSize),
      Clock.systemUTC(),
      resourceProvider,
      initialWeight = SizeWeight(0),
      weigher = { SizeWeight(1) },
      disposer = disposer,
      presentableName = presentableName
  )

  /**
   * A flag indicating whether _this_ cache is already closed.
   * It is protected by the synchronized blocks.
   */
  private var isClosed = false

  /**
   * Enqueues for closing all resources.
   * The resources that have no locks registered at this
   * moment will be closed immediately, while the locked resources
   * will be closed once they become released by their holders.
   *
   * The resources being requested at the time of [close] invocation will
   * be released and closed at the [getResourceCacheEntry].
   * Thus, no new keys can appear after the [close] is invoked.
   */
  override fun close() {
    LOG.info("Closing the $presentableName")
    synchronized(this) {
      if (!isClosed) {
        isClosed = true
        resourceRepository.removeAll()
      }
    }
  }

  /**
   * Provides the [ResourceCacheEntry] that contains
   * the [resource] [ResourceCacheEntry.resource].
   *
   * Possible results of this method invocation
   * are represented as instances of the [ResourceCacheEntryResult].
   * If the [Found] [ResourceCacheEntryResult.Found] is returned,
   * the corresponding [ResourceCacheEntry] must be
   * [closed] [ResourceCacheEntry.close] after being used.
   */
  fun getResourceCacheEntry(key: K): ResourceCacheEntryResult<R> {
    /**
     * Cancel the fetching if _this_ resource cache is already closed.
     */
    synchronized(this) {
      if (isClosed) {
        throw InterruptedException()
      }
    }
    val startTime = Instant.now()
    val repositoryResult = resourceRepository.get(key)
    val lockedResource = with(repositoryResult) {
      when (this) {
        is ResourceRepositoryResult.Found -> lockedResource
        is ResourceRepositoryResult.Failed -> return ResourceCacheEntryResult.Failed(reason, error)
        is ResourceRepositoryResult.NotFound -> return ResourceCacheEntryResult.NotFound(reason)
      }
    }
    /**
     * If _this_ cache was closed after the [key]
     * had been requested, release the lock and register
     * the [key] for deletion: it will be either
     * removed immediately, or after the last holder releases
     * its lock for the same [key].
     */
    synchronized(this) {
      if (isClosed) {
        lockedResource.release()
        resourceRepository.remove(key)
        throw InterruptedException()
      }
      return lockedResource.closeOnException {
        val fetchTime = Duration.between(startTime, Instant.now())
        ResourceCacheEntryResult.Found(ResourceCacheEntry(lockedResource), fetchTime)
      }
    }
  }

}