package com.mbox.stream.domain

/** Replaceable metadata provider. The UI only depends on this interface. */
interface CatalogRepository {
    val name: String
    suspend fun load(): Result<List<Title>>
}
